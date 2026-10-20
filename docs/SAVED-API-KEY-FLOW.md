# Saved API keys in the task flow

Auto uses the user's OpenAI key on the phone. There is no developer or cloud-hosted
key for normal operation. In Settings, enter and check a key with **Remember
encrypted key on this phone** selected to reuse it across plans and Auto sessions.
The existing Android Keystore-backed vault supplies the key for planning,
automatic photo observations, plan revisions and optional OpenAI speech.

A remembered key does not require re-entry or a new key approval for each plan.
Auto session consent still covers the goal, destination, command execution and
automatic photo uploads. Stopping Auto clears its in-memory key without deleting
the remembered encrypted key. Revoked, unreadable or forgotten keys require
attention in Settings; a stored file alone does not prove API access.

The plan status distinguishes saved, next-request temporary and missing keys.
When a saved key exists, Auto creates no temporary-key button or editable key
field. Key checking, replacement and forgetting remain in Settings. Without a
saved key, the existing optional temporary Auto-session input remains available.
A temporary key used for planning is consumed by that request; it is not silently
retained for a later Auto session.

This source change starts from `c51aca0c00a862087c14c295e0c78256ee92e503` on
`feature/ui18-saved-key-controls`. It changes only `AiEntry.kt` and
`AutoAssistPanel.kt`; vault, permissions, transport and consent rules are unchanged.
It is not included in the previously published CatAI-17 APK.

Validation: all 170 AI unit tests and 60 existing UI policy/mutation checks pass,
along with the golden Bluetooth, mobile UI, photo-feedback and Auto boundary
checks. No live API requests or physical phone tests were performed for this
display-only change. AI Lint completes with zero errors and 79 warnings, including
the existing programmatic UI's untranslated text and API compatibility notices.
