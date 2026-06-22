"""Test WOS agent routing validation implementation (Issue #918)."""
import sys
import os
import pytest

# Add scripts directory to path
sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(__file__)), 'scripts'))

from wos.routing import (
    validate_routing,
    classify_work,
    log_routing_decision,
    RoutingResult,
)


class TestClassifyWork:
    """Test work classification."""

    def test_code_analysis_pattern(self):
        result = classify_work("analyze endpoint security")
        assert 'code_analysis' in result

    def test_documentation_pattern(self):
        result = classify_work("document the API flow")
        assert 'documentation' in result

    def test_deployment_pattern(self):
        result = classify_work("deploy to production")
        assert 'deployment' in result

    def test_ambiguous_work(self):
        result = classify_work("do something")
        assert len(result) == 0


class TestValidateRouting:
    """Test routing validation."""

    def test_valid_routing_opencode_code_analysis(self):
        result = validate_routing("analyze endpoint security", "opencode")
        assert result['result'] == RoutingResult.VALID
        assert 'code_analysis' in result['work_types']

    def test_valid_routing_claude_documentation(self):
        result = validate_routing("document the auth flow", "claude")
        assert result['result'] == RoutingResult.VALID
        assert 'documentation' in result['work_types']

    def test_invalid_routing_opencode_deployment(self):
        result = validate_routing("deploy to production", "opencode")
        assert result['result'] == RoutingResult.CAPABILITY_MISMATCH
        assert result['suggested_agent'] == 'antigravity'

    def test_ambiguous_work_routing(self):
        result = validate_routing("do something", "claude")
        assert result['result'] == RoutingResult.AMBIGUOUS

    def test_unknown_agent(self):
        result = validate_routing("analyze code", "unknown_agent")
        assert result['result'] == RoutingResult.CAPABILITY_MISMATCH
        assert result['suggested_agent'] == 'claude'


class TestLogRoutingDecision:
    """Test telemetry logging."""

    def test_log_structure(self):
        validation = validate_routing("analyze code", "opencode")
        log_entry = log_routing_decision("analyze code", "opencode", validation)
        
        assert log_entry['event'] == 'routing_decision'
        assert log_entry['agent'] == 'opencode'
        assert log_entry['result'] == RoutingResult.VALID.value
        assert log_entry['accepted'] is True

    def test_log_truncates_long_description(self):
        long_desc = "a" * 200
        validation = validate_routing(long_desc, "claude")
        log_entry = log_routing_decision(long_desc, "claude", validation)
        
        assert len(log_entry['work_description']) == 100


class TestRealScenarios:
    """Test real scenarios from WOS telemetry."""

    def test_issue_854_authorization_audit(self):
        result = validate_routing("audit endpoint authorization controls", "opencode")
        assert result['result'] == RoutingResult.VALID

    def test_issue_874_dashboard_docs(self):
        result = validate_routing("document business metrics dashboard", "claude")
        assert result['result'] == RoutingResult.VALID

    def test_cross_check_routing(self):
        result = validate_routing("cross-check code changes for correctness", "opencode")
        assert result['result'] == RoutingResult.VALID
