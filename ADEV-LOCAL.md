# Homedir-Specific Doctrine
## Local Extension to A-Dev Framework

**Repository**: github.com/os-santiago/homedir  
**Version**: 1.0  
**Last Updated**: 2026-08-24

---

## Technology Stack

### Core Technologies
- **Language**: Java 21
- **Framework**: Quarkus 3.16.0
- **Build**: Maven 3.9+
- **Database**: PostgreSQL 16
- **Deployment**: K3s on VPS

### Key Dependencies
- Quarkus REST (RESTEasy Reactive)
- Quarkus Qute (templating)
- Quarkus Scheduler
- Quarkus OIDC
- Flyway (database migrations)

---

## Deployment Model

### Environments
1. **Dev**: Local Quarkus dev mode (`mvn quarkus:dev`)
2. **Staging**: Live Alpha with feature flags
3. **Production**: K3s cluster on VPS (72.60.141.165)

### Deployment Strategy
- Feature flags for progressive rollout (not heavy staging)
- Incremental: hidden → non-production → progressive → legacy cleanup
- Validate production impact before advancing

---

## Quality Gates (extends A-Dev QUALITY.md)

### Build Phase
```bash
mvn clean compile
mvn quarkus:dev # Local development
```

### Run Phase
```bash
mvn clean test # Unit tests
mvn verify # Integration tests
curl http://localhost:8080/q/health/live # Health check
curl http://localhost:8080/q/health/ready # Readiness
```

### Walkthrough Phase
- Persona-based validation (admin, user, guest)
- UI/UX review in browser
- Test all i18n languages (pt-BR, en-US, es-ES)
- Mobile responsiveness check

### Evidence Phase
- Conventional commit: `feat(module): description`
- GitHub Actions CI green
- Update CHANGELOG.md
- Documentation in docs/

---

## Specific Constraints

### API Compatibility
- **No breaking changes** to public REST API without:
  1. Deprecation notice (1 release ahead)
  2. Migration guide in docs/
  3. Backward compatibility layer

### Database Migrations
- **Backward compatible** migrations only
- Use Flyway versioned migrations (V###__description.sql)
- Test rollback scenario
- Never modify existing migrations (create new ones)

### Internationalization (i18n)
- **Mandatory** for all user-facing text
- Languages: pt-BR (primary), en-US, es-ES
- No hardcoded strings in:
  - Templates (.qute.html)
  - JavaScript
  - Backend messages
  - UI components
- Use message bundles: `src/main/resources/messages_*.properties`

### Security
- OIDC authentication required for admin routes
- Rate limiting on public endpoints
- Input validation on all forms
- SQL injection prevention (parameterized queries)
- XSS prevention (escape user input)

### Testing
- **Minimum coverage**: 80%
- Test types:
  - Unit: @QuarkusTest
  - Integration: @QuarkusIntegrationTest
  - E2E: Manual walkthrough
- Mock external dependencies
- Test i18n bundles

---

## Branch Workflow (extends A-Dev)

### Branch Names
```
feat/issue-123-feature-description
fix/issue-456-bug-description
docs/update-deployment-guide
chore/upgrade-quarkus-3.16
refactor/simplify-auth-logic
```

### PR Requirements
- Title: Conventional commits format
- Links: `Closes #123`
- Description: Follow PR template
- Tests: Coverage >= 80%
- CI: All checks green
- Review: 1 approval (for P0/P1)

---

## CI/CD Pipeline

### GitHub Actions Workflows
1. **pr-check.yml**: Basic validation
2. **pr-quality-suite.yml**: Quality gates
3. **quality-gates.yml**: Build + test + coverage
4. **deploy-*.yml**: Deployment automation

### Quality Checks
- ✅ Build successful
- ✅ Tests pass
- ✅ Coverage >= 80%
- ✅ No security vulnerabilities (OWASP)
- ✅ No code smells (SpotBugs)
- ✅ I18n bundles complete

---

## Specific Patterns

### REST Endpoint Pattern
```java
@Path("/api/v1/resource")
@ApplicationScoped
public class ResourceController {
    
    @Inject
    ResourceService service;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Resource> list() {
        return service.findAll();
    }
}
```

### Service Layer Pattern
```java
@ApplicationScoped
public class ResourceService {
    
    @Inject
    ResourceRepository repository;
    
    @Transactional
    public Resource create(Resource resource) {
        // Validation
        // Business logic
        return repository.persist(resource);
    }
}
```

### Template Pattern
```html
{#include base.qute.html}
{#title}Page Title{/title}
{#content}
    <h1>{msg:welcome}</h1>
    {#for item in items}
        <div>{item.name}</div>
    {/for}
{/content}
{/include}
```

---

## Common Pitfalls (Anti-Patterns)

### ❌ Don't
- Hardcode text in templates
- Mix feature + refactor in same PR
- Skip i18n for "temporary" features
- Commit .env files with secrets
- Force push to main
- Modify existing migrations
- Break backward compatibility

### ✅ Do
- Use message bundles for all text
- Separate PRs by type
- I18n from day one
- Use .env.example templates
- Branch-per-change workflow
- Create new migrations
- Deprecate before removing

---

## Decision Log

### ADR Location
`docs/decisions/` (Architecture Decision Records)

### Format
```markdown
# ADR-###: Title

**Date**: YYYY-MM-DD
**Status**: Accepted | Rejected | Superseded

## Context
Problem statement

## Decision
What we decided

## Consequences
Impact of decision
```

---

## Observability

### Metrics Endpoints
- `/q/metrics` - Prometheus metrics
- `/q/health/live` - Liveness probe
- `/q/health/ready` - Readiness probe

### Logging
- Use SLF4J
- Levels: ERROR (production), INFO (staging), DEBUG (dev)
- Structure: `[timestamp] [level] [class] message`
- Never log sensitive data (passwords, tokens, PII)

---

## Continuous Improvement

### When to Update This Document
- New pattern emerges from successful PR
- Failure occurs that should have been prevented
- Technology stack upgrade (major version)
- Team learns better practice

### Update Process
1. Create issue documenting need
2. PR with proposed change
3. Link to evidence (PR, issue, incident)
4. Team review
5. Merge when consensus

---

## References

- **A-Dev Canonical**: `.adev/ADEV.md`
- **Quality Standards**: `.adev/QUALITY.md`
- **Project Docs**: `docs/`
- **CHANGELOG**: `CHANGELOG.md`
- **Contributing**: `CONTRIBUTING.md`
