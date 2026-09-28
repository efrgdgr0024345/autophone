"""Portable proposal/photo contract. No network, Bluetooth or shell execution."""
from __future__ import annotations
import base64
import hashlib
import io
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from jsonschema import Draft202012Validator
from PIL import Image, ImageOps, ImageDraw

MODEL = 'gpt-4.1-mini-2025-04-14'
ENDPOINT = 'https://api.openai.com/v1/responses'
MAX_IMAGE_BYTES = 2_000_000
MAX_OUTPUT_TOKENS = 2400
PROMPT = Path(__file__).with_name('prompt.txt').read_text(encoding='utf-8')


def text(limit: int = 800) -> dict:
    return {'type': 'string', 'maxLength': limit}


def array(items: dict, limit: int = 8) -> dict:
    return {'type': 'array', 'items': items, 'maxItems': limit}


def obj(properties: dict) -> dict:
    return {'type': 'object', 'properties': properties, 'required': list(properties), 'additionalProperties': False}


STEP = obj({
    'id': {'type': 'string', 'pattern': '^s[1-9][0-9]*$'},
    'kind': {'type': 'string', 'enum': ['check', 'change', 'manual', 'decision', 'verify']},
    'title': text(120), 'purpose': text(500),
    'command': {'anyOf': [text(512), {'type': 'null'}]},
    'depends_on': array(text(16)), 'expected_result': text(600),
    'effects': text(500), 'if_unexpected': text(600), 'requires_admin': {'type': 'boolean'},
})
SCHEMA = obj({
    'schema_version': {'type': 'string', 'enum': ['guided-photo-1']},
    'goal_summary': text(600), 'out_of_scope': array(text(400), 5),
    'assumptions': array(text(400)), 'questions': array(text(500), 5),
    'observation': obj({
        'source': {'type': 'string', 'enum': ['none', 'user_text', 'photo']},
        'readability': {'type': 'string', 'enum': ['not_applicable', 'clear', 'uncertain', 'unreadable']},
        'safe_summary': text(1200),
        'terminal_state': {'type': 'string', 'enum': ['unknown', 'error', 'password_prompt', 'interactive_prompt', 'output', 'unrelated']},
        'uncertainties': array(text(400), 5),
        'requires_user_confirmation': {'type': 'boolean'},
    }),
    'proposed_changes': array(text(500)), 'steps': array(STEP),
})
VALIDATOR = Draft202012Validator(SCHEMA)


def validate_plan(plan: Any, has_photo: bool = False) -> dict:
    VALIDATOR.validate(plan)
    if has_photo and (plan['observation']['source'] != 'photo' or not plan['observation']['requires_user_confirmation']):
        raise ValueError('Photo interpretation must require user confirmation')
    if not has_photo and plan['observation']['source'] == 'photo':
        raise ValueError('Cannot invent a photo observation')
    seen = set()
    commands = []
    for step in plan['steps']:
        if step['id'] in seen or len(set(step['depends_on'])) != len(step['depends_on']):
            raise ValueError('Duplicate step/dependency')
        if any(dep not in seen for dep in step['depends_on']):
            raise ValueError('Dependencies must refer to earlier steps; no cycles')
        seen.add(step['id'])
        command = step['command']
        if step['kind'] in ('manual', 'decision'):
            if command is not None:
                raise ValueError('Manual/decision steps are not keyboard commands')
        else:
            if not isinstance(command, str) or not command.strip() or any(not 32 <= ord(c) <= 126 for c in command):
                raise ValueError('Command must be one printable ASCII line')
            commands.append(command)
    if plan['questions'] and commands:
        raise ValueError('Resolve blocking questions before proposing commands')
    if has_photo and plan['observation']['readability'] in ('uncertain', 'unreadable') and commands:
        raise ValueError('Do not derive commands from uncertain image text')
    return plan


