#!/usr/bin/env python3
"""Edit a successful real-app screen recording into a Korean, 1080p demo.

Uses the FFmpeg runtime already bundled with Android Studio. Title cards and
caption overlays are original SVG, rendered locally with librsvg. There is no
replacement app UI, fabricated telemetry, music, or synthesized narration.
"""
import argparse
import ctypes as C
from html import escape
import json
import os
from pathlib import Path
import subprocess
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--recording', required=True)
parser.add_argument('--output', default=str(ROOT / 'artifacts/video'))
args = parser.parse_args()
source = Path(args.recording).resolve()
out = Path(args.output).resolve()
work = ROOT / '.tools/video-edits' / out.name
assets = work / 'assets'
segments_dir = work / 'segments'
out.mkdir(parents=True, exist_ok=True)
assets.mkdir(parents=True, exist_ok=True)
segments_dir.mkdir(parents=True, exist_ok=True)
meta = json.loads((source / 'scenes.json').read_text())
if meta.get('result') != 'PASS':
    raise RuntimeError('Only successful, complete demo recordings may be used.')
raw = source / 'drive-mood-raw.mp4'
tools_dir = ROOT / '.tools/video-tools'
if not (tools_dir / 'ffmpeg').exists():
    jars = sorted((ROOT / '.tools/android-studio/plugins/android/lib').glob('ffmpeg-*-linux-x86_64.jar'))
    if not jars:
        raise RuntimeError('Android Studio FFmpeg runtime not found.')
    tools_dir.mkdir(parents=True, exist_ok=True)
    with ZipFile(jars[-1]) as bundle:
        for name in bundle.namelist():
            if name.startswith('org/bytedeco/ffmpeg/linux-x86_64/') and not name.endswith('/'):
                target = tools_dir / Path(name).name
                target.write_bytes(bundle.read(name))
                target.chmod(0o755)
env = os.environ.copy()
env['LD_LIBRARY_PATH'] = str(tools_dir) + (':' + env['LD_LIBRARY_PATH'] if env.get('LD_LIBRARY_PATH') else '')
ffmpeg = str(tools_dir / 'ffmpeg')
ffprobe = str(tools_dir / 'ffprobe')
W, H, FPS = 1920, 1080, 30
BG, INK, MUTED, GREEN = '#F6F3EB', '#2E3F3C', '#58665E', '#32645E'


class SvgRenderer:
    def __init__(self):
        self.rsvg = C.CDLL('librsvg-2.so.2')
        self.cairo = C.CDLL('libcairo.so.2')
        self.gobject = C.CDLL('libgobject-2.0.so.0')
        self.rsvg.rsvg_handle_new_from_data.argtypes = [C.c_char_p, C.c_size_t, C.c_void_p]
        self.rsvg.rsvg_handle_new_from_data.restype = C.c_void_p
        self.rsvg.rsvg_handle_render_cairo.argtypes = [C.c_void_p, C.c_void_p]
        self.rsvg.rsvg_handle_render_cairo.restype = C.c_int
        self.cairo.cairo_image_surface_create.argtypes = [C.c_int, C.c_int, C.c_int]
        self.cairo.cairo_image_surface_create.restype = C.c_void_p
        self.cairo.cairo_create.argtypes = [C.c_void_p]
        self.cairo.cairo_create.restype = C.c_void_p
        self.cairo.cairo_surface_write_to_png.argtypes = [C.c_void_p, C.c_char_p]
        self.cairo.cairo_surface_write_to_png.restype = C.c_int
        self.cairo.cairo_destroy.argtypes = [C.c_void_p]
        self.cairo.cairo_surface_destroy.argtypes = [C.c_void_p]
        self.gobject.g_object_unref.argtypes = [C.c_void_p]

    def render(self, name, content):
        svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080">'
               '<style>text{font-family:"Noto Sans CJK KR",sans-serif}</style>' + content + '</svg>')
        (assets / (name + '.svg')).write_text(svg)
        data = svg.encode()
        handle = self.rsvg.rsvg_handle_new_from_data(data, len(data), None)
        if not handle:
            raise RuntimeError('Invalid SVG: ' + name)
        surface = self.cairo.cairo_image_surface_create(0, W, H)
        context = self.cairo.cairo_create(surface)
        try:
            if not self.rsvg.rsvg_handle_render_cairo(handle, context):
                raise RuntimeError('Could not render SVG: ' + name)
            path = assets / (name + '.png')
            if self.cairo.cairo_surface_write_to_png(surface, str(path).encode()) != 0:
                raise RuntimeError('Could not save SVG render: ' + name)
        finally:
            self.cairo.cairo_destroy(context)
            self.cairo.cairo_surface_destroy(surface)
            self.gobject.g_object_unref(handle)
        return path


