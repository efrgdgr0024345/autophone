"""Adversarial tests for the disposable Linux execution and independent OCR boundaries."""
import json
import unittest
import tempfile
import shutil
from pathlib import Path

from auto_image_demo_host import allowed, ocr_reply, read_nonce_regions, render


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
        self.assertTrue(value['page_marker'].endswith('_0_TRUNCATED'))
        self.assertEqual('unclear', value['outcome'])
        self.assertTrue(value['revision_needed'])

    def test_cleanup_returns_the_full_printed_footer_to_exercise_production_normalization(self):
        marker = 'BC' + 'a' * 24 + '_CLEAN'
        request = {'kind': 'observation', 'preflight': False, 'production_schema': {},
                   'collection_context': json.dumps({'expected_marker': marker, 'page': -1})}
        response = ocr_reply(Path('/unused-test-output'), request, f'{marker}_0\ndemo@linux:~$ ')
        value = json.loads(response['output'][0]['content'][0]['text'])
        self.assertEqual(marker + '_0', value['page_marker'])
        self.assertEqual('cleaned', value['page_state'])

    def test_wrong_capture_id_is_rejected(self):
        marker = 'BC' + 'a' * 24 + '_CAP'
        request = {'kind': 'observation', 'preflight': False, 'production_schema': {},
                   'collection_context': json.dumps({'expected_marker': marker, 'page': 0})}
        response = ocr_reply(Path('/unused-test-output'), request, f"BC{'b' * 24}_CAP_0_DONE\ndemo@linux:~$ ")
        self.assertEqual('', json.loads(response['output'][0]['content'][0]['text'])['page_marker'])

    def test_nonce_region_is_read_from_pixels_without_request_context(self):
        nonce = '2ba2b502b1af27d9253b9f0c'
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            render(output, f'BC{nonce}_P1_1_8_END_0_MORE\ndemo@linux:~$ ', full_width=True)
            text, reads = read_nonce_regions(output / 'feed.jpg')
            self.assertGreater(reads, 0)
            self.assertIn('BC' + nonce, text)

    def test_field_reread_never_substitutes_the_expected_nonce(self):
        actual = '2ba2b502b1af27d9253b9f0c'
        expected = 'a' * 24
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            render(output, f'BC{actual}_P1_1_8_END_0_MORE\ndemo@linux:~$ ', full_width=True)
            shutil.copyfile(output / 'feed.jpg', output / 'photo-001.jpg')
            request = {'kind': 'observation', 'preflight': False, 'id': '001', 'production_schema': {},
                       'collection_context': json.dumps({'expected_marker': f'BC{expected}_P1_1_8', 'page': 1,
                                                        'earlier_pages_untrusted': []})}
            reply = ocr_reply(output, request, field_reader=True)
            value = json.loads(reply['output'][0]['content'][0]['text'])
            self.assertEqual('', value['page_marker'])


if __name__ == '__main__':
    unittest.main()
