#!/usr/bin/env python3
"""Automated issue metadata validation and auto-labeling for GitHub issues."""

import os
import sys
import re
from typing import Dict, List, Tuple, Optional

try:
    from github import Github, Auth
except ImportError:
    print("ERROR: PyGithub not installed. Run: pip install PyGithub", file=sys.stderr)
    sys.exit(1)

VALIDATION_RULES = {
    "required_fields": {
        "title": {"min_length": 10, "max_length": 200},
        "body": {"min_length": 50, "required_sections": ["objective", "scope", "acceptance"]},
    },
    "labels": {
        "type_labels": ["bug", "enhancement", "documentation", "feature-request",
                        "platform-maintenance", "question"],
        "priority_labels": ["priority:P0", "priority:P1", "priority:P2", "priority:P3"],
    },
    "enforcement_mode": "auto-fix",
}

# Title prefix → (type_label, default_priority)
TITLE_PREFIX_MAP = {
    "[bug]": ("bug", "priority:P2"),
    "[feature]": ("enhancement", "priority:P3"),
    "[enhancement]": ("enhancement", "priority:P3"),
    "[docs]": ("documentation", "priority:P3"),
    "[doc]": ("documentation", "priority:P3"),
    "[documentation]": ("documentation", "priority:P3"),
    "[task]": ("platform-maintenance", "priority:P3"),
    "[platform]": ("platform-maintenance", "priority:P3"),
    "[infra]": ("platform-maintenance", "priority:P3"),
    "[devops]": ("platform-maintenance", "priority:P3"),
    "[security]": ("bug", "priority:P0"),
    "[hotfix]": ("bug", "priority:P1"),
    "[epic]": ("enhancement", "priority:P2"),
    "[governance]": ("documentation", "priority:P2"),
}

# Body keyword → type_label (fallback when title prefix doesn't match)
BODY_KEYWORD_MAP = {
    "bug": ["reproduc", "error", "crash", "broken", "fail", "exception", "stack trace",
            "unexpected behavior", "doesn't work", "does not work", "not working"],
    "enhancement": ["feature request", "would be nice", "it would be great",
                    "suggest", "proposal", "new feature", "add support", "add ability"],
    "documentation": ["documentation", "docs", "readme", "typo", "spelling",
                      "missing docs", "update docs"],
    "platform-maintenance": ["infrastructure", "ci/cd", "workflow", "deployment",
                             "pipeline", "platform", "devops", "backup"],
}

# Severity keywords in body → priority override
SEVERITY_PRIORITY_MAP = {
    "critical": "priority:P0",
    "s0": "priority:P0",
    "production-down": "priority:P0",
    "outage": "priority:P0",
    "data loss": "priority:P0",
    "high": "priority:P1",
    "s1": "priority:P1",
    "urgent": "priority:P1",
    "security": "priority:P0",
    "medium": "priority:P2",
    "s2": "priority:P2",
    "low": "priority:P3",
    "s3": "priority:P3",
    "cosmetic": "priority:P3",
}


# Priority labels ordered from most to least severe, for resolving multiple matches.
PRIORITY_SEVERITY_ORDER = ["priority:P0", "priority:P1", "priority:P2", "priority:P3"]


def keyword_matches(text: str, keyword: str) -> bool:
    """Check a keyword against whole words only.

    Plain substring matching produces false positives on short keywords:
    'workflow' contains 'low', 'highlight' contains 'high'.
    """
    return re.search(rf"\b{re.escape(keyword)}\b", text) is not None


def infer_type_from_body(body_lower: str) -> Optional[str]:
    """Pick the type label with the most keyword matches, not the first one to match."""
    scores = {
        type_label: sum(1 for kw in keywords if keyword_matches(body_lower, kw))
        for type_label, keywords in BODY_KEYWORD_MAP.items()
    }
    best_score = max(scores.values(), default=0)
    if best_score == 0:
        return None
    # Ties fall back to BODY_KEYWORD_MAP declaration order, so results stay deterministic.
    return next(t for t in BODY_KEYWORD_MAP if scores[t] == best_score)


def infer_priority_from_body(body_lower: str) -> Optional[str]:
    """Return the most severe priority among all matching severity keywords."""
    matched = [
        priority
        for keyword, priority in SEVERITY_PRIORITY_MAP.items()
        if keyword_matches(body_lower, keyword)
    ]
    if not matched:
        return None
    return min(matched, key=PRIORITY_SEVERITY_ORDER.index)


