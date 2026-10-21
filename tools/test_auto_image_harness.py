"""Adversarial tests for the disposable Linux execution and independent OCR boundaries."""
import json
import unittest
from pathlib import Path

from auto_image_demo_host import allowed, ocr_reply


class AutoImageHarnessTests(unittest.TestCase):
    path = '/tmp/bc-' + 'a' * 24

    def test_cleanup_requires_a_known_ticket_and_exact_bytes(self):
        command = f"rm -- {self.path}/r {self.path}/s {self.path}/o && rmdir -- {self.path} && printf '\\nBC{'a' * 24}_CLEAN_0\\n'"
        self.assertTrue(allowed(command, {self.path}))
        self.assertFalse(allowed(command, set()))
        self.assertFalse(allowed(command + '; id', {self.path}))
        self.assertFalse(allowed(command.replace('/r ', '/other '), {self.path}))

    def test_capture_prefix_and_benign_substring_cannot_authorize_arbitrary_shell(self):
        command = f'(umask 077; b={self.path}; mkdir "$b"; env TERM=dumb PAGER=cat SYSTEMD_PAGER=cat cat /etc/os-release </dev/null; id)'
        tickets = set()
        self.assertFalse(allowed(command, tickets))
        self.assertEqual(set(), tickets)

    def observed(self, suffix):
        marker = 'BC' + 'a' * 24 + '_CAP'
        request = {'kind': 'observation', 'preflight': False, 'production_schema': {},
                   'collection_context': json.dumps({'expected_marker': marker, 'page': 0})}
        response = ocr_reply(Path('/unused-test-output'), request, f'{marker}_{suffix}\ndemo@linux:~$ ')
        return json.loads(response['output'][0]['content'][0]['text'])

    def test_missing_completeness_suffix_cannot_be_accepted(self):
        self.assertNotEqual('captured', self.observed('0')['page_state'])
        self.assertNotEqual('captured', self.observed('0_DON')['page_state'])

    def test_exact_completion_and_overflow_are_distinct(self):
        self.assertEqual('captured', self.observed('0_DONE')['page_state'])
        value = self.observed('0_TRUNCATED')
        self.assertEqual('truncated', value['page_state'])
        self.assertEqual('unclear', value['outcome'])
        self.assertTrue(value['revision_needed'])

    def test_wrong_capture_id_is_rejected(self):
        marker = 'BC' + 'a' * 24 + '_CAP'
        request = {'kind': 'observation', 'preflight': False, 'production_schema': {},
                   'collection_context': json.dumps({'expected_marker': marker, 'page': 0})}
        response = ocr_reply(Path('/unused-test-output'), request, f"BC{'b' * 24}_CAP_0_DONE\ndemo@linux:~$ ")
        self.assertEqual('', json.loads(response['output'][0]['content'][0]['text'])['page_marker'])


if __name__ == '__main__':
    unittest.main()
