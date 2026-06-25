# AI Code Review Setup Guide

## Prerequisites

1. **Anthropic API Key**: Sign up at [console.anthropic.com](https://console.anthropic.com)
2. **Repository Admin Access**: Required to add secrets
3. **GitHub Actions Enabled**: Verify workflows are enabled in repository settings

## Setup Steps

### 1. Obtain Anthropic API Key

1. Visit [Anthropic Console](https://console.anthropic.com)
2. Create an account or sign in
3. Navigate to API Keys section
4. Generate a new API key
5. **Save the key securely** (it won't be shown again)

### 2. Add Secret to Repository

#### Via GitHub UI

1. Go to repository **Settings**
2. Navigate to **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Name: `ANTHROPIC_API_KEY`
5. Value: Paste your API key
6. Click **Add secret**

#### Via GitHub CLI

```bash
gh secret set ANTHROPIC_API_KEY --repo os-santiago/homedir
# Paste your API key when prompted
```

### 3. Verify Workflow File

The AI code review workflow should be present at:

```
.github/workflows/ai-code-review.yml
```

Check that it's configured correctly:

```yaml
env:
  ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
```

### 4. Test the Setup

#### Option A: Create a Test PR

1. Create a feature branch:
   ```bash
   git checkout -b test/ai-review-setup
   ```

2. Make a small code change (e.g., add a new method)

3. Commit and push:
   ```bash
   git add .
   git commit -m "test: verify AI code review setup"
   git push -u origin test/ai-review-setup
   ```

4. Create a pull request:
   ```bash
   gh pr create --title "Test AI Code Review" --body "Testing AI review setup"
   ```

5. Check the **Actions** tab for workflow execution

#### Option B: Manual Workflow Trigger

```bash
# Trigger on an existing PR
gh workflow run ai-code-review.yml -f pr_number=123
```

### 5. Verify Results

After the workflow completes:

1. **Check PR Comments**: Look for AI review report comment
2. **Review Artifacts**: Download report from workflow artifacts
3. **Inspect Summary**: Check GitHub Actions summary page

## Troubleshooting

### Secret Not Found Error

```
Error: ANTHROPIC_API_KEY environment variable not set
```

**Solution**:
- Verify secret name is exactly `ANTHROPIC_API_KEY`
- Check secret is added to correct repository
- Ensure workflow has permission to access secrets

### Workflow Not Triggering

**Possible causes**:
- PR is in draft mode (workflow skips drafts)
- No code files changed (only docs/markdown)
- Workflow file not on base branch

**Solution**:
```bash
# Ensure workflow exists on main branch
git checkout main
git pull
ls -la .github/workflows/ai-code-review.yml
```

### API Rate Limits

```
Error: Rate limit exceeded
```

**Solution**:
- Check Anthropic console for rate limits
- Upgrade API plan if needed
- Reduce number of PRs analyzed simultaneously

### Permission Errors

```
Error: Resource not accessible by integration
```

**Solution**: Update workflow permissions in `.github/workflows/ai-code-review.yml`:

```yaml
permissions:
  contents: read
  pull-requests: write
  issues: write
```

## Cost Management

### Estimating Costs

- **Small PR** (1-3 files): ~$0.01-0.02
- **Medium PR** (4-7 files): ~$0.02-0.04  
- **Large PR** (8-10 files): ~$0.04-0.08

### Optimization Tips

1. **File Limits**: Workflow analyzes max 10 files per PR
2. **Context Limits**: Diffs truncated to 3000 chars, content to 5000 chars
3. **Skip Docs**: Documentation files are excluded
4. **Batch PRs**: Avoid triggering on every commit (use synchronize event)

### Monitoring Usage

Check your usage at [Anthropic Console](https://console.anthropic.com):

- Navigate to **Usage** section
- Monitor monthly spend
- Set up billing alerts

## Security Considerations

### API Key Security

- ✅ **DO**: Store in GitHub Secrets
- ✅ **DO**: Rotate keys periodically
- ✅ **DO**: Use separate keys for prod/staging
- ❌ **DON'T**: Commit keys to repository
- ❌ **DON'T**: Share keys in plain text
- ❌ **DON'T**: Use same key across multiple projects

### Access Control

Limit who can:
- View/modify repository secrets (admin only)
- Trigger workflow_dispatch events
- Access workflow artifacts

### Audit Trail

Monitor:
- Workflow execution logs
- API usage in Anthropic console
- GitHub audit log for secret access

## Advanced Configuration

### Custom File Patterns

Edit `.github/workflows/ai-code-review.yml`:

```yaml
on:
  pull_request:
    paths:
      - 'quarkus-app/src/**/*.java'
      - 'scripts/**/*.py'
      - 'custom-path/**/*.kt'  # Add Kotlin files
```

### Adjust Analysis Depth

Modify `scripts/ci/ai_code_review.py`:

```python
# Increase file limit (default: 10)
for file_path in changed_files[:20]:  # Analyze up to 20 files

# Increase context sizes
diff[:5000]    # Instead of 3000
content[:10000]  # Instead of 5000
```

### Custom Review Prompt

Edit the prompt in `ai_code_review.py` to focus on specific concerns:

```python
prompt = f"""You are an expert code reviewer specializing in Java and Quarkus.

Focus specifically on:
1. Quarkus best practices
2. JAX-RS resource patterns
3. Transaction management
4. Reactive programming patterns

...
"""
```

### Model Selection

Change the Claude model (adjust cost/quality tradeoff):

```python
# In AICodeReviewer class
self.model = "claude-opus-4-8@20250929"      # Higher quality, more expensive
# or
self.model = "claude-haiku-4-5-20251001"     # Faster, less expensive
```

## Integration with Other Tools

### CodeRabbit Integration

AI code review complements [CodeRabbit](https://coderabbit.ai):

- **CodeRabbit**: Continuous review, suggestions, learning
- **This tool**: Deep analysis, custom rules, cost control

Both can run in parallel on PRs.

### SonarQube Integration

Combine with static analysis:

1. SonarQube for code coverage, duplicates, static rules
2. AI review for contextual quality, best practices

### Issue Tracking

Link AI findings to issues:

```bash
# Create issue from high-severity finding
gh issue create --title "Security: SQL Injection in UserService" \
  --label "security,ai-review" \
  --body "AI code review identified potential SQL injection..."
```

## Maintenance

### Regular Tasks

**Weekly**:
- Review false positives
- Monitor API costs
- Check workflow success rate

**Monthly**:
- Rotate API keys
- Review and update prompts
- Update documentation based on findings

**Quarterly**:
- Evaluate model upgrades
- Assess cost vs. value
- Gather team feedback

### Updating the System

Pull latest changes:

```bash
git pull origin main
```

Update dependencies:

```bash
pip install -r scripts/ci/requirements-ai-review.txt --upgrade
```

Test after updates:

```bash
python -m pytest tests/ci/test_ai_code_review.py -v
```

## Getting Help

### Resources

- [Anthropic API Documentation](https://docs.anthropic.com)
- [Claude Model Information](https://www.anthropic.com/claude)
- [GitHub Actions Documentation](https://docs.github.com/actions)

### Support Channels

- **Technical Issues**: Open issue in this repository
- **API Questions**: [Anthropic Support](https://support.anthropic.com)
- **Feature Requests**: [GitHub Discussions](https://github.com/os-santiago/homedir/discussions)

---

*Setup complete? Open a test PR to verify the AI code review is working correctly.*
