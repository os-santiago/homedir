---
name: Autonomous Implementation
about: Issue for AI-driven SDLC autonomous implementation
labels: ready-to-implement
---

## Problem Statement
**[Required]** One sentence: what is broken or missing?

## Expected Behavior
**[Required]** What should happen instead?

## Affected Files (Best Guess)
**[Required]** Which files will likely need changes?
- path/to/likely/file1.java
- path/to/likely/template.html
- path/to/test/TestFile.java

## Acceptance Criteria
**[Required - Maximum 2 for autonomous implementation]**
- [ ] Criterion 1 (testable, specific, measurable)
- [ ] Criterion 2 (optional - only if truly necessary)

## Validation Command
**[Required]** How can the implementation be validated?
```bash
mvn test -Dtest=TestClassName
# or
curl http://localhost:8080/api/endpoint | jq
```

## Complexity Estimation
**[Required]** Select one:
- [ ] **Simple**: 1 file, <50 lines, obvious implementation
- [ ] **Medium**: 2-3 files, <200 lines, clear scope
- [ ] **Complex**: >3 files or architectural decision needed

## Context (Optional)
Additional background, constraints, or related issues.

---

## AI-SDLC Best Practices

### ✅ Good Issue Structure
- Clear, atomic problem statement (one thing only)
- Maximum 2 testable acceptance criteria
- Specific validation command provided
- File paths help scope the work
- Complexity realistically estimated

### ❌ Avoid
- Multiple unrelated changes in one issue
- Vague criteria ("improve performance", "make it better")
- No validation method specified
- Missing affected files
- Complex issues without decomposition plan

### 📋 Decomposition Guidelines
If your issue is **Complex**:
1. Create parent issue with overall goal
2. Break into 2-4 child issues (Simple/Medium each)
3. Each child is independently testable
4. Use `parent-issue` and `child-issue` labels

### 🎯 Atomicity = Autonomy
Per ADEV discipline: atomic issues (1-2 criteria) enable:
- Faster implementation cycles
- Safer deployments
- Higher success rates
- Easier rollback if needed