def parse_response(raw: dict, has_photo: bool) -> dict:
    if raw.get('status') != 'completed':
        raise ValueError('Response incomplete; no sendable plan')
    texts = []
    for item in raw.get('output', []):
        if item.get('type') != 'message':
            continue
        for part in item.get('content', []):
            if part.get('type') == 'refusal':
                raise ValueError('Model refused; no sendable plan')
            if part.get('type') == 'output_text':
                texts.append(part['text'])
    if len(texts) != 1:
        raise ValueError('Expected exactly one complete proposal')
    return validate_plan(json.loads(texts[0]), has_photo)


@dataclass(frozen=True)
class Photo:
    jpeg: bytes

    @property
    def digest(self) -> str:
        return hashlib.sha256(self.jpeg).hexdigest()


def prepare_photo(data: bytes, crop: tuple | None = None, redactions: tuple = ()) -> Photo:
    """Reference preprocessing: orient, crop, flatten opaque redaction, resize, strip metadata.

    The caller MUST preview the resulting Photo and obtain approval for its exact digest.
    This module never captures a camera or uploads anything.
    """
    if not data or len(data) > 12_000_000:
        raise ValueError('Input image missing/too large')
    with Image.open(io.BytesIO(data)) as source:
        if source.width * source.height > 16_000_000 or getattr(source, 'n_frames', 1) != 1:
            raise ValueError('Single still image within pixel limit required')
        image = ImageOps.exif_transpose(source).convert('RGB')
        if crop is not None:
            _rectangle(crop, image.size)
            image = image.crop(crop)
        for rectangle in redactions:
            _rectangle(rectangle, image.size)
            ImageDraw.Draw(image).rectangle(rectangle, fill=(0, 0, 0))
        image.thumbnail((1600, 1600))
        # A new canvas carries pixels only, not EXIF, ICC, thumbnails or source metadata.
        clean = Image.new('RGB', image.size)
        clean.paste(image)
        out = io.BytesIO()
        clean.save(out, format='JPEG', quality=92, optimize=True)
    if len(out.getvalue()) > MAX_IMAGE_BYTES:
        raise ValueError('Crop/retake instead of silently shrinking unreadable text')
    return Photo(out.getvalue())


def _rectangle(rectangle: tuple, size: tuple) -> None:
    if len(rectangle) != 4 or any(type(n) is not int for n in rectangle):
        raise ValueError('Rectangle must use four integer coordinates')
    x0, y0, x1, y1 = rectangle
    if not (0 <= x0 < x1 <= size[0] and 0 <= y0 < y1 <= size[1]):
        raise ValueError('Crop/redaction lies outside the image')


def request_payload(context: dict, photo: Photo | None = None, approved_digest: str | None = None) -> dict:
    allowed = {'goal', 'target', 'current_step', 'feedback', 'previous_plan', 'user_confirmed_results'}
    if set(context) - allowed or not context.get('goal') or not context.get('target'):
        raise ValueError('Missing or unrelated context')
    encoded = json.dumps(context, ensure_ascii=False)
    if len(encoded) > 16000:
        raise ValueError('Context too large')
    content = [{'type': 'input_text', 'text': encoded}]
    if photo is not None:
        if approved_digest != photo.digest:
            raise ValueError('This exact photo must be reviewed before upload')
        if not photo.jpeg.startswith(b'\xff\xd8') or len(photo.jpeg) > MAX_IMAGE_BYTES:
            raise ValueError('Expected bounded reviewed JPEG')
        content.append({'type': 'input_image', 'image_url': 'data:image/jpeg;base64,' + base64.b64encode(photo.jpeg).decode('ascii'), 'detail': 'high'})
    return {
        'model': MODEL, 'instructions': PROMPT,
        'input': [{'role': 'user', 'content': content}],
        'store': False, 'stream': False, 'tools': [], 'tool_choice': 'none',
        'max_output_tokens': MAX_OUTPUT_TOKENS,
        'text': {'format': {'type': 'json_schema', 'name': 'guided_photo_plan', 'strict': True, 'schema': SCHEMA}},
    }
