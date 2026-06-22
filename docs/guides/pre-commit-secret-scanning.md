# Pre-Commit Secret Scanning

This guide explains how to set up local pre-commit secret scanning to prevent accidental credential leaks.

## Overview

The pre-commit hook uses [gitleaks](https://github.com/gitleaks/gitleaks) to scan staged changes for secrets before commit.

## Quick Setup (< 5 minutes)

### 1. Install gitleaks

**macOS:** \`brew install gitleaks\`
**Windows:** \`choco install gitleaks\`
**Linux:** Download from https://github.com/gitleaks/gitleaks/releases

### 2. Install the hook

\`\`\`bash
cp hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
\`\`\`

### 3. Test

\`\`\`bash
echo "aws_key = AKIAIOSFODNN7EXAMPLE" > test.txt
git add test.txt
git commit -m "test"  # Should be blocked
git reset HEAD test.txt && rm test.txt
\`\`\`

Full documentation: See this file for complete usage, troubleshooting, and configuration details.
