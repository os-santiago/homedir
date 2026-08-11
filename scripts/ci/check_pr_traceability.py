#!/usr/bin/env python3
"""Check that a PR references an issue via closes/fixes/resolves #N."""

import os
import re
import sys

try:
    from github import Github, Auth
except ImportError:
    print("ERROR: PyGithub not installed. Run: pip install PyGithub", file=sys.stderr)
    sys.exit(1)

# Match: closes #N, fixes #N, resolves #N (case-insensitive, optional colon)
REFERENCE_PATTERN = re.compile(
    r"(?:close[ds]?|fix(?:e[ds])?|resolve[ds]?)\s*:?\s*#(\d+)",
    re.IGNORECASE,
)

# Also match bare #N references (weaker signal, but still a reference)
BARE_REF_PATTERN = re.compile(r"#(\d+)")


def check_pr(github_token: str, repository: str, pr_number: int) -> tuple:
    """Check if a PR references an issue. Returns (has_reference, message, issue_numbers)."""
    github = Github(auth=Auth.Token(github_token))
    repo = github.get_repo(repository)
    pr = repo.get_pull(pr_number)

    body = pr.body or ""
    title = pr.title or ""
    full_text = f"{title}\n{body}"

    # Strong reference: closes/fixes/resolves #N
    strong_matches = REFERENCE_PATTERN.findall(full_text)
    if strong_matches:
        issue_numbers = [int(n) for n in strong_matches]
        # Verify the issues exist and are open
        valid_issues = []
        warnings = []
        for num in issue_numbers:
            try:
                issue = repo.get_issue(num)
                if issue.state == "closed":
                    warnings.append(f"- Issue #{num} is already closed")
                else:
                    valid_issues.append(num)
            except Exception:
                warnings.append(f"- Issue #{num} not found")

        if valid_issues:
            msg = f"Traceability check passed. References issue(s): {', '.join(f'#{n}' for n in valid_issues)}"
            if warnings:
                msg += "\n\nWarnings:\n" + "\n".join(warnings)
            return ("true", msg, valid_issues)
        elif warnings:
            return ("false", f"PR references issue(s) but all have problems:\n" + "\n".join(warnings), [])

    # Weak reference: bare #N
    bare_matches = BARE_REF_PATTERN.findall(full_text)
    if bare_matches:
        issue_numbers = [int(n) for n in bare_matches]
        msg = (
            f"This PR mentions issue(s) #{', #'.join(str(n) for n in issue_numbers)} "
            f"but does not use `Closes #N`, `Fixes #N`, or `Resolves #N` syntax.\n\n"
            f"Please update the PR body to include:\n"
            f"```\nCloses #<issue-number>\n```\n"
            f"This ensures the issue is automatically closed when the PR is merged."
        )
        return ("false", msg, issue_numbers)

    # No reference at all
    msg = (
        "This PR does not reference any issue.\n\n"
        "Every PR must include a reference to the issue it resolves:\n"
        "```\nCloses #<issue-number>\n```\n\n"
        "If there is no issue for this change, please create one first.\n"
        "Traceability is required per the PR Review Policy."
    )
    return ("false", msg, [])


def main():
    github_token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("REPOSITORY")
    pr_number = os.environ.get("PR_NUMBER")

    if not all([github_token, repository, pr_number]):
        print("ERROR: Missing required environment variables", file=sys.stderr)
        sys.exit(1)

    has_ref, message, issue_nums = check_pr(github_token, repository, int(pr_number))

    # GitHub Actions output
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"has_reference={has_ref}\n")
            f.write(f"message<<EOF\n{message}\nEOF\n")
    # Legacy
    print(f"::set-output name=has_reference::{has_ref}")
    msg_escaped = message.replace("%", "%25").replace("\n", "%0A")
    print(f"::set-output name=report::{msg_escaped}")

    print(f"\nPR #{pr_number} traceability: {'PASS' if has_ref == 'true' else 'FAIL'}")
    print(message)

    # Exit 0 regardless — we don't block the PR, we just label and comment
    sys.exit(0)


if __name__ == "__main__":
    main()
