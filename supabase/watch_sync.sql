-- Install with the Supabase plugin's apply_migration tool.
-- Mobile clients have no DELETE privilege. All write paths use the revision trigger.
create sequence public.jellycine_watch_revision_seq;

create table public.jellycine_watch_sessions (
  owner_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null,
  scope_id text not null check (scope_id ~ '^[0-9a-f]{64}$'),
  item_id text not null check (length(item_id) between 1 and 512),
  name text not null,
  opened_at_ms bigint not null check (opened_at_ms >= 0),
  device_id uuid not null,
  revision bigint not null,
  primary key (owner_id,id),
  unique(owner_id,scope_id,id)
);
create table public.jellycine_watch_spans (
  owner_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null,
  session_id uuid not null,
  scope_id text not null,
  start_ms bigint not null check (start_ms >= 0),
  end_ms bigint not null check (end_ms > start_ms),
  version bigint not null check (version > 0),
  revision bigint not null,
  primary key(owner_id,id),
  foreign key(owner_id,scope_id,session_id)
    references public.jellycine_watch_sessions(owner_id,scope_id,id)
);
create index jellycine_watch_sessions_cursor on public.jellycine_watch_sessions(owner_id,revision);
create index jellycine_watch_spans_cursor on public.jellycine_watch_spans(owner_id,revision);
create index jellycine_watch_spans_session on public.jellycine_watch_spans(owner_id,scope_id,session_id);

alter table public.jellycine_watch_sessions enable row level security;
alter table public.jellycine_watch_spans enable row level security;
revoke all on public.jellycine_watch_sessions,public.jellycine_watch_spans from anon,authenticated;
grant select,insert on public.jellycine_watch_sessions to authenticated;
grant select,insert,update on public.jellycine_watch_spans to authenticated;
grant usage on sequence public.jellycine_watch_revision_seq to authenticated;
create policy watch_sessions_read on public.jellycine_watch_sessions for select to authenticated
  using ((select auth.uid())=owner_id);
create policy watch_sessions_insert on public.jellycine_watch_sessions for insert to authenticated
  with check ((select auth.uid())=owner_id);
create policy watch_spans_read on public.jellycine_watch_spans for select to authenticated
  using ((select auth.uid())=owner_id);
create policy watch_spans_insert on public.jellycine_watch_spans for insert to authenticated
  with check ((select auth.uid())=owner_id);
create policy watch_spans_update on public.jellycine_watch_spans for update to authenticated
  using ((select auth.uid())=owner_id) with check ((select auth.uid())=owner_id);

-- Allocate revisions under an account-level transaction lock. The pull endpoint takes
-- the same lock, so a cursor can never skip a lower revision in an uncommitted write.
create function public.jellycine_watch_stamp() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
  if auth.uid() is null or auth.uid() <> new.owner_id then
    raise exception 'Authentication required' using errcode='42501';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(new.owner_id::text,17490));
  if tg_op='UPDATE' then
    if (new.owner_id,new.id,new.session_id,new.scope_id,new.start_ms)
      is distinct from (old.owner_id,old.id,old.session_id,old.scope_id,old.start_ms) then
      raise exception 'Watch span identity is immutable' using errcode='23514';
    end if;
    if new.version<=old.version then return null; end if;
    if new.end_ms<old.end_ms then
      raise exception 'Watch spans cannot shrink' using errcode='23514';
    end if;
  end if;
  new.revision := pg_catalog.nextval('public.jellycine_watch_revision_seq'::regclass);
  return new;
end $$;
revoke all on function public.jellycine_watch_stamp() from public,anon;
grant execute on function public.jellycine_watch_stamp() to authenticated;
create trigger watch_sessions_revision before insert on public.jellycine_watch_sessions
  for each row execute function public.jellycine_watch_stamp();
create trigger watch_spans_revision before insert or update on public.jellycine_watch_spans
  for each row execute function public.jellycine_watch_stamp();

create function public.jellycine_watch_push(p_sessions jsonb default '[]',p_spans jsonb default '[]')
returns void language plpgsql security invoker set search_path='' as $$
declare caller uuid := auth.uid();
begin
  if caller is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if jsonb_typeof(p_sessions)<>'array' or jsonb_typeof(p_spans)<>'array'
    or jsonb_array_length(p_sessions)>200 or jsonb_array_length(p_spans)>200 then
    raise exception 'Invalid sync batch' using errcode='22023';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(caller::text,17490));
  insert into public.jellycine_watch_sessions(owner_id,id,scope_id,item_id,name,opened_at_ms,device_id)
    select caller,s.id,s.scope_id,s.item_id,s.name,s.opened_at_ms,s.device_id
    from jsonb_to_recordset(p_sessions) as s(id uuid,scope_id text,item_id text,name text,opened_at_ms bigint,device_id uuid)
    on conflict(owner_id,id) do nothing;
  insert into public.jellycine_watch_spans(owner_id,id,session_id,scope_id,start_ms,end_ms,version)
    select caller,p.id,p.session_id,p.scope_id,p.start_ms,p.end_ms,p.version
    from jsonb_to_recordset(p_spans) as p(id uuid,session_id uuid,scope_id text,start_ms bigint,end_ms bigint,version bigint)
    on conflict(owner_id,id) do update set session_id=excluded.session_id,scope_id=excluded.scope_id,
      start_ms=excluded.start_ms,end_ms=excluded.end_ms,version=excluded.version
      where public.jellycine_watch_spans.version<excluded.version;
end $$;
revoke all on function public.jellycine_watch_push(jsonb,jsonb) from public,anon;
grant execute on function public.jellycine_watch_push(jsonb,jsonb) to authenticated;

create function public.jellycine_watch_pull(p_after bigint default 0,p_limit integer default 400)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare caller uuid := auth.uid(); result jsonb;
begin
  if caller is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_after<0 or p_limit<1 or p_limit>400 then raise exception 'Invalid sync page' using errcode='22023'; end if;
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(caller::text,17490));
  with changes as materialized (
    select * from (
      select 'session'::text as kind,revision,
        jsonb_build_object('id',id,'scope_id',scope_id,'item_id',item_id,'name',name,
          'opened_at_ms',opened_at_ms,'device_id',device_id) as payload
      from public.jellycine_watch_sessions where owner_id=caller and revision>p_after
      union all
      select 'span',revision,
        jsonb_build_object('id',id,'session_id',session_id,'scope_id',scope_id,'start_ms',start_ms,
          'end_ms',end_ms,'version',version)
      from public.jellycine_watch_spans where owner_id=caller and revision>p_after
    ) c order by revision limit p_limit+1
  ), page as (select * from changes order by revision limit p_limit)
  select jsonb_build_object(
    'sessions',coalesce(jsonb_agg(payload order by revision) filter(where kind='session'),'[]'::jsonb),
    'spans',coalesce(jsonb_agg(payload order by revision) filter(where kind='span'),'[]'::jsonb),
    'next_cursor',coalesce(max(revision),p_after),
    'has_more',(select count(*)>p_limit from changes)) into result from page;
  return result;
end $$;
revoke all on function public.jellycine_watch_pull(bigint,integer) from public,anon;
grant execute on function public.jellycine_watch_pull(bigint,integer) to authenticated;
