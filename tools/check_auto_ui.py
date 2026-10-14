#!/usr/bin/env python3
"""UI12's explicitly scoped Auto exception; ordinary senders remain frozen TYPE ONLY."""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]


def require(text, values, label):
    for value in values:
        assert value in text, f'Missing {label} boundary: {value}'


def verify(root=ROOT):
    ai = root / 'app/src/ai/java/com/blackcat/remote'
    for name, expected in {'CommandSafety.kt': '99fe8bd98a592efaf3177144f3f503a2738c5287',
                           'LibraryHidTarget.kt': 'fd6f915164dafd114e6790f0cddc2f8cd4241c95'}.items():
        data = (root / 'app/src/main/java/com/blackcat/remote' / name).read_bytes()
        blob = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
        assert blob == expected, f'UI12 changed an ordinary TYPE ONLY transport: {name}'
    state = (ai / 'AutoRunSession.kt').read_text()
    require(state, ('step.command in readOnly', '!step.requiresAdmin', 'step.risk == "low"',
                    'currentHost == host && currentRevision == revision', 'alreadyApproved',
                    'if (!human && !enter) return null', 'approval.generation == generation',
                    'ApprovedCommandSender.send(approval.text, target)',
                    'outcome != SendOutcome.REPORTS_ACCEPTED || !approval.enter',
                    'currentCoroutineContext().ensureActive()',
                    'if (!session.accepts(approval, target.current(), revision()))',
                    'target.sendKey(session.host, 0, 40)', 'revisions < 3',
                    'it.command in originallyApproved || AutoRunPolicy.canRun(it)',
                    'it.command !in executed || AutoRunPolicy.canRun(it)',
                    'const val MAX_CAPTURES = 41'), 'Auto session/Enter/scope')
    assert state.count('target.sendKey(session.host, 0, 40)') == 1
    assert state.count('if (!session.accepts(approval, target.current(), revision()))') == 2
    panel = (ai / 'AutoAssistPanel.kt').read_text()
    require(panel, ('Stop / close', 'consent.isChecked', 'run.check(choiceIds[0])',
                    'if (!consent)', 'previewFrames < 3', 'preflight.allowsNext("")',
                    'current.valid(target.current(), revision())', 'preflight = true',
                    'current.captured()', 'current.observed(token)', 'value.allowsNext(current.current!!.command)',
                    'current.canAdopt(candidate)', 'current.adopt(candidate, newRevision)',
                    'revisionRequests >= 3', 'session?.stop()', 'scope.coroutineContext.cancelChildren()',
                    'observer.cancel()', 'planner.cancel()', 'narrator.stop()',
                    'key = ""', 'bytes?.fill(0)', 'camera?.close()',
                    'No command was sent', 'startSession'
                    ), 'foreground coordinator')
    show = panel.split('fun show()', 1)[1].split('fun dismiss()', 1)[0]
    for forbidden in ('observer.observe(', 'planner.propose(', 'AutoReadOnlySender.send(', 'PendingApiKey.take('):
        assert forbidden not in show, f'Opening Auto triggers work: {forbidden}'
    start = panel.split('private fun startSession(', 1)[1].split('private fun renderSession()', 1)[0]
    assert start.index('if (!consent)') < start.index('PendingApiKey.take(activity)')
    for forbidden in ('manager.init(', 'manager.close(', 'manager.sendKeyboard(', 'registerApp(', 'startDiscovery(',
                      'Runtime.getRuntime', 'ProcessBuilder(', 'FileOutputStream', 'android.util.Log'):
        assert forbidden not in panel, f'Auto crossed frozen core/privacy boundary: {forbidden}'
    observation = (ai / 'AutoObservation.kt').read_text()
    require(observation, ('observedCommand == command', 'terminalState == "shell_prompt"',
                         'outcome == "appears_complete"', 'evidence.any { it.isNotBlank() } && !revisionNeeded',
                         'UNTRUSTED EVIDENCE', 'preflight=true', 'obj.get("revision_needed") !is Boolean',
                         '.put("store", false)', '.put("tool_choice", "none")',
                         'ProposalWire.exactKeys(obj', 'status") != "completed"'), 'vision')
    client = (ai / 'OpenAiAutoObserver.kt').read_text()
    speech = (ai / 'AssistantNarrator.kt').read_text()
    for code in (client, speech):
        require(code, ('as HttpsURLConnection', 'instanceFollowRedirects = false', 'ensureActive()', 'body.fill(0)'), 'HTTPS')
        for forbidden in ('HidManager', 'CommandApproval', 'sendKey(', 'FileOutputStream', 'android.util.Log', 'HostnameVerifier', 'TrustManager'):
            assert forbidden not in code, f'AI client gained input/storage/TLS bypass: {forbidden}'
    require(speech, ('https://api.openai.com/v1/audio/speech', 'gpt-4o-mini-tts', '"response_format", "pcm"',
                     '2_400_000', 'bytes?.fill(0)', '!it.isNetworkConnectionRequired',
                     'generation != token || closed', 'http?.disconnect()', 'speech?.shutdown()'), 'narration')
    narration = speech.split('internal object NarrationText', 1)[1].split('internal object OpenAiSpeechCodec', 1)[0]
    assert 'step.command' not in narration, 'Narration must explain purpose rather than read shell syntax'
    plan = (ai / 'CommandPlan.kt').read_text()
    require(plan, ('const val MAX_COMMANDS = 40', 'const val MAX_REQUEST_LENGTH = 32768',
                   'class PlanPhase', '.put("roadmap"', 'Maximum 40 commands', 'Prevent lockout'), 'detailed planning')
    entry = (ai / 'AiEntry.kt').read_text()
    approval = entry.split('private fun confirmSelected(', 1)[1].split('private fun editSelectedStep()', 1)[0]
    assert 'AlertDialog.Builder' not in approval, 'Duplicate command confirmation returned'
    require(approval, ('planRevision != snapshotRevision', 'selected != index', 'target.current() != host',
                       'CommandApproval(command, host)', 'ApprovedCommandSender.send'), 'single approval')
    print('PASS: scoped Auto Enter, foreground consent/stop, conservative vision, bounded revision, detailed plans and in-memory narration')


if __name__ == '__main__':
    verify()
