# Discord Bot Architecture

## System Overview

The Discord GitHub Bot is a Python-based service that bridges Discord and GitHub, enabling users to create and track GitHub issues directly from Discord.

## High-Level Architecture

```
┌─────────────────┐
│  Discord Users  │
└────────┬────────┘
         │ Slash Commands
         ▼
┌─────────────────┐
│  Discord API    │
└────────┬────────┘
         │ Events
         ▼
┌─────────────────┐      ┌──────────────┐
│  Discord Bot    │◄────►│ Issue        │
│  (bot.py)       │      │ Mapping DB   │
└────────┬────────┘      └──────────────┘
         │
         │ GitHub API
         ▼
┌─────────────────┐
│  GitHub API     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  GitHub Issues  │
└─────────────────┘
```

## Component Architecture

### 1. Discord Client Layer

**Class**: `DiscordGitHubBot`

**Responsibilities**:
- Manage Discord connection and authentication
- Handle slash command registration
- Process user interactions
- Send notifications to users

**Key Methods**:
- `setup_hook()`: Initialize commands and background tasks
- `on_ready()`: Connection established handler
- `tree`: Command tree for slash commands

**Dependencies**:
- `discord.py` library
- Discord bot token (environment variable)

### 2. Command Handler Layer

**Slash Commands**:

#### `/ayuda` - Create Issue
```python
Parameters:
  - titulo: str (required)
  - descripcion: str (required)
  - prioridad: str (optional, default: P3)
  - etiquetas: str (optional)

Flow:
  1. Validate input (priority format)
  2. Build issue body with Discord metadata
  3. Call GitHub API to create issue
  4. Store user-issue mapping
  5. Send confirmation to user
```

#### `/mis-issues` - List Issues
```python
Parameters: None

Flow:
  1. Lookup user's issues in mapping
  2. Fetch issue details from GitHub
  3. Build embed with issue list
  4. Send to user (ephemeral)
```

### 3. GitHub Integration Layer

**Client**: `github.Github`

**Responsibilities**:
- Create issues via GitHub API
- Query issue status and details
- Manage labels and metadata

**API Calls**:
- `repo.create_issue()`: Create new issue
- `repo.get_issue()`: Get issue details
- `issue.labels`: Access issue labels
- `issue.state`: Check if open/closed

**Rate Limits**:
- GitHub API: 5,000 requests/hour (authenticated)
- Implemented: No explicit rate limiting (relies on GitHub)

### 4. Data Persistence Layer

**Current Implementation**: JSON file (`issue_mapping.json`)

**Schema**:
```json
{
  "discord_user_id": [
    {
      "issue_number": 123,
      "title": "Issue title",
      "created_at": "2024-01-01T00:00:00",
      "channel_id": 123456789,
      "validation_notified": false
    }
  ]
}
```

**Operations**:
- `_load_mapping()`: Load from file at startup
- `_save_mapping()`: Persist to file after changes
- Thread safety: None (single-threaded bot)

**Limitations**:
- No concurrent access support
- No transactions
- Manual backup required
- Limited query capabilities

**Production Alternative**:
```python
# PostgreSQL schema
CREATE TABLE users (
    discord_user_id BIGINT PRIMARY KEY,
    github_username VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE issues (
    id SERIAL PRIMARY KEY,
    discord_user_id BIGINT REFERENCES users(discord_user_id),
    issue_number INT NOT NULL,
    repo VARCHAR(255) NOT NULL,
    title TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    channel_id BIGINT,
    validation_notified BOOLEAN DEFAULT FALSE,
    closed_notified BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_discord_user ON issues(discord_user_id);
CREATE INDEX idx_issue_number ON issues(issue_number);
```

### 5. Background Task Layer

**Task**: `check_issue_status`

**Schedule**: Every 5 minutes

**Flow**:
```
1. Iterate all tracked issues
2. For each issue:
   a. Fetch current state from GitHub
   b. Compare with last known state
   c. Detect status changes:
      - Open → Closed
      - Added validation label
   d. Send notification if changed
3. Update state tracking
```

**Optimization Opportunities**:
- Implement incremental sync (only check recently updated)
- Use GitHub webhooks instead of polling
- Batch API calls
- Cache issue states

## Data Flow Diagrams

### Issue Creation Flow

