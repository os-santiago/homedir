# Input Validation Baseline

## Executive Summary

This document establishes a comprehensive baseline for input validation across all homedir write endpoints, prioritizing high-risk operations based on the endpoint authorization matrix. It defines validation rules for 6 field types (text, number, enum, email, URL, date), inventories 15+ critical write endpoints requiring validation, and provides implementation guidelines for both backend (Quarkus/Jakarta) and frontend (TypeScript) validation layers.

## Validation Strategy

### Defense in Depth

Input validation follows a layered approach:

1. **Client-Side Validation** (TypeScript/React): User experience, early feedback
2. **API-Level Validation** (Jakarta Bean Validation): Primary security control, canonical validation
3. **Database Constraints**: Final safety net (NOT NULL, CHECK, UNIQUE)

**Critical Principle**: Backend validation is MANDATORY. Client-side validation is OPTIONAL for UX.

### Validation vs. Sanitization

- **Validation**: Reject invalid input (throw 400 Bad Request)
- **Sanitization**: Transform input to safe format (escape HTML, normalize Unicode)

**Guideline**: Prefer validation over sanitization. Only sanitize when business requirements demand accepting potentially unsafe input (e.g., rich text in community content).

---

## Field Type Validation Rules

### 1. Text Fields

#### Short Text (< 255 chars)
**Use Cases**: Usernames, titles, names, labels

**Validation Rules**:
```java
@NotBlank(message = "Field cannot be empty")
@Size(min = 1, max = 255, message = "Must be 1-255 characters")
@Pattern(regexp = "^[\\p{L}\\p{N}\\p{Zs}._-]+$", message = "Only letters, numbers, spaces, dots, underscores, hyphens")
```

**Rationale**:
- `@NotBlank`: Prevents empty strings, null, whitespace-only input
- `@Size`: Enforces database column limits (VARCHAR(255))
- `@Pattern`: Blocks control characters, prevents injection (XSS, SQLi)

**Frontend Validation** (TypeScript):
```typescript
const validateShortText = (value: string): string | null => {
  if (!value || value.trim().length === 0) return "Field cannot be empty";
  if (value.length > 255) return "Maximum 255 characters";
  if (!/^[\p{L}\p{N}\p{Zs}._-]+$/u.test(value)) return "Invalid characters";
  return null;
};
```

**Exceptions**:
- **Rich Text Fields**: Use HTML sanitizer (OWASP Java HTML Sanitizer) instead of regex
- **Markdown Fields**: Validate against CommonMark spec, sanitize HTML output

---

#### Long Text (> 255 chars)
**Use Cases**: Descriptions, comments, bio, submission abstracts

**Validation Rules**:
```java
@NotBlank(message = "Field cannot be empty")
@Size(min = 1, max = 10000, message = "Must be 1-10,000 characters")
```

**Rationale**:
- No regex pattern (allow full Unicode, multi-line, punctuation)
- 10k char limit prevents database bloat and DoS (oversized payloads)
- HTML sanitization required for rendering (use `org.owasp.html.PolicyFactory`)

**Sanitization Example**:
```java
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

PolicyFactory policy = Sanitizers.FORMATTING
    .and(Sanitizers.LINKS)
    .and(Sanitizers.BLOCKS);
String sanitized = policy.sanitize(userInput);
```

---

### 2. Number Fields

#### Integer Fields
**Use Cases**: IDs, counts, ratings, years

**Validation Rules**:
```java
@NotNull(message = "Field is required")
@Min(value = 0, message = "Must be non-negative")
@Max(value = 2147483647, message = "Exceeds maximum value")
```

**Rationale**:
- `@Min(0)`: Prevents negative values (unless business logic requires, e.g., deltas)
- `@Max(Integer.MAX_VALUE)`: Prevents overflow (Java int is 32-bit signed)

**Special Cases**:
- **IDs**: Use `@Positive` instead of `@Min(0)` (IDs start at 1, not 0)
- **Ratings**: Use `@Min(1)` and `@Max(5)` for 1-5 star ratings
- **Years**: Use `@Min(1900)` and `@Max(2100)` for realistic date ranges

---

#### Decimal Fields
**Use Cases**: Prices, percentages, coordinates

