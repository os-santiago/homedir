# Universal Backup System - Design Document

## Problem Statement

**Current State:**
- Only `cfp-submissions.json` has automatic backups
- Critical files like `events.json`, `speakers.json`, `user-profiles.json` have NO backup
- Lost 30 event talks due to cleanup operation without backup

**Impact:**
- Data loss risk for all platform data
- No recovery mechanism for accidental deletions
- No audit trail of changes

## Solution: Universal Backup System

Extend the existing CFP backup pattern to ALL critical data files.

### Architecture

```
data/
├── events.json
├── speakers.json  
├── user-profiles.json
├── cfp-submissions.json (already has backups)
├── economy-state.json
├── challenge-state.json
├── campaign-state.json
├── community-submissions.json
├── volunteer-submissions.json
└── backups/
    ├── cfp/                    # Existing
    │   └── cfp-submissions-YYYYMMDD-HHMMSS-NNN.json
    ├── events/                 # NEW
    │   └── events-YYYYMMDD-HHMMSS-NNN.json
    ├── speakers/               # NEW
    │   └── speakers-YYYYMMDD-HHMMSS-NNN.json
    ├── profiles/               # NEW
    │   └── user-profiles-YYYYMMDD-HHMMSS-NNN.json
    ├── economy/                # NEW
    │   └── economy-state-YYYYMMDD-HHMMSS-NNN.json
    ├── challenges/             # NEW
    ├── campaigns/              # NEW
    ├── community/              # NEW
    └── volunteers/             # NEW
```

### Implementation Strategy

**Phase 1: Core Infrastructure**
1. Extract backup logic from CFP-specific code to generic utility
2. Create `UniversalBackupService` or extend `PersistenceService`
3. Add configuration per file type

**Phase 2: Integration**
1. Hook into existing `save*()` methods
2. Call backup before each write operation
3. Maintain backward compatibility with CFP backups

**Phase 3: Management**
1. Unified backup listing/browsing
2. Restore endpoints for admins
3. Automatic cleanup (keep last N backups)

### Key Design Decisions

#### 1. Backup Triggers

**Before Every Save:**
```java
public void saveEvents(Map<String, Event> events) {
    maybeBackup("events", eventsFile);  // NEW
    scheduleWrite(eventsFile, events);
}
```

**Debouncing:**
- Min interval: 5 minutes (configurable)
- Prevents backup spam during bulk operations
- Uses same pattern as CFP backups

#### 2. Backup Naming

**Pattern:** `{type}-YYYYMMDD-HHMMSS-NNN.json`

Examples:
- `events-20260726-173000-542.json`
- `speakers-20260726-173015-123.json`  
- `user-profiles-20260726-173030-789.json`

**NNN:** Random 3-digit suffix for collision avoidance

#### 3. Retention Policy

**Default:** Keep last 100 backups per file type
- Configurable via `persistence.backups.max-files`
- Auto-pruning after each backup
- Sorted by timestamp (newest first)

**Storage Estimate:**
- Average file size: ~50 KB
- 100 backups × 9 file types × 50 KB = ~45 MB
- Acceptable for filesystem storage

#### 4. Per-File-Type Toggles

```properties
persistence.backups.include-events=true
persistence.backups.include-speakers=true
persistence.backups.include-profiles=true
```

**Why:** Allow disabling backups for low-value files (e.g., cache files)

### Code Changes

#### File: `PersistenceService.java`

