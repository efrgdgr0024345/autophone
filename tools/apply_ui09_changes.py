#!/usr/bin/env python3
"""Apply the small UI09 edits to large frozen UI files with exact guards.

This script exists so the proven Bluetooth source is not rewritten by hand. Every edit must match
one exact UI08 source fragment, and rerunning the script is intentionally idempotent.
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, marker: str) -> bool:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return False
    count = text.count(old)
    assert count == 1, f"Expected one guarded source fragment in {path}, found {count}"
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    return True


def patch_ai_entry() -> bool:
    path = ROOT / "app/src/ai/java/com/blackcat/remote/AiEntry.kt"
    changed = False

    changed |= replace_once(
        path,
        '''        card.addView(actionRow, matchWrap(top = 12))
        stage.minimumHeight = dp(360)''',
        '''        card.addView(actionRow, matchWrap(top = 12))
        card.addView(
            actionButton("Browse Offline Linux Library", primary = false) {
                LinuxCommandLibraryPanel(activity, manager).show()
            },
            matchWrap(top = 8)
        )
        stage.minimumHeight = dp(360)''',
        'actionButton("Browse Offline Linux Library"',
    )

    changed |= replace_once(
        path,
        '''        add(Tab.PLAN, "Plan")
        add(Tab.STEP, "Step")
        add(Tab.FEEDBACK, "Feedback")
        add(Tab.PREVIEW, "Preview")''',
        '''        add(Tab.PLAN, "Plan")
        add(Tab.STEP, "Step")
        add(Tab.PREVIEW, "Preview")
        add(Tab.FEEDBACK, "Result")''',
        'add(Tab.FEEDBACK, "Result")',
    )

    changed |= replace_once(
        path,
        '''                card.addView(row, matchWrap(top = 5))
            }
        }
        planResult.addView(card, matchWrap())''',
        '''                card.addView(row, matchWrap(top = 5))
            }
            card.addView(
                actionButton("Start with step 1  →", true) {
                    selected = 0
                    switchTab(Tab.STEP)
                },
                matchWrap(top = 10)
            )
        }
        planResult.addView(card, matchWrap())''',
        'actionButton("Start with step 1  →"',
    )
    return changed


def patch_mobile_contract() -> bool:
    path = ROOT / "tools/check_mobile_ui.py"
    changed = False
    changed |= replace_once(
        path,
        "        'Plan', 'Step', 'Feedback', 'Preview', 'TYPE ONLY',",
        "        'Plan', 'Step', 'Preview', 'Result', 'TYPE ONLY',\n        'Browse Offline Linux Library', 'Start with step 1',",
        "'Browse Offline Linux Library', 'Start with step 1'",
    )
    changed |= replace_once(
        path,
        '''        'add(Tab.PLAN', 'add(Tab.STEP', 'add(Tab.FEEDBACK', 'add(Tab.PREVIEW' ''',
        '''        'add(Tab.PLAN', 'add(Tab.STEP', 'add(Tab.PREVIEW', 'add(Tab.FEEDBACK' ''',
        "'add(Tab.PLAN', 'add(Tab.STEP', 'add(Tab.PREVIEW', 'add(Tab.FEEDBACK'",
    )
    changed |= replace_once(
        path,
        "Reference flow tabs must be Plan → Step → Feedback → Preview",
        "Reference flow tabs must be Plan → Step → Preview → Result",
        "Reference flow tabs must be Plan → Step → Preview → Result",
    )
    changed |= replace_once(
        path,
        "PASS: UI08 12-screen reference flow, reviewed photo analysis, system-bar clearance, and frozen V2/API boundaries",
        "PASS: UI09 planner flow, offline Linux library, reviewed photo analysis, and frozen V2/API boundaries",
        "PASS: UI09 planner flow",
    )
    return changed


def patch_offline_contract() -> bool:
    path = ROOT / "tools/check_offline_catalog.py"
    return replace_once(
        path,
        '''    main_activity = (MAIN / "MainActivity.kt").read_text(encoding="utf-8")
    assert "Offline Linux Library" in main_activity, "AI build needs a visible offline-mode entry"
    assert main_activity.count("AiEntry.openAssistant(this@MainActivity){hid}") == 1, "Primary AI hook changed"
''',
        '''    online_entry = (
        ROOT / "app/src/ai/java/com/blackcat/remote/AiEntry.kt"
    ).read_text(encoding="utf-8")
    assert "Browse Offline Linux Library" in online_entry, "AI build needs a visible offline-mode entry"
    assert online_entry.count("LinuxCommandLibraryPanel(activity, manager).show()") == 1, "Offline library entry changed"
''',
        'assert online_entry.count("LinuxCommandLibraryPanel(activity, manager).show()") == 1',
    )


def patch_version() -> bool:
    path = ROOT / "app/build.gradle"
    text = path.read_text(encoding="utf-8")
    if "versionName '9.0-planner-offline-library'" in text:
        return False
    old = "        versionCode 10\n        versionName '8.0-reference-screen-flow'"
    new = "        versionCode 11\n        versionName '9.0-planner-offline-library'"
    assert text.count(old) == 1, "Unexpected Android version source"
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    return True


def write_ci_workflow() -> bool:
    source = ROOT / ".github/workflows/ui08-ci.yml"
    target = ROOT / ".github/workflows/ui09-ci.yml"
    original = source.read_text(encoding="utf-8")
    result = original
    replacements = (
        ("Black Cat UI08 reference-screen flow", "Black Cat UI09 planner and offline Linux library"),
        ("feature/ui08-reference-screen-flow", "feature/ui09-planner-offline-library"),
        ("ui08-${{ github.ref }}", "ui09-${{ github.ref }}"),
        ("Verify and build UI08", "Verify and build UI09"),
        ("Verify golden HID behavior and UI08 contract", "Verify golden HID behavior and UI09 contracts"),
        ("ui08-reports-${{ github.run_id }}", "ui09-reports-${{ github.run_id }}"),
        ("CatAI-08.apk", "CatAI-09.apk"),
        ("CatBT-08.apk", "CatBT-09.apk"),
        ("blackcat-ui08-${{ github.run_id }}", "blackcat-ui09-${{ github.run_id }}"),
        ("Publish exact UI08 prerelease", "Publish exact UI09 prerelease"),
        ('TAG="ui08-${GITHUB_SHA:0:8}"', 'TAG="ui09-${GITHUB_SHA:0:8}"'),
        ('TITLE="Black Cat UI08 physical-test candidate"', 'TITLE="Black Cat UI09 planner + offline library physical-test candidate"'),
        ("c9cb8c3572c5bf25a7efd241870552c0d37460a7", "6eec650cb3fdf2f0e5b9b463c9c20c10539eaa29"),
        ("UI08 applies the approved 12-screen reference layout above the tested UI07 core.", "UI09 adds the guided planner flow and offline Linux command library above the exact tested UI08 core."),
        ("Assistant tabs: Plan → Step → Feedback → Preview with Photo Feedback beside Get Plan.", "Assistant tabs: Plan → Step → Preview → Result, with an explicit Start with step 1 action."),
        ("Target and Settings are standalone reference-style screens using the existing saved-target and encrypted-key stores.", "Offline library: 212 explained commands arranged as Category → Topic → Command with ranked search."),
        ("UI08 applies the approved “Black Cat AI Remote – All Screens (v1)” reference flow above the preserved UI07 Bluetooth/AI/photo core.", "UI09 adds a phone-first AI planner progression and a 212-command offline Linux library above the preserved, tested UI08 Bluetooth/AI/photo core."),
        ("- UI08 reference-screen contract", "- UI09 mobile planner and offline-library contracts"),
    )
    for old, new in replacements:
        assert old in result, f"Missing workflow source fragment: {old}"
        result = result.replace(old, new)

    result = result.replace(
        "  push:\n    branches: [feature/ui09-planner-offline-library]\n",
        "  push:\n    branches: [feature/ui09-planner-offline-library]\n    paths-ignore: ['README.md']\n",
        1,
    )
    result = result.replace(
        "          python3 tools/check_mobile_ui.py\n",
        "          python3 tools/check_mobile_ui.py\n          python3 tools/check_offline_catalog.py\n",
        1,
    )
    result = result.replace(
        "          - frozen Bluetooth HID behavior and mappings\n",
        "          - frozen Bluetooth HID behavior and mappings\n          - 212-command offline catalog integrity and TYPE ONLY boundary\n",
        1,
    )
    result = result.replace(
        "          TYPE ONLY remains explicit; Enter is never appended automatically.\n",
        "          TYPE ONLY remains explicit; Enter is never appended automatically. The offline library performs no API or network call.\n",
        1,
    )

    if target.exists() and target.read_text(encoding="utf-8") == result:
        return False
    target.write_text(result, encoding="utf-8")
    return True


def main() -> None:
    changed = []
    for name, operation in (
        ("AI planner UI", patch_ai_entry),
        ("mobile contract", patch_mobile_contract),
        ("offline contract", patch_offline_contract),
        ("Android version", patch_version),
        ("UI09 CI", write_ci_workflow),
    ):
        if operation():
            changed.append(name)
    print("UI09 transformation complete: " + (", ".join(changed) if changed else "already applied"))


if __name__ == "__main__":
    main()
