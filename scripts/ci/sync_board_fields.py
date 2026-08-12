#!/usr/bin/env python3
"""Sync GitHub Project #6 board fields (Priority, Status, Size, Target date) with issue state.

Triggered by the board-sync workflow on issue and pull_request events. Uses the GitHub
Projects GraphQL API to update ProjectV2 item fields.

Requires a token with ``project`` scope (``GH_TOKEN`` secret). The default
``GITHUB_TOKEN`` does not have project write access for organization projects.
"""

import json
import os
import re
import sys
import urllib.request
import urllib.error
from datetime import date, timedelta

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

PROJECT_NODE_ID = "PVT_kwDOA4EL6M4Be7xd"
REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "")

# Field IDs (ProjectV2)
FIELD_STATUS = "PVTSSF_lADOCUy_bM4Be7xdzhZSW7o"
FIELD_SIZE = "PVTSSF_lADOCUy_bM4Be7xdzhZSYPg"
FIELD_PRIORITY = "PVTSSF_lADOCUy_bM4Be7xdzhZcEeE"
FIELD_TARGET_DATE = "PVTF_lADOCUy_bM4Be7xdzhZSYPw"

# Single-select option IDs
STATUS_TODO = "f75ad846"
STATUS_IN_PROGRESS = "47fc9ee4"
STATUS_DONE = "98236657"

SIZE_XS = "a8f6a1e3"
SIZE_S = "97493099"
SIZE_M = "1d74b9b8"
SIZE_L = "fbb97e94"
SIZE_XL = "bd56866d"

PRIORITY_P0 = "4ff84b10"
PRIORITY_P1 = "3e26f685"
PRIORITY_P2 = "01297487"
PRIORITY_P3 = "fc373de0"

PRIORITY_OPTION_MAP = {
    "priority:P0": PRIORITY_P0,
    "priority:P1": PRIORITY_P1,
    "priority:P2": PRIORITY_P2,
    "priority:P3": PRIORITY_P3,
}

# SLA offsets (days from today) for Target date
SLA_DAYS = {
    "priority:P0": 1,
    "priority:P1": 3,
}

# ---------------------------------------------------------------------------
# GraphQL helpers
# ---------------------------------------------------------------------------

GITHUB_API = "https://api.github.com/graphql"


