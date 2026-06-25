#!/usr/bin/env python3
"""
Unit tests for AI Code Review script.
"""

import json
import unittest
from unittest.mock import Mock, patch, MagicMock
import sys
from pathlib import Path

# Add scripts/ci to path
sys.path.insert(0, str(Path(__file__).parent.parent.parent / 'scripts' / 'ci'))

# Mock anthropic module for testing without installation
sys.modules['anthropic'] = MagicMock()

from ai_code_review import AICodeReviewer, CodeFinding, ReviewReport  # noqa: E402


class TestAICodeReviewer(unittest.TestCase):
    """Test cases for AICodeReviewer class."""

    def setUp(self):
        """Set up test fixtures."""
        self.api_key = "test-api-key"
        self.reviewer = AICodeReviewer(self.api_key)

    @patch('subprocess.run')
    def test_get_changed_files(self, mock_run):
        """Test getting changed files."""
        mock_run.return_value = Mock(
            stdout="file1.java\nfile2.py\nfile3.md\n",
            returncode=0
        )

        files = self.reviewer.get_changed_files("main", "feature-branch")

        self.assertIn("file1.java", files)
        self.assertIn("file2.py", files)
        self.assertNotIn("file3.md", files)  # Filtered out

    @patch('subprocess.run')
    def test_get_file_diff(self, mock_run):
        """Test getting file diff."""
        expected_diff = "+new line\n-old line\n"
        mock_run.return_value = Mock(
            stdout=expected_diff,
            returncode=0
        )

        diff = self.reviewer.get_file_diff("test.java", "main", "feature")

        self.assertEqual(diff, expected_diff)
        mock_run.assert_called_once()

    def test_get_file_content(self):
        """Test reading file content."""
        # Create temporary test file
        test_file = Path(__file__).parent / "test_temp.txt"
        test_content = "test content\nline 2\n"

        try:
            test_file.write_text(test_content)
            content = self.reviewer.get_file_content(str(test_file))
            self.assertEqual(content, test_content)
        finally:
            if test_file.exists():
                test_file.unlink()

    @patch('anthropic.Anthropic')
    def test_analyze_code_success(self, mock_anthropic_class):
        """Test successful code analysis."""
        # Mock API response
        mock_response = Mock()
        mock_response.content = [Mock(text=json.dumps({
            "findings": [
                {
                    "category": "code_smell",
                    "severity": "medium",
                    "title": "Long method",
                    "description": "Method is too long",
                    "line": 42,
                    "suggestion": "Extract to smaller methods"
                }
            ],
            "overall_quality": "good",
            "summary": "Code is generally good with minor issues"
        }))]

        mock_client = Mock()
        mock_client.messages.create.return_value = mock_response
        mock_anthropic_class.return_value = mock_client

        # Reinitialize reviewer with mocked client
        self.reviewer = AICodeReviewer(self.api_key)

        result = self.reviewer.analyze_code(
            "test.java",
            "+public void longMethod() {...}",
            "public class Test { ... }"
        )

        self.assertEqual(len(result["findings"]), 1)
        self.assertEqual(result["findings"][0]["severity"], "medium")
        self.assertEqual(result["overall_quality"], "good")

    @patch('anthropic.Anthropic')
    def test_analyze_code_with_markdown_json(self, mock_anthropic_class):
        """Test analysis with JSON wrapped in markdown."""
        # Mock API response with markdown code block
        mock_response = Mock()
        mock_response.content = [Mock(text="""
Here's the analysis:

```json
{
  "findings": [],
  "overall_quality": "excellent",
  "summary": "No issues found"
}
```
""")]

        mock_client = Mock()
        mock_client.messages.create.return_value = mock_response
        mock_anthropic_class.return_value = mock_client

        self.reviewer = AICodeReviewer(self.api_key)

        result = self.reviewer.analyze_code("test.java", "+code", "content")

        self.assertEqual(result["overall_quality"], "excellent")
        self.assertEqual(len(result["findings"]), 0)

    @patch.object(AICodeReviewer, 'get_changed_files')
    @patch.object(AICodeReviewer, 'get_file_diff')
    @patch.object(AICodeReviewer, 'get_file_content')
    @patch.object(AICodeReviewer, 'analyze_code')
    def test_review_changes(self, mock_analyze, mock_content, mock_diff, mock_files):
        """Test reviewing changes."""
        mock_files.return_value = ["file1.java", "file2.py"]
        mock_diff.return_value = "+new code"
        mock_content.return_value = "file content"
        # Return a fresh dict each time to avoid mutation issues
        mock_analyze.side_effect = lambda file_path, diff, content: {
            "findings": [
                {
                    "category": "naming",
                    "severity": "low",
                    "title": "Variable naming",
                    "description": "Use camelCase",
                    "line": 10,
                    "suggestion": "Rename variable"
                }
            ],
            "overall_quality": "good",
            "summary": "Minor issues"
        }

        report = self.reviewer.review_changes("main", "feature")

        self.assertEqual(report.metrics["files_analyzed"], 2)
        self.assertEqual(report.metrics["total_findings"], 2)
        self.assertEqual(report.metrics["low_severity"], 2)
        # Verify findings have file paths attached
        self.assertTrue(all("file" in f for f in report.findings))
        # Verify we got findings for the analyzed files
        self.assertGreater(len(report.findings), 0)

    @patch.object(AICodeReviewer, 'get_changed_files')
    def test_review_changes_no_files(self, mock_files):
        """Test review with no changed files."""
        mock_files.return_value = []

        report = self.reviewer.review_changes("main", "feature")

        self.assertEqual(report.metrics["files_changed"], 0)
        self.assertEqual(len(report.findings), 0)


    def test_calculate_complexity_metrics_java(self):
        """Test complexity metrics calculation for Java files."""
        java_content = """
public class Test {
    public void method1() {
        if (condition) {
            if (nested) {
                for (int i = 0; i < 10; i++) {
                    // Deep nesting
                }
            }
        }
    }

    private void method2() {}
    protected void method3() {}
}
"""
        metrics = self.reviewer.calculate_complexity_metrics(java_content, "Test.java")

        self.assertGreater(metrics["lines_of_code"], 0)
        self.assertGreaterEqual(metrics["method_count"], 3)
        self.assertGreater(metrics["nesting_depth"], 0)

    def test_calculate_complexity_metrics_python(self):
        """Test complexity metrics calculation for Python files."""
        python_content = """
def function1():
    if condition:
        for item in items:
            if nested:
                pass

def function2():
    pass

class MyClass:
    def method1(self):
        pass
"""
        metrics = self.reviewer.calculate_complexity_metrics(python_content, "test.py")

        self.assertGreater(metrics["lines_of_code"], 0)
        self.assertGreaterEqual(metrics["method_count"], 3)


