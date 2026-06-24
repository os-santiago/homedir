#!/usr/bin/env python3
"""Automated issue metadata validation for GitHub issues."""

import os
import sys
import re
from typing import Dict, List, Tuple

try:
    from github import Github
except ImportError:
    print("ERROR: PyGithub not installed. Run: pip install PyGithub", file=sys.stderr)
    sys.exit(1)

VALIDATION_RULES = {
    "required_fields": {
        "title": {"min_length": 10, "max_length": 200},
        "body": {"min_length": 50, "required_sections": ["objective", "scope", "acceptance"]},
    },
    "labels": {
        "type_labels": ["bug", "enhancement", "documentation", "question"],
        "priority_labels": ["priority:P0", "priority:P1", "priority:P2", "priority:P3"],
    },
    "enforcement_mode": "advisory",
}

class IssueValidator:
    def __init__(self, github_token: str, repository: str):
        self.github = Github(github_token)
        self.repo = self.github.get_repo(repository)
        self.validation_errors = []
        self.validation_warnings = []

    def validate_title(self, title: str) -> bool:
        if not title or not title.strip():
            self.validation_errors.append("❌ Title cannot be empty")
            return False
        if len(title) < VALIDATION_RULES["required_fields"]["title"]["min_length"]:
            self.validation_errors.append(f"❌ Title too short: {len(title)} chars (minimum: 10)")
            return False
        if len(title) > VALIDATION_RULES["required_fields"]["title"]["max_length"]:
            self.validation_warnings.append(f"⚠️ Title too long: {len(title)} chars (maximum: 200)")
        return True

    def validate_body(self, body: str) -> bool:
        if not body:
            self.validation_errors.append("❌ Issue body is empty")
            return False
        if len(body) < VALIDATION_RULES["required_fields"]["body"]["min_length"]:
            self.validation_errors.append(f"❌ Body too short: {len(body)} chars (minimum: 50)")
            return False
        return True

    def validate_labels(self, labels: List[str]) -> bool:
        if not labels:
            self.validation_warnings.append("⚠️ No labels assigned")
            return False
        has_type = any(label in VALIDATION_RULES["labels"]["type_labels"] for label in labels)
        has_priority = any(label in VALIDATION_RULES["labels"]["priority_labels"] for label in labels)
        if not has_type:
            self.validation_warnings.append("⚠️ No type label found")
        if not has_priority:
            self.validation_warnings.append("⚠️ No priority label found")
        return has_type and has_priority

    def validate_issue(self, issue_number: int) -> Tuple[str, str]:
        issue = self.repo.get_issue(issue_number)
        self.validation_errors = []
        self.validation_warnings = []
        
        self.validate_title(issue.title)
        self.validate_body(issue.body or "")
        label_names = [label.name for label in issue.labels]
        self.validate_labels(label_names)
        
        if self.validation_errors:
            status = "incomplete"
        elif self.validation_warnings:
            status = "partial"
        else:
            status = "complete"
        
        report = self._generate_report(issue_number, status)
        return status, report

    def _generate_report(self, issue_number: int, status: str) -> str:
        lines = [
            "## 🤖 Issue Metadata Validation Report",
            f"**Issue:** #{issue_number}",
            f"**Status:** {self._status_emoji(status)} {status.upper()}",
            ""
        ]
        if self.validation_errors:
            lines.append("### ❌ Errors")
            lines.extend(f"- {e}" for e in self.validation_errors)
            lines.append("")
        if self.validation_warnings:
            lines.append("### ⚠️ Warnings")
            lines.extend(f"- {w}" for w in self.validation_warnings)
            lines.append("")
        if status == "complete":
            lines.append("### ✅ All validation checks passed!")
        return "\n".join(lines)

    def _status_emoji(self, status: str) -> str:
        return {"complete": "✅", "partial": "⚠️", "incomplete": "❌"}.get(status, "❓")

def main():
    github_token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("REPOSITORY")
    issue_number = os.environ.get("ISSUE_NUMBER")
    
    if not all([github_token, repository, issue_number]):
        print("ERROR: Missing required environment variables", file=sys.stderr)
        sys.exit(1)
    
    validator = IssueValidator(github_token, repository)
    status, report = validator.validate_issue(int(issue_number))
    
    print(f"::set-output name=validation_status::{status}")
    report_escaped = report.replace("%", "%25").replace("\n", "%0A")
    print(f"::set-output name=report::{report_escaped}")
    print(f"\n✓ Issue #{issue_number} validation: {status.upper()}")
    print(report)
    
    sys.exit(0 if VALIDATION_RULES["enforcement_mode"] == "advisory" or status != "incomplete" else 1)

if __name__ == "__main__":
    main()
