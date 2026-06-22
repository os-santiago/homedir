# Contributing to Homedir

## Security: Pre-commit Secret Scanning

Before contributing, install the pre-commit secret scanning hook:

\`\`\`bash
# 1. Install gitleaks
# macOS: brew install gitleaks
# Windows: scoop install gitleaks
# Linux: Download from GitHub releases

# 2. Install the hook
cp hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# 3. Verify (should block)
echo "aws_key=AKIAIOSFODNN7EXAMPLE" > test.txt
git add test.txt && git commit -m "test" || echo "Working!"
git reset HEAD test.txt && rm test.txt
\`\`\`

See [docs/guides/pre-commit-secret-scanning.md](docs/guides/pre-commit-secret-scanning.md) for details.

---

