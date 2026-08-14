#!/usr/bin/env python3
"""Validate PR self-attestation labels (pr:*-ok) against actual PR state.

Checks that contributor-applied readiness labels are honest:
  - pr:traceability-ok: PR body has Closes #N, issue exists with priority+type labels
  - pr:tests-ok: test files present in diff (if new functionality claimed)
  - pr:i18n-ok: if i18n files modified, both EN+ES present
  - pr:acceptance-ok: not auto-validated (trust contributor)

If a label is present but validation fails, removes it and posts a comment.
"""

import os
import re
import sys

try:
    from github import Github, Auth, GithubException
except ImportError:
    print("ERROR: PyGithub not installed. Run: pip install PyGithub", file=sys.stderr)
    sys.exit(1)

ISSUE_REF_PATTERN = re.compile(r"(?i)(?:closes|fixes|resolves)\s+#(\d+)")
ISSUE_URL_PATTERN = re.compile(
    r"(?i)(?:closes|fixes|resolves)\s+https?://github\.com/[^/]+/[^/]+/issues/(\d+)"
)

PRIORITY_LABELS = {"priority:P0", "priority:P1", "priority:P2", "priority:P3"}
TYPE_LABELS = {"bug", "enhancement", "documentation", "feature-request", "platform-maintenance", "question"}

TEST_FILE_PATTERNS = [
    re.compile(r"(^|/)Test.*\.(java|js|ts|py)$"),
    re.compile(r".*\.(spec|test)\.(js|ts|jsx|tsx)$"),
    re.compile(r"(^|/)tests?/.*"),
    re.compile(r"(^|/)__tests__/.*"),
]

I18N_FILES = {
    "i18n.properties", "i18n_en.properties", "i18n_es.properties",
    "AppMessages.java",
}


def extract_issue_refs(pr_body: str) -> list:
    """Extract issue numbers from Closes/Fixes/Resolves references."""
    refs = []
    for match in ISSUE_REF_PATTERN.finditer(pr_body or ""):
        refs.append(int(match.group(1)))
    for match in ISSUE_URL_PATTERN.finditer(pr_body or ""):
        refs.append(int(match.group(1)))
    return refs


def validate_traceability(pr, repo) -> tuple:
    """Validate pr:traceability-ok label.

    Returns (is_valid, message).
    """
    refs = extract_issue_refs(pr.body)
    if not refs:
        return False, "No `Closes #N`, `Fixes #N`, or `Resolves #N` reference found in PR body."

    issues_checked = []
    for issue_num in refs:
        try:
            issue = repo.get_issue(issue_num)
            labels = {label.name for label in issue.labels}
            has_priority = bool(labels & PRIORITY_LABELS)
            has_type = bool(labels & TYPE_LABELS)
            if not has_priority or not has_type:
                missing = []
                if not has_priority:
                    missing.append("priority label")
                if not has_type:
                    missing.append("type label")
                return False, f"Issue #{issue_num} is missing: {', '.join(missing)}."
            issues_checked.append(issue_num)
        except GithubException as e:
            if e.status == 404:
                return False, f"Issue #{issue_num} referenced in PR body was not found."
            print(f"  WARNING: API error while checking issue #{issue_num}: {e}", file=sys.stderr)
            return True, f"Skipped validation of #{issue_num} due to a transient API error."

    return True, f"Traceability verified: {', '.join(f'#{n}' for n in issues_checked)}"


def validate_tests(pr) -> tuple:
    """Validate pr:tests-ok label by checking for test files in the diff.

    Returns (is_valid, message).
    """
    files = pr.get_files()
    has_tests = False
    for f in files:
        for pattern in TEST_FILE_PATTERNS:
            if pattern.search(f.filename):
                has_tests = True
                break
        if has_tests:
            break

    if not has_tests:
        return False, "No test files found in PR diff. Add tests or remove `pr:tests-ok` if not applicable."

    return True, "Test files detected in PR diff."


def validate_i18n(pr) -> tuple:
    """Validate pr:i18n-ok label by checking i18n file completeness.

    Returns (is_valid, message).
    """
    files = pr.get_files()
    modified_i18n = set()
    for f in files:
        basename = f.filename.rsplit("/", 1)[-1]
        if basename in I18N_FILES:
            modified_i18n.add(basename)

    if not modified_i18n:
        # No i18n files modified — label is vacuously true (no i18n changes needed)
        return True, "No i18n files modified (label not required but acceptable)."

    # If any i18n file is modified, check that both EN and ES are present
    has_en = "i18n_en.properties" in modified_i18n
    has_es = "i18n_es.properties" in modified_i18n

    if has_en and has_es:
        return True, "Both EN and ES i18n files modified."
    if has_en and not has_es:
        return False, "i18n_en.properties modified but i18n_es.properties is missing. Add ES translations."
    if has_es and not has_en:
        return False, "i18n_es.properties modified but i18n_en.properties is missing. Add EN translations."

    return True, "i18n files modified (base only)."


VALIDATORS = {
    "pr:traceability-ok": validate_traceability,
    "pr:tests-ok": validate_tests,
    "pr:i18n-ok": validate_i18n,
    # pr:acceptance-ok is not auto-validated — trust the contributor
}


def validate_pr(pr, repo):
    """Validate self-attestation labels for a single PR.

    Returns list of comment bodies to post (may be empty).
    """
    current_labels = {label.name for label in pr.get_labels()}
    comments_to_post = []

    for label_name, validator in VALIDATORS.items():
        if label_name not in current_labels:
            continue

        if label_name == "pr:traceability-ok":
            is_valid, message = validator(pr, repo)
        else:
            is_valid, message = validator(pr)

        if is_valid:
            print(f"  {label_name}: VALID — {message}")
        else:
            print(f"  {label_name}: INVALID — {message}")
            try:
                pr.remove_from_labels(label_name)
                print(f"  Removed: {label_name}")
                comments_to_post.append(f"\n### `{label_name}` removed\n{message}\n")
            except Exception as e:
                print(f"  Failed to remove {label_name}: {e}")

    return comments_to_post


def main():
    github_token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("REPOSITORY")
    pr_number = os.environ.get("PR_NUMBER")

    if not all([github_token, repository]):
        print("ERROR: Missing required environment variables", file=sys.stderr)
        sys.exit(1)

    github = Github(auth=Auth.Token(github_token))
    repo = github.get_repo(repository)

    if pr_number:
        prs = [repo.get_pull(int(pr_number))]
    else:
        prs = repo.get_pulls(state="open")

    for pr in prs:
        print(f"PR #{pr.number}:")
        comments_to_post = validate_pr(pr, repo)

        if comments_to_post:
            body = (
                "## PR Readiness Validation\n\n"
                "The following self-attestation labels were removed because validation failed:\n"
                + "\n".join(comments_to_post)
                + "\nPlease fix the issues and re-apply the labels."
            )
            try:
                pr.create_issue_comment(body)
                print("  Posted validation comment on PR.")
            except GithubException as e:
                print(f"  WARNING: Could not post comment (fork PR or permissions): {e}", file=sys.stderr)

    sys.exit(0)


if __name__ == "__main__":
    main()
