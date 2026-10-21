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
    require(state, ('fun canRun(step: SuggestedCommand) = step.command in readOnly', '!step.requiresAdmin', 'step.risk == "low"',
                    'currentHost == host && currentRevision == revision', 'alreadyApproved',
                    'if (!human && !enter) return null', 'approval.generation == generation',
                    'ApprovedCommandSender.send(approval.text, target)',
                    'outcome != SendOutcome.REPORTS_ACCEPTED || !approval.enter',
                    'currentCoroutineContext().ensureActive()',
                    'if (!session.accepts(approval, target.current(), revision()))',
                    'target.sendKey(session.host, 0, 40)', 'revisions < MAX_REVISIONS',
                    'canRun(it) &&',
                    'executed.none { previous -> AutoRunPolicy.identity(previous) == AutoRunPolicy.identity(collection.original(it.command)) } || AutoRunPolicy.isReadOnly(it)',
                    'const val MAX_CAPTURES = 120'), 'Auto session/Enter/scope')
    assert state.count('target.sendKey(session.host, 0, 40)') == 1
    assert state.count('if (!session.accepts(approval, target.current(), revision()))') == 2
    panel = (ai / 'AutoAssistPanel.kt').read_text()
    require(panel, ('Stop / close', 'consent.isChecked', 'run.check(choiceIds[0])',
                    'if (!consent)', 'previewFrames < 3', 'preflight.allowsNext("")',
                    'current.valid(target.current(), revision())', 'preflight = true',
                    'current.captured()', 'current.observed(token)', 'value.allowsNext(current.current!!.command)',
                    'current.canAdopt(candidate)', 'current.adopt(candidate, newRevision)',
                    'revisionRequests >= AutoRunSession.MAX_REVISIONS', 'session?.stop()', 'scope.coroutineContext.cancelChildren()',
                    'observer.cancel()', 'planner.cancel()', 'narrator.stop()',
                    'key = ""', 'bytes?.fill(0)', 'camera?.close()',
                    'No command was sent', 'startSession'
                    ), 'foreground coordinator')
    show = panel.split('fun show()', 1)[1].split('fun dismiss()', 1)[0]
    for forbidden in ('observer.observe(', 'planner.propose(', 'AutoReadOnlySender.send(', 'PendingApiKey.take('):
        assert forbidden not in show, f'Opening Auto triggers work: {forbidden}'
    start = panel.split('private fun startSession(', 1)[1].split('private fun renderSession()', 1)[0]
    assert start.index('if (!consent)') < start.index('PendingApiKey.take(activity)')
    revise = panel.split('private suspend fun proposeRevision(', 1)[1].split('private fun explainStep()', 1)[0]
    assert 'if (!value.canAutoRevise(current.current!!.command) && !(operation != null && value.collectionReady(operation)))' in revise, 'Error feedback lost its fresh-prompt guard'
    assert revise.index('if (!value.canAutoRevise(current.current!!.command) && !(operation != null && value.collectionReady(operation)))') < revise.index('planner.propose('), 'Error feedback must establish a fresh prompt before any automatic revision'
    assert 'current.index != originIndex' in revise and 'current.stage != AutoStage.REVIEW_RESULT' in revise, 'Late error revisions must remain bound to their original result'
    for forbidden in ('manager.init(', 'manager.close(', 'manager.sendKeyboard(', 'registerApp(', 'startDiscovery(',
                      'Runtime.getRuntime', 'ProcessBuilder(', 'FileOutputStream', 'android.util.Log'):
        assert forbidden not in panel, f'Auto crossed frozen core/privacy boundary: {forbidden}'
    observation = (ai / 'AutoObservation.kt').read_text()
    assert observation.count('observedCommand == command') == 2, 'Both progression and revision must match the actual command'
    assert observation.count('terminalState == "shell_prompt"') == 4, 'Both progression and revision require a fresh shell prompt'
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
    collector = (ai / 'AutoOutputCollection.kt').read_text()
    assert 'ulimit' not in collector, 'Output capture must not limit files written by the command'
    require(collector, ('SecureRandom()', 'umask 077', 'head -c 1048577', 'echo _TRUNCATED', 'cat >/dev/null', 'LC_ALL=C cat -v',
                        'fold -w 64', 'const val LINES = 8', 'const val MAX_PAGES = 16',
                        'CommandPolicy.problem(command)', 'operations[command]', 'ticket.pages.add(value.pageText)',
                        'if (page > MAX_PAGES) return null', 'Never repeat a transmitted change',
                        'Do not ask the person to scroll'), 'bounded output collection')
    require(state, ('AutoActionContracts.match(words) != null', 'history.interrupt()', '&& !expired',
                    '!op.ticket.truncated', 'fun recordEvidence('), 'diagnostic contracts and truthful task state')
    require(panel, ('operation.ticket.truncated = true', 'current.recordEvidence(value)', 'AutoTaskState.OBSERVED'), 'incomplete evidence')
    require(panel, ('current.insertCollection(step, newRevision)', 'value.collectionReady(operation)',
                    'current.collection.retain(operation, value)', 'current.waitAgain()',
                    'collectionContext =', 'AutoRevisionContext.goal', 'Live command list',
                    'current.prepareCurrent', 'session?.collection?.clear()'), 'adaptive coordinator')
    require(entry, ('quickControls.addView(autoPlayButton', 'root.addView(quickControls', 'quickControls.addView(quickVoiceButton', 'autoPlayButton.isEnabled = commands.isNotEmpty()',
                    'label(command.command, 13f', '▶ Auto Play'), 'visible Auto/command list')
    assert observation.count('pageMarker == op.marker') == 2, 'Page acceptance and resize both require the actual unique marker'
    require(observation, ('pageMarker == op.marker', 'op.ticket.exitCode == exitCode',
                          'page_state', 'page_text', 'earlier pages', 'ALL text between them legible'), 'complete page identity')
    print('PASS: scoped Auto Enter, foreground consent/stop, conservative vision, bounded revision, detailed plans and in-memory narration')


if __name__ == '__main__':
    verify()
