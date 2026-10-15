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


def provider_response(value):
    return {'status': 'completed', 'output': [{'type': 'message', 'content': [
        {'type': 'output_text', 'text': json.dumps(value)}]}]}


def ocr_reply(output, request):
    """Independent perception from the Camera2 JPEG, never from captured stdout/context.

    This is a deterministic test provider, not an OpenAI vision/model test. Missing
    prompt/markers fails closed. The scripted planner below is scoped to this demo.
    """
    if request['kind'] == 'plan':
        context = request['context'].replace('\\/', '/') # JSON's optional slash escaping is not different evidence.
        assert '01 demo-service' in context and '14 END:' in context, 'Not all log pages reached the revision'
        assert '/etc/os-release' in context and 'Diagnose why' in context
        return provider_response({'explanation': 'The collected log points to a distribution mismatch; identify the host before any repair.',
            'roadmap': [], 'assumptions': ['Scripted demo planner, using actual OCR evidence.'],
            'decision': {'mode': 'commands', 'questions': [], 'commands': [{
                'title': 'Identify Linux distribution from feedback', 'command': 'cat /etc/os-release',
                'explanation': 'The log suggests an incompatible package. Read the installed distribution so we can choose a compatible repair.',
                'risk': 'low', 'requires_admin': False, 'expected_result': 'The installed Linux distribution and version.', 'warnings': []}]}})
    photo = output / ('photo-' + request['id'] + '.jpg')
    result = subprocess.run(['tesseract', str(photo), 'stdout', '--psm', '6'],
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20, check=True,
                            env={**os.environ, 'OMP_THREAD_LIMIT': '1'})
    text = result.stdout.decode('utf-8', 'replace')
    (output / ('ocr-' + request['id'] + '.txt')).write_text(text)
    lines = text.splitlines()
    prompt = any(re.search(r'demo@linux:.*\$', line) for line in lines)
    value = {'observed_command': '', 'terminal_state': 'shell_prompt' if prompt else 'unclear',
             'outcome': 'unclear', 'explanation': 'The terminal evidence is not yet readable enough.',
             'visible_evidence': [line[:1000] for line in lines[-3:]], 'revision_needed': False,
             'page_marker': '', 'page_state': 'none', 'page_text': '', 'exit_code': ''}
    if request['preflight']:
        if prompt:
            value.update(outcome='appears_complete', explanation='An idle terminal prompt is visible; the diagnostic can begin.')
        return provider_response(value)
    context = json.loads(request['collection_context'])
    marker, page = context['expected_marker'], context['page']
    # OCR renders underscore separators as either underscores or whitespace.
    # Compare the complete alphanumeric marker, including every random token
    # character and page number. Never repair letters/digits from the context.
    normalized = [re.sub(r'[\s_]+', '', line) for line in lines]
    marker_key = marker.replace('_', '')
    if page <= 0:
        matches = [re.fullmatch(re.escape(marker_key) + r'(\d{1,3})', line) for line in normalized]
        match = next((m for m in matches if m), None)
        if match and prompt:
            value.update(page_marker=marker, page_state='cleaned' if page < 0 else 'captured',
                         exit_code=match[1], outcome='appears_complete', explanation='The visible completion marker and idle prompt match this operation.')
    else:
        footer = next(((i, re.fullmatch(re.escape(marker_key) + r'END(\d{1,3})(MORE|DONE|LIMIT)', line))
                       for i, line in enumerate(normalized) if re.fullmatch(re.escape(marker_key) + r'END(\d{1,3})(MORE|DONE|LIMIT)', line)), None)
        if footer and prompt:
            end, match = footer
            begin = next((i for i, line in enumerate(normalized) if line == marker_key + 'BEGIN'), None)
            value.update(page_marker=marker, exit_code=match[1], page_state='unreadable')
            if begin is not None and begin < end:
                body = '\n'.join(lines[begin + 1:end]).strip('\n')
                evidence = '\n'.join(context['earlier_pages_untrusted']) + '\n' + body
                failed = match[1] != '0' or 'ERROR:' in evidence
                value.update(page_state=match[2].lower(), page_text=body, outcome='error' if failed else 'appears_complete',
                             revision_needed=failed and match[2] != 'MORE',
                             explanation='The collected output reveals a compatibility problem; the remaining plan needs a distribution check.' if failed
                             else 'This numbered page is readable and consistent with the expected diagnostic output.')
    import jsonschema
    jsonschema.validate(value, request['production_schema'])
    return provider_response(value)


