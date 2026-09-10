# Issue Templates - AI-SDLC Guidelines

## Overview

These templates are designed to work seamlessly with the **AI-SDLC autonomous workflow**. Properly structured issues enable the AI worker to understand, implement, validate, and deploy changes with minimal human intervention.

## 🎯 Key Principle: Atomic Issues = High Autonomy

The AI-SDLC works best with **atomic issues**: well-defined, single-purpose work units with clear acceptance criteria.

### Required Fields for AI-SDLC Compatibility

All templates now include these **mandatory** fields:

1. **Problem Statement** - Clear description of what needs to be done
2. **Acceptance Criteria** - Maximum 2 testable, measurable criteria
3. **Affected Files** - Best guess at which files need changes
4. **Validation Command** - Executable command to verify the fix
5. **Complexity Estimation** - Simple / Medium / Complex

## 📝 Template Selection Guide

| Template | Use When |
|----------|----------|
| **Bug Report** | Something is broken and needs fixing |
| **Feature Request** | New functionality or enhancement needed |
| **Task** | Well-defined unit of work (implementation, refactoring, etc.) |
| **Autonomous Implementation** | Direct AI-SDLC execution (auto-labeled `ready-to-implement`) |

## ✅ Writing AI-SDLC-Friendly Issues

### Good Example (Simple - High Autonomy)

```markdown
## Problem Statement
Login page doesn't show validation errors when credentials are incorrect

## Acceptance Criteria
- [ ] Error message displays when login fails
- [ ] Error message is accessible (ARIA label present)

## Affected Files
- src/main/resources/templates/login.html
- src/main/java/com/scanales/homedir/web/LoginController.java

## Validation Command
mvn test -Dtest=LoginControllerTest::testInvalidCredentials

## Complexity
Simple

## Notes (Optional)
Additional context or dependencies.
```

**Why this works:**
- Single, specific problem
- 2 testable criteria
- Clear file scope
- Executable validation
- Realistic complexity estimate

### Bad Example (Low Autonomy)

```markdown
## Problem Statement
Improve the login system

## Acceptance Criteria
- [ ] Better UX
- [ ] More secure
- [ ] Faster
- [ ] Mobile-friendly

## Validation
Manual testing
```

**Why this fails:**
- Vague problem statement
- >2 criteria (requires decomposition)
- Subjective criteria ("better", "faster")
- No specific files
- No executable validation
- Unknown complexity

## 🔄 Decomposition Pattern

For complex work, use the **parent-child pattern**:

### Parent Issue
```markdown
Title: [Epic] Redesign Authentication System
Labels: epic, parent-issue

## Goal
Modern, secure authentication with SSO support

## Child Issues
- #1001 Add OAuth2 provider configuration
- #1002 Implement login UI with new design
- #1003 Add password reset flow
- #1004 Migrate existing user sessions
```

### Child Issue Example
```markdown
Title: Add OAuth2 provider configuration
Labels: child-issue, ready-to-implement
Parent: #1000

## Problem Statement
System needs OAuth2 configuration for Google SSO

## Acceptance Criteria
- [ ] OAuth2 config added to application.properties
- [ ] Google client ID/secret loaded from env vars

## Affected Files
- src/main/resources/application.properties
- src/main/java/com/scanales/homedir/config/SecurityConfig.java

## Validation Command
mvn test -Dtest=OAuth2ConfigTest

## Complexity
Simple

## Notes (Optional)
Additional context or dependencies.
```

## 🤖 AI-SDLC Workflow Triggers

1. **Admission**: Add `ready-to-implement` label (must be authorized)
2. **Review**: AI analyzes issue structure and acceptance criteria
3. **Accept/Reject**:
   - ✅ Accepted → `scc-queued` → autonomous implementation
   - ❌ Rejected → `scc-rejected` with reasoning
   - ⚠️ Needs clarification → `needs-human` with questions

4. **Implementation**: AI claims issue, creates PR, runs validation
5. **Merge**: Auto-merge after CI passes and checks succeed

## 📊 Complexity Guidelines

### Simple (Target for AI-SDLC)
- **Scope**: 1 file, <50 lines
- **Examples**: Fix typo, add validation, small config change
- **Time**: ~5-10 minutes
- **Success Rate**: 95%+

### Medium (Good for AI-SDLC)
- **Scope**: 2-3 files, <200 lines
- **Examples**: Add endpoint, refactor service, update UI component
- **Time**: ~10-20 minutes
- **Success Rate**: 85%+

### Complex (Consider Decomposition)
- **Scope**: >3 files or architectural decision
- **Examples**: New feature with DB schema, multi-layer changes
- **Time**: >20 minutes
- **Success Rate**: 60%+
- **Recommendation**: Break into Simple/Medium child issues

## 🎓 Best Practices

### DO ✅
- Write one sentence problem statements
- Keep criteria testable and measurable
- Provide specific file paths
- Include executable validation commands
- Estimate complexity realistically
- Use labels consistently
- Link related issues

### DON'T ❌
- Mix multiple unrelated changes
- Use vague acceptance criteria
- Skip validation commands
- Ignore complexity estimates
- Create mega-issues without decomposition
- Duplicate existing issues

## 🔗 Related Documentation

- [AI-SDLC Flow](../../docs/HOMEDIR-AI-SDLC-FLOW.md) - Complete workflow diagram
- [Autonomous SDLC](../../docs/autonomous-sdlc.md) - Operating model
- [Decision Policy](../../platform/config/autonomous-decision-policy.yaml) - AI decision rules

## 📞 Support

If the AI-SDLC rejects your issue or marks it `needs-human`:
1. Read the rejection reasoning in the issue comment
2. Address the specific concerns raised
3. Update the issue and re-add `ready-to-implement`
4. For persistent issues, remove `ready-to-implement` and implement manually

## 🎯 Success Metrics

Well-structured issues achieve:
- **95%+ success rate** for Simple issues
- **<20 min E2E time** from issue → merged PR
- **Zero manual intervention** for happy path
- **Automatic remediation** for common CI failures

Track your issue quality in the [AI-SDLC Dashboard](https://homedir-ai-sdlc.opensourcesantiago.io/sdlc/dashboard/).
