"""Export all five candidate icons as phone-only Android adaptive icon resources."""
from pathlib import Path
import math
import re
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
RES = REPO / 'phone/src/main/res'
NS = '{http://www.w3.org/2000/svg}'
AAPT = 'http://schemas.android.com/aapt'
SCALE = 0.82

# foreground indices exclude the background rect; mono groups are (indices, evenOdd).
ICONS = [
    dict(id='01', slug='01-jelly', name='灵动水母', bg='#182E62',
         fg=list(range(0, 7)), mono=[((0,), False), ((1,), False), ((2,), False), ((3,), False), ((4, 5), True)]),
    dict(id='02', slug='02-film-j', name='胶片字母 J', bg='#E9F0FF',
         fg=list(range(0, 5)), mono=[((i for i in range(0, 5)), True)]),
    dict(id='03', slug='03-projection', name='光影放映', bg='#DF503D',
         fg=list(range(0, 3)), mono=[((0,), False), ((1,), False), ((2,), False)]),
    dict(id='04', slug='04-jelly-play', name='果冻播放', bg='#123F3A',
         fg=list(range(0, 3)), mono=[((0, 1), True)]),
    dict(id='05', slug='05-cinema-ticket', name='私人影院', bg='#38233F',
         fg=list(range(0, 13)), mono=[((i for i in range(0, 13)), True)]),
]


def rectangle_path(element):
    x, y, w, h, r = [float(element.get(key, '0')) for key in ('x', 'y', 'width', 'height', 'rx')]
    return (f'M{x+r},{y} H{x+w-r} Q{x+w},{y} {x+w},{y+r} '
            f'V{y+h-r} Q{x+w},{y+h} {x+w-r},{y+h} H{x+r} '
            f'Q{x},{y+h} {x},{y+h-r} V{y+r} Q{x},{y} {x+r},{y} Z')


def geometry(element):
    tag = element.tag.removeprefix(NS)
    if tag == 'path':
        return element.attrib['d']
    if tag == 'rect':
        return rectangle_path(element)
    if tag == 'circle':
        x, y, r = [float(element.attrib[key]) for key in ('cx', 'cy', 'r')]
        return f'M{x-r},{y} A{r},{r} 0,1 0 {x+r},{y} A{r},{r} 0,1 0 {x-r},{y} Z'
    raise ValueError(tag)


def gradient_defs(root):
    gradients = {}
    for gradient in root.iter(NS + 'linearGradient'):
        stops = [node.attrib for node in gradient if node.tag == NS + 'stop']
        gradients['url(#' + gradient.attrib['id'] + ')'] = dict(
            start=stops[0]['stop-color'], end=stops[-1]['stop-color'],
            x1=gradient.attrib.get('x1', '0'), y1=gradient.attrib.get('y1', '0'),
            x2=gradient.attrib.get('x2', '512'), y2=gradient.attrib.get('y2', '512'))
    return gradients


def path_xml(element, gradients, solid=None, even_odd=False):
    data = geometry(element)
    fill_type = ' android:fillType="evenOdd"' if even_odd else ''
    if solid:
        return f'    <path android:fillColor="{solid}"{fill_type} android:pathData="{data}" />\n'
    fill = element.attrib.get('fill', '')
    stroke = element.attrib.get('stroke')
    if fill.startswith('url('):
        gradient = gradients[fill]
        return (f'    <path android:pathData="{data}"{fill_type}>\n'
                f'        <aapt:attr name="android:fillColor">\n'
                f'            <gradient android:type="linear" android:startX="{gradient["x1"]}" '
                f'android:startY="{gradient["y1"]}" android:endX="{gradient["x2"]}" '
                f'android:endY="{gradient["y2"]}" android:startColor="{gradient["start"]}" '
                f'android:endColor="{gradient["end"]}" />\n'
                f'        </aapt:attr>\n    </path>\n')
    if fill and fill != 'none':
        return f'    <path android:fillColor="{fill}"{fill_type} android:pathData="{data}" />\n'
    if stroke:
        return (f'    <path android:strokeColor="{stroke}" android:strokeWidth="{element.attrib["stroke-width"]}" '
                f'android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="@android:color/transparent" '
                f'android:pathData="{data}" />\n')
    raise ValueError('unsupported element')


