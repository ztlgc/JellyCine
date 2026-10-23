-- Run through the Supabase plugin. Fixtures roll back inside a subtransaction.
-- pg_temp is session-local; this does not install a public endpoint or retain users.
create or replace function pg_temp.verify_jellycine_watch_sync() returns jsonb
language plpgsql as $$
declare
  a uuid := gen_random_uuid(); b uuid := gen_random_uuid();
  s uuid := gen_random_uuid(); p uuid := gen_random_uuid(); d uuid := gen_random_uuid();
  sessions jsonb; spans jsonb; first_page jsonb; second_page jsonb;
  report jsonb := '{}'::jsonb;
begin
  begin
    insert into auth.users(id) values(a),(b);
    sessions := jsonb_build_array(jsonb_build_object('id',s,'scope_id',repeat('a',64),
      'item_id','verification-video','name','transaction fixture','opened_at_ms',1000,'device_id',d));
    spans := jsonb_build_array(jsonb_build_object('id',p,'session_id',s,'scope_id',repeat('a',64),
      'start_ms',1000,'end_ms',1500,'version',1));
    perform set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true);
    set local role authenticated;
    perform public.jellycine_watch_push(sessions,spans);
    perform public.jellycine_watch_push(sessions,spans);
    if (select count(*) from public.jellycine_watch_sessions)<>1
      or (select sum(end_ms-start_ms) from public.jellycine_watch_spans)<>500 then
      raise exception 'Duplicate upload changed totals';
    end if;
    report := report || '{"idempotent_replay":true}'::jsonb;
    spans := jsonb_set(jsonb_set(spans,'{0,end_ms}','2000'),'{0,version}','2');
    perform public.jellycine_watch_push('[]',spans);
    perform public.jellycine_watch_push('[]',jsonb_set(jsonb_set(spans,'{0,end_ms}','1500'),'{0,version}','1'));
    if (select end_ms from public.jellycine_watch_spans where id=p)<>2000 then
      raise exception 'Older version overwrote new data';
    end if;
    report := report || '{"monotonic_version":true}'::jsonb;
    first_page := public.jellycine_watch_pull(0,1);
    second_page := public.jellycine_watch_pull((first_page->>'next_cursor')::bigint,1);
    if not (first_page->>'has_more')::boolean or jsonb_array_length(first_page->'sessions')<>1
      or (second_page->>'has_more')::boolean or (second_page#>>'{spans,0,version}')::bigint<>2 then
      raise exception 'Cursor paging failed';
    end if;
    report := report || '{"incremental_paging":true}'::jsonb;
    begin
      update public.jellycine_watch_spans set owner_id=b,version=3 where id=p;
      raise exception 'Ownership change accepted';
    exception when insufficient_privilege then
      report := report || '{"ownership_change_denied":true}'::jsonb;
    end;
    perform set_config('request.jwt.claims',jsonb_build_object('sub',b,'role','authenticated')::text,true);
    if (select count(*) from public.jellycine_watch_sessions)<>0
      or (select count(*) from public.jellycine_watch_spans)<>0
      or jsonb_array_length(public.jellycine_watch_pull(0,400)->'sessions')<>0 then
      raise exception 'Account isolation failed';
    end if;
    report := report || '{"cross_account_read_denied":true}'::jsonb;
    begin
      insert into public.jellycine_watch_sessions(owner_id,id,scope_id,item_id,name,opened_at_ms,device_id)
        values(a,gen_random_uuid(),repeat('a',64),'forbidden','forbidden',1000,d);
      raise exception 'Cross-account insert accepted';
    exception when insufficient_privilege then
      report := report || '{"cross_account_write_denied":true}'::jsonb;
    end;
    reset role;
    set local role anon;
    begin
      perform public.jellycine_watch_pull(0,400);
      raise exception 'Anonymous access accepted';
    exception when insufficient_privilege then
      report := report || '{"anonymous_access_denied":true}'::jsonb;
    end;
    reset role;
    raise exception using errcode='JCT01',message='Rollback verification fixtures';
  exception when sqlstate 'JCT01' then
    return report || jsonb_build_object('fixture_users_retained',
      (select count(*) from auth.users where id in (a,b)));
  end;
end $$;
select pg_temp.verify_jellycine_watch_sync() as verification;
