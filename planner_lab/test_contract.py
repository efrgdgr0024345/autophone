import io
import json
import unittest
from copy import deepcopy
from PIL import Image
from planner_lab.contract import SCHEMA, prepare_photo, request_payload, parse_response, validate_plan
from planner_lab.state import PlanSession


def sample(photo=False):
    def step(i, kind, command, deps):
        return {'id': i, 'kind': kind, 'title': 'Test step', 'purpose': 'Advance the stated goal', 'command': command,
                'depends_on': deps, 'expected_result': 'Inspect result', 'effects': 'Explained to user',
                'if_unexpected': 'Stop and report the result', 'requires_admin': kind == 'change'}
    return {'schema_version': 'guided-photo-1', 'goal_summary': 'Create standard alice', 'out_of_scope': ['No admin rights'],
            'assumptions': [], 'questions': [], 'proposed_changes': [],
            'observation': {'source': 'photo' if photo else 'none', 'readability': 'clear' if photo else 'not_applicable',
                            'safe_summary': 'Observed account information' if photo else '', 'terminal_state': 'output' if photo else 'unknown',
                            'uncertainties': [], 'requires_user_confirmation': photo},
            'steps': [step('s1', 'check', 'id', []), step('s2', 'change', 'sudo adduser alice', ['s1']),
                      step('s3', 'manual', None, ['s2']), step('s4', 'verify', 'id alice', ['s3'])]}


def image_bytes(exif=False):
    out = io.BytesIO()
    im = Image.new('RGB', (300, 180), 'white')
    metadata = Image.Exif()
    if exif:
        metadata[0x010E] = 'private description'
        metadata[0x0112] = 6
    im.save(out, format='JPEG', exif=metadata)
    return out.getvalue()


class ContractTests(unittest.TestCase):
    def test_valid_plan(self):
        self.assertEqual(validate_plan(sample())['steps'][0]['command'], 'id')

    def test_manual_command_rejected(self):
        p = sample(); p['steps'][2]['command'] = 'password'
        with self.assertRaises(Exception): validate_plan(p)

    def test_hidden_controls_rejected(self):
        for value in ['id\nwhoami', 'id\r', 'id\t', '\x1b[2J', 'echo \u202etxt', 'echo \x00']:
            with self.subTest(value=repr(value)):
                p = sample(); p['steps'][0]['command'] = value
                with self.assertRaises(Exception): validate_plan(p)

    def test_blank_command_rejected(self):
        p = sample(); p['steps'][0]['command'] = ' '
        with self.assertRaises(Exception): validate_plan(p)

    def test_duplicate_id_rejected(self):
        p = sample(); p['steps'][1]['id'] = 's1'
        with self.assertRaises(Exception): validate_plan(p)

    def test_forward_or_cyclic_dependency_rejected(self):
        p = sample(); p['steps'][0]['depends_on'] = ['s4']
        with self.assertRaises(Exception): validate_plan(p)

    def test_unknown_dependency_rejected(self):
        p = sample(); p['steps'][1]['depends_on'] = ['s99']
        with self.assertRaises(Exception): validate_plan(p)

    def test_photo_needs_readback(self):
        p = sample(True); p['observation']['requires_user_confirmation'] = False
        with self.assertRaises(Exception): validate_plan(p, True)

    def test_photo_observation_cannot_be_invented(self):
        with self.assertRaises(Exception): validate_plan(sample(True), False)

    def test_unreadable_photo_cannot_propose_commands(self):
        p = sample(True); p['observation']['readability'] = 'unreadable'
        with self.assertRaises(Exception): validate_plan(p, True)

    def test_question_blocks_speculative_commands(self):
        p = sample(); p['questions'] = ['Which account?']
        with self.assertRaises(Exception): validate_plan(p)

    def test_model_cannot_set_completed_state(self):
        p = sample(); p['steps'][0]['completed'] = True
        with self.assertRaises(Exception): validate_plan(p)

    def test_no_tools_no_saved_server_state(self):
        p = request_payload({'goal': 'Show account', 'target': 'Local Ubuntu Bash'})
        self.assertFalse(p['store']); self.assertEqual(p['tools'], []); self.assertEqual(p['tool_choice'], 'none')
        self.assertNotIn('previous_response_id', p)
        self.assertEqual(p['text']['format']['schema'], SCHEMA)

    def test_unrelated_context_rejected(self):
        with self.assertRaises(ValueError): request_payload({'goal': 'x', 'target': 'y', 'bluetooth_mac': 'private'})

    def test_no_photo_without_explicit_review(self):
        ph = prepare_photo(image_bytes())
        with self.assertRaises(ValueError): request_payload({'goal': 'x', 'target': 'y'}, ph)

    def test_photo_payload_uses_reviewed_bytes(self):
        ph = prepare_photo(image_bytes())
        payload = request_payload({'goal': 'x', 'target': 'y'}, ph, ph.digest)
        image = payload['input'][0]['content'][1]
        self.assertEqual(image['type'], 'input_image')
        self.assertTrue(image['image_url'].startswith('data:image/jpeg;base64,'))

    def test_edited_photo_invalidates_upload_consent(self):
        a = prepare_photo(image_bytes())
        b = prepare_photo(image_bytes(), redactions=((5, 5, 80, 80),))
        with self.assertRaises(ValueError): request_payload({'goal': 'x', 'target': 'y'}, b, a.digest)

    def test_orientation_and_metadata_removed(self):
        ph = prepare_photo(image_bytes(True))
        with Image.open(io.BytesIO(ph.jpeg)) as im:
            self.assertEqual(im.size, (180, 300)); self.assertFalse(im.getexif())
        self.assertNotIn(b'private description', ph.jpeg)

    def test_opaque_redaction_is_flattened(self):
        ph = prepare_photo(image_bytes(), redactions=((20, 20, 100, 100),))
        with Image.open(io.BytesIO(ph.jpeg)) as im: self.assertTrue(max(im.getpixel((60, 60))) < 10)

    def test_invalid_redaction_not_silently_ignored(self):
        with self.assertRaises(ValueError): prepare_photo(image_bytes(), redactions=((-1, 0, 100, 100),))

    def test_crop_to_relevant_area(self):
        ph = prepare_photo(image_bytes(), crop=(10, 20, 210, 120))
        with Image.open(io.BytesIO(ph.jpeg)) as im: self.assertEqual(im.size, (200, 100))

    def test_incomplete_response_rejected(self):
        with self.assertRaises(ValueError): parse_response({'status': 'incomplete', 'output': []}, False)

    def test_model_refusal_rejected(self):
        with self.assertRaises(ValueError): parse_response({'status': 'completed', 'output': [{'type': 'message', 'content': [{'type': 'refusal'}]}]}, False)

    def test_valid_response(self):
        raw = {'status': 'completed', 'output': [{'type': 'message', 'content': [{'type': 'output_text', 'text': json.dumps(sample())}]}]}
        self.assertEqual(parse_response(raw, False)['schema_version'], 'guided-photo-1')


