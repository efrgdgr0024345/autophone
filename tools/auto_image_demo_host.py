#!/usr/bin/env python3
"""Disposable emulator demo driver; never part of the phone application.

Run only with the image-auto instrumentation and an emulator video camera reading
camera.mjpeg from OUTPUT. A separate JPEG writer streams OUTPUT/feed.jpg to that
FIFO. This driver executes only two known read-only commands and the application's
bounded capture/page/cleanup wrappers. The reviewing assistant supplies observation
and plan JSON after inspecting the actual Camera2 JPEGs. No OpenAI credentials.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

from PIL import Image, ImageDraw, ImageFont


REMOTE = '/data/user/0/com.blackcat.remote/files/image-auto-demo'
ORIGINALS = ('cat /tmp/blackcat-image-demo.log', 'cat /etc/os-release')


def adb(*args, **kwargs):
    return subprocess.run(['adb', '-s', os.environ.get('ANDROID_SERIAL', 'emulator-5554'), *args], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs).stdout


def render(output, text, clipped=False):
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 12)
    # A deliberately small terminal viewport makes the first eight-line page overflow.
    rows = text.splitlines()
    if clipped:
        rows = rows[-7:]
    else:
        rows = [piece for row in rows for piece in ([row[i:i+64] for i in range(0, len(row), 64)] or [''])][-24:]
    image = Image.new('RGB', (1280, 720), '#101710')
    draw = ImageDraw.Draw(image)
    draw.text((400, 14), 'Black Cat | Linux CAMERA DEMO', font=font, fill='#70e17a')
    draw.line((22, 46, 1258, 46), fill='#3b5540', width=2)
    draw.multiline_text((395, 58), '\n'.join(rows), font=font, fill='#d9eadb', spacing=4)
    temporary = output / 'feed-next.jpg'
    image.save(temporary, quality=97)
    os.replace(temporary, output / 'feed.jpg')
    return image


def allowed(command, tickets):
    capture = re.search(r'b=(/tmp/bc-[0-9a-f]{24}); mkdir', command)
    if capture:
        if not command.startswith('(umask 077; ') or not any(
                'env TERM=dumb PAGER=cat SYSTEMD_PAGER=cat ' + original + ' </dev/null;' in command
                for original in ORIGINALS):
            return False
        if any(token in command for token in ('sudo', 'curl', 'wget', 'chmod', 'chown')):
            return False
        tickets.add(capture[1])
        return True
    return any(command.startswith(f'(b={path}; printf ') or
               command.startswith(f'rm -- {path}/r {path}/s {path}/o && rmdir -- {path} && printf ')
               for path in tickets)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--resume', action='store_true')
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Disposable emulator only'
    source = Path('/tmp/blackcat-image-demo.log')
    if source.exists() and not args.resume:
        raise SystemExit('Refusing to replace an existing demo input; remove the old demo first')
    source.write_text('\n'.join([
        '01 demo-service: starting read-only compatibility check',
        '02 host configuration loaded',
        '03 data directory readable',
        '04 port check completed',
        '05 network access is not needed for this check',
        '06 no system settings changed',
        '07 checking installed demo package',
        '08 ERROR: demo service package is incompatible with this host',
        '09 package was built for a different Linux distribution',
        '10 do not reinstall until the host distribution is identified',
        '11 read /etc/os-release to identify the installed distribution',
        '12 account identity will not explain this compatibility error',
        '13 preserve service configuration and recovery access',
        '14 END: collect OS evidence before selecting any repair',
    ]) + '\n')
    render(output, 'demo@linux:~$ ')
    handled_commands, handled_requests, pushed_replies = set(), set(), set()
    tickets = set()
    started = time.monotonic()
    try:
        while time.monotonic() - started < 1800:
            try:
                listing = adb('shell', 'ls', REMOTE).decode().split()
            except subprocess.CalledProcessError:
                time.sleep(.5)
                continue
            for name in sorted(listing):
                if name.startswith('command-') and name.endswith('.json') and name not in handled_commands:
                    adb('pull', REMOTE + '/' + name, str(output / name))
                    command = json.loads((output / name).read_text())['command']
                    if not allowed(command, tickets):
                        raise RuntimeError('Unexpected command at disposable demo boundary')
                    result = subprocess.run(['/bin/sh', '-c', command], cwd=output, timeout=20,
                                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
                    text = result.stdout.decode('utf-8', 'replace')
                    (output / name.replace('.json', '.txt')).write_text(text)
                    clipped = bool(re.search(r'_P1_1_8_BEGIN', command))
                    image = render(output, 'demo@linux:~$ ' + command + '\n' + text + '\ndemo@linux:~$ ', clipped)
                    image.save(output / name.replace('.json', '-terminal.png'))
                    handled_commands.add(name)
                    print(json.dumps({'executed': name, 'exit_code': result.returncode,
                                      'output_chars': len(text), 'clipped': clipped}), flush=True)
                elif name.startswith('request-') and name.endswith('.json') and name not in handled_requests:
                    adb('pull', REMOTE + '/' + name, str(output / name))
                    request = json.loads((output / name).read_text())
                    if request['kind'] == 'observation':
                        photo = 'photo-' + request['id'] + '.jpg'
                        adb('pull', REMOTE + '/' + photo, str(output / photo))
                    handled_requests.add(name)
                    print(json.dumps({'needs_review': name, 'kind': request['kind']}), flush=True)
            for reply in sorted(output.glob('reply-*.json')):
                if reply.name not in pushed_replies:
                    temporary = REMOTE + '/' + reply.name + '.tmp'
                    adb('push', str(reply), temporary)
                    adb('shell', 'mv', temporary, REMOTE + '/' + reply.name)
                    pushed_replies.add(reply.name)
                    print(json.dumps({'review_delivered': reply.name}), flush=True)
            if 'result.json' in listing:
                adb('pull', REMOTE + '/.', str(output / 'phone-evidence'))
                print((output / 'phone-evidence/result.json').read_text(), flush=True)
                assert not any(Path(path).exists() for path in tickets), 'Target capture files left behind'
                return
            time.sleep(.2)
        raise TimeoutError('Image Auto demo did not finish')
    finally:
        source.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
