"""Probe JD extraction with synthetic text; never print credentials or response bodies."""
import json
import os
from pathlib import Path
import re
import sys
from urllib.request import Request, build_opener, HTTPRedirectHandler
from urllib.error import HTTPError, URLError


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def main():
    settings = {}
    env_file = Path(__file__).resolve().parents[1] / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8-sig").splitlines():
            name, separator, value = line.partition("=")
            if separator and name.strip() in {"GROQ_API_KEY", "JD_LLM_MODEL"}:
                settings[name.strip()] = value.strip()
    key = os.environ.get("GROQ_API_KEY", settings.get("GROQ_API_KEY", ""))
    model = os.environ.get("JD_LLM_MODEL", settings.get("JD_LLM_MODEL", "openai/gpt-oss-120b"))
    if not key.strip():
        print("GROQ_API_KEY_MISSING")
        return 2
    if key != key.strip() or key.startswith(('"', "'")):
        print("GROQ_API_KEY_HAS_QUOTES_OR_WHITESPACE")
        return 2

    def obj(properties):
        return {"type": "object", "properties": properties, "required": list(properties), "additionalProperties": False}

    def fact(kind):
        return obj({"value": {"type": [kind, "null"]}, "evidence": {"type": ["string", "null"]}})

    fields = {name: fact("string") for name in ["role", "company", "location", "workMode", "currency"]}
    fields.update({name: fact("integer") for name in ["minExperience", "salaryMin", "salaryMax"]})
    fields["salaryNegotiable"] = fact("boolean")
    for name in ["requiredSkills", "preferredSkills"]:
        fields[name] = {"type": "array", "items": obj({"name": {"type": "string"}, "evidence": {"type": "string"}})}
    body = {"model": model, "max_completion_tokens": 3500, "reasoning_effort": "low",
            "messages": [{"role": "system", "content": "Extract job facts and exact quotes as evidence. Missing facts use null. Skills are arrays."},
                         {"role": "user", "content": "Example Company seeks Java Developer. Remote. Java required. Salary negotiable."}],
            "response_format": {"type": "json_schema", "json_schema": {"name": "job_description", "strict": True, "schema": obj(fields)}}}
    request = Request("https://api.groq.com/openai/v1/chat/completions", data=json.dumps(body).encode(),
                      headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"}, method="POST")
    try:
        with build_opener(NoRedirect).open(request, timeout=35) as response:
            data = json.loads(response.read(100000))
            choices = data.get("choices", [])
            state = choices[0].get("finish_reason") if choices else None
            valid = False
            if state == "stop":
                try:
                    parsed = json.loads(choices[0]["message"]["content"])
                    valid = isinstance(parsed, dict) and set(parsed) == set(fields)
                except (ValueError, KeyError, TypeError):
                    pass
            print(json.dumps({"http_status": response.status, "response_status": state if state in {"stop", "length", "content_filter"} else "unknown", "schema_fields_present": valid}))
            return 0 if valid else 1
    except HTTPError as error:
        try:
            detail = json.loads(error.read(100000)).get("error", {})
        except (ValueError, AttributeError):
            detail = {}
        if not isinstance(detail, dict):
            detail = {}
        allowed = {"invalid_api_key", "insufficient_quota", "credit_balance_exhausted", "organization_spend_limit_exceeded",
                   "project_spend_limit_exceeded", "organization_usage_limit_exceeded", "model_not_found", "rate_limit_exceeded",
                   "slow_down", "invalid_json_schema", "unsupported_parameter", "unsupported_value", "server_is_overloaded"}
        code = detail.get("code")
        request_id = error.headers.get("x-request-id", "")
        print(json.dumps({"http_status": error.code, "error_code": code if code in allowed else "unknown",
                          "provider_request_id": request_id if re.fullmatch(r"req_[a-zA-Z0-9_-]{1,100}", request_id) else "unavailable"}))
        return 1
    except (URLError, TimeoutError) as error:
        print("NETWORK_ERROR " + type(error).__name__)
        return 3


if __name__ == "__main__":
    sys.exit(main())
