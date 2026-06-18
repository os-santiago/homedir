# Architecture Documentation - Discord Bot

This document provides a comprehensive technical overview of the HomeDir Discord Bot architecture, including system design, component interactions, data flows, and scalability considerations.

## Table of Contents

1. [High-Level Architecture](#high-level-architecture)
2. [Component Descriptions](#component-descriptions)
3. [Data Flow Diagrams](#data-flow-diagrams)
4. [Deployment Architecture](#deployment-architecture)
5. [Technology Stack](#technology-stack)
6. [Scalability Considerations](#scalability-considerations)
7. [Performance Optimization](#performance-optimization)
8. [Error Handling](#error-handling)
9. [Future Enhancements](#future-enhancements)
10. [Design Decisions](#design-decisions)

---

## High-Level Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Discord Platform                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ Discord User │  │ Discord User │  │ Discord User             │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────────────────┘  │
│         │                  │                  │                       │
│         └──────────────────┴──────────────────┘                      │
│                            │                                          │
│                  Slash Commands (/ayuda, /mis-issues)                │
│                            │                                          │
└────────────────────────────┼──────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Discord Bot Application                         │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                   Discord.py Client Layer                      │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐   │  │
│  │  │ Event       │  │ Command     │  │ Slash Command       │   │  │
│  │  │ Listeners   │  │ Tree        │  │ Handlers            │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                             │                                        │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                  Business Logic Layer                          │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │  │
│  │  │ Command      │  │ Rate         │  │ Access           │    │  │
│  │  │ Processors   │  │ Limiter      │  │ Control          │    │  │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘    │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │  │
│  │  │ Data         │  │ Error        │  │ Logging          │    │  │
│  │  │ Formatter    │  │ Handler      │  │ System           │    │  │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘    │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                             │                                        │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                  Integration Layer                             │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │  │
│  │  │ GitHub API   │  │ Cache        │  │ Database         │    │  │
│  │  │ Client       │  │ Manager      │  │ Client           │    │  │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘    │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        External Services                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │ GitHub API   │  │ Redis Cache  │  │ PostgreSQL Database      │  │
│  │ (REST/       │  │ (Optional)   │  │ (Optional)               │  │
│  │  GraphQL)    │  │              │  │                          │  │
│  └──────────────┘  └──────────────┘  └──────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Architectural Patterns

1. **Layered Architecture**: Separation of concerns with distinct layers
2. **Event-Driven**: Responds to Discord events asynchronously
3. **Command Pattern**: Encapsulates commands as objects
4. **Repository Pattern**: Abstracts data access (future)
5. **Singleton Pattern**: Single bot instance per deployment

---

## Component Descriptions

### 1. Discord.py Client Layer

**Purpose**: Manages connection to Discord and handles low-level events.

**Key Components**:

```python
# Bot client initialization
intents = discord.Intents.default()
intents.message_content = True
intents.members = True

bot = commands.Bot(
    command_prefix='!',  # Fallback prefix
    intents=intents,
    help_command=None,  # Custom help implementation
)

# Command tree for slash commands
tree = bot.tree
```

**Responsibilities**:
- Establish and maintain WebSocket connection to Discord
- Handle authentication and heartbeat
- Parse incoming events
- Dispatch commands to handlers
- Manage Discord API rate limits

**Key Methods**:
- `on_ready()`: Triggered when bot successfully connects
- `on_message()`: Process incoming messages (if needed)
- `on_command_error()`: Global error handler
- `tree.sync()`: Synchronize slash commands with Discord

### 2. Command Tree and Handlers

**Purpose**: Route slash commands to appropriate handlers.

```python
@bot.tree.command(name="ayuda", description="Muestra información de ayuda")
async def ayuda_command(interaction: discord.Interaction):
    """
    Help command handler.
    
    Flow:
    1. Validate user permissions
    2. Check rate limits
    3. Generate help embed
    4. Send response
    """
    # Implementation...

@bot.tree.command(name="mis-issues", description="Muestra tus issues de GitHub")
async def mis_issues_command(
    interaction: discord.Interaction,
    username: str = None
):
    """
    GitHub issues command handler.
    
    Flow:
    1. Validate input
    2. Check rate limits
    3. Query GitHub API
    4. Format response
    5. Send embed
    """
    # Implementation...
```

**Design Considerations**:
- Use `async def` for all command handlers
- Defer responses for long-running operations
- Implement proper error handling
- Use ephemeral messages for user-specific data

### 3. Business Logic Layer

#### Command Processors

**Purpose**: Implement core command logic independent of Discord specifics.

```python
class CommandProcessor:
    """Process commands with business logic."""
    
    def __init__(self, github_client, rate_limiter, access_control):
        self.github = github_client
        self.rate_limiter = rate_limiter
        self.access_control = access_control
    
    async def process_help_request(self, user_id: int) -> dict:
        """
        Process help command request.
        
        Returns:
            dict: Formatted help data
        """
        # Check rate limit
        if not self.rate_limiter.is_allowed(user_id):
            raise RateLimitExceeded()
        
        # Generate help content
        help_data = {
            'title': 'Ayuda del Bot',
            'commands': [
                {'name': '/ayuda', 'description': 'Muestra esta ayuda'},
                {'name': '/mis-issues', 'description': 'Ver tus issues'},
            ],
            'footer': 'HomeDir Community Bot'
        }
        
        return help_data
    
    async def process_issues_request(
        self,
        user_id: int,
        github_username: str
    ) -> dict:
        """
        Process GitHub issues request.
        
        Args:
            user_id: Discord user ID
            github_username: GitHub username to query
        
        Returns:
            dict: Formatted issue data
        
        Raises:
            RateLimitExceeded: If rate limit hit
            GitHubAPIError: If GitHub API fails
            InvalidUsername: If username is invalid
        """
        # Validate input
        if not self._validate_username(github_username):
            raise InvalidUsername(github_username)
        
        # Check rate limit
        if not self.rate_limiter.is_allowed(user_id):
            raise RateLimitExceeded()
        
        # Query GitHub (with caching)
        issues = await self.github.get_user_issues(github_username)
        
        # Format response
        return self._format_issues(issues)
    
    def _validate_username(self, username: str) -> bool:
        """Validate GitHub username format."""
        return bool(re.match(r'^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$', username))
    
    def _format_issues(self, issues: list) -> dict:
        """Format issues for Discord display."""
        # Implementation...
```

#### Rate Limiter

**Purpose**: Prevent spam and API abuse.

```python
class RateLimiter:
    """
    Token bucket rate limiter.
    
    Implements per-user and global rate limiting.
    """
    
    def __init__(self, tokens: int, refill_period: int):
        """
        Initialize rate limiter.
        
        Args:
            tokens: Number of tokens per period
            refill_period: Seconds to refill tokens
        """
        self.tokens = tokens
        self.refill_period = refill_period
        self.buckets = {}  # user_id -> (tokens, last_refill)
        self.lock = asyncio.Lock()
    
    async def is_allowed(self, user_id: int) -> bool:
        """Check if user has tokens available."""
        async with self.lock:
            now = time.time()
            
            if user_id not in self.buckets:
                self.buckets[user_id] = (self.tokens - 1, now)
                return True
            
            tokens, last_refill = self.buckets[user_id]
            
            # Refill tokens
            time_passed = now - last_refill
            tokens_to_add = int(time_passed / self.refill_period * self.tokens)
            tokens = min(self.tokens, tokens + tokens_to_add)
            
            # Check if tokens available
            if tokens > 0:
                self.buckets[user_id] = (tokens - 1, now)
                return True
            
            self.buckets[user_id] = (tokens, last_refill)
            return False
```

#### Access Control

**Purpose**: Manage permissions and roles.

```python
class AccessControl:
    """Role-based access control system."""
    
    def __init__(self):
        self.role_hierarchy = {
            'owner': 100,
            'admin': 80,
            'moderator': 50,
            'member': 10,
        }
        self.command_permissions = {
            'ayuda': 'member',
            'mis-issues': 'member',
            'admin-command': 'admin',
        }
    
    def check_permission(
        self,
        user_roles: list[str],
        command: str
    ) -> bool:
        """Check if user can execute command."""
        required_role = self.command_permissions.get(command, 'member')
        required_level = self.role_hierarchy.get(required_role, 0)
        
        user_level = max(
            self.role_hierarchy.get(role, 0)
            for role in user_roles
        )
        
        return user_level >= required_level
```

### 4. Integration Layer

#### GitHub API Client

**Purpose**: Interface with GitHub API.

```python
class GitHubClient:
    """GitHub API client with caching and error handling."""
    
    def __init__(self, token: str, cache_ttl: int = 300):
        """
        Initialize GitHub client.
        
        Args:
            token: GitHub personal access token
            cache_ttl: Cache time-to-live in seconds
        """
        self.github = Github(token)
        self.cache = {}
        self.cache_ttl = cache_ttl
    
    async def get_user_issues(
        self,
        username: str,
        state: str = 'open'
    ) -> list[dict]:
        """
        Get issues for a user.
        
        Args:
            username: GitHub username
            state: Issue state (open, closed, all)
        
        Returns:
            List of issue dictionaries
        
        Raises:
            GitHubAPIError: If API request fails
        """
        cache_key = f"issues:{username}:{state}"
        
        # Check cache
        if cache_key in self.cache:
            cached_data, timestamp = self.cache[cache_key]
            if time.time() - timestamp < self.cache_ttl:
                return cached_data
        
        try:
            # Query GitHub API
            user = self.github.get_user(username)
            issues = user.get_issues(state=state)
            
            # Format response
            result = [
                {
                    'number': issue.number,
                    'title': issue.title,
                    'url': issue.html_url,
                    'state': issue.state,
                    'created_at': issue.created_at.isoformat(),
                    'repository': issue.repository.full_name,
                }
                for issue in issues[:20]  # Limit to 20 issues
            ]
            
            # Cache result
            self.cache[cache_key] = (result, time.time())
            
            return result
        
        except GithubException as e:
            raise GitHubAPIError(f"GitHub API error: {e.data.get('message', str(e))}")
```

#### Cache Manager

**Purpose**: Manage caching across the application.

```python
class CacheManager:
    """
    Generic cache manager with TTL support.
    
    Can be extended to use Redis for distributed caching.
    """
    
    def __init__(self, default_ttl: int = 300):
        """
        Initialize cache manager.
        
        Args:
            default_ttl: Default time-to-live in seconds
        """
        self.cache = {}
        self.default_ttl = default_ttl
        self.lock = asyncio.Lock()
    
    async def get(self, key: str) -> Optional[Any]:
        """Get cached value."""
        async with self.lock:
            if key in self.cache:
                value, timestamp, ttl = self.cache[key]
                if time.time() - timestamp < ttl:
                    return value
                else:
                    del self.cache[key]
            return None
    
    async def set(
        self,
        key: str,
        value: Any,
        ttl: Optional[int] = None
    ):
        """Set cached value with TTL."""
        async with self.lock:
            ttl = ttl or self.default_ttl
            self.cache[key] = (value, time.time(), ttl)
    
    async def invalidate(self, key: str):
        """Invalidate cached value."""
        async with self.lock:
            if key in self.cache:
                del self.cache[key]
    
    async def clear(self):
        """Clear all cached values."""
        async with self.lock:
            self.cache.clear()
```

---

## Data Flow Diagrams

### Slash Command Flow

```
┌─────────┐
│  User   │
└────┬────┘
     │ 1. Execute slash command
     │    /mis-issues username
     ▼
┌─────────────────┐
│ Discord API     │
└────┬────────────┘
     │ 2. HTTP POST to bot
     ▼
┌─────────────────────┐
│ Discord.py Client   │
│ - Parse interaction │
│ - Validate command  │
└────┬────────────────┘
     │ 3. Route to handler
     ▼
┌─────────────────────┐
│ Command Handler     │
│ - Extract params    │
│ - Defer response    │
└────┬────────────────┘
     │ 4. Call processor
     ▼
┌─────────────────────┐
│ Command Processor   │
│ - Validate input    │
│ - Check rate limit  │
└────┬────────────────┘
     │ 5. Query data
     ▼
┌─────────────────────┐
│ GitHub Client       │
│ - Check cache       │
│ - Call GitHub API   │
│ - Cache result      │
└────┬────────────────┘
     │ 6. Return data
     ▼
┌─────────────────────┐
│ Data Formatter      │
│ - Format for        │
│   Discord embed     │
└────┬────────────────┘
     │ 7. Send response
     ▼
┌─────────────────────┐
│ Discord API         │
│ - Display embed     │
└────┬────────────────┘
     │ 8. User sees result
     ▼
┌─────────┐
│  User   │
└─────────┘
```

### Error Handling Flow

```
┌──────────────┐
│ Any Component│
└──────┬───────┘
       │ 1. Error occurs
       ▼
┌───────────────────┐
│ Error Handler     │
│ - Catch exception │
│ - Log error       │
│ - Determine type  │
└──────┬────────────┘
       │
       ├─── 2a. User error ────────┐
       │                            ▼
       │                 ┌──────────────────┐
       │                 │ Send user-friendly│
       │                 │ error message     │
       │                 └──────────────────┘
       │
       ├─── 2b. Rate limit ────────┐
       │                            ▼
       │                 ┌──────────────────┐
       │                 │ Send cooldown    │
       │                 │ message          │
       │                 └──────────────────┘
       │
       ├─── 2c. API error ─────────┐
       │                            ▼
       │                 ┌──────────────────┐
       │                 │ Retry with       │
       │                 │ exponential      │
       │                 │ backoff          │
       │                 └──────────────────┘
       │
       └─── 2d. Critical error ────┐
                                    ▼
                         ┌──────────────────┐
                         │ Alert admins     │
                         │ Log to file      │
                         │ Consider shutdown│
                         └──────────────────┘
```

---

## Deployment Architecture

### Single-Instance Deployment (Current)

```
┌─────────────────────────────────────┐
│        Production Server             │
│                                      │
│  ┌────────────────────────────────┐ │
│  │  Systemd Service               │ │
│  │  - Auto-restart on failure     │ │
│  │  - Resource limits             │ │
│  │  - Security hardening          │ │
│  └────────┬───────────────────────┘ │
│           │                          │
│  ┌────────▼───────────────────────┐ │
│  │  Discord Bot Process           │ │
│  │  - Python 3.8+                 │ │
│  │  - Virtual environment         │ │
│  │  - Environment variables       │ │
│  └────────┬───────────────────────┘ │
│           │                          │
│  ┌────────▼───────────────────────┐ │
│  │  Local Storage                 │ │
│  │  - Logs                        │ │
│  │  - In-memory cache             │ │
│  └────────────────────────────────┘ │
│                                      │
└──────────────┬───────────────────────┘
               │
               ├─── Discord API (HTTPS)
               │
               └─── GitHub API (HTTPS)
```

### Scalable Deployment (Future)

```
┌──────────────────────────────────────────────────────────┐
│                     Load Balancer                         │
│                   (Discord Sharding)                      │
└────┬──────────────────┬──────────────────┬───────────────┘
     │                  │                  │
┌────▼─────┐      ┌────▼─────┐      ┌────▼─────┐
│ Bot      │      │ Bot      │      │ Bot      │
│ Instance │      │ Instance │      │ Instance │
│ (Shard 0)│      │ (Shard 1)│      │ (Shard 2)│
└────┬─────┘      └────┬─────┘      └────┬─────┘
     │                  │                  │
     └──────────────────┴──────────────────┘
                        │
         ┌──────────────┼──────────────┐
         │              │              │
    ┌────▼────┐   ┌────▼────┐   ┌────▼────┐
    │ Redis   │   │ Postgres│   │ Message │
    │ Cache   │   │ Database│   │ Queue   │
    └─────────┘   └─────────┘   └─────────┘
```

### Container Deployment (Docker)

```dockerfile
# Dockerfile
FROM python:3.11-slim

# Create non-root user
RUN useradd -r -u 1000 -m discord-bot

# Set working directory
WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY --chown=discord-bot:discord-bot . .

# Switch to non-root user
USER discord-bot

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:8080/health')" || exit 1

# Run bot
CMD ["python", "bot.py"]
```

```yaml
# docker-compose.yml
version: '3.8'

services:
  discord-bot:
    build: .
    restart: unless-stopped
    env_file:
      - .env
    volumes:
      - ./logs:/app/logs
    networks:
      - bot-network
    depends_on:
      - redis
      - postgres
  
  redis:
    image: redis:7-alpine
    restart: unless-stopped
    volumes:
      - redis-data:/data
    networks:
      - bot-network
  
  postgres:
    image: postgres:15-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: discord_bot
      POSTGRES_USER: bot
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - bot-network

networks:
  bot-network:
    driver: bridge

volumes:
  redis-data:
  postgres-data:
```

---

## Technology Stack

### Core Technologies

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| Runtime | Python | 3.8+ | Programming language |
| Discord API | discord.py | 2.3+ | Discord bot framework |
| GitHub API | PyGithub | 2.1+ | GitHub integration |
| Environment | python-dotenv | 1.0+ | Configuration management |

### Optional Technologies (For Scaling)

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Cache | Redis | Distributed caching |
| Database | PostgreSQL | Persistent data storage |
| Message Queue | RabbitMQ/Kafka | Async task processing |
| Monitoring | Prometheus + Grafana | Metrics and dashboards |
| Logging | ELK Stack | Centralized logging |
| Container | Docker | Containerization |
| Orchestration | Kubernetes | Container orchestration |

---

## Scalability Considerations

### Current Limitations

1. **Single Instance**
   - No horizontal scaling
   - Single point of failure
   - Limited to one server's resources

2. **In-Memory Storage**
   - Cache lost on restart
   - Rate limit state not persisted
   - No shared state across instances

3. **Synchronous Processing**
   - Blocks on long-running operations
   - Limited concurrent request handling

### Scalability Solutions

#### 1. Discord Sharding

Discord requires sharding for bots in 2,500+ servers.

```python
# Automatic sharding
bot = commands.AutoShardedBot(
    command_prefix='!',
    intents=intents,
    shard_count=4  # Or None for automatic
)

# Manual sharding for more control
shard_ids = [0, 1]  # This instance handles shards 0 and 1
shard_count = 4     # Total shards across all instances

bot = commands.Bot(
    command_prefix='!',
    intents=intents,
    shard_ids=shard_ids,
    shard_count=shard_count
)
```

#### 2. Distributed Caching (Redis)

```python
import aioredis

class RedisCache:
    """Redis-based cache for distributed systems."""
    
    def __init__(self, redis_url: str):
        self.redis = aioredis.from_url(redis_url)
    
    async def get(self, key: str) -> Optional[str]:
        """Get cached value."""
        return await self.redis.get(key)
    
    async def set(self, key: str, value: str, ttl: int = 300):
        """Set cached value with TTL."""
        await self.redis.setex(key, ttl, value)
    
    async def delete(self, key: str):
        """Delete cached value."""
        await self.redis.delete(key)
```

#### 3. Database Integration

```python
from sqlalchemy import create_engine, Column, Integer, String, DateTime
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

Base = declarative_base()

class UserStats(Base):
    """Track user statistics."""
    __tablename__ = 'user_stats'
    
    id = Column(Integer, primary_key=True)
    discord_id = Column(String, unique=True, nullable=False)
    github_username = Column(String)
    commands_used = Column(Integer, default=0)
    last_activity = Column(DateTime)
    created_at = Column(DateTime)

# Database setup
engine = create_engine('postgresql://user:pass@localhost/discord_bot')
Session = sessionmaker(bind=engine)
```

#### 4. Message Queue Integration

```python
import pika

class TaskQueue:
    """RabbitMQ task queue for async processing."""
    
    def __init__(self, rabbitmq_url: str):
        self.connection = pika.BlockingConnection(
            pika.URLParameters(rabbitmq_url)
        )
        self.channel = self.connection.channel()
        self.channel.queue_declare(queue='bot_tasks', durable=True)
    
    def enqueue_task(self, task_type: str, data: dict):
        """Enqueue task for background processing."""
        message = json.dumps({'type': task_type, 'data': data})
        
        self.channel.basic_publish(
            exchange='',
            routing_key='bot_tasks',
            body=message,
            properties=pika.BasicProperties(
                delivery_mode=2,  # Make message persistent
            )
        )
```

### Load Estimation

**Current Capacity (Single Instance)**:
- Concurrent users: ~1,000
- Commands/minute: ~100
- API calls/minute: ~50 (considering caching)
- Memory usage: ~200MB
- CPU usage: <10%

**Scaling Triggers**:
- CPU usage > 70% sustained
- Memory usage > 80%
- Command latency > 2 seconds
- Error rate > 1%
- Server count approaching 2,000

---

## Performance Optimization

### 1. Caching Strategy

```python
class MultiLevelCache:
    """Multi-level caching strategy."""
    
    def __init__(self, local_cache, redis_cache):
        self.local = local_cache  # L1: In-memory
        self.redis = redis_cache  # L2: Redis
    
    async def get(self, key: str) -> Optional[Any]:
        """Get from L1, then L2."""
        # Try L1
        value = await self.local.get(key)
        if value is not None:
            return value
        
        # Try L2
        value = await self.redis.get(key)
        if value is not None:
            # Populate L1
            await self.local.set(key, value)
            return value
        
        return None
    
    async def set(self, key: str, value: Any, ttl: int = 300):
        """Set in both L1 and L2."""
        await self.local.set(key, value, ttl)
        await self.redis.set(key, value, ttl)
```

### 2. Connection Pooling

```python
from sqlalchemy.pool import QueuePool

# PostgreSQL connection pool
engine = create_engine(
    'postgresql://user:pass@localhost/discord_bot',
    poolclass=QueuePool,
    pool_size=10,
    max_overflow=20,
    pool_pre_ping=True,  # Verify connections
    pool_recycle=3600,   # Recycle connections after 1 hour
)

# GitHub API connection reuse
github_client = Github(
    token,
    per_page=100,  # Reduce API calls
    timeout=15,    # Connection timeout
    retry=3,       # Auto-retry failed requests
)
```

### 3. Async Operations

```python
async def fetch_multiple_users(usernames: list[str]) -> dict:
    """Fetch multiple users concurrently."""
    tasks = [
        github_client.get_user_async(username)
        for username in usernames
    ]
    
    results = await asyncio.gather(*tasks, return_exceptions=True)
    
    return {
        username: result
        for username, result in zip(usernames, results)
        if not isinstance(result, Exception)
    }
```

### 4. Database Query Optimization

```python
# Use eager loading to prevent N+1 queries
from sqlalchemy.orm import joinedload

session.query(User)\
    .options(joinedload(User.stats))\
    .filter(User.active == True)\
    .all()

# Use indexing for frequently queried fields
class User(Base):
    __tablename__ = 'users'
    
    id = Column(Integer, primary_key=True)
    discord_id = Column(String, index=True, unique=True)  # Indexed
    github_username = Column(String, index=True)          # Indexed
```

---

## Error Handling

### Error Hierarchy

```python
class BotError(Exception):
    """Base exception for bot errors."""
    pass

class UserError(BotError):
    """User-caused errors (invalid input, etc.)."""
    pass

class RateLimitExceeded(UserError):
    """Rate limit exceeded."""
    pass

class InvalidInput(UserError):
    """Invalid user input."""
    pass

class APIError(BotError):
    """External API errors."""
    pass

class GitHubAPIError(APIError):
    """GitHub API errors."""
    pass

class DiscordAPIError(APIError):
    """Discord API errors."""
    pass

class SystemError(BotError):
    """Internal system errors."""
    pass

class DatabaseError(SystemError):
    """Database errors."""
    pass

class ConfigurationError(SystemError):
    """Configuration errors."""
    pass
```

### Centralized Error Handler

```python
class ErrorHandler:
    """Centralized error handling."""
    
    def __init__(self, logger):
        self.logger = logger
    
    async def handle_error(
        self,
        error: Exception,
        interaction: discord.Interaction
    ):
        """Handle errors based on type."""
        
        if isinstance(error, RateLimitExceeded):
            await interaction.response.send_message(
                "⏳ Por favor espera antes de usar este comando nuevamente.",
                ephemeral=True
            )
        
        elif isinstance(error, InvalidInput):
            await interaction.response.send_message(
                f"❌ Entrada inválida: {str(error)}",
                ephemeral=True
            )
        
        elif isinstance(error, GitHubAPIError):
            self.logger.error(f"GitHub API error: {error}")
            await interaction.response.send_message(
                "❌ Error al conectar con GitHub. Por favor intenta más tarde.",
                ephemeral=True
            )
        
        elif isinstance(error, SystemError):
            self.logger.critical(f"System error: {error}")
            await interaction.response.send_message(
                "❌ Error interno del sistema. Los administradores han sido notificados.",
                ephemeral=True
            )
            # Alert admins
            await self.alert_admins(error)
        
        else:
            # Unexpected error
            self.logger.exception(f"Unexpected error: {error}")
            await interaction.response.send_message(
                "❌ Ocurrió un error inesperado.",
                ephemeral=True
            )

# Global error handler
@bot.tree.error
async def on_app_command_error(
    interaction: discord.Interaction,
    error: app_commands.AppCommandError
):
    """Global error handler for slash commands."""
    await error_handler.handle_error(error.original, interaction)
```

### Retry Logic

```python
from tenacity import retry, stop_after_attempt, wait_exponential

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10)
)
async def call_github_api(endpoint: str):
    """Call GitHub API with automatic retry."""
    try:
        response = await github_client.get(endpoint)
        return response
    except Exception as e:
        logger.warning(f"API call failed, retrying: {e}")
        raise
```

---

## Future Enhancements

### Phase 1: Core Improvements (1-3 months)

1. **Enhanced Commands**
   - `/create-issue`: Create GitHub issues from Discord
   - `/search-issues`: Search issues with filters
   - `/issue-status`: Get detailed issue information
   - `/assign-issue`: Assign issues to users

2. **Webhook Integration**
   - Receive GitHub webhook events
   - Post issue updates to Discord channels
   - Notify on PR reviews, merges, etc.

3. **User Linking**
   - Link Discord accounts to GitHub accounts
   - Persistent user preferences
   - Role assignment based on GitHub activity

### Phase 2: Advanced Features (3-6 months)

1. **Analytics Dashboard**
   - Web-based admin panel
   - Usage statistics
   - Performance metrics
   - User activity tracking

2. **Multi-Repository Support**
   - Support multiple GitHub repositories
   - Per-channel repository configuration
   - Cross-repo issue search

3. **Advanced Notifications**
   - Customizable notification rules
   - Digest mode (daily/weekly summaries)
   - Priority-based notifications

### Phase 3: Enterprise Features (6-12 months)

1. **Plugin System**
   - Allow custom command plugins
   - Third-party integrations
   - Plugin marketplace

2. **Machine Learning**
   - Issue categorization
   - Spam detection
   - Sentiment analysis
   - Auto-labeling

3. **Advanced Integrations**
   - Jira integration
   - Slack bridge
   - CI/CD integration
   - Project management tools

---

## Design Decisions

### Why Discord.py?

**Pros**:
- Well-maintained and actively developed
- Comprehensive documentation
- Built-in support for slash commands
- Async/await support
- Large community

**Cons**:
- Python-only
- Higher memory usage than some alternatives

**Alternatives Considered**:
- discord.js (Node.js)
- JDA (Java)
- Serenity (Rust)

### Why PyGithub?

**Pros**:
- Mature library
- Good documentation
- Supports both REST and GraphQL
- Rate limit handling

**Cons**:
- Synchronous by default
- Some API features lag behind GitHub releases

**Alternatives Considered**:
- ghapi
- github3.py
- Direct REST API calls

### Why Slash Commands?

**Pros**:
- Modern Discord interface
- Built-in parameter validation
- Better user experience
- Discoverability

**Cons**:
- Requires bot permissions
- Global command updates take time
- Less flexible than text commands

### Architecture Patterns

**Why Layered Architecture?**
- Separation of concerns
- Easier testing
- Better maintainability
- Clear responsibilities

**Why Event-Driven?**
- Natural fit for Discord
- Async processing
- Scalability
- Responsiveness

---

## Conclusion

This architecture is designed to be:
- **Modular**: Easy to extend and modify
- **Scalable**: Can grow from single instance to distributed system
- **Maintainable**: Clear structure and separation of concerns
- **Secure**: Built-in security best practices
- **Performant**: Optimized for responsiveness

As the bot grows, the architecture supports gradual migration from a simple single-instance deployment to a complex distributed system without major rewrites.

**Last Updated:** 2026-06-18  
**Version:** 1.0.0  
**Maintainer:** HomeDir Team
