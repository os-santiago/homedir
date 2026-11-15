"""Approval-policy consistency and the unchanged legacy human-review path."""
import importlib.util
import json
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("label_pr_state", ROOT / "scripts/ci/label_pr_state.py")
labeler = importlib.util.module_from_spec(spec)
with patch.dict("sys.modules", {"github": SimpleNamespace(Github=object, Auth=object)}):
    spec.loader.exec_module(labeler)


def pr_with_labels(*labels):
    return SimpleNamespace(merged=False, state="open", draft=False, mergeable=True,
                           get_labels=lambda: [SimpleNamespace(name=name) for name in labels])


class AgentReviewPolicyTests(unittest.TestCase):
    def test_low_label_and_zero_humans_are_not_an_agent_audit(self):
        with patch.object(labeler, "get_ci_status", return_value="passed"), \
                patch.object(labeler, "count_human_approvals", return_value=(0, False)):
            self.assertEqual(labeler.get_pr_state_label(pr_with_labels("pr:risk-low"))[0], labeler.LABEL_NEEDS_REVIEW)


    def test_human_path_and_change_requests_remain_enforced(self):
        for risk, count in labeler.RISK_APPROVALS.items():
            with patch.object(labeler, "get_ci_status", return_value="passed"), \
                    patch.object(labeler, "count_human_approvals", return_value=(count, False)):
                self.assertEqual(labeler.get_pr_state_label(pr_with_labels(risk))[0], labeler.LABEL_APPROVED)
            with patch.object(labeler, "get_ci_status", return_value="passed"), \
                    patch.object(labeler, "count_human_approvals", return_value=(count, True)):
                self.assertEqual(labeler.get_pr_state_label(pr_with_labels(risk))[0], labeler.LABEL_CHANGES_REQUESTED)


    def test_declarative_policy_preserves_higher_risk_and_requires_authenticated_evidence(self):
        policy = json.loads((ROOT / "config/ai-sdlc/review-policy.json").read_text())
        self.assertEqual(policy["version"], "homedir.review-policy/v1")
        self.assertEqual(policy["defaultDecision"], "needs-human")
        self.assertIs(policy["approvalLabelsAreEvidence"], False)
        self.assertEqual(set(policy["risks"]), {"low", "medium", "high", "critical"})
        for risk, settings in policy["risks"].items():
            self.assertEqual(settings["humanApprovals"], labeler.RISK_APPROVALS["pr:risk-" + risk])
            self.assertIs(settings["agentAuditAlternative"], risk == "low")
        self.assertIs(policy["agentAudit"]["trustedVerifierAuthenticationRequired"], True)
        self.assertIs(policy["agentAudit"]["invalidatedByAnyBoundFieldChange"], True)
        self.assertIs(policy["agentAudit"]["rawModelTextIsApproval"], False)
        self.assertEqual(set(policy["agentAudit"]["boundFields"]), {
            "repository", "pullRequest", "issueRevision", "headSHA", "baseSHA",
            "candidateDigest", "policyRevision", "auditorIdentity",
        })

    def test_feedback_checks_and_issue_gates_are_not_waived(self):
        policy = json.loads((ROOT / "config/ai-sdlc/review-policy.json").read_text())
        self.assertEqual(policy["requiredGates"], {
            "currentIssueEligible": True, "currentHeadAndBase": True,
            "requiredChecksSuccessful": True, "unresolvedReviewThreads": 0,
            "outstandingChangeRequests": 0, "actionableCommentsAddressed": True,
        })
        self.assertIs(policy["agentAudit"]["riskMustBeVerifiedBeyondLabels"], True)
        self.assertIs(policy["agentAudit"]["independentOfImplementation"], True)
        self.assertIs(policy["agentAudit"]["scopeMustBeReviewed"], True)
        self.assertEqual(policy["agentAudit"]["missingOrAmbiguousEvidence"], "needs-human")
