#!/usr/bin/env python3
"""UI-milestone boundary: preserve API/transport and approved Black Cat assets."""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
AI = ROOT / 'app/src/ai/java/com/blackcat/remote'

FROZEN_BLOBS = {
    'AiTransportScope.kt': 'cc257bb1997aab17060d06206b37d6b20937d8f0',
    'ApiKeyVault.kt': '59cfbb20d555c5b0ea377db3720b7c2d91d04eb5',
    'CommandPlan.kt': '14c17d8e7296a55c459293ea7c56e860a2627371',
    'OpenAiPlanner.kt': '786bc3b4cbd3d584003aee5377ced3115a15ba3a',
}
ASSETS = {
    'black_cat_emblem.webp': '8966a939fd5349610be8322e74ec9b94448030480f53a6f2b60864bee919fb19',
    'black_cat_peek.webp': 'ccd27b1f3c6a2880a9c1030746c5a9cd4a71bd27964ab853b7c2adfb28cf7263',
}
FULL_CAT_SHA256 = '080b328670716e8211e283c951d2fdf0e3c9359191083c89e5b84bad76fd9786'


def git_blob(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def verify(root: Path = ROOT) -> None:
    ai = root / 'app/src/ai/java/com/blackcat/remote'
    for name, expected in FROZEN_BLOBS.items():
        actual = git_blob((ai / name).read_bytes())
        assert actual == expected, f'UI milestone changed frozen API/transport source: {name}'

    entry = (ai / 'AiEntry.kt').read_text(encoding='utf-8')
    assert 'CheckBox' not in entry, 'Redundant approval checkboxes were reintroduced'
    assert 'targetField' not in entry, 'Free-text target field returned; use saved dropdown/custom edit flow'
    for text in (
        'Plan', 'Step', 'Preview', 'Result', 'TYPE ONLY',
        'R.drawable.black_cat_emblem', 'R.drawable.black_cat_peek',
        'Edit / custom…', 'getSharedPreferences(TARGET_PREFS',
        'putString(TARGET_KEY', 'Ubuntu Linux / Bash', 'Debian Linux / Bash',
        'Fedora Linux / Bash', 'Arch Linux / Bash', 'Kali Linux / Bash',
        'Linux Assistant\\nPlan, review & type',
        'R.drawable.black_cat_full', 'BlackCatStyle.applySystemBarInsets(root)',
        'lastTypedCommand = command', 'activeTab = Tab.FEEDBACK'
    ):
        assert text in entry, f'Missing approved UI/target element: {text}'
    for forbidden in ('android.permission.CAMERA', 'input_image', 'getUserMedia', 'manager.sendKeyboard', 'manager.init(', 'manager.close(', 'registerApp(', 'unregisterApp('):
        assert forbidden not in entry, f'UI-only milestone crossed a frozen boundary: {forbidden}'
    assert entry.count('planner.propose(') == 1, 'OpenAI requests must remain explicit and singular in the UI path'
    nav = entry.split('private fun buildBottomNav()', 1)[1].split('private fun scrollPage', 1)[0]
    order = [nav.index(token) for token in (
        'add(Tab.PLAN', 'add(Tab.STEP', 'add(Tab.PREVIEW', 'add(Tab.FEEDBACK'
    )]
    assert order == sorted(order), 'Bottom flow must be Plan → Step → Preview → Result'
    show = entry.split('fun show() {', 1)[1].split('private fun buildShell()', 1)[0]
    for forbidden in ('planner.propose(', 'ApprovedCommandSender.send(', 'sendKeyboard(', 'openSettings()'):
        assert forbidden not in show, f'Opening the panel must not trigger work: {forbidden}'
    dismiss = entry.split('fun dismiss() {', 1)[1].split('private fun buildShell()', 1)[0]
    for forbidden in ('planner.propose(', 'ApprovedCommandSender.send(', 'manager.'):
        assert forbidden not in dismiss, f'Closing the panel must not trigger API/HID work: {forbidden}'

    for source_set in ('ai', 'main'):
        assets = root / f'app/src/{source_set}/res/drawable-nodpi'
        for name, expected in ASSETS.items():
            data = (assets / name).read_bytes()
            actual = hashlib.sha256(data).hexdigest()
            assert actual == expected, f'Approved Black Cat artwork changed in {source_set}: {name}'

    full_cat = root / 'app/src/main/res/drawable-nodpi/black_cat_full.webp'
    assert hashlib.sha256(full_cat.read_bytes()).hexdigest() == FULL_CAT_SHA256, 'Approved full cat portrait changed'

    style = (root / 'app/src/main/java/com/blackcat/remote/BlackCatStyle.kt').read_text(encoding='utf-8')
    for value in ('245, 248, 245', '22, 97, 70', '18, 44, 36'):
        assert value in style, f'Shared Black Cat style palette changed unexpectedly: {value}'
    for text in ('applySystemBarInsets', 'WindowInsets.Type.systemBars()', 'systemWindowInsetBottom'):
        assert text in style, f'Missing Android system-bar inset handling: {text}'

    manifest = (root / 'app/src/ai/AndroidManifest.xml').read_text(encoding='utf-8')
    assert 'CAMERA' not in manifest, 'Camera permission belongs to the later photo milestone'
    print('PASS: approved UI06 uses full cat-on-white hero, dark control chrome, focused remote tiles, saved target flow, system-bar clearance, and frozen API/HID boundaries')


if __name__ == '__main__':
    verify()