**New Fields:**
```java
// Backup directories
private Path eventsBackupsDir;
private Path speakersBackupsDir;
private Path profilesBackupsDir;
private Path economyBackupsDir;
private Path challengesBackupsDir;
private Path campaignsBackupsDir;
private Path communityBackupsDir;
private Path volunteersBackupsDir;

// Configuration
@ConfigProperty(name = "persistence.backups.enabled", defaultValue = "true")
boolean universalBackupsEnabled;

@ConfigProperty(name = "persistence.backups.max-files", defaultValue = "100")
int universalBackupsMaxFiles;

@ConfigProperty(name = "persistence.backups.min-interval-ms", defaultValue = "300000")
long universalBackupsMinIntervalMs;

// Per-type toggles
@ConfigProperty(name = "persistence.backups.include-events", defaultValue = "true")
boolean backupEvents;

@ConfigProperty(name = "persistence.backups.include-speakers", defaultValue = "true")
boolean backupSpeakers;

// ... (more toggles)

// Last backup timestamps per type
private final ConcurrentHashMap<String, AtomicLong> lastBackupTimes = new ConcurrentHashMap<>();
```

**New Methods:**
```java
/**
 * Generic backup method for any file type.
 * Replaces CFP-specific maybeBackupCfpSubmissions().
 */
private void maybeBackup(String type, Path sourceFile, Path backupsDir, boolean enabled) {
    if (!universalBackupsEnabled || !enabled) {
        return;
    }
    
    long now = System.currentTimeMillis();
    AtomicLong lastBackup = lastBackupTimes.computeIfAbsent(type, k -> new AtomicLong(0));
    long previous = lastBackup.get();
    
    if (previous > 0 && (now - previous) < universalBackupsMinIntervalMs) {
        return; // Too soon
    }
    
    if (!lastBackup.compareAndSet(previous, now)) {
        return; // Another thread won
    }
    
    try {
        Files.createDirectories(backupsDir);
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        String random = String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        String filename = type + "-" + timestamp + "-" + random + ".json";
        Path backup = backupsDir.resolve(filename);
        
        Files.copy(sourceFile, backup, StandardCopyOption.REPLACE_EXISTING);
        
        pruneOldBackups(backupsDir, universalBackupsMaxFiles);
        
        LOG.infof("Backed up %s to %s", type, backup.getFileName());
        
    } catch (Exception e) {
        LOG.warnf(e, "Failed to backup %s", type);
        lastBackup.compareAndSet(now, previous); // Revert on failure
    }
}

/**
 * Generic backup pruning (keep last N files).
 */
private void pruneOldBackups(Path backupsDir, int maxFiles) throws IOException {
    try (var stream = Files.list(backupsDir)) {
        var backups = stream
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().endsWith(".json"))
            .sorted(Comparator.comparing(Path::toFile).reversed()) // Newest first
            .toList();
            
        if (backups.size() <= maxFiles) {
            return;
        }
        
        for (int i = maxFiles; i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
```

**Modified Methods:**
```java
public void saveEvents(Map<String, Event> events) {
    maybeBackup("events", eventsFile, eventsBackupsDir, backupEvents); // NEW
    scheduleWrite(eventsFile, events);
}

public void saveSpeakers(Map<String, Speaker> speakers) {
    maybeBackup("speakers", speakersFile, speakersBackupsDir, backupSpeakers); // NEW
    scheduleWrite(speakersFile, speakers);
}

public void saveUserProfiles(Map<String, UserProfile> profiles) {
    maybeBackup("user-profiles", profilesFile, profilesBackupsDir, backupProfiles); // NEW
    scheduleWrite(profilesFile, profiles);
}

// ... (similar for all save methods)
```

**@PostConstruct Update:**
```java
@PostConstruct
void init() {
    // ... existing code ...
    
    // Initialize backup directories
    Path backupsRoot = dataDir.resolve("backups");
    eventsBackupsDir = backupsRoot.resolve("events");
    speakersBackupsDir = backupsRoot.resolve("speakers");
    profilesBackupsDir = backupsRoot.resolve("profiles");
    economyBackupsDir = backupsRoot.resolve("economy");
    challengesBackupsDir = backupsRoot.resolve("challenges");
    campaignsBackupsDir = backupsRoot.resolve("campaigns");
    communityBackupsDir = backupsRoot.resolve("community");
    volunteersBackupsDir = backupsRoot.resolve("volunteers");
    
    // Create directories
    try {
        Files.createDirectories(eventsBackupsDir);
        Files.createDirectories(speakersBackupsDir);
        Files.createDirectories(profilesBackupsDir);
        // ... (more directories)
        LOG.info("Universal backup system initialized");
    } catch (IOException e) {
        LOG.error("Failed to create backup directories", e);
    }
}
```

