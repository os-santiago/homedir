# Security Policy

> **Full security documentation**: [docs/en/SECURITY.md](docs/en/SECURITY.md) | [Español](docs/es/SECURITY.md)

## Reporting a Vulnerability

**Do not report security vulnerabilities through public GitHub issues.**

### How to Report

Use one of these private channels:

1. **GitHub Security Advisories** (Preferred)
   - Navigate to the repository [Security tab](https://github.com/os-santiago/homedir/security/advisories)
   - Click "Report a vulnerability"
   - Fill out the private vulnerability report form

2. **Email**
   - Send to: sergio.canales.e@gmail.com
   - Subject: `HOMEDIR SECURITY: <short description>`
   - Include: affected version, environment, impact, reproduction steps

### Response Timeline

- **Acknowledgement**: Within 48 hours
- **Initial assessment**: Within 5 business days
- **Status updates**: At least weekly until resolution

| Severity | Target Fix Time | Disclosure Window |
|----------|----------------|-------------------|
| Critical (P0) | ≤ 7 days | ≤ 30 days |
| High (P1) | ≤ 14-21 days | ≤ 60 days |
| Medium (P2) | Next release | ≤ 90 days |
| Low (P3) | As time permits | Next release |

## Supported Versions

We support only the **current major version**. Previous majors become unsupported (EOL) immediately upon new major release.

**Current active major: 2.x**

| Version | Supported |
|---------|-----------|
| 2.x | ✅ Active |
| < 2.0 | ❌ Unsupported |

## Security Features

- **Authentication**: OAuth2/OIDC (GitHub, Discord, Google)
- **Authorization**: Role-based access control (RBAC)
- **Secret Scanning**: Pre-commit hooks with gitleaks
- **Dependency Scanning**: Automated Dependabot alerts
- **HTTPS**: Enforced in production
- **Input Validation**: Server-side validation
- **CSRF Protection**: Built-in Quarkus protection

## Safe Harbor

We support good-faith security research that:
- Avoids privacy violations and data destruction
- Uses only your own accounts/data
- Reports vulnerabilities promptly
- Keeps details confidential until fix is released

We will not pursue legal action against researchers following these guidelines.

## Additional Resources

- **Full Security Policy**: [docs/en/SECURITY.md](docs/en/SECURITY.md)
- **Security Hardening Guide**: [docs/en/development/security-hardening-baseline.md](docs/en/development/security-hardening-baseline.md)
- **Endpoint Authorization Matrix**: [docs/security/endpoint-authorization-matrix.yaml](docs/security/endpoint-authorization-matrix.yaml)
- **Secret Scanning Setup**: [docs/guides/pre-commit-secret-scanning.md](docs/guides/pre-commit-secret-scanning.md)
- **Supply Chain Security**: [docs/en/features/supply-chain-security.md](docs/en/features/supply-chain-security.md)

## Security Contact

- **Vulnerability Reports**: Use GitHub Security Advisories (preferred) or sergio.canales.e@gmail.com
- **General Security Questions**: [GitHub Discussions - Security](https://github.com/os-santiago/homedir/discussions/categories/security)

---

**Last Updated**: June 2026
