# SCC Autonomous Decision Guidelines

## Purpose
Reduce `needs-human` escalations by providing clear decision-making guidelines based on best practices and operational continuity.

## Core Principle
**When multiple valid approaches exist, choose the one that:**
1. ✅ Follows established patterns in the codebase
2. ✅ Minimizes breaking changes
3. ✅ Improves maintainability
4. ✅ Is reversible if wrong
5. ✅ Has lowest operational risk

## Decision Matrix for Common Scenarios

### 1. Performance Optimization

#### CSS/JS File Consolidation
**Issue Type**: Large CSS/JS files with overlapping styles

**Automatic Decision**:
- ✅ **CHOOSE**: Consolidate into single optimized file
- ✅ **KEEP**: More specific selectors over generic ones
- ✅ **REMOVE**: Duplicate rules (keep last declaration)
- ✅ **PRESERVE**: All currently applied styles (no visual regression)
- ✅ **ADD**: Comments marking removed duplicates for audit trail

**Example**:
```css
/* Before: homedir.css (49KB) + retro-theme.css (54KB) */
/* After: styles.css (optimized, ~70KB) */

/* Consolidated from homedir.css and retro-theme.css */
/* Removed 150 duplicate rules, kept more specific selectors */
.button { /* from retro-theme.css - more specific */ }
```

**Rationale**: Single file reduces HTTP requests, easier to maintain, no breaking changes

---

### 2. Code Refactoring

#### Callback → Async/Await
**Issue Type**: Convert callbacks to modern async/await

**Automatic Decision**:
- ✅ **PATTERN**: Use async/await for all new conversions
- ✅ **ERROR HANDLING**: Wrap in try/catch blocks
- ✅ **BACKWARDS COMPAT**: Keep old function signatures if exported
- ✅ **TESTS**: Update tests to use async patterns

**Example**:
```javascript
// Before
function getData(callback) {
  db.query('SELECT *', (err, rows) => {
    if (err) return callback(err);
    callback(null, rows);
  });
}

// After (AUTOMATICALLY CHOOSE THIS)
async function getData() {
  try {
    const rows = await db.query('SELECT *');
    return rows;
  } catch (error) {
    throw error; // Preserve error propagation
  }
}
```

---

### 3. Naming Conventions

#### Variable/Function Renaming
**Issue Type**: Improve naming clarity

**Automatic Decision**:
- ✅ **FOLLOW**: Existing codebase patterns (camelCase vs snake_case)
- ✅ **PREFER**: Descriptive names over abbreviations
- ✅ **KEEP**: Public API names unchanged (add deprecation if needed)
- ✅ **UPDATE**: All references in same PR

**Decision Tree**:
```
Is it a public API?
├─ YES → Add @deprecated, create new name, update docs
└─ NO  → Rename directly + update all references
```

---

### 4. Dependency Updates

#### Package Version Bumps
**Issue Type**: Update outdated dependencies

**Automatic Decision**:
- ✅ **MINOR/PATCH**: Auto-update if tests pass
- ❌ **MAJOR**: Requires human approval (check CHANGELOG, update code if breaking changes are minor)
- ✅ **SECURITY**: Always update, fix breaking changes
- ✅ **LOCKFILE**: Update package-lock.json/yarn.lock

**Breaking Change Threshold**:
- <10 lines of code change → **AUTO-FIX**
- 10-50 lines → **AUTO-FIX** if patterns are clear
- >50 lines → **needs-human**

---

### 5. Error Handling

#### Add Error Handling
**Issue Type**: Missing error handlers

**Automatic Decision**:
- ✅ **HTTP**: Return appropriate status codes (400, 404, 500)
- ✅ **ASYNC**: Use try/catch
- ✅ **SYNC**: Use if/else error checks
- ✅ **LOGGING**: Always log errors with context
- ✅ **USER-FACING**: Return safe error messages (no stack traces)

**Template**:
```javascript
try {
  const result = await riskyOperation();
  return { success: true, data: result };
} catch (error) {
  logger.error('riskyOperation failed', { error, context });
  return { 
    success: false, 
    error: 'Operation failed. Please try again.' 
  };
}
```

---

### 6. Test Coverage

#### Missing Tests
**Issue Type**: Add tests for uncovered code

**Automatic Decision**:
- ✅ **PATTERN**: Follow existing test patterns in codebase
- ✅ **COVERAGE**: Focus on happy path + 2 most common error cases
- ✅ **MOCK**: Mock external dependencies (DB, APIs, filesystem)
- ✅ **ASSERT**: Test return values, not implementation details

**Minimum Coverage Decision**:
```
New function added?
├─ Public API → Add 3 tests (happy, error, edge case)
├─ Internal util → Add 2 tests (happy, main error)
└─ Private helper → Add 1 test (happy path)
```

---

### 7. Documentation

#### Missing/Outdated Docs
**Issue Type**: Add or update documentation

**Automatic Decision**:
- ✅ **API DOCS**: JSDoc/TSDoc for public functions
- ✅ **README**: Update if public interface changed
- ✅ **EXAMPLES**: Add code example for non-obvious use cases
- ✅ **CHANGELOG**: Add entry for user-facing changes

**Decision Rule**:
- Changed public API → **UPDATE** README + API docs
- Changed internal code → **UPDATE** code comments only
- New feature → **ADD** example in docs/examples/

---

### 8. Security Fixes

#### Input Validation
**Issue Type**: Add input validation for security

