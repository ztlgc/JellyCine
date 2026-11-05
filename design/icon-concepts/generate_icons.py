"""Generate five original JellyCine icon concepts, editable SVGs and PNG previews.
Requires Pillow. Run from the repository root with python -I.
"""
from pathlib import Path
from html import escape
import math
import re
from PIL import Image, ImageDraw, ImageFont, ImageColor

ROOT = Path(__file__).resolve().parent
SCALE = 2
SIZE = 512


def rgb(color):
    return ImageColor.getrgb(color)


def path_points(data):
    tokens = re.findall(r'[MLCZ]|-?\d+(?:\.\d+)?', data)
    i, current, start, points = 0, (0, 0), (0, 0), []
    while i < len(tokens):
        command = tokens[i]
        i += 1
        if command in ('M', 'L'):
            current = (float(tokens[i]), float(tokens[i + 1]))
            i += 2
            if command == 'M':
                start = current
            points.append(current)
        elif command == 'C':
            values = list(map(float, tokens[i:i + 6]))
            i += 6
            p0, p1, p2, p3 = current, values[:2], values[2:4], values[4:]
            for step in range(1, 49):
                t = step / 48
                u = 1 - t
                points.append(tuple(u**3*p0[k] + 3*u*u*t*p1[k] + 3*u*t*t*p2[k] + t**3*p3[k] for k in (0, 1)))
            current = tuple(p3)
        elif command == 'Z':
            points.append(start)
            current = start
    return [(round(x * SCALE), round(y * SCALE)) for x, y in points]


class Icon:
    def __init__(self, slug, title, background):
        self.slug, self.title = slug, title
        self.image = Image.new('RGBA', (SIZE*SCALE, SIZE*SCALE))
        self.defs, self.elements = [], []
        self.rect(0, 0, 512, 512, 112, background)

    def paint(self, mask, fill):
        if isinstance(fill, tuple):
            layer = Image.new('RGBA', self.image.size)
            draw = ImageDraw.Draw(layer)
            a, b = rgb(fill[0]), rgb(fill[1])
            for y in range(layer.height):
                t = y / (layer.height - 1)
                draw.line((0, y, layer.width, y), fill=tuple(round(x*(1-t)+z*t) for x,z in zip(a,b)) + (255,))
            identifier = 'g' + str(len(self.defs))
            self.defs.append(f'<linearGradient id="{identifier}" x1="0" y1="0" x2="0" y2="512" gradientUnits="userSpaceOnUse"><stop stop-color="{fill[0]}"/><stop offset="1" stop-color="{fill[1]}"/></linearGradient>')
            svg_fill = f'url(#{identifier})'
        else:
            layer = Image.new('RGBA', self.image.size, fill)
            svg_fill = fill
        self.image.paste(layer, (0, 0), mask)
        return svg_fill

    def rect(self, x, y, width, height, radius, fill):
        mask = Image.new('L', self.image.size)
        ImageDraw.Draw(mask).rounded_rectangle(tuple(round(v*SCALE) for v in (x,y,x+width,y+height)), radius=radius*SCALE, fill=255)
        color = self.paint(mask, fill)
        self.elements.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="{radius}" fill="{color}"/>')

    def circle(self, x, y, radius, fill):
        mask = Image.new('L', self.image.size)
        ImageDraw.Draw(mask).ellipse(tuple(round(v*SCALE) for v in (x-radius,y-radius,x+radius,y+radius)), fill=255)
        color = self.paint(mask, fill)
        self.elements.append(f'<circle cx="{x}" cy="{y}" r="{radius}" fill="{color}"/>')

    def path(self, data, fill=None, stroke=None, width=1):
        mask = Image.new('L', self.image.size)
        draw = ImageDraw.Draw(mask)
        points = path_points(data)
        if fill:
            draw.polygon(points, fill=255)
        else:
            draw.line(points, fill=255, width=width*SCALE, joint='curve')
            r = width*SCALE/2
            for x,y in [points[0], points[-1]]:
                draw.ellipse((x-r,y-r,x+r,y+r), fill=255)
        color = self.paint(mask, fill or stroke)
        attr = f'fill="{color}"' if fill else f'fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"'
        self.elements.append(f'<path d="{data}" {attr}/>')

    def save(self):
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512" role="img" aria-label="{escape(self.title)}"><title>{escape(self.title)}</title><defs>{"".join(self.defs)}</defs>{"".join(self.elements)}</svg>'
        (ROOT / f'{self.slug}.svg').write_text(svg, encoding='utf-8')
        self.image.save(ROOT / f'{self.slug}-1024.png')
        return self