renderer = SvgRenderer()


def rect(x, y, width, height, color, radius=0, stroke=None):
    return f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="{radius}" fill="{color}"' + (f' stroke="{stroke}" stroke-width="2"' if stroke else '') + '/>'


def text(x, y, value, size=28, color=INK, weight=400, anchor='start'):
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-weight="{weight}" text-anchor="{anchor}">{escape(value)}</text>'


def lines(x, y, value, size=28, leading=44, color=INK, weight=400):
    return ''.join(text(x, y + index * leading, line, size, color, weight) for index, line in enumerate(value.split('\n')))


def landscape():
    return ('<g transform="translate(1180 200)"><defs><clipPath id="land"><rect width="620" height="650" rx="48"/></clipPath></defs>'
            '<g clip-path="url(#land)">' + rect(0, 0, 620, 650, '#E0EDF3') +
            '<circle cx="470" cy="150" r="66" fill="#EDB984"/>' +
            '<path d="M0 350Q150 210 310 350Q460 230 620 320V650H0Z" fill="#CADAC0"/>' +
            '<path d="M0 450Q160 290 340 430Q500 330 620 390V650H0Z" fill="#9CBAA9"/>' +
            '<path d="M0 590Q180 470 350 570Q500 470 620 510V650H0Z" fill="#6C9586"/>' +
            '<path d="M85 650C110 540 460 530 420 440S270 370 355 300L380 300C330 365 500 375 500 450S320 600 300 650Z" fill="#FFF9E9"/>' +
            '</g></g>')


def title_card(end=False):
    body = rect(0, 0, W, H, BG)
    body += text(112, 100, 'PLEOS CONNECT APP PROTOTYPE', 23, GREEN, 600)
    body += text(112, 270, 'Drive Mood', 100, INK, 700)
    if not end:
        body += lines(112, 400, '차량의 움직임을 읽고,\n주차 후 짧은 쉼을 건넵니다.', 54, 82, INK, 600)
        body += lines(112, 650, '조향·브레이크 리듬  /  상황에 맞는 문장\n30초 숨 고르기  /  여정 리포트', 28, 50, MUTED)
        body += text(112, 920, '실제 앱 녹화 · 일부 대기 구간 편집 · 호흡 과정 정상 속도', 23, MUTED)
    else:
        body += lines(112, 405, '주행 중엔 간결하게,\n주차 후엔 여유롭게.', 58, 88, INK, 600)
        body += lines(112, 660, 'Vehicle SDK · NaviHelper SDK 연동 프로토타입\n본 영상은 Pleos 에뮬레이터의 앱 내 시연 입력입니다.', 26, 48, MUTED)
        body += text(112, 920, 'DRIVE MOOD' + (' ' + meta['app_version'] if meta.get('app_version') else ''), 22, GREEN, 600)
    return body + landscape()


crop = [int(value) for value in next(scene['crop'] for scene in meta['scenes'] if scene['scene'] == 'ready').split(',')]
cx, cy, cw, ch = crop
# Normalize to chroma boundaries, trimming at most one edge pixel.
cx = (cx + 1) // 2 * 2
cy = (cy + 1) // 2 * 2
cw = cw // 2 * 2 - 2
ch = ch // 2 * 2
if not (cw > 1000 and ch > 800):
    raise RuntimeError('Invalid app-window crop: ' + str(crop))
vh = 884
vw = round(cw / ch * vh / 2) * 2
vx, vy = 650, 70
if vx + vw > W - 20:
    raise RuntimeError('App window does not fit the video composition.')


def overlay(kicker, title, body, caption):
    content = text(64, 76, 'Drive Mood', 36, GREEN, 700)
    content += text(64, 240, kicker, 24, '#98502F', 600)
    content += lines(64, 334, title, 58, 84, INK, 700)
    content += lines(64, 560, body, 28, 48, MUTED)
    content += rect(64, 844, 96, 4, GREEN)
    content += text(64, 902, 'PLEOS CONNECT', 20, GREEN, 600)
    content += text(64, 938, '앱 내 시연 데이터', 22, MUTED)
    content += text(vx, 43, '실제 앱 화면 · Pleos 에뮬레이터', 21, MUTED)
    content += rect(vx - 2, vy - 2, vw + 4, vh + 4, 'none', 0, '#CCD5CB')
    content += rect(0, 984, W, 96, '#203D37')
    content += text(W / 2, 1044, caption, 31, '#FFFFFF', 500, 'middle')
    return content


