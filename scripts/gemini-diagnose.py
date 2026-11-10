#!/usr/bin/env python3
"""Compare minimal and structured Gemini generateContent requests.

Reads GEMINI_API_KEY from the environment only. Never prints the key, request
bodies with secrets, or free-form Google error messages. Prints HTTP status,
RPC status, ErrorInfo.reason, and allowlisted field paths.
"""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATH = ROOT / "backend/src/main/resources/ai/analysis-schema.json"
INSTRUCTION_PATH = ROOT / "backend/src/main/resources/ai/analysis-instruction.txt"
ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
MODEL = os.environ.get("GEMINI_MODEL", "gemini-3.5-flash-lite")
STATUSES = {
    "INVALID_ARGUMENT",
    "UNAUTHENTICATED",
    "PERMISSION_DENIED",
    "NOT_FOUND",
    "FAILED_PRECONDITION",
    "RESOURCE_EXHAUSTED",
    "UNAVAILABLE",
    "INTERNAL",
    "DEADLINE_EXCEEDED",
}
REASONS = {
    "API_KEY_INVALID",
    "API_KEY_EXPIRED",
    "API_KEY_SERVICE_BLOCKED",
    "API_KEY_HTTP_REFERRER_BLOCKED",
    "API_KEY_IP_ADDRESS_BLOCKED",
    "SERVICE_DISABLED",
    "ACCESS_TOKEN_TYPE_UNSUPPORTED",
    "CREDENTIALS_MISSING",
    "BILLING_DISABLED",
    "LOCATION_POLICY_VIOLATED",
}
FIELD_ALLOW = (
    "generationconfig",
    "responsejsonschema",
    "responseschema",
    "responsemimetype",
    "responseformat",
    "systeminstruction",
    "contents",
    "model",
    "maxoutputtokens",
)
BOUND_KEYS = {
    "maxItems",
    "minItems",
    "minimum",
    "maximum",
    "minLength",
    "maxLength",
    "minProperties",
    "maxProperties",
    "uniqueItems",
}
FIELD_RE = re.compile(r"^[A-Za-z0-9._\[\]]{1,120}$")


def strip_bounds(node):
    if isinstance(node, dict):
        return {k: strip_bounds(v) for k, v in node.items() if k not in BOUND_KEYS}
    if isinstance(node, list):
        return [strip_bounds(x) for x in node]
    return node


def summarize(body: bytes) -> tuple[str, str, list[str]]:
    status = ""
    reason = ""
    fields: list[str] = []
    try:
        error = json.loads(body.decode("utf-8", "replace")).get("error", {})
        code = error.get("status") or ""
        if code in STATUSES:
            status = code
        for detail in error.get("details") or []:
            value = detail.get("reason") or ""
            if value in REASONS and not reason:
                reason = value
            for violation in detail.get("fieldViolations") or []:
                field = violation.get("field") or ""
                if not FIELD_RE.match(field):
                    continue
                lower = field.lower()
                if any(token in lower for token in FIELD_ALLOW):
                    fields.append(field)
    except Exception:
        pass
    return status, reason, fields


def post(api_key: str, payload: dict) -> tuple[int, str, str, list[str]]:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        ENDPOINT.format(model=MODEL),
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": api_key,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            body = resp.read(200000)
            return resp.status, *summarize(body)
    except urllib.error.HTTPError as err:
        body = err.read(200000)
        return err.code, *summarize(body)


def main() -> int:
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        print("GEMINI_API_KEY is not set in this process environment.", file=sys.stderr)
        return 2
    if len(api_key) > 4096 or any(ord(c) < 33 or ord(c) > 126 for c in api_key):
        print("GEMINI_API_KEY fails local header-safety checks.", file=sys.stderr)
        return 2

    full_schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    wire_schema = strip_bounds(full_schema)
    instruction = INSTRUCTION_PATH.read_text(encoding="utf-8")
    synthetic = "개발 초보인데 결제 경험자와 협업하며 배우고 싶어."

    cases = [
        ("minimal_text", {"contents": [{"role": "user", "parts": [{"text": "ping"}]}]}),
        (
            "json_mime_only",
            {
                "contents": [{"role": "user", "parts": [{"text": 'Return {"ok":true}'}]}],
                "generationConfig": {"responseMimeType": "application/json", "maxOutputTokens": 256},
            },
        ),
        (
            "tiny_schema",
            {
                "contents": [{"role": "user", "parts": [{"text": "Say hello in JSON."}]}],
                "generationConfig": {
                    "responseMimeType": "application/json",
                    "responseJsonSchema": {
                        "type": "object",
                        "properties": {"reply": {"type": "string"}},
                        "required": ["reply"],
                        "additionalProperties": False,
                    },
                    "maxOutputTokens": 256,
                },
            },
        ),
        (
            "full_schema_current_file",
            {
                "systemInstruction": {"parts": [{"text": instruction}]},
                "contents": [{"role": "user", "parts": [{"text": synthetic}]}],
                "generationConfig": {
                    "responseMimeType": "application/json",
                    "responseJsonSchema": full_schema,
                    "maxOutputTokens": 4096,
                },
            },
        ),
        (
            "simplified_wire_schema",
            {
                "systemInstruction": {"parts": [{"text": instruction}]},
                "contents": [{"role": "user", "parts": [{"text": synthetic}]}],
                "generationConfig": {
                    "responseMimeType": "application/json",
                    "responseJsonSchema": wire_schema,
                    "maxOutputTokens": 4096,
                },
            },
        ),
    ]

    print(f"model={MODEL}")
    print(f"key_present=yes key_len={len(api_key)}")
    failures = 0
    for name, payload in cases:
        http_status, status, reason, fields = post(api_key, payload)
        field_part = f" fields={','.join(fields)}" if fields else ""
        line = f"{name}: HTTP {http_status}"
        if status:
            line += f" / {status}"
        if reason:
            line += f" / {reason}"
        line += field_part
        print(line)
        if http_status != 200:
            failures += 1
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
