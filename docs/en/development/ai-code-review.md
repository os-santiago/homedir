# AI-Powered Code Review

## Overview

Homedir includes an automated AI-powered code review system that analyzes pull requests for code quality, security, performance, and best practices using Claude AI.

## Features

The AI code review analyzes the following aspects:

1. **Code Smells**: Duplicated code, long methods, large classes, excessive parameters
2. **Complexity Metrics**: Cyclomatic complexity, nesting depth, cognitive complexity
3. **Naming Conventions**: Clear, descriptive names following language conventions
4. **Documentation**: Missing or inadequate comments, outdated documentation
5. **Performance Anti-patterns**: Inefficient algorithms, unnecessary computations, resource leaks
6. **Security Concerns**: Potential vulnerabilities, unsafe practices
7. **Best Practices**: Language-specific idioms, SOLID principles, error handling

## How It Works

### Automatic Triggers

The AI code review workflow runs automatically on:

- Pull request opened
- Pull request reopened
- Pull request synchronized (new commits pushed)
- Pull request marked ready for review

### Manual Trigger

You can also manually trigger a review using workflow dispatch:

```bash
gh workflow run ai-code-review.yml -f pr_number=123
```

## Report Format

The AI review generates a comprehensive report that includes:

### Summary Section
- Model used for analysis
- Analysis timestamp
- Total files analyzed
- Overall assessment

### Findings by Category

Findings are grouped by category with severity indicators:

- 🔴 **High Severity**: Critical issues requiring immediate attention
- 🟡 **Medium Severity**: Important issues to address
- 🟢 **Low Severity**: Minor improvements or suggestions

### Code Metrics

The report includes metrics such as:
- Total files analyzed
- Number of findings per severity level
- Overall code quality assessment

## Example Report

```markdown
## 🤖 AI Code Review Report

**Analysis Date:** 2026-06-25T19:00:00Z
**Model:** claude-sonnet-4.5

### Summary
Reviewed 3 files. Found 5 findings: 1 high, 2 medium, 2 low severity.

#### 🔒 Security Concerns (1)
1. 🔴 **SQL Injection Risk**
   - **File:** `quarkus-app/src/main/java/com/scanales/homedir/service/UserService.java` (Line 42)
   - Unsanitized user input used in SQL query
   - **Suggestion:** Use parameterized queries or JPA criteria API

#### ⚡ Performance Anti-patterns (2)
1. 🟡 **N+1 Query Problem**
   - **File:** `quarkus-app/src/main/java/com/scanales/homedir/service/ProjectService.java` (Line 85)
   - Loading related entities in a loop
   - **Suggestion:** Use JOIN FETCH or batch loading

### Code Metrics
| Metric | Value |
|--------|-------|
| files_analyzed | 3 |
| total_findings | 5 |
| high_severity | 1 |
| medium_severity | 2 |
| low_severity | 2 |
```

## Configuration

### Required Secrets

Add the following secret to your repository:

- `ANTHROPIC_API_KEY`: Your Anthropic API key for Claude access

### Environment Variables

The workflow uses the following configuration:

```yaml
env:
  ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
```

### File Filters

The workflow analyzes changes in:

- `quarkus-app/src/**/*.java` - Java source files
- `scripts/**/*.sh` - Shell scripts
- `scripts/**/*.py` - Python scripts
- `.github/workflows/**` - GitHub Actions workflows

Documentation and markdown files are excluded to focus on code quality.

## Interpreting Results

### High Severity Findings

High severity findings typically indicate:
- Security vulnerabilities
- Critical performance issues
- Data loss risks
- Breaking changes

**Action Required**: Review and address before merging.

### Medium Severity Findings

Medium severity findings include:
- Code maintainability issues
- Minor performance concerns
- Best practice violations
- Documentation gaps

**Recommended**: Address when practical, may defer if justified.

### Low Severity Findings

Low severity findings are:
- Style improvements
- Minor optimizations
- Suggestions for clarity

**Optional**: Consider for code quality improvement.

## Integration with Development Workflow

### Pre-Merge Checklist

1. ✅ All automated tests pass
2. ✅ Code review by human reviewers
3. ✅ AI code review completed
4. ⚠️ High-severity AI findings addressed or documented
5. ✅ Security scans pass
6. ✅ Load tests pass (if applicable)

### Addressing AI Findings

When the AI identifies issues:

1. **Review the finding**: Understand the concern
2. **Validate**: Determine if it's a true positive
3. **Fix or document**: Either fix the issue or document why it's acceptable
4. **Discuss**: If unclear, discuss with team in PR comments

## Limitations

The AI code review is a **supplement**, not a replacement for:

- Human code review
- Automated testing
- Static analysis tools
- Security scanning

### Known Limitations

- **Context window**: Analysis is limited to changed files and surrounding context
- **False positives**: AI may flag acceptable patterns as issues
- **Language coverage**: Best results for Java; other languages may have less detailed analysis
- **No runtime analysis**: Cannot detect runtime-only issues

## Best Practices

### For Authors

1. **Keep PRs focused**: Smaller PRs get better AI analysis
2. **Write descriptive commit messages**: Helps AI understand intent
3. **Add context**: Use PR description to explain unusual patterns
4. **Review findings critically**: AI suggestions are guidance, not commands

### For Reviewers

1. **Use AI findings as starting points**: Follow up with deeper investigation
2. **Validate security findings**: Always verify security concerns
3. **Consider context**: AI may lack business logic understanding
4. **Provide feedback**: Help improve the system by noting false positives

## Troubleshooting

### Workflow Not Running

Check:
- Pull request is not in draft mode
- Changes include code files (not just docs/markdown)
- `ANTHROPIC_API_KEY` secret is configured
- Workflow file is on the target branch

### Analysis Timeout

If analysis times out:
- PR may be too large (>10 files analyzed)
- Consider splitting into smaller PRs
- Some files may be very large

### Unexpected Results

If AI findings seem incorrect:
- Review the specific code and context
- Check for similar patterns elsewhere
- Discuss in PR comments
- Document rationale if disagreeing with suggestion

## Cost Considerations

AI code review uses the Anthropic API, which has associated costs:

- **Model**: Claude Sonnet 4.5
- **Typical cost**: ~$0.01-0.05 per PR (varies by size)
- **Optimization**: Limited to 10 files per PR to control costs

Monitor usage in your Anthropic console and adjust limits as needed.

## Future Enhancements

Planned improvements:

- [ ] Custom rule configuration per project
- [ ] Integration with issue tracking
- [ ] Automated fix suggestions (PRs)
- [ ] Historical trend analysis
- [ ] Team-specific coding standards
- [ ] Multi-language optimization
- [ ] Incremental analysis (only new changes)

## Support

For issues or questions:

- **GitHub Issues**: [Report a bug](https://github.com/os-santiago/homedir/issues)
- **Discussions**: [Ask questions](https://github.com/os-santiago/homedir/discussions)
- **Discord**: [Join the community](https://discord.gg/3eawzc9ybc)

---

*This feature uses Claude AI to enhance code quality. Review AI suggestions critically and use human judgment for final decisions.*