**Validation Rules**:
```java
@NotNull(message = "Field is required")
@DecimalMin(value = "0.00", message = "Must be non-negative")
@DecimalMax(value = "999999.99", message = "Exceeds maximum value")
@Digits(integer = 6, fraction = 2, message = "Maximum 6 digits, 2 decimal places")
```

**Rationale**:
- `@Digits`: Prevents precision loss (e.g., 0.1 + 0.2 != 0.3 in floating point)
- Use `BigDecimal` in Java (never `double` for money/financial values)

---

### 3. Enum Fields

#### Fixed Enumeration
**Use Cases**: Status, type, category, role

**Validation Rules**:
```java
@NotNull(message = "Field is required")
public enum SubmissionStatus {
  DRAFT, SUBMITTED, UNDER_REVIEW, ACCEPTED, REJECTED, WITHDRAWN
}

@Valid
private SubmissionStatus status;
```

**Rationale**:
- Java enum provides compile-time safety (invalid values rejected by Jackson/Quarkus)
- Frontend should use same enum values (TypeScript union type)

**Frontend Validation** (TypeScript):
```typescript
type SubmissionStatus = "DRAFT" | "SUBMITTED" | "UNDER_REVIEW" | "ACCEPTED" | "REJECTED" | "WITHDRAWN";

const validateStatus = (value: string): value is SubmissionStatus => {
  const validStatuses: SubmissionStatus[] = ["DRAFT", "SUBMITTED", "UNDER_REVIEW", "ACCEPTED", "REJECTED", "WITHDRAWN"];
  return validStatuses.includes(value as SubmissionStatus);
};
```

**Special Cases**:
- **User-Provided Enums**: If users can create custom categories, validate against database whitelist (not hardcoded enum)
- **Case Sensitivity**: Always uppercase enums for consistency

---

### 4. Email Fields

**Use Cases**: User email, contact email, notification recipients

**Validation Rules**:
```java
@NotBlank(message = "Email is required")
@Email(message = "Invalid email format", regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
@Size(max = 320, message = "Email exceeds maximum length")
```

**Rationale**:
- `@Email`: Jakarta Bean Validation built-in (RFC 5322 compliant)
- Custom regex: Stricter than default (blocks edge cases like `user@localhost`)
- 320 chars: RFC 5321 maximum (64 local + 1 @ + 255 domain)

**Additional Checks**:
- **Domain Validation**: Check MX record exists (optional, for critical flows like registration)
- **Disposable Email Detection**: Block temporary email providers (optional, use `validator.js` library)

**Frontend Validation** (TypeScript):
```typescript
const validateEmail = (value: string): string | null => {
  if (!value) return "Email is required";
  if (value.length > 320) return "Email too long";
  const emailRegex = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  if (!emailRegex.test(value)) return "Invalid email format";
  return null;
};
```

---

### 5. URL Fields

**Use Cases**: Website, social media links, avatar URLs, external resources

**Validation Rules**:
```java
@NotBlank(message = "URL is required")
@URL(message = "Invalid URL format", protocol = "https", host = "*")
@Size(max = 2048, message = "URL exceeds maximum length")
```

**Rationale**:
- `@URL`: Jakarta Bean Validation built-in (validates scheme, host, port)
- `protocol = "https"`: Enforce HTTPS only (security best practice)
- 2048 chars: Browser URL bar limit (IE/Edge)

**Special Cases**:
- **Internal URLs**: If accepting relative URLs (e.g., `/profile`), use separate validation (not `@URL`)
- **Whitelisted Domains**: For security-critical URLs (e.g., OAuth callback), validate against whitelist

**Frontend Validation** (TypeScript):
```typescript
const validateURL = (value: string): string | null => {
  if (!value) return "URL is required";
  if (value.length > 2048) return "URL too long";
  try {
    const url = new URL(value);
    if (url.protocol !== "https:") return "Only HTTPS URLs allowed";
    return null;
  } catch {
    return "Invalid URL format";
  }
};
```

---

### 6. Date/Time Fields

#### Date Fields
**Use Cases**: Event dates, deadlines, birthdates

**Validation Rules**:
```java
@NotNull(message = "Date is required")
@Future(message = "Date must be in the future")
@JsonFormat(pattern = "yyyy-MM-dd")
private LocalDate eventDate;
```

