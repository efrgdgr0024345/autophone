"""UI-contract reference with a fake sender ONLY. No real transport can be attached."""
from __future__ import annotations
from copy import deepcopy
from dataclasses import dataclass
from .contract import validate_plan


@dataclass(frozen=True)
class RequestToken:
    revision: int
    context: int
    step: str | None
    photo_digest: str | None


@dataclass(frozen=True)
class Approval:
    revision: int
    context: int
    step: str
    command: str
    host: str


class FakeKeyboard:
    def __init__(self):
        self.characters = []
        self.host = 'lab-host-session-1'


class PlanSession:
    def __init__(self):
        self.plan = None
        self.pending = None
        self.pending_token = None
        self.revision = 0
        self.context = 0
        self.selected = None
        self.photo_digest = None
        self.photo_approved = False
        self.observation_confirmed = False
        self.confirmed = set()
        self.approval = None
        self.keyboard = FakeKeyboard()
        self.status = 'no_plan'

    def token(self):
        return RequestToken(self.revision, self.context, self.selected, self.photo_digest)

    def select(self, step):
        if self.plan is None or step not in {s['id'] for s in self.plan['steps']}:
            raise ValueError('Unknown step')
        self.selected = step
        self.approval = None

    def attach_photo(self, digest):
        self.context += 1
        self.photo_digest = digest
        self.photo_approved = False
        self.observation_confirmed = False
        self.pending = None
        self.approval = None
        self.status = 'photo_review'

    def approve_photo(self, digest):
        if digest != self.photo_digest or digest is None:
            raise ValueError('Photo changed or missing')
        self.photo_approved = True
        # Not a keyboard approval, result confirmation, or permission to advance.

    def receive(self, proposal, token):
        if token != self.token():
            return False
        if token.photo_digest and not self.photo_approved:
            raise ValueError('Photo was not approved for submission')
        validate_plan(proposal, token.photo_digest is not None)
        self.pending = deepcopy(proposal)
        self.pending_token = token
        self.approval = None
        self.observation_confirmed = token.photo_digest is None
        self.status = 'interpretation_review' if token.photo_digest else 'plan_review'
        return True

    def confirm_observation(self):
        if self.pending is None or self.pending_token != self.token():
            raise ValueError('No current observation')
        self.observation_confirmed = True
        self.status = 'plan_review'

    def accept_revision(self):
        if self.pending is None or self.pending_token != self.token() or not self.observation_confirmed:
            raise ValueError('Review current interpretation first')
        old = {s['id']: s for s in self.plan['steps']} if self.plan else {}
        self.plan = self.pending
        self.pending = None
        self.confirmed = {s['id'] for s in self.plan['steps'] if s['id'] in self.confirmed and old.get(s['id']) == s}
        self.revision += 1
        self.selected = None
        self.photo_digest = None
        self.photo_approved = False
        self.approval = None
        self.status = 'planned'

    def confirm_result(self, step):
        if self.plan is None or step not in {s['id'] for s in self.plan['steps']}:
            raise ValueError('Unknown result')
        # Represents an explicit USER confirmation, never called by a model/parser.
        self.confirmed.add(step)

    def invalidate(self):
        self.context += 1
        self.pending = None
        self.approval = None
        self.photo_approved = False
        self.status = 'review_required'

    def approve_command(self, empty_prompt_confirmed):
        if not empty_prompt_confirmed or self.pending is not None or self.photo_digest is not None:
            raise ValueError('Human review/checkpoint required')
        step = next((s for s in self.plan['steps'] if s['id'] == self.selected), None) if self.plan else None
        if not step or step['command'] is None or any(d not in self.confirmed for d in step['depends_on']):
            raise ValueError('Step not ready or not a command')
        if self.keyboard.host is None:
            raise ValueError('No destination')
        self.approval = Approval(self.revision, self.context, step['id'], step['command'], self.keyboard.host)
        return self.approval

    def simulate_send(self, approval, interrupt_after=None):
        if approval is not self.approval or approval is None:
            raise ValueError('Fresh explicit approval required')
        self.approval = None
        if (approval.revision, approval.context, approval.host) != (self.revision, self.context, self.keyboard.host):
            raise ValueError('Destination or context changed')
        for index, char in enumerate(approval.command):
            if interrupt_after is not None and index == interrupt_after:
                self.invalidate()
            if (approval.context, approval.host) != (self.context, self.keyboard.host):
                self.status = 'partial_text'
                return False
            self.keyboard.characters.append(char)
        self.status = 'typed_result_unknown'
        return True
