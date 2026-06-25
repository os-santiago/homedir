#!/usr/bin/env python3
"""
AI-Powered Code Review Script
Analyzes code changes using Claude API for comprehensive quality checks.
"""

import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from typing import List, Dict, Optional, Any

try:
    import anthropic
except ImportError:
    print("Error: anthropic package not installed. Run: pip install anthropic", file=sys.stderr)
    sys.exit(1)


@dataclass
class CodeFinding:
    """Represents a code review finding."""
    category: str
    severity: str
    title: str
    description: str
    file: Optional[str] = None
    line: Optional[int] = None
    suggestion: Optional[str] = None


@dataclass
class ReviewReport:
    """Complete code review report."""
    model: str
    summary: str
    findings: List[Dict[str, Any]]
    metrics: Dict[str, Any]


class AICodeReviewer:
    """AI-powered code reviewer using Claude."""

    def __init__(self, api_key: str):
        """Initialize the code reviewer with Anthropic API key."""
        self.client = anthropic.Anthropic(api_key=api_key)
        self.model = "claude-sonnet-4-5@20250929"

    def get_changed_files(self, base_ref: str, head_ref: str) -> List[str]:
        """Get list of changed files between base and head."""
        try:
            result = subprocess.run(
                ["git", "diff", "--name-only", base_ref, head_ref],
                capture_output=True,
                text=True,
                check=True
            )
            files = [f.strip() for f in result.stdout.strip().split('\n') if f.strip()]
            # Filter relevant files
            return [
                f for f in files
                if f.endswith(('.java', '.py', '.sh', '.yml', '.yaml'))
                and not f.startswith('docs/')
            ]
        except subprocess.CalledProcessError as e:
            print(f"Error getting changed files: {e}", file=sys.stderr)
            return []

    def get_file_diff(self, file_path: str, base_ref: str, head_ref: str) -> str:
        """Get diff for a specific file."""
        try:
            result = subprocess.run(
                ["git", "diff", base_ref, head_ref, "--", file_path],
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout
        except subprocess.CalledProcessError as e:
            print(f"Error getting diff for {file_path}: {e}", file=sys.stderr)
            return ""

    def get_file_content(self, file_path: str) -> str:
        """Read current file content."""
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                return f.read()
        except Exception as e:
            print(f"Error reading {file_path}: {e}", file=sys.stderr)
            return ""

    def calculate_complexity_metrics(self, content: str, file_path: str) -> Dict[str, Any]:
        """Calculate basic complexity metrics for code."""
        metrics = {
            "lines_of_code": len(content.split('\n')),
            "nesting_depth": 0,
            "method_count": 0
        }

        # Count methods/functions based on file type
        if file_path.endswith('.java'):
            metrics["method_count"] = content.count('public ') + content.count('private ') + content.count('protected ')
        elif file_path.endswith('.py'):
            metrics["method_count"] = content.count('def ')

        # Estimate max nesting depth (simple heuristic)
        max_depth = 0
        current_depth = 0
        for line in content.split('\n'):
            stripped = line.lstrip()
            if stripped and not stripped.startswith('#') and not stripped.startswith('//'):
                indent = len(line) - len(line.lstrip())
                if file_path.endswith('.py'):
                    current_depth = indent // 4
                else:
                    current_depth = line.count('{') - line.count('}')
                max_depth = max(max_depth, current_depth)

        metrics["nesting_depth"] = max_depth
        return metrics

    def analyze_code(self, file_path: str, diff: str, content: str) -> Dict[str, Any]:
        """Analyze code using Claude API."""
        # Calculate complexity metrics
        complexity = self.calculate_complexity_metrics(content, file_path)

        prompt = f"""You are an expert code reviewer. Analyze the following code changes and provide comprehensive feedback.

Focus on:
1. **Code Smells**: Duplicated code, long methods, large classes, excessive parameters, dead code
2. **Complexity Metrics**: Cyclomatic complexity, nesting depth (current: {complexity['nesting_depth']}), cognitive complexity
3. **Naming Conventions**: Clear, descriptive names following language conventions (camelCase for Java, snake_case for Python)
4. **Documentation**: Missing or inadequate comments, outdated documentation, missing docstrings/JavaDoc
5. **Performance Anti-patterns**: Inefficient algorithms, N+1 queries, unnecessary computations, resource leaks, unbounded collections
6. **Security Concerns**: SQL injection, XSS, hardcoded credentials, unsafe deserialization, path traversal
7. **Best Practices**: Language-specific idioms, SOLID principles, proper error handling, null safety

**File:** {file_path}
**Complexity Metrics:** LOC={complexity['lines_of_code']}, Methods={complexity['method_count']}, Max Nesting={complexity['nesting_depth']}

**Diff:**
```diff
{diff[:3000]}
```

**Full Context (current state):**
```
{content[:5000]}
```

Provide your analysis in JSON format:
{{
  "findings": [
    {{
      "category": "code_smell|complexity|naming|documentation|performance|security|best_practice",
      "severity": "low|medium|high",
      "title": "Brief title",
      "description": "Detailed explanation",
      "line": <line_number or null>,
      "suggestion": "How to fix"
    }}
  ],
  "overall_quality": "excellent|good|fair|needs_improvement",
  "summary": "Overall assessment of changes",
  "complexity_concerns": ["List any specific complexity concerns"]
}}

Be specific, actionable, and constructive. Focus on real issues, not minor style preferences.
If nesting depth > 4 or method count > 20 per file, flag as complexity concern.
"""

        try:
            response = self.client.messages.create(
                model=self.model,
                max_tokens=4096,
                temperature=0.2,
                messages=[
                    {"role": "user", "content": prompt}
                ]
            )

            # Parse the response - extract text from the first content block
            response_text = ""
            for block in response.content:
                if hasattr(block, 'text'):
                    response_text = block.text
                    break

            content = response_text

            # Extract JSON from response (handle markdown code blocks)
            if "```json" in content:
                json_start = content.find("```json") + 7
                json_end = content.find("```", json_start)
                content = content[json_start:json_end].strip()
            elif "```" in content:
                json_start = content.find("```") + 3
                json_end = content.find("```", json_start)
                content = content[json_start:json_end].strip()

            return json.loads(content)

        except json.JSONDecodeError as e:
            print(f"Error parsing AI response for {file_path}: {e}", file=sys.stderr)
            print(f"Raw response: {content[:500]}", file=sys.stderr)
            return {"findings": [], "overall_quality": "unknown", "summary": "Analysis failed"}
        except Exception as e:
            print(f"Error analyzing {file_path}: {e}", file=sys.stderr)
            return {"findings": [], "overall_quality": "unknown", "summary": f"Error: {str(e)}"}

    def review_changes(self, base_ref: str, head_ref: str, max_files: int = 15) -> ReviewReport:
        """Review all changed files."""
        changed_files = self.get_changed_files(base_ref, head_ref)

        if not changed_files:
            return ReviewReport(
                model=self.model,
                summary="No relevant files changed",
                findings=[],
                metrics={"files_changed": 0}
            )

        print(f"Analyzing {len(changed_files)} changed files (max {max_files})...")

        all_findings = []
        quality_scores = []
        files_analyzed = 0
        complexity_warnings = []

        for file_path in changed_files[:max_files]:
            print(f"  - Analyzing {file_path}...")

            diff = self.get_file_diff(file_path, base_ref, head_ref)
            if not diff:
                continue

            content = self.get_file_content(file_path)
            if not content:
                continue

            # Calculate complexity upfront
            complexity = self.calculate_complexity_metrics(content, file_path)
            if complexity['nesting_depth'] > 4:
                complexity_warnings.append(f"{file_path}: High nesting depth ({complexity['nesting_depth']})")
            if complexity['method_count'] > 20:
                complexity_warnings.append(f"{file_path}: High method count ({complexity['method_count']})")

            analysis = self.analyze_code(file_path, diff, content)
            files_analyzed += 1

            # Add file path to each finding
            for finding in analysis.get("findings", []):
                finding["file"] = file_path
                all_findings.append(finding)

            quality_scores.append(analysis.get("overall_quality", "unknown"))

        # Generate summary
        high_severity = len([f for f in all_findings if f.get("severity") == "high"])
        medium_severity = len([f for f in all_findings if f.get("severity") == "medium"])
        low_severity = len([f for f in all_findings if f.get("severity") == "low"])

        summary_parts = [
            f"Reviewed {files_analyzed}/{len(changed_files)} files",
            f"Found {len(all_findings)} findings: {high_severity} high, {medium_severity} medium, {low_severity} low severity"
        ]

        if complexity_warnings:
            summary_parts.append(f"Complexity warnings: {len(complexity_warnings)}")

        summary = ". ".join(summary_parts) + "."

        # Categorize findings
        categories: Dict[str, int] = {}
        for finding in all_findings:
            cat = finding.get("category", "unknown")
            categories[cat] = categories.get(cat, 0) + 1

        metrics = {
            "files_analyzed": files_analyzed,
            "total_files": len(changed_files),
            "total_findings": len(all_findings),
            "high_severity": high_severity,
            "medium_severity": medium_severity,
            "low_severity": low_severity,
            "complexity_warnings": len(complexity_warnings),
            "categories": categories
        }

        return ReviewReport(
            model=self.model,
            summary=summary,
            findings=all_findings,
            metrics=metrics
        )


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="AI-powered code review")
    parser.add_argument("--pr-number", required=True, help="Pull request number")
    parser.add_argument("--base-ref", required=True, help="Base git reference")
    parser.add_argument("--head-ref", required=True, help="Head git reference")
    parser.add_argument("--repository", required=True, help="Repository name (owner/repo)")
    parser.add_argument("--output", default="ai-review-report.json", help="Output file path")

    args = parser.parse_args()

    # Get API key from environment
    api_key = os.getenv("ANTHROPIC_API_KEY")
    if not api_key:
        print("Error: ANTHROPIC_API_KEY environment variable not set", file=sys.stderr)
        sys.exit(1)

    # Initialize reviewer
    reviewer = AICodeReviewer(api_key)

    # Run review
    print(f"Starting AI code review for PR #{args.pr_number}")
    print(f"Base: {args.base_ref}, Head: {args.head_ref}")

    report = reviewer.review_changes(args.base_ref, args.head_ref)

    # Save report
    report_dict = {
        "model": report.model,
        "summary": report.summary,
        "findings": report.findings,
        "metrics": report.metrics,
        "pr_number": args.pr_number,
        "repository": args.repository
    }

    with open(args.output, 'w') as f:
        json.dump(report_dict, f, indent=2)

    print(f"\nReview complete! Report saved to {args.output}")
    print(f"Summary: {report.summary}")

    # Exit with non-zero if high severity issues found
    if report.metrics.get("high_severity", 0) > 0:
        print(f"\n⚠️  Warning: Found {report.metrics['high_severity']} high-severity issues")
        # Don't fail the build, just warn
        sys.exit(0)

    sys.exit(0)


if __name__ == "__main__":
    main()