icons = []
a = Icon('01-jelly', '灵动水母', ('#182E62', '#101C3C'))
a.path('M 184 285 C 184 317 158 329 174 360', stroke='#54BDF2', width=25)
a.path('M 230 290 C 230 327 209 345 229 382', stroke='#81DBF5', width=25)
a.path('M 278 290 C 278 328 301 341 281 378', stroke='#81DBF5', width=25)
a.path('M 326 285 C 326 316 350 334 334 357', stroke='#54BDF2', width=25)
a.path('M 130 263 C 130 184 178 125 256 125 C 334 125 382 184 382 263 C 382 283 368 294 349 294 L 163 294 C 144 294 130 283 130 263 Z', fill=('#BBF5FF','#39A9E8'))
a.path('M 235 183 C 230 180 225 183 225 190 L 225 250 C 225 257 230 260 235 257 L 285 226 C 291 222 291 218 285 214 Z', fill='#173163')
a.path('M 164 229 C 170 193 187 175 209 166', stroke='#E0FBFF', width=10)
icons.append(a.save())

b = Icon('02-film-j', '胶片字母 J', '#E9F0FF')
b.path('M 174 128 L 343 128 L 343 291 C 343 357 310 388 251 388 C 194 388 159 356 157 306 L 219 306 C 221 325 230 335 250 335 C 270 335 281 322 281 298 L 281 185 L 174 185 Z', fill='#2256D8')
for x in (190, 233, 276):
    b.rect(x, 143, 25, 15, 4, '#E9F0FF')
b.path('M 296 216 L 325 235 L 296 254 Z', fill='#E9F0FF')
icons.append(b.save())

c = Icon('03-projection', '光影放映', '#DF503D')
c.path('M 321 158 C 299 139 274 130 246 130 C 174 130 121 184 121 256 C 121 328 174 382 246 382 C 274 382 299 373 321 354 L 284 309 C 273 319 261 323 246 323 C 208 323 181 294 181 256 C 181 218 208 189 246 189 C 261 189 273 193 284 203 Z', fill='#FFF1D6')
c.path('M 245 226 C 239 223 234 226 234 233 L 234 279 C 234 286 239 289 245 286 L 291 261 C 297 258 297 254 291 251 Z', fill='#FFF1D6')
c.path('M 316 230 L 389 192 L 389 320 L 316 282 Z', fill='#FFB79C')
icons.append(c.save())

d = Icon('04-jelly-play', '果冻播放', '#123F3A')
d.path('M 175 120 C 145 105 120 127 123 161 L 138 347 C 140 382 165 402 196 386 L 373 294 C 407 277 410 246 376 226 Z', fill=('#E2FFAC','#5ED7A8'))
d.path('M 222 210 C 216 207 210 211 211 219 L 216 298 C 217 306 222 310 229 306 L 298 268 C 305 264 305 258 298 254 Z', fill='#123F3A')
d.path('M 153 177 L 163 320', stroke='#E8FFD1', width=12)
icons.append(d.save())