**Rationale**:
- `@Future`: Ensures date is after current date (for events, deadlines)
- `@Past`: For birthdates, historical records
- `LocalDate`: Prefer over `Date` (avoids timezone issues)

**Additional Checks**:
- **Date Range**: Use `@FutureOrPresent` + custom validator for "within 2 years" constraint
- **Business Logic**: Validate CFP deadline < event date (cross-field validation)

---

#### Timestamp Fields
**Use Cases**: Created/updated timestamps, scheduled actions

**Validation Rules**:
```java
@NotNull(message = "Timestamp is required")
@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
private Instant createdAt;
```

**Rationale**:
- Use `Instant` (UTC) for all server-side timestamps (never `LocalDateTime`)
- Frontend converts to user's timezone for display

**Validation Considerations**:
- **Clock Skew**: Accept timestamps within +/- 5 minutes of server time (prevents client clock drift issues)
- **Replay Attacks**: Reject timestamps older than 15 minutes for sensitive operations

---

## High-Risk Write Endpoints Inventory

Based on the endpoint authorization matrix (255 endpoints analyzed), the following 15+ write endpoints are prioritized for validation due to CRITICAL_ADMIN_UNPROTECTED or ESCRITURA_USUARIO risk levels.

### CRITICAL: Unprotected Internal Endpoints

#### 1. `/api/internal/insights/events` (POST)
**Risk**: CRITICAL_ADMIN_UNPROTECTED  
**Security**: NONE (no authentication)  
**Source**: `InternalInsightsIngestResource.java::appendEvent`

**Required Validation**:
- **Event Type** (enum): Must be one of [PAGE_VIEW, CLICK, FORM_SUBMIT, ERROR]
- **Timestamp** (Instant): Must be within -5 to +5 minutes of server time
- **User ID** (Long): Must be positive integer or null (for anonymous events)
- **Payload** (JSON): Max 10KB, validate against JSON schema

**Validation Status**: MISSING - endpoint has no authentication or input validation  
**Recommendation**: Add IP whitelist (internal services only) + full input validation

---

#### 2. `/api/internal/insights/initiatives/start` (POST)
**Risk**: CRITICAL_ADMIN_UNPROTECTED  
**Security**: NONE  
**Source**: `InternalInsightsIngestResource.java::startInitiative`

**Required Validation**:
- **Initiative Name** (text): 1-255 chars, alphanumeric + spaces
- **Owner ID** (Long): Must reference valid user
- **Start Date** (LocalDate): Must be today or future
- **Metadata** (JSON): Max 5KB

**Validation Status**: MISSING  
**Recommendation**: Move to `/api/private/admin/insights/initiatives/start` (require authentication)

---

#### 3. `/private/github/callback` (GET)
**Risk**: CRITICAL_ADMIN_UNPROTECTED  
**Security**: NONE  
**Source**: `GithubLinkResource.java::callback`

**Required Validation**:
- **Code** (query param): Alphanumeric, 20-40 chars (GitHub OAuth code format)
- **State** (query param): Must match CSRF token in session (prevent CSRF)

**Validation Status**: PARTIAL (likely validates state, but no authentication)  
**Recommendation**: Add `@Authenticated` annotation + validate state parameter

---

### HIGH: User Write Operations

#### 4. `/api/community/lightning/threads` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `CommunityLightningApiResource.java::createThread`

**Required Validation**:
- **Title** (text): 1-255 chars, no HTML
- **Content** (long text): 1-10,000 chars, sanitize HTML
- **Tags** (array): Max 5 tags, each 1-50 chars

**Validation Status**: UNKNOWN (requires code review)  
**Recommendation**: Implement validation + rate limiting (max 10 threads/hour per user)

---

#### 5. `/api/community/lightning/threads/{id}/comments` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `CommunityLightningApiResource.java::addComment`

**Required Validation**:
- **Thread ID** (path param): Must reference existing thread
- **Content** (long text): 1-5,000 chars, sanitize HTML
- **Parent Comment ID** (optional): Must reference existing comment in same thread

