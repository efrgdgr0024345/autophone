#!/usr/bin/env python3
"""UI11 compact flow, versioned API contract, frozen transport/capture and approved artwork."""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
AI = ROOT / 'app/src/ai/java/com/blackcat/remote'

FROZEN_BLOBS = {
    'AiTransportScope.kt': 'cc257bb1997aab17060d06206b37d6b20937d8f0',
    'ApiKeyVault.kt': '59cfbb20d555c5b0ea377db3720b7c2d91d04eb5',
    'PhotoCaptureController.kt': '4d81d5142f8c1a050bd284cc5a00ac4c27920720',
}
# Historical API baseline stays recorded. The user's UI11 request explicitly authorizes only
# a current-model / exclusive-decision wire migration, not a transport or approval rewrite.
UI10_API_BASELINE_BLOBS = {
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


def require_contract(text: str, required: tuple[str, ...], name: str) -> None:
    for value in required:
        assert value in text, f'Missing {name} boundary: {value}'


def verify_api_contract(ai: Path) -> None:
    plan = (ai / 'CommandPlan.kt').read_text(encoding='utf-8')
    require_contract(plan, (
        'const val DEFAULT_MODEL = "gpt-6.1-sol"',
        'val MODELS = listOf(DEFAULT_MODEL, "gpt-6-luna")',
        'const val OUTPUT_TOKEN_LIMIT = 16384',
        'require(model in MODELS)', 'require(goal.isNotBlank() && goal.length <= MAX_REQUEST_LENGTH)',
        '.put("decision", ProposalWire.decisionSchema(MAX_COMMANDS, 1000))',
        '.put("additionalProperties", false)',
        'return JSONObject().put("anyOf", JSONArray()',
        '.put(branch("clarify", 1, 8, 0))', '.put(branch("commands", 0, 0, maxCommands))',
        'ProposalWire.exactKeys(root, setOf("explanation", "roadmap", "assumptions", "decision"))',
        'exactKeys(obj, setOf("mode", "questions", "commands"))',
        'if (questions.isNotEmpty() && array.length() > 0)',
        'if (array.length() > maxCommands)', 'if (CommandPolicy.problem(command) != null)',
        'item.get("requires_admin") !is Boolean',
        'if (root.optString("status") != "completed")',
        'if (output.length() > 32)', 'if (texts.size != 1)',
        'if (part.optString("type") == "refusal")',
        'Each command object is ONE separately reviewed step', 'UNTRUSTED EVIDENCE',
        'not one', 'multiline string', 'Never claim execution succeeded',
    ), 'UI11 exclusive command-decision')
    photo = (ai / 'PhotoAnalysis.kt').read_text(encoding='utf-8')
    require_contract(photo, (
        '.put("decision", ProposalWire.decisionSchema(8, 1200))',
        'ProposalWire.exactKeys(root, setOf("summary", "observations", "decision"))',
        'ProposalWire.parseDecision(root.getJSONObject("decision"), 8, 1200)',
        'require(model in PlanCodec.MODELS)', 'require(jpeg.size in 1..4_500_000)',
        'UNTRUSTED EVIDENCE', 'EMPTY commands array',
        'if (root.optString("status") != "completed")', 'if (output.length() > 32)',
        'if (texts.size != 1)', 'if (part.optString("type") == "refusal")',
        'input_image', '.put("detail", "high")',
    ), 'UI11 reviewed photo-decision')
    for codec, name in ((plan, 'planner'), (photo, 'photo')):
        require_contract(codec, (
            '.put("store", false)', '.put("stream", false)',
            '.put("tools", JSONArray())', '.put("tool_choice", "none")',
            '.put("reasoning", JSONObject().put("effort", "low"))',
            '.put("strict", true)', '.put("type", "json_schema")',
        ), name + ' request privacy/schema')
        for forbidden in ('HidManager', 'CommandTarget', 'CommandApproval', 'ApprovedCommandSender', 'SharedPreferences', 'FileOutputStream'):
            assert forbidden not in codec, f'Wire codec gained execution/storage capability: {forbidden}'
    planner = (ai / 'OpenAiPlanner.kt').read_text(encoding='utf-8')
    assert 'const val ENDPOINT = "https://api.openai.com/v1/responses"' in planner
    analyzer = (ai / 'OpenAiPhotoAnalyzer.kt').read_text(encoding='utf-8')
    assert 'URL(OpenAiPlanner.ENDPOINT)' in analyzer
    for client, name in ((planner, 'planner'), (analyzer, 'photo')):
        require_contract(client, (
            'as HttpsURLConnection', 'http.instanceFollowRedirects = false',
            'http.requestMethod = "POST"', 'http.connectTimeout = 10000',
            'http.readTimeout = READ_TIMEOUT_MS' if name == 'planner' else 'http.readTimeout = 60000',
            'http.useCaches = false', 'currentCoroutineContext().ensureActive()',
            'out.size() + count > 262144', 'body.fill(0)', 'http.disconnect()',
            'if (connection === http) connection = null',
        ), name + ' HTTPS/cancellation')
        if name == 'planner':
            require_contract(client, ('const val READ_TIMEOUT_MS = 180000',
                                      'const val REQUEST_TIMEOUT_MS = 195000L'), 'bounded detailed-plan deadline')
        for forbidden in ('HidManager', 'CommandTarget', 'sendKeyboard(', 'SharedPreferences',
                          'FileOutputStream', 'HostnameVerifier', 'TrustManager', 'SSLContext', 'android.util.Log'):
            assert forbidden not in client, f'API client crossed privacy/transport boundary: {forbidden}'
    checker = (ai / 'OpenAiKeyChecker.kt').read_text(encoding='utf-8')
    require_contract(checker, (
        'const val MODELS_ENDPOINT = "https://api.openai.com/v1/models/"',
        'require(model in PlanCodec.MODELS)', 'http.requestMethod = "GET"',
        'http.instanceFollowRedirects = false', 'http.readTimeout = 15000',
        'output.size() + count > 65536', 'currentCoroutineContext().ensureActive()',
        'value.optString("id") == model && value.optString("object") == "model"',
        'http.disconnect()', 'status == KeyCheckStatus.VERIFIED',
    ), 'explicit metadata key check')
    for forbidden in ('HidManager', 'CommandApproval', 'ApprovedCommandSender', 'propose(',
                      'requestMethod = "POST"', 'SharedPreferences', 'FileOutputStream',
                      'HostnameVerifier', 'TrustManager', 'SSLContext', 'android.util.Log'):
        assert forbidden not in checker, f'Key verification gained generation/storage/transport capability: {forbidden}'


def verify(root: Path = ROOT) -> None:
    ai = root / 'app/src/ai/java/com/blackcat/remote'
    for name, expected in FROZEN_BLOBS.items():
        actual = git_blob((ai / name).read_bytes())
        assert actual == expected, f'UI milestone changed frozen API/transport source: {name}'
    verify_api_contract(ai)

    entry = (ai / 'AiEntry.kt').read_text(encoding='utf-8')
    assert 'CheckBox' not in entry, 'Redundant approval checkboxes were reintroduced'
    assert 'targetField' not in entry, 'Free-text target field returned; use saved dropdown/custom edit flow'
    for text in (
        'Plan', 'Step', 'Preview', 'Result', 'TYPE ONLY',
        'Browse Offline Linux Library', 'Start with step 1',
        'getSharedPreferences(TARGET_PREFS', 'Target: $selectedTarget',
        'Change target in Settings', 'Ubuntu Linux / Bash', 'Debian Linux / Bash',
        'Fedora Linux / Bash', 'Arch Linux / Bash', 'Kali Linux / Bash',
        'Linux Assistant', 'Photo Feedback',
        'Get Plan', 'openPlanPhotoFeedback', 'buildAppNav',
        'BlackCatStyle.applySystemBarInsets(root)',
        'lastTypedCommand = command', 'activeTab = Tab.FEEDBACK'
    ):
        assert text in entry, f'Missing approved UI/target element: {text}'
    for forbidden in ('manager.sendKeyboard', 'manager.init(', 'manager.close(', 'registerApp(', 'unregisterApp('):
        assert forbidden not in entry, f'AI entry crossed a frozen HID boundary: {forbidden}'
    assert entry.count('planner.propose(') == 1, 'OpenAI requests must remain explicit and singular in the UI path'
    nav = entry.split('private fun buildBottomNav()', 1)[1].split('private fun buildAppNav()', 1)[0]
    stage_tokens = ('add(Tab.PLAN', 'add(Tab.STEP', 'add(Tab.PREVIEW', 'add(Tab.FEEDBACK')
    assert all(nav.count(token) == 1 for token in stage_tokens), 'Exactly four reviewed plan stages are required'
    order = [nav.index(token) for token in stage_tokens]
    assert order == sorted(order), 'Reference flow tabs must be Plan → Step → Preview → Result'
    shell = entry.split('private fun buildShell()', 1)[1].split('private fun buildStatusBar()', 1)[0]
    assert shell.index('root.addView(buildBottomNav()') < shell.index('root.addView(buildStatusBar()'), 'Plan stages must start at the top'
    assert 'ImageView(' not in entry and 'buildTopBar()' not in entry, 'Cat pictures must not consume the compact AI task screens'
    assert 'Spinner(' not in entry, 'Target and model controls belong in Settings'
    assert '?.createBottomNavigation("AI") { hideForNavigation() }' in entry, 'AI panel must use the shared four-entry navigation'
    assert 'assistantPanels[activity]?.hideForNavigation()' in entry, 'Nested navigation must hide the existing assistant rather than create another'
    hide = entry.split('fun hideForNavigation()', 1)[1].split('private fun resumeForNavigation()', 1)[0]
    for required in ('if (!panelVisible) return', 'cancelWork()', 'confirmation?.dismiss()', 'voice.cancel()', 'photoPanel?.dismiss()',
                     'libraryPanel?.dismiss()', 'workspace.touch()', 'oneShotKey = ""', 'target.close()', 'dialog.hide()'):
        assert required in hide, f'Navigation must invalidate work/approvals: {required}'
    assert 'workspace.clear()' not in hide, 'Navigating to keyboard/settings must retain the editable draft'
    resume = entry.split('private fun resumeForNavigation()', 1)[1].split('private fun cancelWork()', 1)[0]
    for forbidden in ('planner.propose(', 'ApprovedCommandSender.send(', 'sendKeyboard('):
        assert forbidden not in resume, f'Reopening a draft must not resume work: {forbidden}'
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
    assert hashlib.sha256(portrait.read_bytes()).hexdigest() == PORTRAIT_SHA256, 'Existing photo-enabled Black Cat portrait changed'
    full_cat = root / 'app/src/main/res/drawable-nodpi/black_cat_full.webp'
    assert hashlib.sha256(full_cat.read_bytes()).hexdigest() == '96e9cd8a3e8d199ecd2d403f769d52c79d9861c3161d0c111a708c74dff17895', 'UI10 repaired full Black Cat hero changed'

    style = (root / 'app/src/main/java/com/blackcat/remote/BlackCatStyle.kt').read_text(encoding='utf-8')
    for value in ('245, 248, 245', '22, 97, 70', '18, 44, 36'):
        assert value in style, f'Shared Black Cat style palette changed unexpectedly: {value}'
    for text in ('applySystemBarInsets', 'WindowInsets.Type.systemBars()', 'systemWindowInsetBottom'):
        assert text in style, f'Missing Android system-bar inset handling: {text}'

    manifest = (root / 'app/src/ai/AndroidManifest.xml').read_text(encoding='utf-8')
    assert 'android.permission.CAMERA' in manifest and 'android.permission.INTERNET' in manifest
    assert 'android.permission.RECORD_AUDIO' in manifest, 'AI-only opt-in dictation permission missing'
    for forbidden_permission in ('READ_MEDIA_IMAGES', 'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE'):
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

    standalone = (ai / 'AiStandalonePanels.kt').read_text(encoding='utf-8')
    for required in ('TargetSystemPanel', 'AiSettingsPanel', 'Target system', 'Custom target system',
                     'ApiKeyVault', 'Check & use API key', 'Check & save API key', 'Save model only',
                     'createDiagnosticsView()', 'Change target system', 'PlanCodec.MODELS',
                     'putString(TARGET_KEY', 'Edit / Custom target…'):
        assert required in standalone, f'Missing standalone UI08 screen element: {required}'
    for forbidden in ('planner.propose(', 'sendKeyboard(', 'registerApp(', 'startDiscovery('):
        assert forbidden not in standalone, f'Standalone target/settings screen crossed an execution/Bluetooth boundary: {forbidden}'
    assert 'ImageView(' not in standalone, 'Settings must use compact functional headings'
    assert 'createBottomNavigation(active, close)' in standalone, 'Settings must use shared four-entry navigation'

    photo_panel = (ai / 'PhotoFeedbackPanel.kt').read_text(encoding='utf-8')
    photo_header = photo_panel.split('private fun buildHeader()', 1)[1].split('private fun render()', 1)[0]
    assert 'ImageView(' not in photo_header and 'R.drawable.black_cat' not in photo_header, 'Photo feedback must use a compact functional heading'
    for required in ('Review photo', 'Send photo to OpenAI', 'Photo analysis ready', 'Review & TYPE ONLY', 'ApprovedCommandSender.send'):
        assert required in photo_panel, f'Missing reviewed photo flow element: {required}'
    for forbidden in ('sendKeyboard(', 'KeyEvent.KEYCODE_ENTER', 'Runtime.getRuntime', 'ProcessBuilder('):
        assert forbidden not in photo_panel, f'Photo UI bypassed review/sender boundary: {forbidden}'

    main = (root / 'app/src/main/java/com/blackcat/remote/MainActivity.kt').read_text(encoding='utf-8')
    for required in ('Black Cat AI', 'Remote · Automate · Control', 'Make Discoverable', 'Remote Controls', 'Handler(Looper.getMainLooper()).postDelayed'):
        assert required in main, f'Missing UI08 home/Bluetooth reference element: {required}'

    print('PASS: compact UI11 plan/settings navigation, exclusive API decisions, reviewed photos and frozen HID/transport/key/capture boundaries')


if __name__ == '__main__':
    verify()