### Admin Restore Endpoint

**New Resource:** `AdminBackupRestoreResource.java`

```java
@Path("/private/admin/backups")
public class AdminBackupRestoreResource {
    
    @GET
    @Path("{type}/list")
    public Response listBackups(@PathParam("type") String type) {
        // List available backups for type (events, speakers, etc.)
    }
    
    @POST
    @Path("{type}/restore/{filename}")
    public Response restoreBackup(
        @PathParam("type") String type,
        @PathParam("filename") String filename) {
        // Restore specific backup
        // Create backup of current state first (safety)
        // Replace current file with backup
    }
    
    @GET
    @Path("{type}/download/{filename}")
    public Response downloadBackup(
        @PathParam("type") String type,
        @PathParam("filename") String filename) {
        // Download backup file
    }
}
```

### Testing Strategy

**Unit Tests:**
- Backup creation
- Pruning old backups
- Debouncing logic

**Integration Tests:**
- End-to-end save → backup flow
- Restore functionality
- Multiple file types

**Manual Testing:**
1. Create event → verify backup created
2. Wait 6 minutes → create another event → verify second backup
3. Create 101 events → verify only 100 backups kept
4. Restore from backup → verify data restored

### Migration Plan

**Phase 1: Deploy (No Breaking Changes)**
- Add backup code
- Backups created automatically
- Existing code unchanged

**Phase 2: Monitor (1 week)**
- Verify backups creating correctly
- Check disk usage
- Monitor performance impact

**Phase 3: Enable Restore UI**
- Add admin endpoints
- Add UI for browsing/restoring backups

### Rollback Plan

If issues occur:
1. Set `persistence.backups.enabled=false` in properties
2. Backups stop immediately
3. Existing functionality unaffected

### Performance Impact

**Write Performance:**
- Backup is async (doesn't block save)
- Debounced (max 1 per 5 minutes)
- Minimal impact (<1ms added latency)

**Disk I/O:**
- One extra file copy per save (if not debounced)
- Pruning is quick (simple file list + delete)

**Storage:**
- ~45 MB for 100 backups × 9 file types
- Negligible on modern systems

### Security Considerations

**Access Control:**
- Backups in `data/backups/` (not web-accessible)
- Restore endpoints require admin role
- No backup of sensitive files (secrets, tokens)

**Data Privacy:**
- Backups contain same data as main files
- Apply same access controls
- Consider encryption at rest (future enhancement)

### Future Enhancements

**V2 Features:**
1. Backup compression (gzip)
2. Point-in-time restore UI
3. Backup to S3/object storage
4. Diff viewer (compare backup vs current)
5. Scheduled backups (independent of saves)
6. Backup verification/integrity checks

### Metrics & Observability

**New Metrics:**
- `backup_created_total{type}` - Counter
- `backup_failed_total{type}` - Counter
- `backup_pruned_total{type}` - Counter
- `backup_size_bytes{type}` - Gauge
- `backup_age_seconds{type}` - Gauge (oldest backup)

**Logs:**
- INFO: Backup created successfully
- WARN: Backup failed (with reason)
- INFO: Pruned N old backups

### Summary

**Benefits:**
✅ Prevents data loss for ALL critical files
✅ Recovery from accidental deletions
✅ Audit trail of changes
✅ Minimal performance impact
✅ Easy to disable if needed

**Risks:**
⚠️ Increased disk usage (~45 MB)
⚠️ Slightly more complex code
⚠️ Need to test restore flow

**Recommendation:** IMPLEMENT IMMEDIATELY
- High value (prevents catastrophic data loss)
- Low risk (can be disabled anytime)
- Proven pattern (CFP backups work well)