**Validation Status**: UNKNOWN  
**Recommendation**: Validate thread ownership (can't comment on locked threads)

---

#### 6. `/api/events/{eventId}/cfp/submissions` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `CfpSubmissionApiResource.java::create`

**Required Validation**:
- **Event ID** (path param): Must reference open CFP event
- **Title** (text): 1-255 chars
- **Abstract** (long text): 100-1,000 chars (enforce minimum for quality)
- **Speaker Bio** (long text): 50-500 chars
- **Session Type** (enum): [TALK, WORKSHOP, PANEL, LIGHTNING]
- **Duration** (integer): [15, 30, 45, 60] minutes only

**Validation Status**: PARTIAL (likely has basic validation)  
**Recommendation**: Add cross-field validation (workshop must be 60+ minutes)

---

#### 7. `/api/events/{eventId}/cfp/submissions/{id}/status` (PUT)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `CfpSubmissionApiResource.java::updateStatus`

**Required Validation**:
- **Submission ID** (path param): Must reference user's own submission (authorization check)
- **Status** (enum): Valid transitions only (e.g., DRAFT -> SUBMITTED, not SUBMITTED -> DRAFT)
- **Reason** (text, optional): 1-500 chars (required for WITHDRAWN status)

**Validation Status**: UNKNOWN  
**Recommendation**: Implement state machine validator (prevent invalid transitions)

---

#### 8. `/api/economy/purchase` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `EconomyApiResource.java::purchase`

**Required Validation**:
- **Item ID** (Long): Must reference available catalog item
- **Quantity** (integer): 1-100 (prevent bulk purchase abuse)
- **Payment Method** (enum): [CREDITS, POINTS, CASH]
- **Idempotency Key** (UUID): Required (prevent duplicate purchases)

**Validation Status**: CRITICAL - financial transaction  
**Recommendation**: Add double-submit protection + audit logging

---

#### 9. `/api/community/submissions` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `CommunitySubmissionApiResource.java::create`

**Required Validation**:
- **Type** (enum): [BLOG_POST, VIDEO, PODCAST, TALK, PROJECT]
- **Title** (text): 1-255 chars
- **URL** (URL): HTTPS only, max 2048 chars
- **Description** (long text): 50-1,000 chars
- **Tags** (array): 1-10 tags, each 1-50 chars

**Validation Status**: UNKNOWN  
**Recommendation**: Validate URL is reachable (optional, async check)

---

#### 10. `/admin/api/notifications/broadcast` (POST)
**Risk**: ESCRITURA_ADMIN  
**Security**: @RolesAllowed("admin")  
**Source**: `AdminNotificationResource.java::broadcast`

**Required Validation**:
- **Message** (text): 1-500 chars
- **Audience** (enum): [ALL_USERS, ACTIVE_USERS, EVENT_ATTENDEES, SUBSCRIBERS]
- **Priority** (enum): [LOW, NORMAL, HIGH, CRITICAL]
- **Channels** (array): [EMAIL, PUSH, SMS] - at least one required

**Validation Status**: CRITICAL - mass notification  
**Recommendation**: Add confirmation step (require `confirmBroadcast: true` flag)

---

#### 11. `/private/admin/events/new` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `AdminEventResource.java::saveEvent`

**Required Validation**:
- **Event Name** (text): 1-255 chars
- **Start Date** (LocalDate): Must be future
- **End Date** (LocalDate): Must be >= start date
- **Location** (text): 1-500 chars
- **Capacity** (integer): 1-100,000 (realistic venue size)
- **Event Type** (enum): [CONFERENCE, MEETUP, WORKSHOP, WEBINAR]

**Validation Status**: UNKNOWN  
**Recommendation**: Add cross-field validation (end date >= start date)

---

#### 12. `/private/admin/campaigns/publish-now` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `AdminCampaignsResource.java::publishNow`

**Required Validation**:
- **Draft ID** (Long): Must reference approved draft (status = APPROVED)
- **Channels** (array): At least one channel required
- **Override Schedule** (boolean): Default false (safety)

**Validation Status**: CRITICAL - publishing action  
**Recommendation**: Add authorization check (only campaign owner or admin)

---

#### 13. `/private/profile/speaker` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `ProfileResource.java::updateSpeakerProfile`

**Required Validation**:
- **Display Name** (text): 1-100 chars
- **Bio** (long text): 50-1,000 chars
- **Avatar URL** (URL): HTTPS only, max 2048 chars, whitelisted domains (CDN only)
- **Social Links** (array): Max 5 links, each valid URL
- **Speaking Topics** (array): 1-10 topics, each 1-100 chars

**Validation Status**: UNKNOWN  
**Recommendation**: Validate avatar URL domain (prevent phishing via malicious avatars)

---

#### 14. `/api/events/{eventId}/volunteers/submissions` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `VolunteerSubmissionApiResource.java::create`

**Required Validation**:
- **Event ID** (path param): Must reference open volunteer program
- **Role Preferences** (array): 1-5 roles, each from enum [REGISTRATION, AV_SUPPORT, MODERATION, LOGISTICS]
- **Availability** (array): Array of time slots, each with start/end time
- **Experience** (long text): 50-1,000 chars
- **Emergency Contact** (text): 1-255 chars

**Validation Status**: UNKNOWN  
**Recommendation**: Validate availability slots don't overlap

---

#### 15. `/private/admin/backup/upload` (POST)
**Risk**: ESCRITURA_USUARIO  
**Security**: @Authenticated  
**Source**: `AdminBackupResource.java::upload`

**Required Validation**:
- **File** (multipart): Max 100MB, allowed extensions [.zip, .tar.gz, .sql]
- **File Type** (enum): [FULL_BACKUP, INCREMENTAL, CONFIG_ONLY]
- **Overwrite Existing** (boolean): Default false (prevent accidental overwrite)

**Validation Status**: CRITICAL - file upload vulnerability  
**Recommendation**: Scan uploaded file for malware, validate archive integrity

---

### Additional High-Risk Endpoints (Brief)

- `/api/community/lightning/comments/{id}/report` (POST) - Validate report reason (enum)
- `/api/community/lightning/threads/{id}/report` (POST) - Same as above
- `/api/events/{eventId}/cfp/submissions/{id}/rating` (PUT) - Validate rating 1-5
- `/private/admin/campaigns/bulk-action` (POST) - Validate action type (enum), limit batch size
- `/private/profile/link-discord` (POST) - Validate Discord OAuth code
- `/auth/session/refresh` (POST) - Validate refresh token format (JWT)

---

## Implementation Guidelines

### Backend Validation (Quarkus/Jakarta)

#### 1. Enable Bean Validation
```xml
<!-- pom.xml -->
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-hibernate-validator</artifactId>
</dependency>
```

#### 2. Annotate DTOs
```java
public class CreateThreadRequest {
  @NotBlank(message = "Title is required")
  @Size(min = 1, max = 255, message = "Title must be 1-255 characters")
  private String title;

  @NotBlank(message = "Content is required")
  @Size(min = 1, max = 10000, message = "Content must be 1-10,000 characters")
  private String content;

  @Size(max = 5, message = "Maximum 5 tags")
  private List<@NotBlank @Size(max = 50) String> tags;

  // Getters/setters
}
```

#### 3. Validate in Endpoint
```java
@POST
@Path("/threads")
@Authenticated
public Response createThread(@Valid CreateThreadRequest request) {
  // Validation happens automatically via @Valid
  // If validation fails, Quarkus returns 400 Bad Request with error details
  String sanitized = htmlSanitizer.sanitize(request.getContent());
  // ... business logic
}
```

#### 4. Custom Validators
For complex validation (cross-field, state machine), use custom validators:

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventDateValidator.class)
public @interface ValidEventDates {
  String message() default "End date must be after start date";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}