class IssueValidator:
    def __init__(self, github_token: str, repository: str):
        self.github = Github(auth=Auth.Token(github_token))
        self.repo = self.github.get_repo(repository)
        self.validation_errors: List[str] = []
        self.validation_warnings: List[str] = []
        self.auto_actions: List[str] = []

    def validate_title(self, title: str) -> bool:
        if not title or not title.strip():
            self.validation_errors.append("Title cannot be empty")
            return False
        if len(title) < VALIDATION_RULES["required_fields"]["title"]["min_length"]:
            self.validation_errors.append(
                f"Title too short: {len(title)} chars (minimum: 10)")
            return False
        if len(title) > VALIDATION_RULES["required_fields"]["title"]["max_length"]:
            self.validation_warnings.append(
                f"Title too long: {len(title)} chars (maximum: 200)")
        return True

    def validate_body(self, body: str) -> bool:
        if not body:
            self.validation_errors.append("Issue body is empty")
            return False
        if len(body) < VALIDATION_RULES["required_fields"]["body"]["min_length"]:
            self.validation_errors.append(
                f"Body too short: {len(body)} chars (minimum: 50)")
            return False
        return True

    def infer_type_label(self, title: str, body: str) -> Optional[str]:
        """Infer type label from title prefix or body keywords."""
        title_lower = title.lower()
        for prefix, (type_label, _) in TITLE_PREFIX_MAP.items():
            if title_lower.startswith(prefix):
                return type_label

        return infer_type_from_body(body.lower())

    def infer_priority_label(self, title: str, body: str) -> str:
        """Infer priority label from title prefix, body severity keywords, or default."""
        title_lower = title.lower()
        for prefix, (_, priority) in TITLE_PREFIX_MAP.items():
            if title_lower.startswith(prefix):
                return priority

        return infer_priority_from_body(body.lower()) or "priority:P2"

    def auto_assign_labels(self, issue, label_names: List[str]) -> List[str]:
        """Auto-assign missing type and priority labels. Returns updated label list."""
        has_type = any(l in VALIDATION_RULES["labels"]["type_labels"] for l in label_names)
        has_priority = any(l in VALIDATION_RULES["labels"]["priority_labels"] for l in label_names)

        labels_to_add = []

        if not has_type:
            inferred_type = self.infer_type_label(issue.title, issue.body or "")
            if inferred_type:
                labels_to_add.append(inferred_type)
                self.auto_actions.append(f"Auto-assigned type label: `{inferred_type}`")
            else:
                self.validation_warnings.append(
                    "No type label found and could not auto-infer — applied `needs-human`")

        if not has_priority:
            inferred_priority = self.infer_priority_label(issue.title, issue.body or "")
            labels_to_add.append(inferred_priority)
            self.auto_actions.append(f"Auto-assigned priority label: `{inferred_priority}`")

        # If we couldn't infer type, flag for human
        if not has_type and not any(
            l in VALIDATION_RULES["labels"]["type_labels"] for l in labels_to_add
        ):
            labels_to_add.append("needs-human")
            self.auto_actions.append("Applied `needs-human` label (type could not be inferred)")

        if labels_to_add:
            try:
                issue.add_to_labels(*labels_to_add)
            except Exception as e:
                self.validation_warnings.append(f"Failed to auto-assign labels: {e}")

        return label_names + labels_to_add

    def validate_labels(self, labels: List[str]) -> bool:
        if not labels:
            self.validation_warnings.append("No labels assigned")
            return False
        has_type = any(label in VALIDATION_RULES["labels"]["type_labels"] for label in labels)
        has_priority = any(label in VALIDATION_RULES["labels"]["priority_labels"] for label in labels)
        if not has_type:
            self.validation_warnings.append("No type label found")
        if not has_priority:
            self.validation_warnings.append("No priority label found")
        return has_type and has_priority

    def validate_issue(self, issue_number: int) -> Tuple[str, str]:
        issue = self.repo.get_issue(issue_number)
        self.validation_errors = []
        self.validation_warnings = []
        self.auto_actions = []

        self.validate_title(issue.title)
        self.validate_body(issue.body or "")
        label_names = [label.name for label in issue.labels]

        # Auto-fix mode: assign missing labels
        if VALIDATION_RULES["enforcement_mode"] == "auto-fix":
            label_names = self.auto_assign_labels(issue, label_names)

        # Re-validate after auto-fix
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
            "## Issue Metadata Validation Report",
            f"**Issue:** #{issue_number}",
            f"**Status:** {self._status_emoji(status)} {status.upper()}",
            ""
        ]
        if self.validation_errors:
            lines.append("### Errors")
            lines.extend(f"- {e}" for e in self.validation_errors)
            lines.append("")
        if self.validation_warnings:
            lines.append("### Warnings")
            lines.extend(f"- {w}" for w in self.validation_warnings)
            lines.append("")
        if self.auto_actions:
            lines.append("### Auto-Fix Actions")
            lines.extend(f"- {a}" for a in self.auto_actions)
            lines.append("")
        if status == "complete":
            lines.append("All validation checks passed!")
        return "\n".join(lines)

    def _status_emoji(self, status: str) -> str:
        return {"complete": "white_check_mark", "partial": "warning",
                "incomplete": "x"}.get(status, "?")


def main():
    github_token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("REPOSITORY")
    issue_number = os.environ.get("ISSUE_NUMBER")

    if not all([github_token, repository, issue_number]):
        print("ERROR: Missing required environment variables", file=sys.stderr)
        sys.exit(1)

    validator = IssueValidator(github_token, repository)
    status, report = validator.validate_issue(int(issue_number))

    # GitHub Actions output (deprecated syntax but works)
    print(f"::set-output name=validation_status::{status}")
    report_escaped = report.replace("%", "%25").replace("\n", "%0A")
    print(f"::set-output name=report::{report_escaped}")
    # Also use GITHUB_OUTPUT for newer runners
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"validation_status={status}\n")
            f.write(f"report<<EOF\n{report}\nEOF\n")

    print(f"\nIssue #{issue_number} validation: {status.upper()}")
    print(report)

    # In auto-fix mode, only fail on structural errors (not label warnings,
    # since we auto-fixed them or flagged needs-human)
    sys.exit(0 if status != "incomplete" else 1)


if __name__ == "__main__":
    main()
