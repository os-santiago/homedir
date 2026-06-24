# Label Migration Runbook

**Version**: 1.0  
**Target**: Legacy → Canonical Label Taxonomy  
**Owner**: Maintainer Team  
**Last Updated**: 2026-06-23

## 🎯 Migration Objectives

1. **Replace** legacy labels with canonical namespaced labels
2. **Eliminate** EN/ES duplicates
3. **Preserve** historical traceability (issue history retains old labels)
4. **Automate** enforcement post-migration

## 📋 Pre-Migration Checklist

- [ ] **Backup current labels**: Export to JSON
- [ ] **Announce migration**: Post issue + discussion with 1-week notice
- [ ] **Update documentation**: Issue templates, CONTRIBUTING.md, triage guides
- [ ] **Test scripts**: Run migration script on test repository first
- [ ] **Assign DRI**: Designated Responsible Individual for rollback if needed

## 🔄 Migration Phases

### Phase 1: Preparation (Week 1)

#### 1.1 Export Current Labels (Backup)

```bash
# Export all labels to JSON backup
gh label list --limit 1000 --json name,description,color > labels-backup-$(date +%Y%m%d).json
```

#### 1.2 Create Canonical Labels

```bash
# Type labels
gh label create "type:bug" --description "Something isn't working | Algo no funciona" --color "d73a4a"
gh label create "type:feature" --description "New feature or enhancement | Nueva función o mejora" --color "a2eeef"
gh label create "type:docs" --description "Documentation improvements | Mejoras de documentación" --color "0075ca"
gh label create "type:question" --description "Further information requested | Se solicita más información" --color "d876e3"

# State labels
gh label create "state:duplicate" --description "Duplicate | Duplicado" --color "cfd3d7"
gh label create "state:wontfix" --description "Will not be addressed | No se abordará" --color "ffffff"
gh label create "state:invalid" --description "Not valid | No válido" --color "e4e669"

# Collaboration labels
gh label create "collab:good-first-issue" --description "Good for newcomers | Bueno para principiantes" --color "7057ff"
gh label create "collab:help-wanted" --description "Extra help needed | Se necesita ayuda" --color "008672"

# Domain labels
gh label create "domain:hackathon" --description "Hackathon events | Eventos hackathon" --color "5319e7"
gh label create "domain:evento" --description "Community events | Eventos comunitarios" --color "fbca04"
gh label create "domain:codex" --description "AI/KB integration | Integración IA/KB" --color "ededed"

# Automation labels
gh label create "automation:wos-review" --description "Triggers WOS hook | Activa hook WOS" --color "7057ff"
```

#### 1.3 Mark Legacy Labels as Deprecated

```bash
gh label edit "bug" --description "⚠️ DEPRECATED: Use 'type:bug'. Removal: 2026-07-15"
gh label edit "error" --description "⚠️ DEPRECATED: Use 'type:bug'. Removal: 2026-07-15"
# (repeat for all legacy labels)
```

### Phase 2: Bulk Relabeling (Week 2)

#### 2.1 Migration Script

```bash
#!/bin/bash
# scripts/migrate-labels.sh

set -e

declare -A LABEL_MAP=(
  ["bug"]="type:bug"
  ["error"]="type:bug"
  ["enhancement"]="type:feature"
  ["mejora"]="type:feature"
  ["documentation"]="type:docs"
  ["question"]="type:question"
  ["pregunta"]="type:question"
  ["duplicate"]="state:duplicate"
  ["wontfix"]="state:wontfix"
  ["no solucionar"]="state:wontfix"
  ["invalid"]="state:invalid"
  ["no valido"]="state:invalid"
  ["good first issue"]="collab:good-first-issue"
  ["buen primer issue"]="collab:good-first-issue"
  ["help wanted"]="collab:help-wanted"
  ["Se necesita ayuda"]="collab:help-wanted"
  ["hackathon"]="domain:hackathon"
  ["evento"]="domain:evento"
  ["codex"]="domain:codex"
  ["wos-review"]="automation:wos-review"
)

for LEGACY in "${!LABEL_MAP[@]}"; do
  CANONICAL="${LABEL_MAP[$LEGACY]}"
  echo "Migrating '$LEGACY' -> '$CANONICAL'..."
  
  gh issue list --label "$LEGACY" --state all --limit 1000 --json number --jq '.[].number' \
    | xargs -I {} gh issue edit {} --add-label "$CANONICAL" --remove-label "$LEGACY"
done

echo "Migration complete!"
```

#### 2.2 Run Migration

```bash
chmod +x scripts/migrate-labels.sh
./scripts/migrate-labels.sh | tee migration-log-$(date +%Y%m%d).txt
```

#### 2.3 Verification

```bash
# Verify no issues remain with legacy labels
gh issue list --label "bug" --state all --json number --jq 'length'
# (repeat for each legacy label - should return 0)
```

### Phase 3: Cleanup (Week 3)

#### 3.1 Update Issue Templates

Replace legacy label references in `.github/ISSUE_TEMPLATE/*.md`:

```yaml
labels: type:bug  # was: bug
```

#### 3.2 Remove Legacy Labels

```bash
# CAUTION: Permanent deletion (issue history preserved)
gh label delete "bug" --yes
gh label delete "error" --yes
# (repeat for all legacy labels)
```

#### 3.3 Add CI Validation

Create `.github/workflows/validate-labels.yml`:

```yaml
name: Validate Labels
on:
  issues:
    types: [opened, labeled]
  pull_request:
    types: [opened, labeled]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - name: Check required type label
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          NUM="${{ github.event.issue.number || github.event.pull_request.number }}"
          if ! gh issue view "$NUM" --json labels --jq '.labels[].name' | grep -q '^type:'; then
            echo "::warning::Missing 'type:' label - will be added during triage"
          fi
```

## 🔄 Rollback Procedure

If migration causes issues:

```bash
# 1. Restore legacy labels from backup
jq -r '.[] | "gh label create \"\(.name)\" --description \"\(.description)\" --color \(.color)"' \
  labels-backup-YYYYMMDD.json | bash

# 2. Reverse migration (swap LABEL_MAP keys/values in script)
# 3. Announce rollback and revised timeline
```

## 📊 Success Metrics

- [ ] 100% coverage: All open issues have `type:*` label
- [ ] Zero legacy: No issues with deprecated labels
- [ ] CI passing: Validation workflow succeeds
- [ ] No blocking feedback from community

## 📚 References

- **Canonical Taxonomy**: [LABEL_TAXONOMY.md](./LABEL_TAXONOMY.md)
- **Triage Guide**: [LABEL_TRIAGE_GUIDE.md](./LABEL_TRIAGE_GUIDE.md)

---

**DRI**: Maintainer Team  
**Review Date**: 2026-07-30
