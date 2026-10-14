#!/usr/bin/env python3
"""UI10 boundaries complement the frozen Bluetooth/API guards and executable state tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

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
    print('PASS: UI10 editable feedback, lazy AI-only dictation, reviewed photo revisions and stale-result guards')


if __name__ == '__main__':
    verify()