```
User                Discord Bot           GitHub API          Storage
 │                       │                     │                 │
 │ /ayuda command        │                     │                 │
 ├──────────────────────►│                     │                 │
 │                       │                     │                 │
 │                       │ Validate input      │                 │
 │                       │                     │                 │
 │                       │ Create issue        │                 │
 │                       ├────────────────────►│                 │
 │                       │                     │                 │
 │                       │ Issue #123          │                 │
 │                       │◄────────────────────┤                 │
 │                       │                     │                 │
 │                       │ Store mapping       │                 │
 │                       ├─────────────────────┼────────────────►│
 │                       │                     │                 │
 │ Success embed         │                     │                 │
 │◄──────────────────────┤                     │                 │
 │                       │                     │                 │
```

### Notification Flow

```
Background Task      GitHub API       Storage         Discord API      User
      │                  │               │                 │            │
      │ Check status     │               │                 │            │
      │ (every 5 min)    │               │                 │            │
      │                  │               │                 │            │
      │ Get issue #123   │               │                 │            │
      ├─────────────────►│               │                 │            │
      │                  │               │                 │            │
      │ State: closed    │               │                 │            │
      │◄─────────────────┤               │                 │            │
      │                  │               │                 │            │
      │ Load mapping     │               │                 │            │
      ├──────────────────┼──────────────►│                 │            │
      │                  │               │                 │            │
      │ User IDs         │               │                 │            │
      │◄─────────────────┼───────────────┤                 │            │
      │                  │               │                 │            │
      │ Send notification│               │                 │            │
      ├──────────────────┼───────────────┼────────────────►│            │
      │                  │               │                 │            │
      │                  │               │    Notification │            │
      │                  │               │                 ├───────────►│
      │                  │               │                 │            │
```

## Deployment Architecture

### Development Environment

```
Developer Machine
├── Python 3.9+
├── bot.py
├── .env (local config)
└── issue_mapping.json (local storage)
```

### Production Environment (Recommended)

```
┌─────────────────────────────────────────┐
│          Cloud Provider (AWS/GCP)       │
│                                         │
│  ┌────────────────────────────────┐    │
│  │  Compute Instance (EC2/GCE)    │    │
│  │                                │    │
│  │  ├── Docker Container          │    │
│  │  │   ├── Python Runtime        │    │
│  │  │   ├── bot.py                │    │
│  │  │   └── Dependencies          │    │
│  │  │                              │    │
│  │  └── systemd service            │    │
│  └────────────┬───────────────────┘    │
│               │                         │
│  ┌────────────▼───────────────────┐    │
│  │  Secrets Manager               │    │
│  │  ├── DISCORD_BOT_TOKEN         │    │
│  │  ├── GITHUB_TOKEN              │    │
│  │  └── Database credentials      │    │
│  └────────────────────────────────┘    │
│                                         │
│  ┌────────────────────────────────┐    │
│  │  Database (PostgreSQL/RDS)     │    │
│  │  ├── users table               │    │
│  │  └── issues table              │    │
│  └────────────────────────────────┘    │
│                                         │
│  ┌────────────────────────────────┐    │
│  │  Monitoring                    │    │
│  │  ├── CloudWatch/Stackdriver    │    │
│  │  └── Application logs          │    │
│  └────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

### Alternative: Serverless Architecture

For better scalability and cost efficiency:

```
Discord → API Gateway → Lambda Functions ← DynamoDB
                            ↓
                        GitHub API

Components:
- API Gateway: Receive Discord interactions
- Lambda 1: Handle commands (create issue)
- Lambda 2: Process webhooks (status changes)
- DynamoDB: User-issue mapping
- EventBridge: Scheduled tasks (if needed)
```

## Security Architecture

### Authentication Flow

```
┌──────────────┐
│   Discord    │
│    User      │
└──────┬───────┘
       │
       │ 1. OAuth (Discord handles)
       ▼
┌──────────────┐
│   Discord    │
│   Server     │
└──────┬───────┘
       │
       │ 2. Slash Command
       ▼
┌──────────────┐      3. Validate      ┌──────────────┐
│  Discord     ├────────────────────────►│   Discord    │
│   Bot        │◄────────────────────────┤    API       │
└──────┬───────┘      Token             └──────────────┘
       │
       │ 4. Create Issue (with PAT)
       ▼
