# Contributing to Homedir

## Security: Pre-commit Secret Scanning

Before contributing, install the pre-commit secret scanning hook:

```bash
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
```

See [docs/guides/pre-commit-secret-scanning.md](docs/guides/pre-commit-secret-scanning.md) for details.

---

## Contributor License Agreement

By contributing to Homedir, you agree to the terms of the [Contributor License Agreement](CLA.md). Each commit must include a `Signed-off-by` trailer:

```bash
git commit -s -m "feat: my contribution"
```

This certifies that you have the right to submit the contribution under the Apache 2.0 license and that you have read and agree to the CLA.

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch: `git checkout -b feat/issue-XXX-description`
4. Make your changes
5. Commit with `git commit -s` (signed-off-by)
6. Push and create a Pull Request

## Pull Request Guidelines

- Reference the issue: `Closes #XXX`
- Use [conventional commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `chore:`, etc.
- Ensure all CI checks pass
- Update documentation if needed
- Add tests for new functionality

## Code Style

- Java: Follow existing project conventions (Quarkus)
- Python: Follow PEP 8
- Documentation: Use Markdown with English primary, Spanish stubs where applicable

## Need Help?

Open a [Discussion](https://github.com/os-santiago/homedir/discussions) or check the [docs](docs/) for guides.
