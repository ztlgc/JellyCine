"""Check Android Chinese resource coverage and formatting without an Android device."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RESOURCE_DIRS = (
    "phone/src/main/res",
    "shared/src/androidMain/res",
    "data/src/androidMain/res",
    "core/src/main/res",
)
FORMAT = re.compile(r"%(?:\d+\$)?[+\-# 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


def read_resources(folder):
    entries = {}
    for file in sorted(folder.glob("*.xml")):
        for element in ET.parse(file).getroot():
            if element.tag not in {"string", "plurals", "string-array"}:
                continue
            key = (element.tag, element.attrib["name"])
            if key in entries:
                raise ValueError(f"Duplicate resource: {file}: {key}")
            entries[key] = element
    return entries


def formats(element):
    return sorted(FORMAT.findall("".join(element.itertext())))


def check():
    errors = []
    total = 0
    for directory in RESOURCE_DIRS:
        base = ROOT / directory
        default = read_resources(base / "values")
        chinese = read_resources(base / "values-zh-rCN")
        for key, original in default.items():
            if original.attrib.get("translatable") == "false":
                continue
            translated = chinese.get(key)
            if translated is None:
                errors.append(f"{directory}: missing {key}")
                continue
            total += 1
            if key[0] == "plurals":
                # Chinese only needs 'other', but its arguments must match the source.
                original = original.find("item[@quantity='other']")
                translated = translated.find("item[@quantity='other']")
                if original is None or translated is None:
                    errors.append(f"{directory}: missing plural 'other' for {key}")
                    continue
            if key[0] == "string-array":
                source_items = list(original)
                target_items = list(translated)
                if len(source_items) != len(target_items):
                    errors.append(f"{directory}: different array lengths for {key}")
                    continue
                pairs = zip(source_items, target_items)
            else:
                pairs = [(original, translated)]
            for source, target in pairs:
                if formats(source) != formats(target):
                    errors.append(f"{directory}: format arguments differ for {key}")
        print(f"{directory}: {len(default)} default / {len(chinese)} Chinese resources")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"PASS: {total} translatable resources have Chinese entries and matching format arguments.")


if __name__ == "__main__":
    check()