┌──────────────┐
│   GitHub     │
│    API       │
└──────────────┘
```

### Secrets Management

```
Environment Variables (Development)
    ↓
Secrets Manager (Production)
    ↓
Bot Application
    ↓
In-Memory Only (Never Logged)
```

## Scalability Considerations

### Current Limitations

1. **Single Instance**: No horizontal scaling
2. **Polling**: Inefficient for high volume
3. **File-based Storage**: No concurrent access
4. **No Caching**: Repeated API calls

### Scaling Strategy

#### Phase 1: Vertical Scaling
- Increase compute resources
- Optimize polling interval
- Add caching layer (Redis)

#### Phase 2: Horizontal Scaling
- Move to database (PostgreSQL)
- Implement webhook-based notifications
- Add load balancer for multiple instances
- Use message queue (RabbitMQ/SQS)

#### Phase 3: Microservices
```
┌─────────────────┐
│  Command Service│  (Handle Discord commands)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Issue Service  │  (Create/manage issues)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Webhook Service│  (Process GitHub events)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Notification    │  (Send Discord messages)
│    Service      │
└─────────────────┘
```

## Error Handling

### Error Types and Responses

1. **Discord API Errors**
   - Connection lost → Automatic reconnect
   - Rate limit → Exponential backoff
   - Invalid command → User-friendly error message

2. **GitHub API Errors**
   - Authentication failure → Log error, notify admin
   - Rate limit exceeded → Queue requests, retry later
   - Repository not found → Configuration error, log and alert

3. **Storage Errors**
   - File write failure → Retry, log error
   - Corrupted data → Restore from backup
   - Disk full → Alert admin

### Retry Strategy

```python
from tenacity import retry, stop_after_attempt, wait_exponential

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=4, max=10)
)
async def create_github_issue(repo, title, body, labels):
    """Create GitHub issue with retry logic."""
    return repo.create_issue(title=title, body=body, labels=labels)
```

## Performance Optimization

### Caching Strategy

```python
from functools import lru_cache
from datetime import datetime, timedelta

class CachedGitHubClient:
    def __init__(self, github_client):
        self.client = github_client
        self.cache = {}
        self.cache_ttl = timedelta(minutes=5)
    
    def get_issue(self, issue_number):
        cache_key = f"issue_{issue_number}"
        
        if cache_key in self.cache:
            cached_data, timestamp = self.cache[cache_key]
            if datetime.now() - timestamp < self.cache_ttl:
                return cached_data
        
        # Fetch from API
        issue = self.client.get_issue(issue_number)
        self.cache[cache_key] = (issue, datetime.now())
        return issue
```

### Batch Processing

Instead of checking issues one by one:

```python
async def check_multiple_issues(issue_numbers):
    """Check multiple issues in parallel."""
    tasks = [fetch_issue(num) for num in issue_numbers]
    return await asyncio.gather(*tasks)
```

## Monitoring and Observability

### Metrics to Track

1. **Application Metrics**
   - Issues created per hour/day
   - Command response time
   - Notification delivery rate
   - Error rate

2. **System Metrics**
   - CPU/Memory usage
   - API call rate
   - Database query time
   - Queue length

3. **Business Metrics**
   - Active users
   - Issues resolved
   - Average resolution time
   - User satisfaction

### Logging Structure

```python
{
  "timestamp": "2024-01-01T12:00:00Z",
  "level": "INFO",
  "event": "issue_created",
  "user_id": "123456789",
  "issue_number": 456,
  "repo": "owner/repo",
  "duration_ms": 234
}
```

## Future Enhancements

### Short-term
1. Webhook integration (replace polling)
2. Database migration (PostgreSQL)
3. Enhanced error handling
4. Comprehensive testing

### Medium-term
1. Comment on issues from Discord
2. Issue search and filters
3. Assignment and labels management
4. Multi-repository support

### Long-term
1. AI-powered issue categorization
2. Automatic similar issue detection
3. Integration with project boards
4. Analytics dashboard

## References

- [Discord.py Documentation](https://discordpy.readthedocs.io/)
- [PyGithub Documentation](https://pygithub.readthedocs.io/)
- [Discord API Best Practices](https://discord.com/developers/docs/topics/gateway)
- [GitHub API Documentation](https://docs.github.com/en/rest)
