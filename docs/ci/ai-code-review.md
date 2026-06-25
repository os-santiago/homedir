# AI-Powered Code Review

Automated code quality analysis using Claude AI that runs on every pull request to provide comprehensive feedback on code changes.

## Overview

The AI code review workflow analyzes code changes for:

- **Code Smells**: Duplicated code, long methods, large classes, excessive parameters
- **Complexity Metrics**: Cyclomatic complexity, nesting depth, cognitive complexity
- **Naming Conventions**: Clear, descriptive names following language conventions
- **Documentation**: Missing or inadequate comments, outdated documentation
- **Performance Anti-patterns**: Inefficient algorithms, unnecessary computations, resource leaks
- **Security Concerns**: Potential vulnerabilities, unsafe practices
- **Best Practices**: Language-specific idioms, SOLID principles, error handling

## Workflow Trigger

The workflow runs automatically on:

- Pull request opened, reopened, or synchronized
- Pull request marked ready for review
- Manual workflow dispatch with PR number

File types analyzed:
- Java files (`*.java`)
- Python files (`*.py`)
- Shell scripts (`*.sh`)
- YAML files (`*.yml`, `*.yaml`)

Files excluded:
- Documentation files in `docs/`

## Configuration

### Required Secrets

Set in repository settings → Secrets and variables → Actions:

- `ANTHROPIC_API_KEY`: Claude API key for AI analysis

### Workflow File

Location: `.github/workflows/ai-code-review.yml`

### Review Script

Location: `scripts/ci/ai_code_review.py`

## Usage

### Automatic Review

1. Create a pull request
2. Wait for the "AI Code Review" workflow to complete
3. Review the automated comment posted to the PR

### Manual Review

Trigger a review for a specific PR:

```bash
gh workflow run ai-code-review.yml -f pr_number=123
```

## Review Report

The workflow posts a comment on the PR with:

### Summary Section
- Analysis date
- Model version used
- Number of files analyzed
- Overall findings count by severity

### Findings by Category

Each finding includes:
- Severity level (🔴 high, 🟡 medium, 🟢 low)
- Title and description
- File path and line number
- Actionable suggestion for improvement

### Code Metrics

Aggregate metrics across analyzed files:
- Total files analyzed
- Total findings count
- Severity distribution

## Severity Levels

- **High (🔴)**: Critical issues that should be addressed before merge
  - Security vulnerabilities
  - Major performance problems
  - Severe code smells

- **Medium (🟡)**: Important issues to address
  - Moderate complexity
  - Best practice violations
  - Documentation gaps

- **Low (🟢)**: Minor improvements
  - Naming conventions
  - Code style preferences
  - Optional optimizations

## Review Artifacts

The workflow uploads:

- **Report JSON**: `ai-review-report.json` with full analysis
- **Retention**: 30 days
- **Download**: Via GitHub Actions artifacts

## Limitations

- Maximum 10 files analyzed per PR to avoid timeouts
- 15-minute workflow timeout
- Analysis limited to first 3000 characters of diff and 5000 characters of file content
- Only non-draft PRs are analyzed

## Integration with Quality Gates

High-severity findings trigger a workflow warning but do not block the PR. This allows human review to make the final decision.

To enforce blocking on high-severity issues, modify the workflow:

```yaml
- name: Check review outcome
  if: success()
  run: |
    high_severity=$(jq '[.findings[]? | select(.severity == "high")] | length' ai-review-report.json)
    if [ "$high_severity" -gt 0 ]; then
      echo "::error::Found $high_severity high-severity issues"
      exit 1
    fi
```

## Local Testing

Test the review script locally:

```bash
# Install dependencies
pip install -r scripts/ci/requirements-ai-review.txt

# Set API key
export ANTHROPIC_API_KEY="your-key-here"

# Run review
python scripts/ci/ai_code_review.py \
  --pr-number 123 \
  --base-ref origin/main \
  --head-ref feature-branch \
  --repository owner/repo
```

## Running Tests

```bash
# Run AI code review tests
python -m pytest tests/ci/test_ai_code_review.py -v

# Run with coverage
python -m pytest tests/ci/test_ai_code_review.py --cov=scripts.ci.ai_code_review
```

## Cost Considerations

The AI review uses Claude Sonnet 4.5 model:

- Approximate cost: $3 per million input tokens, $15 per million output tokens
- Typical PR review: ~5,000-10,000 tokens (input + output)
- Estimated cost per PR: $0.05-$0.20

Monitor usage via Anthropic Console to track costs.

## Customization

### Adjust File Filters

Edit `scripts/ci/ai_code_review.py`:

```python
return [
    f for f in files
    if f.endswith(('.java', '.py', '.sh', '.yml', '.yaml', '.js', '.ts'))
    and not f.startswith('docs/')
]
```

### Modify Analysis Prompt

Edit the `analyze_code` method in `scripts/ci/ai_code_review.py` to customize:
- Focus areas
- Severity thresholds
- Output format

### Change Model

Update the model version in `scripts/ci/ai_code_review.py`:

```python
self.model = "claude-sonnet-4-6@20260301"  # Use newer model
```

## Troubleshooting

### Workflow Fails to Run

- Verify `ANTHROPIC_API_KEY` is set in repository secrets
- Check that PR is not in draft state
- Ensure PR targets main branch

### No Comment Posted

- Check workflow logs for errors
- Verify GitHub token has `pull-requests: write` permission
- Ensure `ai-review-report.json` was generated

### API Rate Limits

If hitting rate limits:
- Reduce number of files analyzed in script
- Implement exponential backoff in API calls
- Use prompt caching to reduce costs

### Timeout Issues

For large PRs:
- Increase timeout in workflow (max 30 minutes)
- Reduce files analyzed per run
- Split analysis into multiple jobs

## Best Practices

1. **Review AI findings critically** - AI may produce false positives
2. **Use as a supplement** - Not a replacement for human code review
3. **Address high-severity issues** - Before requesting human review
4. **Provide context** - Add PR comments explaining why certain findings don't apply
5. **Monitor costs** - Track API usage in Anthropic Console

## Related Documentation

- [CI/CD Pipeline](../en/ci-cd.md)
- [Quality Gates](.github/workflows/quality-gates.yml)
- [PR Check Workflow](.github/workflows/pr-check.yml)
- [Claude API Documentation](https://docs.anthropic.com/claude/reference)

## Support

For issues or questions:
1. Check workflow logs in GitHub Actions
2. Review test output: `pytest tests/ci/test_ai_code_review.py -v`
3. Open an issue in the repository
