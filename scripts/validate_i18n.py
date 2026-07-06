#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_DIR = ROOT / "quarkus-app" / "src" / "main" / "resources"
TEMPLATE_DIR = RESOURCE_DIR / "templates"
APP_MESSAGES = ROOT / "quarkus-app" / "src" / "main" / "java" / "com" / "scanales" / "homedir" / "config" / "AppMessages.java"
BUNDLES = [
    RESOURCE_DIR / "i18n.properties",
    RESOURCE_DIR / "i18n_en.properties",
    RESOURCE_DIR / "i18n_es.properties",
]
KEY_RE = re.compile(r"^[a-z][a-z0-9]*(?:[._][a-z0-9]+)*$")
I18N_REFERENCE_RE = re.compile(r"\{i18n:([A-Za-z0-9_.-]+)(?:\([^{}]*\))?\}")
APP_MESSAGE_METHOD_RE = re.compile(r"\bString\s+([A-Za-z0-9_]+)\s*\([^)]*\)\s*;")
REDUNDANT_FILES = [
    RESOURCE_DIR / "messages.properties",
    RESOURCE_DIR / "messages_es.properties",
    RESOURCE_DIR / "messages" / "i18n.properties",
    RESOURCE_DIR / "messages" / "i18n_es.properties",
]


def split_property(line):
    escaped = False
    for index, char in enumerate(line):
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char in "=:":
            return line[:index].strip(), line[index + 1 :].strip()
    return None, None


def parse_properties(path):
    if not path.exists():
        raise FileNotFoundError(path)

    keys = set()
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("!"):
            continue

        key, _value = split_property(stripped)
        if not key:
            print(f"WARNING: ignoring malformed properties line {path.relative_to(ROOT)}:{line_number}")
            continue
        keys.add(key)

    return keys


def scan_template_references():
    references = []
    if not TEMPLATE_DIR.exists():
        return references

    for path in sorted(TEMPLATE_DIR.rglob("*")):
        if not path.is_file():
            continue
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue

        for line_number, line in enumerate(content.splitlines(), start=1):
            for match in I18N_REFERENCE_RE.finditer(line):
                references.append((match.group(1), path, line_number))

    return references


def parse_app_message_methods():
    if not APP_MESSAGES.exists():
        return set()
    return set(APP_MESSAGE_METHOD_RE.findall(APP_MESSAGES.read_text(encoding="utf-8")))


def format_sample(values, limit=12):
    values = sorted(values)
    sample = [f"  - {value}" for value in values[:limit]]
    if len(values) > limit:
        sample.append(f"  ... and {len(values) - limit} more")
    return sample


def main():
    errors = []
    bundle_keys = {}

    print("Validating i18n bundles and Qute references...")

    for bundle in BUNDLES:
        try:
            keys = parse_properties(bundle)
        except FileNotFoundError:
            errors.append(f"ERROR: required bundle not found: {bundle.relative_to(ROOT)}")
            continue
        bundle_keys[bundle.name] = keys
        print(f"- {bundle.relative_to(ROOT)}: {len(keys)} keys")

    if len(bundle_keys) == len(BUNDLES):
        canonical_name = BUNDLES[0].name
        canonical_keys = bundle_keys[canonical_name]
        for bundle_name, keys in bundle_keys.items():
            missing = canonical_keys - keys
            extra = keys - canonical_keys
            if missing:
                errors.append(f"ERROR: {bundle_name} is missing {len(missing)} keys from {canonical_name}:")
                errors.extend(format_sample(missing))
            if extra:
                errors.append(f"ERROR: {bundle_name} has {len(extra)} keys not present in {canonical_name}:")
                errors.extend(format_sample(extra))

    if bundle_keys:
        all_keys = set().union(*bundle_keys.values())
        invalid_names = [key for key in all_keys if not KEY_RE.fullmatch(key)]
        if invalid_names:
            errors.append(
                "ERROR: i18n keys must use lowercase snake_case or dot.notation "
                "(letters, digits, '_' and '.' only):"
            )
            errors.extend(format_sample(invalid_names))

    references = scan_template_references()
    referenced_keys = {key for key, _path, _line in references}
    print(f"- template references: {len(references)} references, {len(referenced_keys)} distinct keys")

    message_methods = parse_app_message_methods()
    if APP_MESSAGES.exists() and not message_methods:
        errors.append(
            f"ERROR: no message methods could be extracted from {APP_MESSAGES.relative_to(ROOT)}; "
            "check APP_MESSAGE_METHOD_RE against the current file format"
        )
    elif message_methods:
        print(f"- AppMessages.java methods: {len(message_methods)} message keys")
        missing_methods = referenced_keys - message_methods
        if missing_methods:
            errors.append(f"ERROR: AppMessages.java is missing {len(missing_methods)} methods for referenced template keys:")
            errors.extend(format_sample(missing_methods))

        invalid_methods = [key for key in message_methods if not KEY_RE.fullmatch(key)]
        if invalid_methods:
            errors.append("ERROR: AppMessages.java methods must follow the i18n key naming convention:")
            errors.extend(format_sample(invalid_methods))

    for bundle_name, keys in bundle_keys.items():
        missing_references = referenced_keys - keys
        if missing_references:
            errors.append(f"ERROR: {bundle_name} is missing {len(missing_references)} referenced template keys:")
            errors.extend(format_sample(missing_references))

    invalid_references = [key for key in referenced_keys if not KEY_RE.fullmatch(key)]
    if invalid_references:
        errors.append("ERROR: Qute i18n references must use the same key naming convention:")
        errors.extend(format_sample(invalid_references))

    for path in REDUNDANT_FILES:
        if path.exists():
            errors.append(f"ERROR: redundant i18n file found: {path.relative_to(ROOT)}")

    if errors:
        print("\nI18N VALIDATION FAILED")
        print("=" * 60)
        for error in errors:
            print(error)
        return 1

    print("\nI18N VALIDATION PASSED")
    print("=" * 60)
    print("- all configured i18n bundles have identical key sets")
    print("- all Qute {i18n:*} references resolve in every configured bundle")
    print("- all Qute {i18n:*} references resolve in the AppMessages message bundle interface")
    print("- bundle keys and template references follow the naming convention")
    print("- no redundant messages*.properties files were found")
    return 0


if __name__ == "__main__":
    sys.exit(main())
