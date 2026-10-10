#!/usr/bin/env python3
"""Render a Podman environment file from production secrets and configuration."""

import base64
import os
from pathlib import Path
import re
import sys


REQUIRED_SECRETS = (
    "DB_PASSWORD",
    "FLYWAY_DB_PASSWORD",
    "RABBITMQ_USERNAME",
    "RABBITMQ_PASSWORD",
    "PARTY_IDENTIFIER_ENCRYPTION_KEY_V1",
    "PARTY_IDENTIFIER_INDEX_HMAC_KEY",
    "PARTY_REGISTRATION_IDEMPOTENCY_HMAC_KEY",
    "PARTY_CURSOR_SIGNING_KEY_V1",
)
CRYPTOGRAPHIC_KEYS = REQUIRED_SECRETS[4:]


def _validate_required_secrets(environment: dict[str, str]) -> None:
    """Ensure all required production secrets are present and non-empty."""
    for name in REQUIRED_SECRETS:
        if not environment.get(name, "").strip():
            raise ValueError(f"Missing production secret: {name}")


def _validate_variable_name(line_number: int, name: str, separator: str, known_variables: set[str]) -> None:
    """Ensure a template line contains a valid and non-duplicate variable name."""
    if not separator or not re.fullmatch(r"[A-Z][A-Z0-9_]*", name) or name in known_variables:
        raise ValueError(f"Invalid or duplicate template variable at line {line_number}")


def _validate_variable_value(name: str, value: str) -> None:
    """Ensure a resolved variable value is non-empty and has no control characters."""
    if not value.strip() or "REPLACE_WITH_" in value:
        raise ValueError(f"Missing production value: {name}")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise ValueError(f"Production value must be a single line without control characters: {name}")


def _parse_template_variable(
    line_number: int,
    line: str,
    environment: dict[str, str],
    known_variables: set[str],
) -> tuple[str, str]:
    """Extract and validate a configuration key and value from a template line."""
    name, separator, default = line.partition("=")
    _validate_variable_name(line_number, name, separator, known_variables)
    value = environment.get(name) or default
    _validate_variable_value(name, value)
    return name, value


def _render_template_lines(
    template: Path,
    environment: dict[str, str],
) -> tuple[list[str], dict[str, str]]:
    """Parse the template file, resolve environment overrides, and preserve literal lines."""
    lines: list[str] = []
    values: dict[str, str] = {}
    for line_number, line in enumerate(template.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            lines.append(line)
            continue
        name, value = _parse_template_variable(line_number, line, environment, set(values))
        # Podman reads literal NAME=value lines; shell quoting would alter the secret.
        values[name] = value
        lines.append(f"{name}={value}")
    return lines, values


def _ensure_secrets_in_template(values: dict[str, str]) -> None:
    """Ensure all required secrets are present in the parsed template."""
    for name in REQUIRED_SECRETS:
        if name not in values:
            raise ValueError(f"Required secret is missing from the template: {name}")


def _decode_cryptographic_key(name: str, encoded_key: str) -> bytes:
    """Decode a Base64 cryptographic key and verify it has exactly 32 bytes."""
    try:
        decoded = base64.b64decode(encoded_key, validate=True)
    except ValueError as error:
        raise ValueError(f"Production key must be Base64-encoded: {name}") from error
    if len(decoded) != 32:
        raise ValueError(f"Production key must decode to exactly 32 bytes: {name}")
    return decoded


def _validate_cryptographic_keys(values: dict[str, str]) -> None:
    """Verify that all cryptographic keys are valid 32-byte Base64 sequences and independent."""
    decoded_keys = [_decode_cryptographic_key(name, values[name]) for name in CRYPTOGRAPHIC_KEYS]
    if len(set(decoded_keys)) != len(decoded_keys):
        raise ValueError("Production encryption, HMAC, and cursor keys must be independent")


def _is_valid_port(port: str) -> bool:
    """Check whether a port string is an ASCII decimal between 1 and 65535."""
    return port.isascii() and port.isdecimal() and 1 <= int(port) <= 65535


def _validate_application_configuration(values: dict[str, str]) -> None:
    """Verify service configuration values meet deployment constraints."""
    if values["PARTY_IDENTIFIER_CURRENT_ENCRYPTION_KEY_VERSION"] != "1":
        raise ValueError("This deployment template only provisions encryption key version 1")
    if values["PARTY_CURSOR_CURRENT_SIGNING_KEY_ID"] != "v1":
        raise ValueError("This deployment template only provisions cursor signing key v1")
    if values["RABBITMQ_HOST"].startswith(("http://", "https://", "amqp://", "amqps://")):
        raise ValueError("RABBITMQ_HOST must be a hostname, without a URL scheme")
    if not _is_valid_port(values["RABBITMQ_PORT"]):
        raise ValueError("RABBITMQ_PORT must be between 1 and 65535")
    if values["PARTY_OUTBOX_MODE"] not in {"disabled", "stored-only", "published"}:
        raise ValueError("PARTY_OUTBOX_MODE must be disabled, stored-only, or published")


def render_environment(template: Path, environment: dict[str, str]) -> str:
    """Validate secrets and preserve literal values in Podman's env-file format."""
    _validate_required_secrets(environment)
    lines, values = _render_template_lines(template, environment)
    _ensure_secrets_in_template(values)
    _validate_cryptographic_keys(values)
    _validate_application_configuration(values)
    return "\n".join(lines) + "\n"


def main() -> int:
    """Create a private output file only after all production values validate."""
    if len(sys.argv) != 3:
        print("Usage: render-env.py TEMPLATE OUTPUT", file=sys.stderr)
        return 1
    try:
        rendered = render_environment(Path(sys.argv[1]), dict(os.environ))
        descriptor = os.open(sys.argv[2], os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8") as output:
                output.write(rendered)
        except BaseException:
            Path(sys.argv[2]).unlink(missing_ok=True)
            raise
    except (ValueError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    print("Production environment validated and generated")
    return 0


if __name__ == "__main__":
    sys.exit(main())
