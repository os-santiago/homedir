# Test Plan: Issue #864 - GitHub Webhook → WOS Flow

**Issue**: #864 - Prueba en vivo: webhook GitHub -> WOS  
**Objective**: Validate the complete integration flow from GitHub webhook to WOS delegation to Discord notification  
**Date**: 2026-06-24  
**Branch**: fix/issue-864

## Test Scenario

Validate that when a `wos-review` label is added to an issue, the following flow executes correctly:

1. GitHub webhook fires on label addition
2. Webhook payload is received at `https://homedir.opensourcesantiago.io/github-webhook`
3. WOS (Workspace OS) processes the webhook
4. WOS delegates the review task to Claude
5. Discord notification is sent with task status

## Preconditions

- [x] Webhook is configured and active (ID: 644594980)
- [x] Webhook URL: `https://homedir.opensourcesantiago.io/github-webhook`
- [x] Webhook events: `issues`, `pull_request`
- [x] Last webhook response: 200 OK
- [x] Issue #864 exists with `wos-review` label

## Test Steps

### Step 1: Verify Webhook Configuration

**Action**: Check webhook is properly configured  
**Command**: `gh api repos/:owner/:repo/hooks/644594980`

**Expected Result**:
- Webhook active: ✓
- Events include `issues`: ✓
- URL configured: ✓
- Last response 200 OK: ✓

**Status**: ✅ PASSED (verified 2026-06-24)

### Step 2: Trigger Webhook Event

**Action**: Manipulate issue #864 to trigger webhook  
**Method**: Remove and re-add `wos-review` label

**Expected Result**:
- Webhook delivery created
- Response status: 200 OK
- Delivery shows in webhook deliveries list

**Status**: ✅ PASSED

**Actual Trigger**: Label remove + re-add sequence at 2026-06-24 16:10 UTC

### Step 3: Verify Webhook Delivery

**Action**: Check recent webhook deliveries for issue #864  
**Command**: `gh api repos/:owner/:repo/hooks/644594980/deliveries`

**Expected Result**:
- Delivery entry exists for issue #864
- Action matches trigger (labeled, unlabeled)
- Status code: 200
- Duration < 5s

**Status**: ✅ PASSED

**Actual Results**:
- Unlabeled event: delivered_at=2026-06-24T16:10:12.651Z, duration=0.56s, status_code=200
- Labeled event: delivered_at=2026-06-24T16:10:21.609Z, duration=0.64s, status_code=200
- Delivery ID: 3827497518367777000
- GUID: 32891850-6fe7-11f1-8db6-39d1a967a907

### Step 4: Verify WOS Processing

**Action**: Check WOS logs/output for issue #864 processing  
**Location**: Check `.workspace-os/` directory for delegation artifacts

**Expected Result**:
- WOS delegation file created
- Issue metadata extracted correctly
- Delegation prompt includes issue context

**Status**: ⚠️ NOT VERIFIED - No local artifact found

**Actual Results**:
- No delegation artifact found in `.workspace-os/` directory
- Most recent WOS file: `checkpoint-issue-838.md` (from earlier today)
- Files searched: `*864*`, `*wos*`, `*webhook*` patterns
- No files modified within 10 minutes of webhook delivery

**Hypothesis**: WOS may process webhooks asynchronously or in an external system, not writing local filesystem artifacts for test issues

### Step 5: Verify Discord Notification

**Action**: Check Discord channel for notification  
**Expected Channel**: [TBD - specify Discord channel]

**Expected Result**:
- Discord message sent
- Message includes issue link
- Message includes delegation status
- Timestamp matches webhook delivery

**Status**: ⏳ PENDING - Manual verification required

## Validation Criteria

- [x] Webhook fires on issue update ✅
- [x] Webhook delivers successfully (200 OK) ✅
- [ ] WOS receives and processes webhook payload ⚠️ (Cannot verify locally)
- [ ] WOS creates delegation for wos-review labeled issue ⚠️ (Cannot verify locally)
- [ ] Discord notification sent ⏳ (Requires manual Discord check)
- [x] Webhook latency < 5 seconds ✅ (0.64s actual)

## Commands Used

```bash
# Trigger webhook by label manipulation
gh issue edit 864 --remove-label "wos-review"
gh issue edit 864 --add-label "wos-review"

# Check webhook deliveries
gh api repos/:owner/:repo/hooks/644594980/deliveries --jq '.[0:3]'

# Search for WOS artifacts
find .workspace-os -type f -name "*864*"
```

## Conclusion

✅ **GitHub webhook infrastructure validated**  
⚠️ Downstream WOS/Discord steps require external system access or manual verification
