#!/usr/bin/env python3
"""Validate the offline Linux library and its non-executing, TYPE-ONLY boundary."""
from __future__ import annotations

import base64
import csv
import gzip
import hashlib
import io
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "app/src/main/assets"
ASSET_PARTS = [ASSET_DIR / f"linux_commands_{index:02d}.b64" for index in range(7)]
EXPECTED_GZIP_SHA256 = "7c579dc600f4073187800b948893feb2a2231f5a1af91429a50a82ff9880ae35"
MAIN = ROOT / "app/src/main/java/com/blackcat/remote"
EXPECTED_HEADER = [
    "id", "category", "topic", "title", "command", "explanation",
    "expected", "risk", "admin", "compatibility", "tags",
]
FORBIDDEN_COMMAND_FRAGMENTS = (
    "rm -rf /",
    "mkfs.",
    "wipefs ",
    "dd if=",
    "shutdown ",
    "poweroff",
    "reboot",
    ":(){",
    "| sh",
    "| bash",
    "chmod -r 777 /",
    "chown -r ",
)


def load_rows() -> list[dict[str, str]]:
    for part in ASSET_PARTS:
        assert part.exists() and part.stat().st_size > 2_000, f"Catalog chunk missing or too small: {part.name}"
    encoded = "".join(part.read_text(encoding="ascii").strip() for part in ASSET_PARTS)
    assert len(encoded) > 20_000, "Compressed command catalog is implausibly small"
    compressed = base64.b64decode(encoded, validate=True)
    assert hashlib.sha256(compressed).hexdigest() == EXPECTED_GZIP_SHA256, "Offline catalog bytes changed unexpectedly"
    with gzip.open(io.BytesIO(compressed), "rt", encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        assert reader.fieldnames == EXPECTED_HEADER, "Offline command header changed"
        return list(reader)


def verify() -> None:
    rows = load_rows()
    assert len(rows) >= 200, f"Expected at least 200 offline commands, found {len(rows)}"
    ids = [row["id"] for row in rows]
    assert len(ids) == len(set(ids)), "Offline command IDs must be unique"

    categories = Counter()
    topics: set[tuple[str, str]] = set()
    for row in rows:
        ident = row["id"]
        assert re.fullmatch(r"[a-z0-9][a-z0-9-]{2,79}", ident), f"Invalid command ID: {ident}"
        for field in ("category", "topic", "title", "command", "explanation", "expected", "compatibility"):
            assert row[field].strip(), f"{ident}: missing {field}"
            assert "\t" not in row[field] and "\r" not in row[field] and "\n" not in row[field], f"{ident}: control character in {field}"
        command = row["command"]
        assert len(command) <= 512, f"{ident}: command too long"
        assert all(32 <= ord(char) <= 126 for char in command), f"{ident}: command must be printable ASCII"
        lower = command.lower()
        assert not any(fragment in lower for fragment in FORBIDDEN_COMMAND_FRAGMENTS), f"{ident}: destructive/opaque command excluded from offline library"
        assert row["risk"] in {"low", "medium", "high"}, f"{ident}: invalid risk"
        assert row["admin"] in {"true", "false"}, f"{ident}: invalid admin flag"
        if re.search(r"(^| )sudo( |$)", command):
            assert row["admin"] == "true", f"{ident}: sudo command not marked admin"
        if row["risk"] == "high":
            assert row["admin"] == "true", f"{ident}: high-risk entry must be explicitly admin"
        categories[row["category"]] += 1
        topics.add((row["category"], row["topic"]))

    assert len(categories) >= 12, "Offline tree needs broad category coverage"
    assert len(topics) >= 35, "Offline tree needs useful topic depth"
    assert categories["Network"] >= 25, "Network troubleshooting coverage regressed"

    searchable = "\n".join(
        " ".join(
            row[key].lower()
            for key in ("category", "topic", "title", "command", "explanation", "expected", "tags")
        )
        for row in rows
    )
    for concept in (
        "network", "wifi", "dns", "disk space", "memory", "cpu", "temperature",
        "ports", "firewall", "systemd", "logs", "permissions", "packages",
        "docker", "git", "ssh",
    ):
        assert concept in searchable, f"Missing search coverage for: {concept}"

    catalog = (MAIN / "LinuxCommandCatalog.kt").read_text(encoding="utf-8")
    for required in (
        "MIN_EXPECTED_COMMANDS = 200",
        "ASSET_PARTS",
        "GZIPInputStream",
        "fun categories(",
        "fun topics(",
        "fun search(",
        "SYNONYMS",
        "CommandPolicy.problem(command)",
    ):
        assert required in catalog, f"Catalog implementation missing: {required}"

    panel = (MAIN / "LinuxCommandLibraryPanel.kt").read_text(encoding="utf-8")
    for required in (
        "Offline Linux Library",
        "no API · no network",
        "Browse by task",
        "Approve & type once",
        "TYPE ONLY",
        "CommandPolicy.problem",
        "CommandApproval",
        "ApprovedCommandSender.send",
        "press Enter yourself",
        "LinuxCommandCatalog.search",
    ):
        assert required in panel, f"Offline mobile flow missing: {required}"
    for forbidden in (
        "KEYCODE_ENTER",
        "Runtime.getRuntime",
        "ProcessBuilder(",
        "HttpsURLConnection",
        "URL(",
        "manager.init(",
        "manager.close(",
        "registerApp(",
        "unregisterApp(",
        "startDiscovery(",
        "createBond(",
    ):
        assert forbidden not in panel, f"Offline library crossed a safety boundary: {forbidden}"

    target = (MAIN / "LibraryHidTarget.kt").read_text(encoding="utf-8")
    for forbidden in (
        "registerApp(",
        "unregisterApp(",
        "getProfileProxy(",
        "createBond(",
        "startDiscovery(",
        "manager.close(",
    ):
        assert forbidden not in target, f"Library transport may only observe existing HID state: {forbidden}"
    assert ") : CommandTarget" in target and "manager.sendKeyboard" in target

    offline_entry = (
        ROOT / "app/src/offline/java/com/blackcat/remote/AiEntry.kt"
    ).read_text(encoding="utf-8")
    assert "LinuxCommandLibraryPanel" in offline_entry, "Offline build must open the local command library"
    assert "AI features are available" not in offline_entry, "Offline assistant may not remain a placeholder"

    online_entry = (
        ROOT / "app/src/ai/java/com/blackcat/remote/AiEntry.kt"
    ).read_text(encoding="utf-8")
    assert "Browse Offline Linux Library" in online_entry, "AI build needs a visible offline-mode entry"
    assert online_entry.count("LinuxCommandLibraryPanel(activity, manager, onClosed = { libraryPanel = null }, onSelectForPlan") == 1, "Offline library selection entry changed"
    assert "workspace.append(SuggestedCommand(item.title, item.command" in online_entry, "Library selection must update local plan data only"

    from check_learning_catalog import verify as verify_learning
    verify_learning(ROOT, schema=False)

    print(
        f"PASS: preserved {len(rows)} offline commands, {len(categories)} categories, "
        f"{len(topics)} topics; no API or automatic Enter path"
    )


if __name__ == "__main__":
    verify()
