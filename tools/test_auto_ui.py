#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path
from shutil import copytree, ignore_patterns
from check_auto_ui import ROOT, verify


class AutoBoundaryTests(unittest.TestCase):
    def mutate(self, file, before, after):
        with tempfile.TemporaryDirectory() as temporary:
            dst = Path(temporary) / 'repo'
            copytree(ROOT, dst, ignore=ignore_patterns('.git', '.gradle', 'build', '__pycache__', 'docs'))
            p = dst / file
            original = p.read_text()
            self.assertIn(before, original)
            p.write_text(original.replace(before, after, 1))
            with self.assertRaises(AssertionError): verify(dst)

    def test_current_tree(self): verify(ROOT)
    def test_no_enter_in_ordinary_sender(self):
        self.mutate('app/src/main/java/com/blackcat/remote/CommandSafety.kt',
                    'delay(20)', 'target.sendKey(approval.host, 0, 40); delay(20)')
    def test_auto_must_check_host_before_enter(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoRunSession.kt',
                    'if (!session.accepts(approval, target.current(), revision()))', 'if (false)')
    def test_failed_send_cannot_execute(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoRunSession.kt',
                    'outcome != SendOutcome.REPORTS_ACCEPTED || !approval.enter', 'false')
    def test_stop_must_cancel_all_pending_jobs(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoAssistPanel.kt',
                    'scope.coroutineContext.cancelChildren()', '// removed')
    def test_upload_requires_consent(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoAssistPanel.kt', 'if (!consent)', 'if (false)')
    def test_preflight_cannot_be_bypassed(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoAssistPanel.kt', '!preflight.allowsNext("")', 'false')
    def test_vision_must_match_the_command(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoObservation.kt', 'observedCommand == command', 'true')
    def test_vision_must_see_a_shell_prompt(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoObservation.kt', 'terminalState == "shell_prompt"', 'true')
    def test_revised_plan_must_stay_in_scope(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoRunSession.kt',
                    'it.command in originallyApproved || AutoRunPolicy.canRun(it)', 'true')
    def test_readonly_cannot_trust_risk_alone(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoRunSession.kt', 'step.command in readOnly', 'true')
    def test_speech_cannot_follow_redirects(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AssistantNarrator.kt',
                    'instanceFollowRedirects = false', 'instanceFollowRedirects = true')
    def test_duplicate_command_dialog_rejected(self):
        # Insert inside the approval method, so the guard validates the actual send boundary.
        self.mutate('app/src/ai/java/com/blackcat/remote/AiEntry.kt',
                    'CommandPolicy.problem(command)?.let { setStatus(it, error = true); return }',
                    'AlertDialog.Builder(activity)\n        CommandPolicy.problem(command)?.let { setStatus(it, error = true); return }')
    def test_error_revision_requires_a_fresh_terminal_prompt(self):
        self.mutate('app/src/ai/java/com/blackcat/remote/AutoAssistPanel.kt',
                    'if (!value.canAutoRevise(current.current!!.command))', 'if (false)')


if __name__ == '__main__': unittest.main()