class TestCodeFinding(unittest.TestCase):
    """Test cases for CodeFinding dataclass."""

    def test_code_finding_creation(self):
        """Test creating a CodeFinding."""
        finding = CodeFinding(
            category="security",
            severity="high",
            title="SQL Injection Risk",
            description="Unsanitized input in query",
            file="Database.java",
            line=42,
            suggestion="Use parameterized queries"
        )

        self.assertEqual(finding.category, "security")
        self.assertEqual(finding.severity, "high")
        self.assertEqual(finding.line, 42)


class TestReviewReport(unittest.TestCase):
    """Test cases for ReviewReport dataclass."""

    def test_review_report_creation(self):
        """Test creating a ReviewReport."""
        findings = [
            {
                "category": "performance",
                "severity": "medium",
                "title": "Inefficient loop",
                "description": "Use stream API",
                "file": "Service.java",
                "line": 100
            }
        ]

        report = ReviewReport(
            model="claude-sonnet-4-5",
            summary="Found 1 issue",
            findings=findings,
            metrics={"total": 1}
        )

        self.assertEqual(report.model, "claude-sonnet-4-5")
        self.assertEqual(len(report.findings), 1)
        self.assertEqual(report.metrics["total"], 1)


if __name__ == '__main__':
    unittest.main()