class StateTests(unittest.TestCase):
    def setUp(self):
        self.s = PlanSession()
        self.s.receive(sample(), self.s.token()); self.s.accept_revision()

    def test_selection_does_not_type(self):
        self.s.select('s1'); self.assertFalse(self.s.keyboard.characters)

    def test_photo_approval_is_not_command_approval(self):
        self.s.attach_photo('image'); self.s.approve_photo('image')
        self.assertIsNone(self.s.approval); self.assertFalse(self.s.keyboard.characters)

    def test_photo_response_does_not_advance(self):
        self.s.attach_photo('image'); self.s.approve_photo('image')
        self.s.receive(sample(True), self.s.token())
        with self.assertRaises(ValueError): self.s.accept_revision()
        self.assertFalse(self.s.confirmed); self.assertFalse(self.s.keyboard.characters)

    def test_readback_confirmation_does_not_send(self):
        self.s.attach_photo('image'); self.s.approve_photo('image')
        self.s.receive(sample(True), self.s.token()); self.s.confirm_observation(); self.s.accept_revision()
        self.assertFalse(self.s.confirmed); self.assertFalse(self.s.keyboard.characters)

    def test_late_result_rejected(self):
        token = self.s.token(); self.s.invalidate()
        self.assertFalse(self.s.receive(sample(), token))

    def test_changed_step_rejects_previous_result(self):
        self.s.select('s1'); token = self.s.token(); self.s.select('s2')
        self.assertFalse(self.s.receive(sample(), token))

    def test_dependencies_require_human_confirmation(self):
        self.s.select('s2')
        with self.assertRaises(ValueError): self.s.approve_command(True)
        self.s.confirm_result('s1'); self.assertIsNotNone(self.s.approve_command(True))

    def test_manual_step_not_sendable(self):
        self.s.select('s3')
        with self.assertRaises(ValueError): self.s.approve_command(True)

    def test_approval_requires_terminal_review(self):
        self.s.select('s1')
        with self.assertRaises(ValueError): self.s.approve_command(False)

    def test_types_only_without_enter(self):
        self.s.select('s1'); self.s.simulate_send(self.s.approve_command(True))
        self.assertEqual(''.join(self.s.keyboard.characters), 'id')
        self.assertEqual(self.s.status, 'typed_result_unknown'); self.assertFalse(self.s.confirmed)

    def test_one_use_approval(self):
        self.s.select('s1'); a = self.s.approve_command(True); self.s.simulate_send(a)
        with self.assertRaises(ValueError): self.s.simulate_send(a)

    def test_host_change_rejects_send(self):
        self.s.select('s1'); a = self.s.approve_command(True); self.s.keyboard.host = 'different-session'
        with self.assertRaises(ValueError): self.s.simulate_send(a)
        self.assertFalse(self.s.keyboard.characters)

    def test_capture_invalidates_approval(self):
        self.s.select('s1'); a = self.s.approve_command(True); self.s.attach_photo('new-photo')
        with self.assertRaises(ValueError): self.s.simulate_send(a)

    def test_changed_photo_rejects_old_response(self):
        self.s.attach_photo('one'); self.s.approve_photo('one'); token = self.s.token()
        self.s.attach_photo('two')
        self.assertFalse(self.s.receive(sample(True), token))

    def test_partial_send_never_resumes(self):
        self.s.select('s1'); a = self.s.approve_command(True)
        self.assertFalse(self.s.simulate_send(a, interrupt_after=1))
        self.assertEqual(self.s.keyboard.characters, ['i'])
        with self.assertRaises(ValueError): self.s.simulate_send(a)

    def test_modified_step_does_not_inherit_completion(self):
        self.s.confirm_result('s1'); p = sample(); p['steps'][0]['command'] = 'whoami'
        self.s.receive(p, self.s.token()); self.s.accept_revision()
        self.assertNotIn('s1', self.s.confirmed)


if __name__ == '__main__': unittest.main()
