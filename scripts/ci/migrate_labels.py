#!/usr/bin/env python3
"""Migrate legacy Spanish labels to canonical English equivalents."""

import os
import sys

try:
    from github import Github, Auth
except ImportError:
    print("ERROR: PyGithub not installed. Run: pip install PyGithub", file=sys.stderr)
    sys.exit(1)

# Legacy ES label → Canonical EN label
LABEL_MIGRATION_MAP = {
    "error": "bug",
    "mejora": "enhancement",
    "buen primer issue": "good first issue",
    "no valido": "invalid",
    "no solucionar": "wontfix",
    "pregunta": "question",
    "Se necesita ayuda": "help wanted",
}

# Labels that are NOT migrated (valid domain labels with no EN equivalent)
SKIP_LABELS = {"evento", "hackathon"}


def migrate_labels(github_token: str, repository: str) -> tuple:
    """Migrate legacy ES labels on all open issues and PRs. Returns (count, details)."""
    github = Github(auth=Auth.Token(github_token))
    repo = github.get_repo(repository)

    migration_count = 0
    details = []

    # Process open issues
    issues = repo.get_issues(state="open")
    for issue in issues:
        label_names = [label.name for label in issue.labels]
        labels_to_remove = []
        labels_to_add = []

        for label_name in label_names:
            if label_name in LABEL_MIGRATION_MAP:
                en_label = LABEL_MIGRATION_MAP[label_name]
                if en_label not in label_names and en_label not in labels_to_add:
                    labels_to_remove.append(label_name)
                    labels_to_add.append(en_label)
                    migration_count += 1
                    details.append(f"Issue #{issue.number}: `{label_name}` → `{en_label}`")
                elif en_label in label_names:
                    # EN label already present, just remove the ES one
                    labels_to_remove.append(label_name)
                    details.append(f"Issue #{issue.number}: removed duplicate `{label_name}` (EN `{en_label}` already present)")

        if labels_to_remove or labels_to_add:
            try:
                for label in labels_to_remove:
                    issue.remove_from_labels(label)
                for label in labels_to_add:
                    issue.add_to_labels(label)

                # Post a comment on the issue
                comment_lines = ["Label migration performed:"]
                for i in range(len(labels_to_remove)):
                    if i < len(labels_to_add):
                        comment_lines.append(f"- `{labels_to_remove[i]}` → `{labels_to_add[i]}`")
                    else:
                        comment_lines.append(f"- Removed `{labels_to_remove[i]}` (duplicate)")
                issue.create_comment("\n".join(comment_lines))
            except Exception as e:
                details.append(f"  ERROR on issue #{issue.number}: {e}")

    return (migration_count, "\n".join(details) if details else "No migrations needed")


def main():
    github_token = os.environ.get("GITHUB_TOKEN")
    repository = os.environ.get("REPOSITORY")

    if not all([github_token, repository]):
        print("ERROR: Missing required environment variables", file=sys.stderr)
        sys.exit(1)

    count, details = migrate_labels(github_token, repository)

    # GitHub Actions output
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"migration_count={count}\n")
            f.write(f"details<<EOF\n{details}\nEOF\n")

    print(f"\nLabel migration complete: {count} migrations performed")
    if details:
        print(details)

    sys.exit(0)


if __name__ == "__main__":
    main()