scenes = {item['scene']: (int(item['at_ms']) - meta['capture_start_ms']) / 1000 for item in meta['scenes']}
clips = []


def add(scene, next_scene, maximum, kicker, title, body, caption):
    start = scenes[scene] + .25
    duration = min(maximum, scenes[next_scene] - start - .15)
    if duration < .5:
        raise RuntimeError('Scene too short to use honestly: ' + scene)
    clips.append(dict(name=scene, source_start=start, duration=duration, kicker=kicker, title=title, body=body, caption=caption, playback_rate=1))


add('home', 'chooser', 6.5, '01 / 출발 전', '나의 속도로,\n오늘의 길을', '상황에 맞는 인사\n이번·직전 여정의 리듬 요약', '차량의 움직임을 리듬으로 읽는 인카 동반자, Drive Mood입니다.')
add('chooser', 'steady', 1.5, '01 / 시연 시작', '주차한 상태에서\n여정 시작', '현재 앱의 시연 기능으로\n핵심 흐름을 재현합니다.', '주차 상태에서 앱의 여정 시연을 시작합니다.')
add('steady', 'changed', 8, '02 / 운전 리듬', '작은 움직임을\n리듬으로', '조향·브레이크 패턴\n잔잔하게 움직이는 리듬 그림', '큰 상태와 잔잔한 리듬 그림을 함께 표시합니다. 그림의 움직임은 센서 측정값이 아닙니다.')
add('changed', 'parked', 8, '03 / 변화 관찰', '변화가 이어지면\n짧은 제안', '관찰된 신호의 변화\n안전하게 주차한 뒤 쉬어가기', '조향 변화가 이어지면 주차 후 잠깐 쉬어가도록 제안합니다.')
add('parked', 'breathing', 3.2, '04 / 주차 후', '차를 세운 뒤,\n나를 위한 30초', '주차가 확인되면\n숨 고르기를 시작할 수 있어요.', '호흡 가이드는 주차 상태에서만 시작할 수 있습니다.')
breath_start = scenes['breathing'] - .15
breath_duration = scenes['complete'] - breath_start + .35
for index, (offset, length, caption) in enumerate([
    (0, 9.5, '3초 들숨과 3초 날숨을 다섯 번, 편안한 만큼 호흡합니다.'),
    (9.5, 10, '실선은 관찰된 시연 조향, 점선은 호흡 가이드입니다.'),
    (19.5, breath_duration - 19.5, '실내 온도와 빛은 제안이며, 차량 설정을 자동 변경하지 않습니다.'),
]):
    clips.append(dict(name=f'breathing-{index + 1}', source_start=breath_start + offset, duration=length,
                      kicker='05 / 30초 숨 고르기', title='들이쉬고,\n편안하게 내쉬고',
                      body='30초 호흡 과정 연속 시연\n현재 실내와 제안 상태 비교', caption=caption, playback_rate=1))
add('complete', 'aftercare', 2.6, '05 / 숨 고르기 완료', '짧은 쉼을\n마쳤어요', '완료한 호흡을 기록하고\n다음 여정을 준비해요.', '짧은 쉼을 마치고, 다음 여정을 준비합니다.')
add('aftercare', 'nextdrive', 3, '06 / 여정 계속', '여정은 계속,\n나의 속도로', '주차 중 여정 요약\n분석된 구간을 명확히 표시', '현재 여정의 기록과 분석한 구간을 확인합니다.')
add('nextdrive', 'report', 8, '06 / 새로운 관찰', '다음 주행을\n새롭게 관찰', '새 차량 신호로 리듬 갱신\n호흡 자체로 점수를 바꾸지 않아요.', '다음 주행의 새 신호로 리듬을 다시 살펴봅니다.')
add('report', 'details', 7, '07 / 도착 리포트', '오늘의 길을\n한눈에', '주행 시간 · 리듬 변화\n분석 범위 · 숨 고르기 기록', '여정을 마치면 관찰한 구간과 함께 리듬을 남깁니다.')
add('details', 'history', 4, '07 / 기록의 근거', '관찰한 만큼,\n분명한 요약', '분석 구간과 주행 시간 구분\n여정의 리듬 변화 확인', '분석한 구간과 주행 시간을 함께 보여줍니다.')
add('history', 'end', 6, '08 / 지난 여정', '지난 여정도,\n다시 살펴봐요', '기기 안에 남기는 여정 기록\n시연 기록은 별도로 구분', '지난 여정을 다시 확인하고, 시연 기록은 따로 구분합니다.')