public class EventDateValidator implements ConstraintValidator<ValidEventDates, CreateEventRequest> {
  @Override
  public boolean isValid(CreateEventRequest request, ConstraintValidatorContext context) {
    if (request.getStartDate() == null || request.getEndDate() == null) {
      return true; // Let @NotNull handle null checks
    }
    return request.getEndDate().isAfter(request.getStartDate()) ||
           request.getEndDate().isEqual(request.getStartDate());
  }
}

@ValidEventDates
public class CreateEventRequest {
  @NotNull private LocalDate startDate;
  @NotNull private LocalDate endDate;
}
```

---

### Frontend Validation (TypeScript/React)

#### 1. Validation Utility Library
```typescript
// utils/validation.ts
export const validators = {
  shortText: (value: string, fieldName: string): string | null => {
    if (!value || value.trim().length === 0) return `${fieldName} is required`;
    if (value.length > 255) return `${fieldName} must be 255 characters or less`;
    if (!/^[\p{L}\p{N}\p{Zs}._-]+$/u.test(value)) 
      return `${fieldName} contains invalid characters`;
    return null;
  },

  longText: (value: string, min: number, max: number, fieldName: string): string | null => {
    if (!value || value.trim().length === 0) return `${fieldName} is required`;
    if (value.length < min) return `${fieldName} must be at least ${min} characters`;
    if (value.length > max) return `${fieldName} must be ${max} characters or less`;
    return null;
  },

  email: (value: string): string | null => {
    if (!value) return "Email is required";
    const emailRegex = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
    if (!emailRegex.test(value)) return "Invalid email format";
    return null;
  },

  url: (value: string): string | null => {
    if (!value) return "URL is required";
    try {
      const url = new URL(value);
      if (url.protocol !== "https:") return "Only HTTPS URLs allowed";
      return null;
    } catch {
      return "Invalid URL format";
    }
  },
};
```

#### 2. Form Validation Hook
```typescript
// hooks/useFormValidation.ts
import { useState } from 'react';