def adb(*args, **kwargs):
    return subprocess.run(['adb', '-s', os.environ.get('ANDROID_SERIAL', 'emulator-5554'), *args], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs).stdout


def render(output, text, clipped=False, full_width=False):
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf', 26 if full_width else 12)
    # A deliberately small terminal viewport makes the first eight-line page overflow.
    rows = text.splitlines()
    if clipped:
        rows = rows[-7:]
    else:
        columns = 72 if full_width else 64
        rows = [piece for row in rows for piece in ([row[i:i+columns] for i in range(0, len(row), columns)] or [''])][-(20 if full_width else 24):]
    image = Image.new('RGB', (1280, 720), '#101710')
    draw = ImageDraw.Draw(image)
    draw.text((22 if full_width else 400, 14), 'Black Cat | Linux CAMERA DEMO', font=font, fill='#70e17a')
    draw.line((22, 46, 1258, 46), fill='#3b5540', width=2)
    draw.multiline_text((22 if full_width else 395, 58), '\n'.join(rows), font=font, fill='#d9eadb', spacing=4)
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
    parser.add_argument('--run-id', required=True, help='Match instrumentation demoRunId; never process a previous run')
    parser.add_argument('--resume', action='store_true', help='Resume this same run from completed command journals; never repeat an ambiguous command')
    parser.add_argument('--automatic-ocr', action='store_true', help='Use a clearly labelled OCR/scripted test provider; no live model')
    parser.add_argument('--inject-vision-frame', action='store_true', help='Test-only image injection at the observer boundary; Camera2 lifecycle is still exercised separately')
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
    render(output, 'demo@linux:~$ ', full_width=args.inject_vision_frame)
    handled_commands, handled_requests, pushed_replies = set(), set(), set()
    tickets = set()
    previous_commands = sorted(output.glob('command-*.json'))
    if previous_commands and not args.resume:
        raise RuntimeError('Use a fresh output directory, or explicitly resume its completed command journals')
    if args.resume:
        assert all(p.with_suffix('.txt').exists() for p in previous_commands), 'Ambiguous command journal; refusing replay'
        for path in previous_commands:
            assert allowed(json.loads(path.read_text())['command'], tickets), 'Unexpected journal command'
            handled_commands.add(path.name)
        handled_requests = {p.name for p in output.glob('request-*.json')
                            if (output / p.name.replace('request-', 'reply-')).exists()}
        print(json.dumps({'resumed_completed_commands': len(handled_commands), 'commands_replayed': 0}), flush=True)
    last_command = ''
    pushed_frame = 0
    started = time.monotonic()
    try:
        while time.monotonic() - started < 1800:
            try:
                if adb('shell', 'cat', REMOTE + '/run-id').decode() != args.run_id:
                    time.sleep(.5)
                    continue
                # List only AFTER matching the ID: a previous directory listing
                # may belong to the old fixture that instrumentation just reset.
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
                    image = render(output, 'demo@linux:~$ ' + command + '\n' + text + '\ndemo@linux:~$ ', clipped, args.inject_vision_frame)
                    last_command = command
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
                    if args.automatic_ocr:
                        response = ocr_reply(output, request)
                        reply = output / ('reply-' + request['id'] + '.json')
                        temporary = reply.with_suffix('.tmp')
                        temporary.write_text(json.dumps(response))
                        temporary.replace(reply)
                        print(json.dumps({'test_provider': 'tesseract/scripted', 'reply': reply.name,
                                          'live_openai': False}), flush=True)
            # Instrumentation starts by replacing its private fixture directory. If the
            # host starts first, its initial frame may be removed during that reset.
            # Re-deliver missing files even when the rendered image has not changed.
            if args.inject_vision_frame and ('frame-ready.json' not in listing or 'frame.jpg' not in listing or
                    (output / 'feed.jpg').stat().st_mtime_ns != pushed_frame):
                adb('push', str(output / 'feed.jpg'), REMOTE + '/frame-next.jpg')
                adb('shell', 'mv', REMOTE + '/frame-next.jpg', REMOTE + '/frame.jpg')
                ready = output / 'frame-ready.json'
                ready.write_text(json.dumps({'command': last_command}))
                adb('push', str(ready), REMOTE + '/frame-ready.tmp')
                adb('shell', 'mv', REMOTE + '/frame-ready.tmp', REMOTE + '/frame-ready.json')
                pushed_frame = (output / 'feed.jpg').stat().st_mtime_ns
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
