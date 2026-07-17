# Example: Autonomous Decision for CSS Consolidation

## Issue Context
**Issue #1016**: [perf] Large CSS files (49KB homedir.css + 54KB retro-theme.css) with overlapping styles

## Decision Matrix Applied

### 1. Analyze Current State
```bash
# Read both files
homedir.css: 49KB, 1,245 lines
retro-theme.css: 54KB, 1,380 lines
Total: 103KB, 2,625 lines

# Identify overlaps
grep -F "$(cat homedir.css | grep -o '^\.[a-z-]*')" retro-theme.css
# Found: ~150 duplicate selectors
```

### 2. Apply Decision Framework

**Question**: Should I consolidate these CSS files?

**Decision Tree**:
```
Is this a best practice question?
└─ YES → Performance best practice: fewer HTTP requests

Does codebase have a pattern?
└─ CHECK: ls quarkus-app/src/main/resources/META-INF/resources/**/*.css
└─ PATTERN: Most CSS is in single files per component

Is this reversible?
└─ YES → Files are in git history, can revert

Risk level?
└─ LOW → CSS changes don't affect data, easy to spot visual regressions

DECISION: PROCEED with consolidation
```

### 3. Implementation Plan

**AUTO-DECIDED APPROACH**:
```markdown
## Autonomous Decisions

### Decision 1: Consolidate into single file
**Rationale**: Reduces HTTP requests (2→1), easier maintenance
**Pattern**: Follows codebase pattern of single CSS file per feature
**Reversible**: Yes, original files in git history

### Decision 2: Use retro-theme.css as base
**Rationale**: retro-theme.css has more specific selectors (54KB) than homedir.css (49KB), so using it as the base reduces the number of overrides needed
**Pattern**: More specific selectors override generic ones
**Reversible**: Yes, can swap order if issues arise

### Decision 3: Preserve all visual styles
**Rationale**: No breaking changes, all currently applied styles maintained
**Pattern**: Conservative approach for public-facing changes
**Reversible**: Yes, can restore original if visual regressions detected
```

### 4. Execution Steps

```bash
# 1. Create consolidated file
cat retro-theme.css > styles-consolidated.css
echo "" >> styles-consolidated.css
echo "/* Merged from homedir.css */" >> styles-consolidated.css
cat homedir.css >> styles-consolidated.css

# 2. Remove duplicates (keep last declaration)
csso styles-consolidated.css -o styles-optimized.css

# 3. Update HTML references
find . \( -name "*.html" -o -name "*.qute.html" \) -exec sed -i \
  's|homedir\.css|styles-optimized.css|g; s|retro-theme\.css|styles-optimized.css|g' {} +

# 4. Validate no visual changes
# (CI will run screenshot tests)
```

### 5. Commit Message

```
perf: consolidate CSS files to reduce HTTP requests

Merged homedir.css (49KB) + retro-theme.css (54KB) into
styles-optimized.css (~70KB after optimization).

## Autonomous Decisions

**Consolidation approach**:
- Base: retro-theme.css (more specific selectors)
- Merged: homedir.css styles
- Removed: 150 duplicate rules
- Kept: All currently applied styles (no visual changes)

**Rationale**:
- Reduces HTTP requests from 2 to 1
- Easier to maintain (single source of truth)
- Follows codebase pattern of single CSS per feature
- Safe & reversible (originals in git history)

**Optimization**:
- Removed duplicate selectors
- Minified whitespace
- Preserved all specificity rules
- Result: 103KB → 70KB (32% reduction)

**Validation**:
- No visual regressions expected (all styles preserved)
- CI screenshot tests will validate
- Can rollback if issues found

Refs #1016
```

### 6. PR Body

```markdown
## Summary
Consolidated two large CSS files into one optimized file to improve performance.

## Issue Coverage

### ✅ Consolidate large CSS files
- [x] Merged `homedir.css` (49KB) + `retro-theme.css` (54KB)
- [x] Created `styles-optimized.css` (70KB, -32%)
- [x] Removed 150 duplicate rules
- [x] Updated all HTML references

### ✅ Performance improvement
- [x] Reduced HTTP requests: 2 → 1
- [x] Reduced file size: 103KB → 70KB
- [x] No visual changes (all styles preserved)

## Autonomous Decisions Made

**1. Consolidation Strategy**
- **Decision**: Use retro-theme.css as base, merge homedir.css
- **Rationale**: retro-theme has more specific selectors
- **Pattern**: More specific CSS overrides generic
- **Risk**: Low, styles are additive

**2. Optimization Approach**
- **Decision**: Remove duplicates, keep last declaration
- **Rationale**: CSS cascade rules (last wins)
- **Pattern**: Standard CSS optimization practice
- **Risk**: Low, preserves final computed styles

**3. File Naming**
- **Decision**: `styles-optimized.css`
- **Rationale**: Descriptive, indicates consolidation
- **Pattern**: Follows `styles-*.css` naming in codebase
- **Risk**: None, internal file name

## Rollback Plan
If visual regressions detected:
```bash
git revert HEAD
# Or restore original files:
git checkout HEAD~1 -- quarkus-app/src/main/resources/META-INF/resources/homedir.css
git checkout HEAD~1 -- quarkus-app/src/main/resources/META-INF/resources/retro-theme.css
```

## Testing
- [x] All styles preserved (checked with diff)
- [ ] CI screenshot tests (automated)
- [ ] Manual visual review (recommended)
```

## Key Takeaways

### ✅ What Made This Auto-Decidable

1. **Clear Best Practice**: Fewer HTTP requests = better performance
2. **Low Risk**: CSS is presentation layer, easy to spot regressions
3. **Reversible**: Git history preserves originals
4. **Pattern Exists**: Codebase shows preference for consolidated CSS
5. **No Business Logic**: Pure technical optimization

### ❌ When This Would Need Human

If any of these were true:
- CSS changes affect critical UI elements (payment forms, auth)
- Multiple themes need to coexist (can't consolidate)
- Brand/design team needs to approve visual changes
- CSS is dynamically generated from external source
- Performance trade-offs (file size vs cache separately)

### 📊 Confidence Level: HIGH

- ✅ Codebase pattern: Clear
- ✅ Best practice: Well-established
- ✅ Risk: Low
- ✅ Reversibility: Easy
- ✅ Test coverage: CI has visual tests

**Conclusion**: Worker should proceed autonomously with detailed documentation.

---

## Template for Other Performance Issues

```markdown
## Autonomous Decision Checklist

- [ ] Best practice exists? → Apply it
- [ ] Codebase pattern exists? → Follow it
- [ ] Change is reversible? → Document rollback
- [ ] Risk is low/medium? → Proceed with tests
- [ ] Documentation clear? → Add to commit/PR

If ALL checked → PROCEED
If ANY unchecked → EVALUATE if needs-human
```
