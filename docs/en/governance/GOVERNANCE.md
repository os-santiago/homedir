# Homedir Governance Model

## Overview

Homedir is an open-source project governed by a **benevolent maintainer model**. This document defines the roles, responsibilities, and decision-making processes for the project.

## Roles

### Maintainers

Maintainers are responsible for the overall health and direction of the project. Their responsibilities include:

- Reviewing and merging contributions
- Setting technical direction and roadmap
- Enforcing the Code of Conduct
- Managing releases and versioning
- Onboarding new contributors

**Current Maintainers**:
- Sergio Canales (@scanalesespinoza / @scanales-stack)

### Contributors

Contributors are individuals who actively participate through issues, pull requests, reviews, or community engagement. To become a recognized contributor:

1. Submit at least 3 non-trivial pull requests that are merged
2. Participate in code reviews
3. Demonstrate adherence to project standards and Code of Conduct

### Users

Users are the broader community who use Homedir and may provide feedback through issues or discussions.

## Decision-Making

### Day-to-Day Decisions

Maintainers make day-to-day technical decisions through review and merge of pull requests. Significant decisions should be documented in the relevant PR or issue.

### Significant Decisions

Decisions that affect the project's architecture, governance, or community require:

1. An issue describing the proposal (with "RFC:" prefix)
2. At least 2 business days for community feedback
3. Consensus among maintainers
4. Documentation of the decision and rationale

### Voting

When consensus cannot be reached, maintainers may call for a vote:
- **Simple majority** for most decisions (≥50% of maintainers)
- **Super-majority** (≥66%) for governance changes, license changes, or maintainer appointments/removals

## Contribution Process

1. **Issues**: Start by creating an issue describing the proposed change
2. **Discussion**: Allow time for community feedback
3. **Implementation**: Create a branch and implement the change
4. **Pull Request**: Submit a PR referencing the issue
5. **Review**: At least one maintainer must approve the PR
6. **Merge**: Approved PRs are merged by a maintainer

All contributions must comply with:
- [CODE_OF_CONDUCT.md](../../CODE_OF_CONDUCT.md)
- [CONTRIBUTING.md](../../CONTRIBUTING.md)
- Project coding standards and conventions

## Release Process

Releases follow [Semantic Versioning](https://semver.org/) (MAJOR.MINOR.PATCH):

- **PATCH** (3.x.x): Bug fixes and minor changes (backward compatible)
- **MINOR** (3.x.0): New features (backward compatible)
- **MAJOR** (x.0.0): Breaking changes

Releases are automated via GitHub Actions workflows. The `main` branch always reflects the latest release candidate.

## Code of Conduct Enforcement

All community spaces follow the [Code of Conduct](../../CODE_OF_CONDUCT.md). Maintainers are responsible for enforcing it, following the enforcement guidelines documented there.

## Governance Changes

Amendments to this governance model follow the same process as significant decisions with super-majority vote.

---

*Last updated: 2026-06-23*