export const useFormValidation = <T extends Record<string, any>>(
  initialValues: T,
  validators: Record<keyof T, (value: any) => string | null>
) => {
  const [values, setValues] = useState<T>(initialValues);
  const [errors, setErrors] = useState<Partial<Record<keyof T, string>>>({});

  const validate = (field?: keyof T): boolean => {
    const fieldsToValidate = field ? [field] : Object.keys(validators);
    const newErrors: Partial<Record<keyof T, string>> = {};
    
    fieldsToValidate.forEach((f) => {
      const error = validators[f as keyof T](values[f as keyof T]);
      if (error) newErrors[f as keyof T] = error;
    });

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (field: keyof T, value: any) => {
    setValues({ ...values, [field]: value });
    // Optional: validate on change
    const error = validators[field](value);
    setErrors({ ...errors, [field]: error || undefined });
  };

  return { values, errors, validate, handleChange };
};
```

#### 3. Usage Example
```typescript
// components/CreateThreadForm.tsx
import { useFormValidation } from '@/hooks/useFormValidation';
import { validators } from '@/utils/validation';

const CreateThreadForm = () => {
  const { values, errors, validate, handleChange } = useFormValidation(
    { title: '', content: '', tags: [] },
    {
      title: (v) => validators.shortText(v, 'Title'),
      content: (v) => validators.longText(v, 1, 10000, 'Content'),
      tags: (v) => v.length > 5 ? 'Maximum 5 tags' : null,
    }
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    try {
      await api.post('/api/community/lightning/threads', values);
      // Success
    } catch (error) {
      // Handle error
    }
  };

  return (
    <form onSubmit={handleSubmit}>
      <input
        value={values.title}
        onChange={(e) => handleChange('title', e.target.value)}
      />
      {errors.title && <span className="error">{errors.title}</span>}
      {/* ... other fields */}
    </form>
  );
};
```

---

## Validation Checklist

Use this checklist when implementing validation for new endpoints:

### Pre-Implementation
- [ ] Endpoint is documented in authorization matrix with risk level
- [ ] Required field types identified (text, number, enum, etc.)
- [ ] Business logic constraints documented (e.g., "CFP deadline < event date")
- [ ] Cross-field validation requirements identified

### Backend Implementation
- [ ] DTO created with Jakarta Bean Validation annotations
- [ ] `@Valid` annotation added to endpoint method signature
- [ ] Custom validators implemented for complex rules
- [ ] HTML sanitization applied to long text fields
- [ ] Enum values match TypeScript frontend types
- [ ] Unit tests cover validation edge cases (empty, null, oversized, special chars)

### Frontend Implementation
- [ ] Validation utility functions created (reusable across forms)
- [ ] Form validation hook implemented
- [ ] Error messages user-friendly (not technical jargon)
- [ ] Validation triggers on blur (not on every keystroke, for UX)
- [ ] Submit button disabled until all fields valid

### Security Review
- [ ] No SQL injection vectors (parameterized queries used)
- [ ] No XSS vectors (HTML sanitized or escaped)
- [ ] No command injection vectors (no `Runtime.exec()` with user input)
- [ ] No path traversal vectors (file paths validated against whitelist)
- [ ] No CSRF vectors (state/nonce validated for OAuth callbacks)

### Testing
- [ ] Unit tests: Valid inputs accepted
- [ ] Unit tests: Invalid inputs rejected with correct error messages
- [ ] Integration tests: 400 Bad Request returned for invalid payloads
- [ ] Manual testing: Edge cases (Unicode, emojis, control characters, max length)
- [ ] Security testing: Attempt injection attacks (use OWASP ZAP, see #838)

---

## Prioritization by Risk

Based on the authorization matrix, prioritize validation implementation in this order:

### Phase 1: CRITICAL_ADMIN_UNPROTECTED (Weeks 1-2)
**Endpoints**: 5 total  
- `/api/internal/insights/events` (POST)
- `/api/internal/insights/initiatives/start` (POST)
- `/private/github/callback` (GET)
- `/private/github/connect` (GET)
- `/private/github/start` (GET)

**Action**: Add authentication + full input validation OR restrict to internal network

---

### Phase 2: High-Risk User Write (Weeks 3-6)
**Endpoints**: 15+ critical user write operations  
- Community content creation (threads, comments, submissions)
- CFP submissions (create, update, status changes)
- Economy transactions (purchases)
- Profile updates (speaker profile, social links)
- Volunteer submissions

**Action**: Implement validation for all fields + HTML sanitization + rate limiting

---

### Phase 3: Admin Write Operations (Weeks 7-9)
**Endpoints**: 87 total user write, 5 admin write  
- Event creation/editing
- Campaign publishing
- Notification broadcasting
- Backup uploads

**Action**: Add authorization checks + confirmation steps for destructive actions

---

### Phase 4: Remaining Write Endpoints (Weeks 10-12)
**Endpoints**: All remaining POST/PUT/PATCH/DELETE  
- Rating updates
- Configuration changes
- Metadata updates

**Action**: Full validation coverage + automated testing

---

## Success Metrics

### Coverage
- **Target**: 100% of write endpoints (POST/PUT/PATCH/DELETE) have input validation
- **Current**: TBD (requires code audit, see Phase 1 checklist)
- **Measurement**: Grep for `@POST|@PUT|@PATCH|@DELETE` annotations, cross-reference with validation presence

### Quality
- **Target**: 0 injection vulnerabilities detected by DAST (see #838 DAST Integration Spec)
- **Target**: < 1% of validation errors are false positives (blocking valid input)
- **Measurement**: Monitor 400 Bad Request rate in production logs

### Performance
- **Target**: Validation adds < 10ms latency per request (measured at p99)
- **Measurement**: APM tracing (Quarkus Micrometer metrics)

---

## Open Questions / Future Work

1. **File Upload Validation**: Should we implement virus scanning for file uploads (e.g., `/private/admin/backup/upload`)?
   - **Recommendation**: Use ClamAV integration (Quarkus extension available)

2. **Rate Limiting**: How does validation interact with rate limiting (see #859)?
   - **Recommendation**: Validate input BEFORE rate limiting check (prevent malformed requests from consuming rate limit quota)

3. **Internationalization**: Should validation error messages support i18n?
   - **Recommendation**: Yes, use Quarkus i18n bundle (`messages.properties`)

4. **GraphQL Validation**: Does homedir use GraphQL? (requires different validation approach)
   - **Action**: Confirm with product team; GraphQL has built-in validation (schema-based)

---

## References

- [Jakarta Bean Validation Specification](https://jakarta.ee/specifications/bean-validation/3.0/)
- [OWASP Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html)
- [OWASP Java HTML Sanitizer](https://github.com/OWASP/java-html-sanitizer)
- [Quarkus Validation Guide](https://quarkus.io/guides/validation)
- Issue #838: Parent AppSec Tracking Issue
- Issue #854: Endpoint Authorization Matrix (source: `docs/security/endpoint-authorization-matrix.yaml`)
- Issue #859: Rate Limiting Audit

---

**Document Version**: 1.0  
**Last Updated**: 2026-06-23  
**Author**: Claude Code (Sonnet 4.5)  
**Reviewers**: [Pending]  
**Status**: Draft (awaiting review)
