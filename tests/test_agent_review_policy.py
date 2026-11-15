"""Approval-policy consistency and the legacy labeler's fail-closed boundary."""
import importlib.util
import json
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("label_pr_state", ROOT / "scripts/ci/label_pr_state.py")
labeler = importlib.util.module_from_spec(spec)
with patch.dict("sys.modules", {"github": SimpleNamespace(Github=object, Auth=object)}):
    spec.loader.exec_module(labeler)


def pr_with_labels(*labels):
    return SimpleNamespace(merged=False, state="open", draft=False, mergeable=True,
                           get_labels=lambda: [SimpleNamespace(name=name) for name in labels])


class AgentReviewPolicyTests(unittest.TestCase):
    def test_invalid_or_ambiguous_risk_cannot_select_low_threshold(self):
        for labels in ((), ("pr:risk-low", "pr:risk-high"), ("pr:risk-low", "pr:risk-critical"),
                       ("pr:risk-unknown",), ("pr:risk-low", "pr:risk-unknown")):
            with patch.object(labeler, "get_ci_status", return_value="passed"), \
                    patch.object(labeler, "count_human_approvals", return_value=(99, False)):
                self.assertEqual(labeler.get_pr_state_label(pr_with_labels(*labels))[0], labeler.LABEL_BLOCKED)


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

    def test_threshold_uses_same_observation_as_validation(self):
        pr = pr_with_labels("pr:risk-critical")
        pr.get_labels = Mock(side_effect=[
            [SimpleNamespace(name="pr:risk-critical")],
            [SimpleNamespace(name="pr:risk-low"), SimpleNamespace(name="pr:risk-critical")],
        ])
        with patch.object(labeler, "get_ci_status", return_value="passed"), \
                patch.object(labeler, "count_human_approvals", return_value=(1, False)):
            self.assertEqual(labeler.get_pr_state_label(pr), (labeler.LABEL_NEEDS_REVIEW, 1, 3, False))
        pr.get_labels.assert_called_once()

    def test_refreshed_pr_risk_is_validated(self):
        for labels in (("pr:risk-low", "pr:risk-critical"), ("pr:risk-critical",)):
            refreshed = pr_with_labels(*labels)
            original = pr_with_labels("pr:risk-low")
            original.mergeable = None
            original.number = 1
            original.get_labels = Mock(side_effect=AssertionError("stale PR labels"))
            original.base = SimpleNamespace(repo=SimpleNamespace(get_pull=Mock(return_value=refreshed)))
            with patch.object(labeler.time, "sleep"), \
                    patch.object(labeler, "get_ci_status", return_value="passed"), \
                    patch.object(labeler, "count_human_approvals", return_value=(1, False)):
                expected = labeler.LABEL_BLOCKED if len(labels) > 1 else labeler.LABEL_NEEDS_REVIEW
                self.assertEqual(labeler.get_pr_state_label(original)[0], expected)
            original.get_labels.assert_not_called()
