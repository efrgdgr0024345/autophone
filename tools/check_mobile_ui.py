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
PORTRAIT_SHA256 = 'c6a98d9b2063c4ae4edc8f877db6db36bfce7fd20d11560e992b65027112d2d0'


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
        'R.drawable.black_cat_peek',
        'Edit / custom…', 'getSharedPreferences(TARGET_PREFS',
        'putString(TARGET_KEY', 'Ubuntu Linux / Bash', 'Debian Linux / Bash',
        'Fedora Linux / Bash', 'Arch Linux / Bash', 'Kali Linux / Bash',
        'BlackCatStyle.styleButton(activity, this, primary = true, compact = true)',
        'Linux Assistant', 'Photo Feedback', 'R.drawable.black_cat_portrait',
        'BlackCatStyle.applySystemBarInsets(root)',
        'lastTypedCommand = command', 'activeTab = Tab.FEEDBACK'
    ):
        assert text in entry, f'Missing approved UI/target element: {text}'
    for forbidden in ('manager.sendKeyboard', 'manager.init(', 'manager.close(', 'registerApp(', 'unregisterApp('):
        assert forbidden not in entry, f'AI entry crossed a frozen HID boundary: {forbidden}'
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
    portrait = root / 'app/src/main/res/drawable-nodpi/black_cat_portrait.webp'
    assert hashlib.sha256(portrait.read_bytes()).hexdigest() == PORTRAIT_SHA256, 'Large approved Black Cat portrait changed'

    style = (root / 'app/src/main/java/com/blackcat/remote/BlackCatStyle.kt').read_text(encoding='utf-8')
    for value in ('245, 248, 245', '22, 97, 70', '18, 44, 36'):
        assert value in style, f'Shared Black Cat style palette changed unexpectedly: {value}'
    for text in ('applySystemBarInsets', 'WindowInsets.Type.systemBars()', 'systemWindowInsetBottom'):
        assert text in style, f'Missing Android system-bar inset handling: {text}'

    manifest = (root / 'app/src/ai/AndroidManifest.xml').read_text(encoding='utf-8')
    assert 'android.permission.CAMERA' in manifest and 'android.permission.INTERNET' in manifest
    for forbidden_permission in ('READ_MEDIA_IMAGES', 'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE', 'RECORD_AUDIO'):
        assert forbidden_permission not in manifest, f'Photo milestone requested broad/unrelated permission: {forbidden_permission}'

    capture = (ai / 'PhotoCaptureController.kt').read_text(encoding='utf-8')
    for forbidden in ('HidManager', 'sendKeyboard(', 'startActivity(', 'ACTION_IMAGE_CAPTURE', 'MediaStore'):
        assert forbidden not in capture, f'Camera controller crossed boundary: {forbidden}'
    assert 'CameraDevice.TEMPLATE_STILL_CAPTURE' in capture and 'ImageReader.newInstance' in capture

    photo_contract = (ai / 'PhotoAnalysis.kt').read_text(encoding='utf-8')
    for required in ('UNTRUSTED EVIDENCE', 'EMPTY commands array', 'input_image', 'detail', 'high', 'store', 'false'):
        assert required in photo_contract, f'Missing photo-analysis contract: {required}'
    analyzer = (ai / 'OpenAiPhotoAnalyzer.kt').read_text(encoding='utf-8')
    assert 'HidManager' not in analyzer and 'CommandTarget' not in analyzer
    assert 'OpenAiPlanner.ENDPOINT' in analyzer

    photo_panel = (ai / 'PhotoFeedbackPanel.kt').read_text(encoding='utf-8')
    for required in ('Review photo', 'Send photo to OpenAI', 'Photo analysis ready', 'Review & TYPE ONLY', 'ApprovedCommandSender.send'):
        assert required in photo_panel, f'Missing reviewed photo flow element: {required}'
    for forbidden in ('sendKeyboard(', 'KeyEvent.KEYCODE_ENTER', 'Runtime.getRuntime', 'ProcessBuilder('):
        assert forbidden not in photo_panel, f'Photo UI bypassed review/sender boundary: {forbidden}'

    print('PASS: approved white-cat/black-panel UI, saved target flow, in-panel reviewed photo analysis, system-bar clearance, and frozen V2/API boundaries')


if __name__ == '__main__':
    verify()