"""Render a static mock of the in-app launcher icon picker for review."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SCALE = 1
W, H = 1080, 760
FONT = 'C:/Windows/Fonts/msyh.ttc'
BOLD = 'C:/Windows/Fonts/msyhbd.ttc'


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size * SCALE)


board = Image.new('RGB', (W, H), '#000000')
draw = ImageDraw.Draw(board)
draw.text((72, 40), '设置 → 界面 · 应用图标选择（界面示意）', font=font(21), fill='#8A8A92')
draw.rounded_rectangle((48, 96, W - 48, H - 72), radius=32, fill='#0E0E12', outline='#2C2C34', width=2)
draw.text((84, 140), '应用图标', font=font(28, True), fill='#FFFFFF')
draw.text((84, 190), '选择桌面和应用列表中的图标样式。', font=font(19), fill='#B8B8BE')

names = ['灵动水母', '胶片字母 J', '光影放映', '果冻播放', '私人影院']
files = ['01-jelly-1024.png', '02-film-j-1024.png', '03-projection-1024.png', '04-jelly-play-1024.png', '05-cinema-ticket-1024.png']
selected = 1
slot_width = (W - 168) / 5
icon_size = 104
for index, (name, filename) in enumerate(zip(names, files)):
    center = 84 + slot_width * (index + 0.5)
    left = round(center - icon_size / 2)
    top = 300
    icon = Image.open(HERE / filename).convert('RGBA').resize((icon_size * SCALE, icon_size * SCALE), Image.Resampling.LANCZOS)
    mask = Image.new('L', icon.size)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, icon.width - 1, icon.height - 1), radius=28 * SCALE, fill=255)
    icon.putalpha(mask)
    board.paste(icon, (left * SCALE, top * SCALE), icon)
    is_selected = index == selected
    draw.rounded_rectangle(
        (left * SCALE, top * SCALE, (left + icon_size) * SCALE, (top + icon_size) * SCALE),
        radius=28 * SCALE,
        outline='#6FA8FF' if is_selected else '#33333A',
        width=(4 if is_selected else 2) * SCALE,
    )
    label_width = draw.textlength(name, font=font(15))
    draw.text(((center * SCALE) - label_width / 2, 430 * SCALE), name,
              font=font(15), fill='#FFFFFF' if is_selected else '#9C9CA4')

draw.text((84, 580), '部分启动器需要几秒刷新；已固定的旧图标可能需要重新添加。', font=font(16), fill='#7C7C86')
draw.text((84, 626), '默认：02 胶片字母 J', font=font(16), fill='#6FA8FF')
board.save(HERE / 'app-icon-switcher-preview.png')
print('Rendered', HERE / 'app-icon-switcher-preview.png')
