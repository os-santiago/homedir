"""
WOS Agent Routing Validation.

Implements routing validation for workspace-os agent delegation to reduce
wrong_agent errors from 6.9% to <1% (Issue #918).
"""
import re
from enum import Enum
from typing import List, Dict, Any


class RoutingResult(Enum):
    """Routing validation results."""
    VALID = "valid"
    AMBIGUOUS = "ambiguous"
    DECOMPOSE = "decompose"
    CAPABILITY_MISMATCH = "capability_mismatch"


WORK_TYPE_PATTERNS = {
    'code_analysis': r'(analyze|review|audit|check|validate)',
    'refactor': r'(refactor|restructure|clean|simplify|optimize)',
    'documentation': r'(document|spec|readme|guide|runbook)',
    'planning': r'(plan|design|architect|roadmap)',
    'deployment': r'(deploy|release|ship|rollout)',
    'security': r'(security|auth|permission|vulnerability)',
    'testing': r'test|coverage',
}

AGENT_CAPABILITIES = {
    'opencode': ['code_analysis', 'refactor', 'testing'],
    'claude': ['documentation', 'planning', 'code_analysis'],
    'antigravity': ['deployment', 'security', 'infrastructure'],
}


def classify_work(work_description: str) -> List[str]:
    """Classify work description into work types."""
    matches = []
    for work_type, pattern in WORK_TYPE_PATTERNS.items():
        if re.search(pattern, work_description, re.IGNORECASE):
            matches.append(work_type)
    return matches


def validate_routing(work_description: str, agent: str) -> Dict[str, Any]:
    """Validate that work can be routed to the specified agent."""
    work_types = classify_work(work_description)

    if agent not in AGENT_CAPABILITIES:
        return {
            'result': RoutingResult.CAPABILITY_MISMATCH,
            'work_types': work_types,
            'reason': f'Unknown agent: {agent}',
            'suggested_agent': 'claude',
        }

    if not work_types:
        return {
            'result': RoutingResult.AMBIGUOUS,
            'work_types': [],
            'reason': 'Work description is too vague to classify',
        }

    if len(work_types) > 2:
        return {
            'result': RoutingResult.DECOMPOSE,
            'work_types': work_types,
            'reason': f'Work involves {len(work_types)} types; consider decomposing',
        }

    agent_caps = AGENT_CAPABILITIES[agent]
    matching_caps = [wt for wt in work_types if wt in agent_caps]

    if matching_caps:
        return {
            'result': RoutingResult.VALID,
            'work_types': work_types,
            'reason': f'Agent {agent} has {matching_caps[0]} capability',
        }

    suggested_agent = _suggest_agent(work_types)
    return {
        'result': RoutingResult.CAPABILITY_MISMATCH,
        'work_types': work_types,
        'reason': f'Agent {agent} cannot handle {work_types[0]}',
        'suggested_agent': suggested_agent,
    }


def _suggest_agent(work_types: List[str]) -> str:
    """Suggest best agent for work types."""
    if not work_types:
        return 'claude'

    scores = {agent: 0 for agent in AGENT_CAPABILITIES}
    for work_type in work_types:
        for agent, caps in AGENT_CAPABILITIES.items():
            if work_type in caps:
                scores[agent] += 1

    best_agent = max(scores.items(), key=lambda x: (x[1], x[0] == 'claude'))
    return best_agent[0]


def log_routing_decision(
    work_description: str,
    agent: str,
    validation: Dict[str, Any],
    accepted: bool = True
) -> Dict[str, Any]:
    """Create telemetry log entry for routing decision."""
    return {
        'event': 'routing_decision',
        'work_description': work_description[:100],
        'agent': agent,
        'result': validation['result'].value,
        'work_types': validation['work_types'],
        'accepted': accepted,
        'suggested_agent': validation.get('suggested_agent'),
        'reason': validation['reason'],
    }