encode = ['-c:v', 'libopenh264', '-b:v', '5M', '-pix_fmt', 'yuv420p', '-r', str(FPS), '-g', '60', '-threads', '4']
playlist = []
subtitles = []
timeline = []
cursor = 0.0


def execute(command, name):
    with (segments_dir / (name + '.log')).open('w') as log:
        subprocess.run([ffmpeg, '-hide_banner', '-y', *command], env=env, stdout=log, stderr=subprocess.STDOUT, check=True)


def stamp(seconds):
    milliseconds = round(seconds * 1000)
    return f'{milliseconds // 3600000:02}:{milliseconds // 60000 % 60:02}:{milliseconds // 1000 % 60:02},{milliseconds % 1000:03}'


def card(name, end=False):
    global cursor
    duration = 6
    image = renderer.render(name, title_card(end))
    target = segments_dir / (name + '.mp4')
    print('render', name, duration, flush=True)
    execute(['-loop', '1', '-framerate', str(FPS), '-i', str(image), '-t', str(duration), '-an', *encode, str(target)], name)
    playlist.append(target)
    timeline.append(dict(name=name, output_start=cursor, duration=duration, original_title_card=True))
    cursor += duration


card('intro')
for index, clip in enumerate(clips):
    name = f'{index + 1:02}-{clip["name"]}'
    # Use frame-exact lengths to avoid accumulating muxer rounding at joins.
    duration = max(1, round(clip['duration'] * FPS)) / FPS
    image = renderer.render(name, overlay(clip['kicker'], clip['title'], clip['body'], clip['caption']))
    target = segments_dir / (name + '.mp4')
    chain = (f'[0:v:0]crop={cw}:{ch}:{cx}:{cy},scale={vw}:{vh}:flags=lanczos,setsar=1,fps={FPS},'
             f'pad={W}:{H}:{vx}:{vy}:color=0xF6F3EB[screen];[screen][1:v:0]overlay=0:0:format=auto,format=yuv420p[final]')
    print('render', clip['name'], round(duration, 2), flush=True)
    execute(['-ss', f'{clip["source_start"]:.3f}', '-i', str(raw), '-loop', '1', '-framerate', str(FPS), '-i', str(image),
             '-filter_complex', chain, '-map', '[final]', '-t', f'{duration:.6f}', '-an', *encode, str(target)], name)
    playlist.append(target)
    subtitles.append((cursor, cursor + duration, clip['caption']))
    timeline.append(dict(clip, output_start=cursor, duration=duration))
    cursor += duration
card('outro', end=True)
manifest = work / 'concat.txt'
manifest.write_text('\n'.join("file '" + str(path).replace("'", "'\\''") + "'" for path in playlist) + '\n')
final = out / 'DriveMood_demo_ko_1080p.mp4'
execute(['-f', 'concat', '-safe', '0', '-i', str(manifest), '-map', '0:v:0', '-c', 'copy', '-map_metadata', '-1',
         '-metadata', 'title=Drive Mood - Pleos Prototype Demo', '-movflags', '+faststart', str(final)], 'final-mux')
srt = out / 'DriveMood_demo_ko.srt'
srt.write_text('\n\n'.join(f'{index + 1}\n{stamp(start)} --> {stamp(end)}\n{caption}' for index, (start, end, caption) in enumerate(subtitles)) + '\n')
report = json.loads(subprocess.check_output([ffprobe, '-v', 'error', '-show_format', '-show_streams', '-of', 'json', str(final)], env=env))
report['format']['filename'] = final.name
(out / 'video-info.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
(out / 'timeline.json').write_text(json.dumps(dict(source=os.path.relpath(raw, ROOT), capture_meta=os.path.relpath(source / 'scenes.json', ROOT), app_version=meta.get('app_version'),
    app_crop=[cx, cy, cw, ch], timeline=timeline, output_duration=cursor, audio='none; Korean captions',
    note='Actual screen recording. Waiting intervals edited; all retained footage plays at normal speed.',
    output=final.name), ensure_ascii=False, indent=2) + '\n')
cover_at = next(clip['output_start'] for clip in timeline if clip['name'] == 'steady') + 2
execute(['-ss', str(cover_at), '-i', str(final), '-frames:v', '1', str(out / 'DriveMood_demo_cover.png')], 'cover')
print('Video ready:', final, 'seconds:', round(cursor, 2), 'bytes:', final.stat().st_size, flush=True)