**Automatic Decision**:
- ✅ **ALWAYS VALIDATE**: User input, URL parameters, headers
- ✅ **WHITELIST**: Prefer whitelisting over blacklisting
- ✅ **SANITIZE**: HTML before use, parameterized queries for SQL
- ✅ **LIMIT**: Add rate limiting for public endpoints

**Auto-Apply Pattern**:
```javascript
function updateUser(userId, data) {
  // ALWAYS add validation
  if (!userId || !String(userId).match(/^[0-9]+$/)) {
    throw new Error('Invalid user ID');
  }
  
  // ALWAYS sanitize
  const sanitized = {
    name: sanitizeHtml(data.name),
    email: validator.isEmail(data.email) ? data.email : null
  };
  
  return db.update('users', userId, sanitized);
}
```

---

## When to Still Escalate to needs-human

Only escalate when **ANY** of these are true:

1. ❌ **Ambiguity is fundamental**, not solvable with best practices
2. ❌ **Multiple approaches have EQUAL merit** (rare)
3. ❌ **Decision impacts system architecture** significantly
4. ❌ **Requires business/product judgment** (not technical)
5. ❌ **Risk of data loss or security breach** if wrong

### Examples of Valid needs-human

✅ **YES - needs-human**:
- "Should we migrate database from PostgreSQL to MySQL?" (architecture decision)
- "Should we store PII in this table?" (security/legal decision)
- "Should we deprecate this public API?" (product decision)

❌ **NO - Auto-decide**:
- "Should we use camelCase or snake_case?" → Follow codebase pattern
- "Should we consolidate these CSS files?" → YES, always consolidate
- "Should we add error handling?" → YES, always add
- "Should we update this dependency?" → Check changelog, auto-decide

---

## Operational Continuity Guidelines

### Prefer Incremental Over Big-Bang

When refactoring, choose the approach that:
- ✅ Can be deployed independently
- ✅ Can be rolled back easily
- ✅ Doesn't require downtime
- ✅ Doesn't break existing clients

**Example**:
```
Option A: Rewrite entire auth system (needs-human)
Option B: Extract one auth method at a time (auto-proceed)

→ CHOOSE Option B
```

### Preserve Backwards Compatibility

When in doubt:
- ✅ Add new code alongside old code
- ✅ Add deprecation warnings
- ✅ Plan removal for next major version
- ✅ Update migration guide

**Pattern**:
```javascript
// OLD (deprecated but keep)
function oldFunction(arg) {
  console.warn('oldFunction is deprecated, use newFunction');
  return newFunction(arg);
}

// NEW
function newFunction(arg) {
  // improved implementation
}
```

---

## Decision Confidence Levels

Before proceeding, assess confidence:

### HIGH Confidence (Proceed Automatically)
- ✅ Codebase has clear patterns to follow
- ✅ Change is reversible
- ✅ Tests will catch regressions
- ✅ Change aligns with best practices
- ✅ Risk is low

### MEDIUM Confidence (Proceed with Extra Validation)
- ⚠️ Pattern exists but not consistent
- ⚠️ Change is mostly reversible
- ⚠️ Some test coverage exists
- ⚠️ **ACTION**: Add extra tests, add detailed commit message

### LOW Confidence (Consider needs-human)
- ❌ No clear pattern in codebase
- ❌ Change is hard to reverse
- ❌ No test coverage
- ❌ High risk of breaking production

---

## Prompt Enhancement for SCC

Add this to SCC prompts:

```markdown
## AUTONOMOUS DECISION MODE

You are operating in autonomous mode. When faced with ambiguity:

1. **CHECK**: Does the codebase have an existing pattern? → Follow it
2. **CHECK**: Does the decision matrix (see guidelines) cover this? → Apply it
3. **CHECK**: Is this a best practice question? → Apply industry standard
4. **CHECK**: Can I make a safe, reversible choice? → Proceed confidently

**Examples**:

- "Should I consolidate these CSS files?" → YES (reduces HTTP requests, easier maintenance)
- "Should I use async/await or callbacks?" → async/await (modern standard)
- "Should I add error handling?" → YES, always
- "Should I add tests?" → YES, following existing test patterns
- "Should I update this dependency?" → Check changelog, auto-update if safe

**ONLY escalate to needs-human if**:
- Decision requires business/product judgment
- Multiple technically equal approaches with different tradeoffs
- High risk of data loss or security breach
- Fundamental architecture change

**DO NOT escalate for**:
- Coding style (follow codebase)
- Best practices (apply standard)
- Performance optimizations (apply safe optimizations)
- Refactoring (prefer incremental, reversible changes)

When you proceed autonomously, add a commit message explaining your decision and rationale.
```

---

## Monitoring and Feedback Loop

Track decisions made autonomously:

```javascript
// In commit message:
[autonomous-decision] Consolidated CSS files

Decision: Merged homedir.css + retro-theme.css → styles.css
Rationale: Reduces HTTP requests, removes 150 duplicate rules
Pattern: Kept more specific selectors, preserved all visual styles
Reversible: Yes, original files in git history
Risk: Low, no breaking changes to class names
```

This allows humans to:
1. Review autonomous decisions
2. Provide feedback to improve guidelines
3. Track confidence vs. outcomes
4. Refine decision matrix over time

---

## Version
**Version**: 1.0  
**Last Updated**: 2026-07-16  
**Next Review**: 2026-08-16
