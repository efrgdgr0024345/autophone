#!/usr/bin/env python3
"""UI11 feedback, dictation and checked-key privacy boundaries alongside executable state tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re

ROOT = Path(__file__).resolve().parents[1]


def verify(root=ROOT):
    ai = root / 'app/src/ai/java/com/blackcat/remote'
    entry = (ai / 'AiEntry.kt').read_text()
    for text in ('Apply updated plan', 'Keep current plan', 'Edit this step', 'Speak feedback',
                 'Request updated plan', 'openPlanPhotoFeedback', 'onSelectForPlan',
                 'workspace.accept(token)', 'planRevision != requestRevision',
                 'workspace.confirmResult(lastTypedIndex, planRevision)', 'suspendForBackground'):
        assert text in entry, f'Missing feedback/revision boundary: {text}'
    assert 'Photo/text result analysis is not enabled' not in entry
    voice = (ai / 'VoiceFeedbackController.kt').read_text()
    for text in ('Manifest.permission.RECORD_AUDIO', 'createOnDeviceSpeechRecognizer',
                 'EXTRA_PREFER_OFFLINE', 'token != generation', 'recognizer = null', 'speech?.destroy()'):
        assert text in voice, f'Missing dictation boundary: {text}'
    for forbidden in ('HidManager', 'CommandApproval', 'ApprovedCommandSender', 'propose(',
                      'HttpsURLConnection', 'FileOutputStream', 'MediaRecorder', 'SharedPreferences'):
        assert forbidden not in voice, f'Dictation crossed a privacy/execution boundary: {forbidden}'
    namespace = '{http://schemas.android.com/apk/res/android}'
    main = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
    assert 'android.permission.RECORD_AUDIO' not in {
        x.attrib[namespace + 'name'] for x in main.findall('uses-permission')
    }, 'Microphone capability leaked into the shared/offline app'
    photo = (ai / 'PhotoFeedbackPanel.kt').read_text()
    for text in ('Use photo feedback in plan', 'if (onUseAnalysis == null)',
                 'generation != analysisGeneration', 'analysisJob?.cancel()', 'requestKey = ""'):
        assert text in photo, f'Missing photo revision/cancellation boundary: {text}'
    workspace = (ai / 'PlanWorkspace.kt').read_text()
    for forbidden in ('HidManager', 'CommandApproval', 'ApprovedCommandSender', 'FileOutputStream'):
        assert forbidden not in workspace, f'Plan edits crossed execution/storage boundary: {forbidden}'
    settings = (ai / 'AiStandalonePanels.kt').read_text()
    for text in (
        'private val keyChecker: ApiKeyChecker = OpenAiKeyChecker()',
        'isChecked = false', 'if (!result.verified)', 'if (replacingKey && remember)',
        'withContext(Dispatchers.IO) { vault.save(value) }',
        'if (replacingKey && !remember) PendingApiKey.put(activity, value)',
        'if (!isCurrent(generation, model)) return@launch',
        '!destroyed && generation == checkGeneration && dialog.isShowing && model == selectedModel()',
        'checkGeneration++', 'keyChecker.cancel()', 'checkJob?.cancel()',
        'override fun onActivityPaused(a: Activity)', 'invalidateCheck("Verification cancelled',
        'keyInput.text.clear()', 'isSaveEnabled = false',
        'View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS', 'EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING',
    ):
        assert text in settings, f'Missing verified-key/cancellation/privacy boundary: {text}'
    assert settings.count('vault.save(') == 1, 'Key storage must have one explicit verified path'
    assert settings.index('if (!result.verified)') < settings.index('vault.save(value)'), 'Verification must succeed before key storage'
    key_selection = 'if (replacingKey && !remember) PendingApiKey.put(activity, value) else PendingApiKey.clear(activity)'
    assert re.sub(r'\s+', '', key_selection) in re.sub(r'\s+', '', settings), 'Selecting a verified saved key must clear an older queued key'
    forget = settings.split('private fun forgetKey()', 1)[1].split('private fun selectedModel()', 1)[0]
    assert 'PendingApiKey.clear(activity)' in forget, 'Forgetting must clear any queued key'
    assert forget.index('PendingApiKey.clear(activity)') < forget.index('scope.launch'), 'Forgetting a queued key must be synchronous before cancellable disk deletion'
    settings_show = settings.split('class AiSettingsPanel', 1)[1].split('fun show()', 1)[1].split('private fun build()', 1)[0]
    for forbidden in ('keyChecker.check(', 'checkAndSave()', 'vault.save(', 'PendingApiKey.put('):
        assert forbidden not in settings_show, f'Opening Settings must not verify or save a key: {forbidden}'
    pending = (ai / 'PendingApiKey.kt').read_text()
    for text in ('WeakHashMap<Activity, String>()', 'if (activity.isDestroyed || activity.isFinishing) return',
                 'override fun onActivityDestroyed(a: Activity) { clear(a) }',
                 'override fun onActivityPaused(a: Activity) { clear(a) }',
                 'fun take(activity: Activity): String = keys.remove(activity).orEmpty()',
                 'fun clear(activity: Activity) { keys.remove(activity) }'):
        assert text in pending, f'Missing memory-only one-use key boundary: {text}'
    for forbidden in ('SharedPreferences', 'FileOutputStream', 'HttpsURLConnection', 'URL(',
                      'sendKeyboard(', 'ApprovedCommandSender', 'propose(', 'android.util.Log', 'println('):
        assert forbidden not in pending, f'Pending key gained persistence/network/execution capability: {forbidden}'
    assert entry.count('PendingApiKey.take(activity)') == 1, 'Only the explicit plan request may consume a pending key in AiEntry'
    generate = entry.split('private fun generateProposal(', 1)[1].split('private fun confirmSelected()', 1)[0]
    assert 'PendingApiKey.take(activity)' in generate, 'The pending plan key must be consumed by explicit generation'
    photo_open = entry.split('private fun openPlanPhotoFeedback()', 1)[1].split('private fun openSettings()', 1)[0]
    assert 'PendingApiKey.take(' not in photo_open, 'Opening or cancelling a photo must not consume the queued key'
    assert photo.count('PendingApiKey.take(activity)') == 1, 'Photo keys may only be consumed once, by explicit upload'
    analyze = photo.split('private fun analyzePhoto()', 1)[1].split('private fun renderAnalyzing()', 1)[0]
    assert 'requestKey.ifBlank { PendingApiKey.take(activity) }' in analyze, 'Only explicit reviewed photo upload may consume a pending key'
    print('PASS: UI11 editable feedback, lazy AI-only dictation, reviewed revisions, verified optional key storage and cancelled stale results')


if __name__ == '__main__':
    verify()
