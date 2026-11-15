"""Approval-policy consistency and the legacy labeler's fail-closed boundary."""
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
    def test_invalid_or_ambiguous_risk_cannot_select_low_threshold(self):
        for labels in ((), ("pr:risk-low", "pr:risk-high"), ("pr:risk-low", "pr:risk-critical"),
                       ("pr:risk-unknown",), ("pr:risk-low", "pr:risk-unknown")):
            with patch.object(labeler, "get_ci_status", return_value="passed"), \
                    patch.object(labeler, "count_human_approvals", return_value=(99, False)):
                assert labeler.get_pr_state_label(pr_with_labels(*labels))[0] == labeler.LABEL_BLOCKED
    
    
    def test_low_label_and_zero_humans_are_not_an_agent_audit(self):
        with patch.object(labeler, "get_ci_status", return_value="passed"), \
                patch.object(labeler, "count_human_approvals", return_value=(0, False)):
            assert labeler.get_pr_state_label(pr_with_labels("pr:risk-low"))[0] == labeler.LABEL_NEEDS_REVIEW
    
    
    def test_human_path_and_change_requests_remain_enforced(self):
        for risk, count in labeler.RISK_APPROVALS.items():
            with patch.object(labeler, "get_ci_status", return_value="passed"), \
                    patch.object(labeler, "count_human_approvals", return_value=(count, False)):
                assert labeler.get_pr_state_label(pr_with_labels(risk))[0] == labeler.LABEL_APPROVED
            with patch.object(labeler, "get_ci_status", return_value="passed"), \
                    patch.object(labeler, "count_human_approvals", return_value=(count, True)):
                assert labeler.get_pr_state_label(pr_with_labels(risk))[0] == labeler.LABEL_CHANGES_REQUESTED
    
    
    def test_declarative_policy_preserves_higher_risk_and_requires_authenticated_evidence(self):
        policy = json.loads((ROOT / "config/ai-sdlc/review-policy.json").read_text())
        assert policy["version"] == "homedir.review-policy/v1"
        assert policy["defaultDecision"] == "needs-human"
        assert policy["approvalLabelsAreEvidence"] is False
        for risk, settings in policy["risks"].items():
            assert settings["humanApprovals"] == labeler.RISK_APPROVALS["pr:risk-" + risk]
            assert settings["agentAuditAlternative"] is (risk == "low")
        assert policy["agentAudit"]["trustedVerifierAuthenticationRequired"] is True
        assert policy["agentAudit"]["invalidatedByAnyBoundFieldChange"] is True
        assert policy["agentAudit"]["rawModelTextIsApproval"] is False
        assert {"headSHA", "baseSHA", "candidateDigest", "auditorIdentity"} <= set(policy["agentAudit"]["boundFields"])