def vector(body, size, scale=None):
    aapt = f'\n    xmlns:aapt="{AAPT}"' if 'aapt:attr' in body else ''
    group_open = '' if scale is None else f'    <group android:pivotX="256" android:pivotY="256" android:scaleX="{scale}" android:scaleY="{scale}">\n'
    group_close = '' if scale is None else '    </group>\n'
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{aapt}\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="512"\n    android:viewportHeight="512">\n'
            + group_open + body + group_close + '</vector>\n')


def write(folder, name, content):
    destination = RES / folder / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(content, encoding='utf-8')


def element_radius(element):
    tag = element.tag.removeprefix(NS)
    if tag == 'circle':
        x, y, r = [float(element.attrib[key]) for key in ('cx', 'cy', 'r')]
        return math.hypot(x - 256, y - 256) + r
    if tag == 'rect':
        x, y, w, h = [float(element.attrib[key]) for key in ('x', 'y', 'width', 'height')]
        return max(math.hypot(cx - 256, cy - 256) for cx in (x, x + w) for cy in (y, y + h))
    numbers = [float(value) for value in re.findall(r'-?\d+(?:\.\d+)?', geometry(element))]
    points = list(zip(numbers[0::2], numbers[1::2]))
    return max(math.hypot(x - 256, y - 256) for x, y in points)


for icon in ICONS:
    root = ET.parse(HERE / f'{icon["slug"]}.svg').getroot()
    elements = [node for node in root if node.tag in {NS+'rect', NS+'circle', NS+'path'}]
    background, foreground = elements[0], elements[1:]
    gradients = gradient_defs(root)
    number = icon['id']

    write('drawable', f'ic_app_icon_{number}.xml',
          vector(''.join(path_xml(node, gradients) for node in elements), 128))

    write('drawable', f'ic_launcher_{number}_background.xml', vector(
        f'    <path android:fillColor="{icon["bg"]}" android:pathData="M0,0 H512 V512 H0 Z" />\n', 108))

    write('drawable', f'ic_launcher_{number}_foreground.xml', vector(
        ''.join(path_xml(foreground[index], gradients) for index in icon['fg']), 108, SCALE))

    mono = []
    for indices, even_odd in icon['mono']:
        nodes = [foreground[index] for index in indices]
        if even_odd:
            compound = ' '.join(geometry(node) for node in nodes)
            mono.append(f'    <path android:fillColor="#FFFFFF" android:fillType="evenOdd" android:pathData="{compound}" />\n')
        else:
            for node in nodes:
                mono.append(path_xml(node, gradients, solid='#FFFFFF'))
    write('drawable', f'ic_launcher_{number}_monochrome.xml', vector(''.join(mono), 108, SCALE))

    write('mipmap-anydpi', f'ic_launcher_{number}.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_{number}_background" />
    <foreground android:drawable="@drawable/ic_launcher_{number}_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_{number}_monochrome" />
</adaptive-icon>
''')

    # Every foreground point must stay inside the central 66dp safe circle.
    radius = max(element_radius(foreground[index]) for index in icon['fg']) * SCALE * 108 / 512
    assert radius < 33, (number, radius)
    print(f'{number} {icon["name"]}: max radius {radius:.2f}dp')

# Default icon is 02 (Film J): application fallback, in-app logo and splash art.
write('drawable', 'jellycine_logo.xml', (RES / 'drawable/ic_app_icon_02.xml').read_text(encoding='utf-8'))
for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
    write('mipmap-anydpi', name, (RES / 'mipmap-anydpi/ic_launcher_02.xml').read_text(encoding='utf-8'))
for name in ('ic_splash_logo.xml', 'splash_icon.xml'):
    write('drawable', name, '''<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/jellycine_logo" />
</layer-list>
''')

for stale in ('ic_private_cinema_background.xml', 'ic_private_cinema_foreground.xml', 'ic_private_cinema_monochrome.xml'):
    (RES / 'drawable' / stale).unlink(missing_ok=True)
print('Exported 5 adaptive icon sets, previews, default 02 assets and splash art.')