e = Icon('05-cinema-ticket', '私人影院', '#38233F')
e.path('M 129 151 L 383 151 C 394 151 402 159 402 170 L 402 224 C 383 224 371 237 371 256 C 371 275 383 288 402 288 L 402 342 C 402 353 394 361 383 361 L 129 361 C 118 361 110 353 110 342 L 110 288 C 129 288 141 275 141 256 C 141 237 129 224 110 224 L 110 170 C 110 159 118 151 129 151 Z', fill='#F0CE88')
e.circle(256, 256, 64, '#38233F')
e.path('M 244 220 C 239 217 236 220 236 225 L 236 287 C 236 292 239 295 244 292 L 289 261 C 294 258 294 254 289 251 Z', fill='#F0CE88')
for x in (164, 339):
    for y in (181, 215, 249, 283, 317):
        e.rect(x, y, 9, 16, 4, '#98744E')
icons.append(e.save())

font_path = 'C:/Windows/Fonts/msyh.ttc'
bold_path = 'C:/Windows/Fonts/msyhbd.ttc'

def font(size, bold=False):
    return ImageFont.truetype(bold_path if bold else font_path, size)

sheet = Image.new('RGB', (1800, 1060), '#F4F6FA')
draw = ImageDraw.Draw(sheet)
draw.text((74, 58), 'JellyCine', font=font(44, True), fill='#19253B')
draw.text((75, 122), '五种图标方向，选择你的私人影院标识', font=font(24), fill='#53617A')
draw.text((1454, 77), 'Android 应用图标提案', font=font(19), fill='#53617A')
draw.line((75, 184, 1725, 184), fill='#D7DFEA', width=2)
notes = [
    ('保留水母记忆，轮廓更干净', '深海蓝 / 冰晶青'),
    ('字母与胶片，简洁而鲜明', '雾白 / 钴蓝'),
    ('字母 C 化为放映中的光', '朱红 / 奶油白'),
    ('柔软的果冻，直接的播放感', '深松绿 / 青柠'),
    ('一张电影票，开启私人影院', '葡萄紫 / 香槟金'),
]
for i, icon in enumerate(icons):
    x = 75 + i*336
    draw.text((x, 222), f'{i+1:02d}', font=font(23, True), fill='#77859A')
    large = icon.image.resize((264,264), Image.Resampling.LANCZOS)
    sheet.paste(large, (x, 275), large)
    draw.text((x, 570), icon.title, font=font(29, True), fill='#19253B')
    draw.text((x, 624), notes[i][0], font=font(19), fill='#53617A')
    draw.text((x, 657), notes[i][1], font=font(17), fill='#77859A')
    draw.line((x, 715, x+264, 715), fill='#D7DFEA', width=2)
    draw.text((x, 738), '桌面缩略效果', font=font(16), fill='#53617A')
    for offset, size in [(0,64),(98,48),(180,32)]:
        thumb = icon.image.resize((size,size), Image.Resampling.LANCZOS)
        sheet.paste(thumb, (x+offset, 786+(64-size)//2), thumb)
        draw.text((x+offset, 867), f'{size}px', font=font(14), fill='#77859A')
    # Circular crop approximates the Android circular launcher mask.
    thumb = icon.image.resize((56,56), Image.Resampling.LANCZOS)
    mask = Image.new('L', (112,112))
    ImageDraw.Draw(mask).ellipse((0,0,111,111), fill=255)
    thumb.putalpha(mask.resize((56,56), Image.Resampling.LANCZOS))
    sheet.paste(thumb, (x, 918), thumb)
    draw.text((x+72, 933), '圆形裁切预览', font=font(16), fill='#53617A')
sheet.save(ROOT / 'JellyCine-five-icon-concepts.png')

cards = []
for i, icon in enumerate(icons):
    cards.append(f'<article><span class="number">0{i+1}</span><img class="hero" src="{icon.slug}.svg" alt="{icon.title}图标"><h2>{icon.title}</h2><p>{notes[i][0]}</p><p class="palette">{notes[i][1]}</p><div class="sizes"><img src="{icon.slug}.svg" width="64" height="64" alt="64像素"><img src="{icon.slug}.svg" width="48" height="48" alt="48像素"><img src="{icon.slug}.svg" width="32" height="32" alt="32像素"><img class="round" src="{icon.slug}.svg" width="56" height="56" alt="圆形裁切"></div><a href="{icon.slug}.svg" download>下载 SVG</a><a href="{icon.slug}-1024.png" download>下载 PNG</a></article>')
(ROOT/'index.html').write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>JellyCine · 五款图标提案</title><style>*{box-sizing:border-box}body{margin:0;padding:48px;background:#f4f6fa;color:#19253b;font-family:"Microsoft YaHei",sans-serif}header{max-width:1700px;margin:auto auto 42px;border-bottom:1px solid #d7dfea;padding-bottom:28px}h1{font-size:38px;margin:0 0 12px}header p{margin:0;color:#53617a;font-size:20px}main{max-width:1700px;margin:auto;display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:36px}.number{display:block;color:#77859a;font-size:20px;margin-bottom:24px}.hero{width:100%;max-width:270px;display:block}h2{font-size:25px;margin:26px 0 14px}p{color:#53617a;line-height:1.7;font-size:15px}.palette{color:#77859a}.sizes{min-height:100px;display:flex;gap:16px;align-items:center;margin:24px 0 16px;border-top:1px solid #d7dfea}.round{border-radius:50%}a{display:inline-block;color:#2256d8;padding:10px 12px 10px 0;font-size:14px;text-underline-offset:4px}a:focus-visible{outline:3px solid #2256d8;outline-offset:4px}footer{max-width:1700px;margin:48px auto 0;color:#53617a;font-size:14px}@media(max-width:1200px){main{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:760px){body{padding:28px 22px}main{grid-template-columns:repeat(2,minmax(0,1fr));gap:32px 20px}.sizes{flex-wrap:wrap;padding-top:18px;gap:12px}h2{font-size:21px}}@media(max-width:400px){main{grid-template-columns:1fr}.hero{max-width:256px}}</style><header><h1>JellyCine</h1><p>五种图标方向，选择你的私人影院标识</p></header><main>''' + ''.join(cards) + '''</main><footer>候选设计稿。包含大图、小尺寸和圆形裁切预览；选定后再制作 Android 自适应图标与单色主题图标。</footer></html>''', encoding='utf-8')
(ROOT/'README.md').write_text('''# JellyCine 图标候选稿

五款原创矢量设计，用于选择 Android 手机端应用图标方向，尚未替换应用资源。

| 编号 | 名称 | 构思 |
| --- | --- | --- |
| 01 | 灵动水母 | 将原有水母与电影概念简化为水母轮廓和播放负形 |
| 02 | 胶片字母 J | 以 J 为主轮廓，顶部胶片孔建立电影关联 |
| 03 | 光影放映 | 字母 C、播放键与投影光束组合 |
| 04 | 果冻播放 | 不规则柔软三角轮廓，呼应 Jelly 与播放 |
| 05 | 私人影院 | 金色电影票与播放符号，强调私人影院氛围 |

打开 `JellyCine-five-icon-concepts.png` 查看对比图，或打开 `index.html` 查看响应式展示。每款提供 512 viewBox 的 SVG 和 1024×1024 RGBA PNG。SVG 为可编辑源文件；PNG 通过 Pillow 超采样渲染。

对比图展示 64px、48px、32px 缩略图和圆形裁切示意。这些是候选稿，并非已经完成设备验收的 Android adaptive-icon 资源。选定后需导出前景、背景、单色层，并核对安全区域与真实启动器效果。

重新生成：`python -I design/icon-concepts/generate_icons.py`（需要 Pillow 和 Windows 微软雅黑字体）。
''', encoding='utf-8')
print(f'Generated {len(icons)} SVGs, {len(icons)} 1024px PNGs, comparison sheet and HTML in {ROOT}')