def graphql_query(query, variables=None):
    """Execute a GraphQL query against the GitHub API."""
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print("ERROR: No token available (set GITHUB_TOKEN)", file=sys.stderr)
        sys.exit(1)

    body = json.dumps({"query": query, "variables": variables or {}}).encode("utf-8")
    req = urllib.request.Request(
        GITHUB_API,
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/vnd.github+json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body_text = e.read().decode("utf-8", errors="replace")
        print(f"ERROR: GraphQL request failed ({e.code}): {body_text}", file=sys.stderr)
        sys.exit(1)

    if "errors" in result:
        errors = json.dumps(result["errors"], indent=2)
        print(f"ERROR: GraphQL returned errors:\n{errors}", file=sys.stderr)
        sys.exit(1)
    return result.get("data", {})


def rest_get(path):
    """Execute a REST GET request against the GitHub API."""
    token = os.environ.get("GITHUB_TOKEN")
    req = urllib.request.Request(
        f"https://api.github.com{path}",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
        },
        method="GET",
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


# ---------------------------------------------------------------------------
# Project item lookup
# ---------------------------------------------------------------------------

def find_project_item_id(issue_node_id):
    """Find the ProjectV2 item ID for a given issue/PR node ID."""
    query = """
    query($projectId: ID!, $contentId: ID!) {
      node(id: $projectId) {
        ... on ProjectV2 {
          items(first: 100) {
            nodes {
              id
              content {
                ... on Issue { id }
                ... on PullRequest { id }
              }
            }
          }
        }
      }
    }
    """
    data = graphql_query(query, {"projectId": PROJECT_NODE_ID, "contentId": issue_node_id})
    items = data.get("node", {}).get("items", {}).get("nodes", [])
    for item in items:
        content = item.get("content")
        if content and content.get("id") == issue_node_id:
            return item["id"]
    return None


def get_current_field_values(item_id):
    """Retrieve current field values for a project item."""
    query = """
    query($itemId: ID!) {
      node(id: $itemId) {
        ... on ProjectV2Item {
          fieldValues(first: 20) {
            nodes {
              ... on ProjectV2ItemFieldSingleSelectValue {
                field { ... on ProjectV2FieldCommon { name } }
                optionId
              }
              ... on ProjectV2ItemFieldDateValue {
                field { ... on ProjectV2FieldCommon { name } }
                date
              }
              ... on ProjectV2ItemFieldTextValue {
                field { ... on ProjectV2FieldCommon { name } }
                text
              }
            }
          }
        }
      }
    }
    """
    data = graphql_query(query, {"itemId": item_id})
    nodes = data.get("node", {}).get("fieldValues", {}).get("nodes", [])
    result = {}
    for node in nodes:
        field_name = node.get("field", {}).get("name")
        if not field_name:
            continue
        if "optionId" in node:
            result[field_name] = {"type": "single_select", "optionId": node["optionId"]}
        elif "date" in node:
            result[field_name] = {"type": "date", "date": node["date"]}
        elif "text" in node:
            result[field_name] = {"type": "text", "text": node["text"]}
    return result


# ---------------------------------------------------------------------------
# Field updates
# ---------------------------------------------------------------------------

def update_single_select_field(item_id, field_id, option_id):
    """Update a single-select field on a project item."""
    mutation = """
    mutation($input: UpdateProjectV2ItemFieldValueInput!) {
      updateProjectV2ItemFieldValue(input: $input) {
        projectV2Item { id }
      }
    }
    """
    variables = {
        "input": {
            "projectId": PROJECT_NODE_ID,
            "itemId": item_id,
            "fieldId": field_id,
            "value": {"singleSelectOptionId": option_id},
        }
    }
    graphql_query(mutation, variables)


def update_date_field(item_id, field_id, date_str):
    """Update a date field on a project item."""
    mutation = """
    mutation($input: UpdateProjectV2ItemFieldValueInput!) {
      updateProjectV2ItemFieldValue(input: $input) {
        projectV2Item { id }
      }
    }
    """
    variables = {
        "input": {
            "projectId": PROJECT_NODE_ID,
            "itemId": item_id,
            "fieldId": field_id,
            "value": {"date": date_str},
        }
    }
    graphql_query(mutation, variables)


# ---------------------------------------------------------------------------
# Size estimation
# ---------------------------------------------------------------------------

def estimate_size(body):
    """Heuristic size estimation based on issue body content.

    Returns one of SIZE_XS, SIZE_S, SIZE_M, SIZE_L, SIZE_XL, or None if uncertain.
    """
    if not body:
        return None

    body_lower = body.lower()

    # XL: multi-repo, architecture, epic-level
    if any(kw in body_lower for kw in ("multi-repo", "architecture", "epic", "cross-repo")):
        return SIZE_XL

    # L: major refactor, new system, >10 files
    if any(kw in body_lower for kw in ("refactor major", "major refactor", "new system", "rewrite", "migration")):
        return SIZE_L
    file_count_match = re.search(r"(\d+)\s+files", body_lower)
    if file_count_match and int(file_count_match.group(1)) > 10:
        return SIZE_L

    # M: feature, 3-10 files
    if any(kw in body_lower for kw in ("feature", "enhancement", "new endpoint", "new page")):
        return SIZE_M
    if file_count_match and 3 <= int(file_count_match.group(1)) <= 10:
        return SIZE_M

    # S: 1-3 files, <50 lines
    line_count_match = re.search(r"(\d+)\s+lines?", body_lower)
    if line_count_match and int(line_count_match.group(1)) < 50:
        return SIZE_S
    if file_count_match and int(file_count_match.group(1)) <= 3:
        return SIZE_S

    # XS: typo, 1-file, text fix
    if any(kw in body_lower for kw in ("typo", "1-file", "text fix", "i18n", "label")):
        return SIZE_XS

    return None


# ---------------------------------------------------------------------------
# PR ↔ issue linking
# ---------------------------------------------------------------------------

def has_open_pr_for_issue(issue_number):
    """Check if an issue has a linked open pull request."""
    owner_repo = REPOSITORY or os.environ.get("GITHUB_REPOSITORY", "")
    if not owner_repo:
        return False
    path = f"/repos/{owner_repo}/issues/{issue_number}/timeline"
    try:
        events = rest_get(path)
    except Exception:
        return False
    for event in events:
        if event.get("event") == "cross-referenced":
            source = event.get("source", {}).get("issue", {})
            if source and source.get("pull_request"):
                pr_data = rest_get(f"/repos/{owner_repo}/pulls/{source['number']}")
                if pr_data.get("state") == "open":
                    return True
    return False


# ---------------------------------------------------------------------------
# Main sync logic
# ---------------------------------------------------------------------------

def sync_issue(issue_number, issue_node_id, labels, is_closed, is_new, assignees):
    """Sync board fields for an issue."""
    item_id = find_project_item_id(issue_node_id)
    if not item_id:
        print(f"INFO: Issue #{issue_number} is not in Project #6 — skipping.")
        return

    current = get_current_field_values(item_id)
    print(f"INFO: Issue #{issue_number} current fields: {current}")

    # 1. Sync Priority from labels
    priority_label = next((lbl for lbl in labels if lbl in PRIORITY_OPTION_MAP), None)
    if priority_label:
        desired_priority = PRIORITY_OPTION_MAP[priority_label]
        current_priority = current.get("Priority", {}).get("optionId")
        if current_priority != desired_priority:
            print(f"INFO: Updating Priority → {priority_label}")
            update_single_select_field(item_id, FIELD_PRIORITY, desired_priority)

    # 2. Sync Status
    if is_closed:
        current_status = current.get("Status", {}).get("optionId")
        if current_status != STATUS_DONE:
            print(f"INFO: Updating Status → Done (issue closed)")
            update_single_select_field(item_id, FIELD_STATUS, STATUS_DONE)
    elif is_new and not assignees:
        current_status = current.get("Status", {}).get("optionId")
        if current_status != STATUS_TODO:
            print(f"INFO: Updating Status → Todo (new issue, no assignee)")
            update_single_select_field(item_id, FIELD_STATUS, STATUS_TODO)
    elif assignees and has_open_pr_for_issue(issue_number):
        current_status = current.get("Status", {}).get("optionId")
        if current_status != STATUS_IN_PROGRESS:
            print(f"INFO: Updating Status → In progress (assigned + open PR)")
            update_single_select_field(item_id, FIELD_STATUS, STATUS_IN_PROGRESS)

    # 3. Auto-estimate Size (only if not already set)
    if "Size" not in current and is_new:
        size = estimate_size(os.environ.get("ISSUE_BODY", ""))
        if size:
            size_name = {SIZE_XS: "XS", SIZE_S: "S", SIZE_M: "M", SIZE_L: "L", SIZE_XL: "XL"}.get(size, "?")
            print(f"INFO: Auto-estimating Size → {size_name}")
            update_single_select_field(item_id, FIELD_SIZE, size)

    # 4. Set Target date for P0/P1 (only if not already set)
    if "Target date" not in current and priority_label in SLA_DAYS:
        days = SLA_DAYS[priority_label]
        target = (date.today() + timedelta(days=days)).isoformat()
        print(f"INFO: Setting Target date → {target} ({priority_label} SLA +{days}d)")
        update_date_field(item_id, FIELD_TARGET_DATE, target)

    print(f"INFO: Sync complete for issue #{issue_number}")


def sync_pr(pr_number, pr_node_id, is_closed):
    """Sync board fields for a PR (update Status of linked issues)."""
    # PRs themselves may not be in the project, but linked issues are.
    # The PR close event should trigger Status sync on linked issues.
    owner_repo = REPOSITORY or os.environ.get("GITHUB_REPOSITORY", "")
    if not owner_repo:
        return

    # Find issues referenced by this PR
    pr_data = rest_get(f"/repos/{owner_repo}/pulls/{pr_number}")
    pr_body = pr_data.get("body", "") or ""

    # Extract issue numbers from "Closes #N" / "Fixes #N" / "Refs #N"
    issue_refs = set()
    for match in re.finditer(r"(?:closes|fixes|resolves|refs)\s+#(\d+)", pr_body, re.IGNORECASE):
        issue_refs.add(int(match.group(1)))

    for issue_num in issue_refs:
        issue_data = rest_get(f"/repos/{owner_repo}/issues/{issue_num}")
        issue_labels = [lbl["name"] for lbl in issue_data.get("labels", [])]
        issue_assignees = [a["login"] for a in issue_data.get("assignees", [])]
        issue_node_id = issue_data.get("node_id")
        issue_closed = issue_data.get("state") == "closed"

        if issue_node_id:
            sync_issue(issue_num, issue_node_id, issue_labels, issue_closed, False, issue_assignees)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    event = os.environ.get("EVENT_NAME", "")
    action = os.environ.get("EVENT_ACTION", "")

    if event == "issues":
        issue_number = int(os.environ.get("ISSUE_NUMBER", "0"))
        issue_node_id = os.environ.get("ISSUE_NODE_ID", "")
        labels = os.environ.get("ISSUE_LABELS", "").split(",")
        labels = [lbl.strip() for lbl in labels if lbl.strip()]
        is_closed = action == "closed"
        is_new = action == "opened"
        assignees = [a for a in os.environ.get("ISSUE_ASSIGNEES", "").split(",") if a.strip()]

        if not issue_number or not issue_node_id:
            print("ERROR: ISSUE_NUMBER and ISSUE_NODE_ID are required for issues event", file=sys.stderr)
            sys.exit(1)

        sync_issue(issue_number, issue_node_id, labels, is_closed, is_new, assignees)

    elif event == "pull_request":
        pr_number = int(os.environ.get("PR_NUMBER", "0"))
        pr_node_id = os.environ.get("PR_NODE_ID", "")
        is_closed = action in ("closed",)

        if not pr_number:
            print("ERROR: PR_NUMBER is required for pull_request event", file=sys.stderr)
            sys.exit(1)

        sync_pr(pr_number, pr_node_id, is_closed)

    else:
        print(f"INFO: Unsupported event: {event} — skipping.")
        return

    print("Board sync finished.")


if __name__ == "__main__":
    main()
