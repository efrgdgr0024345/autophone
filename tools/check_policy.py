#!/usr/bin/env python3
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

frozen = {
    "HidManager.kt": "9f2b44f1b927567ccb6dc38cfb0bb9c1e7157d52",
    "HidDescriptor.kt": "62a1f0b98f1608680e3df47c8a59f5359f8b100a",
    "HidReports.kt": "5b4369900a92948a58b5d25fcab1022ca8d7fdcf",
    "Diagnostics.kt": "50f57a2bcdbb3b37b245023441178314f47940ab",
}
for name, expected in frozen.items():
    path = Path("app/src/main/java/com/blackcat/remote") / name
    actual = subprocess.check_output(["git", "hash-object", str(path)], text=True).strip()
    assert actual == expected, f"Golden HID core changed: {name}"

android = "{http://schemas.android.com/apk/res/android}"
def permissions(path):
    return {x.attrib[android + "name"] for x in ET.parse(path).getroot().findall("uses-permission")}

base = {"android.permission." + x for x in ("BLUETOOTH","BLUETOOTH_ADMIN","BLUETOOTH_CONNECT","BLUETOOTH_ADVERTISE")}
assert permissions("app/src/main/AndroidManifest.xml") == base
assert permissions("app/src/ai/AndroidManifest.xml") == {"android.permission.INTERNET"}
app = ET.parse("app/src/main/AndroidManifest.xml").getroot().find("application")
assert app.attrib[android + "allowBackup"] == "false"
assert app.attrib[android + "usesCleartextTraffic"] == "false"
planner = Path("app/src/ai/java/com/blackcat/remote/OpenAiPlanner.kt").read_text()
assert "CommandTarget" not in planner
assert "android.util.Log" not in planner
assert "ProcessBuilder" not in planner and "Runtime.getRuntime" not in planner
assert Path("GHOSTBOARD_LICENSE").read_bytes() == Path("app/src/main/assets/GHOSTBOARD_LICENSE").read_bytes()
assert Path("LINKPAD_LICENSE").read_bytes() == Path("app/src/main/assets/LINKPAD_LICENSE").read_bytes()
print("PASS: golden HID core frozen; AI-only Internet permission; licences bundled")
