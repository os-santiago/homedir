# Security Documentation - Discord Bot

This document outlines comprehensive security best practices, guidelines, and recommendations for the HomeDir Discord Bot.

## Table of Contents

1. [Authentication and Authorization](#authentication-and-authorization)
2. [Input Validation and Sanitization](#input-validation-and-sanitization)
3. [Rate Limiting and Anti-Spam](#rate-limiting-and-anti-spam)
4. [Access Control](#access-control)
5. [Data Privacy and GDPR Compliance](#data-privacy-and-gdpr-compliance)
6. [Logging and Monitoring](#logging-and-monitoring)
7. [Incident Response](#incident-response)
8. [Security Checklist](#security-checklist)
9. [Dependency Management](#dependency-management)
10. [Deployment Security](#deployment-security)

---

## Authentication and Authorization

### Discord Bot Token Security

**Critical:** The Discord bot token is the most sensitive credential. Compromise of this token allows complete control of the bot.

#### Best Practices

1. **Environment Variables**
   ```bash
   # ALWAYS store tokens in environment variables or .env files
   DISCORD_BOT_TOKEN=your_token_here
   
   # NEVER hardcode in source:
   # token = "YOUR_DISCORD_TOKEN_HERE"  # ❌ WRONG - Never hardcode tokens!
   ```

2. **File Permissions**
   ```bash
   # Restrict .env file access
   chmod 600 .env
   chown bot-user:bot-group .env
   ```

3. **Git Ignore Rules**
   ```gitignore
   # Always add to .gitignore
   .env
   .env.*
   *.env
   secrets/
   config/secrets.yaml
   ```

4. **Token Rotation**
   - Rotate tokens every 90 days minimum
   - Immediately rotate if compromise is suspected
   - Use different tokens for dev/staging/production
   - Document rotation procedures

5. **Token Storage**
   - Use secret management services in production:
     - AWS Secrets Manager
     - Azure Key Vault
     - HashiCorp Vault
     - Google Secret Manager
   - Never store tokens in:
     - Source code
     - Docker images
     - Configuration files committed to git
     - Log files
     - Error messages

### GitHub Token Security

GitHub Personal Access Tokens (PAT) provide access to repositories and user data.

#### Best Practices

1. **Minimal Scopes**
   ```bash
   # Only grant necessary permissions
   Required: repo (if private repos), read:user
   Avoid: admin:org, delete_repo, admin:enterprise
   ```

2. **Fine-Grained Tokens**
   - Use fine-grained PATs instead of classic tokens
   - Limit to specific repositories
   - Set expiration dates (90 days maximum)
   - Regularly audit token usage

3. **Token Validation**
   ```python
   # Validate token on startup
   def validate_github_token(token):
       try:
           g = Github(token)
           user = g.get_user()
           logger.info(f"GitHub token valid for user: {user.login}")
           return True
       except Exception as e:
           logger.error(f"Invalid GitHub token: {e}")
           return False
   ```

### OAuth 2.0 Considerations

If implementing user-specific GitHub actions:

1. **Use OAuth Flow**
   - Never ask users for their passwords or PATs
   - Implement proper OAuth 2.0 authorization code flow
   - Store refresh tokens securely
   - Implement token expiration and renewal

2. **State Parameter**
   - Always use state parameter to prevent CSRF
   - Generate cryptographically random state values
   - Validate state on callback

---

## Input Validation and Sanitization

### User Input Validation

All user inputs must be validated before processing.

#### Command Arguments

```python
# Example validation for username input
def validate_github_username(username: str) -> bool:
    """
    Validate GitHub username format.
    
    Rules:
    - 1-39 characters
    - Alphanumeric and hyphens only
    - Cannot start/end with hyphen
    - Cannot contain consecutive hyphens
    """
    if not username or len(username) > 39:
        return False
    
    # Regex pattern for valid GitHub usernames
    pattern = r'^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$'
    return re.match(pattern, username) is not None

# Example validation for issue numbers
def validate_issue_number(issue_num: str) -> bool:
    """Validate issue number is a positive integer."""
    try:
        num = int(issue_num)
        return 0 < num < 1000000  # Reasonable upper limit
    except (ValueError, TypeError):
        return False
```

#### String Length Limits

```python
# Define maximum lengths for user inputs
MAX_USERNAME_LENGTH = 39
MAX_SEARCH_QUERY_LENGTH = 256
MAX_ISSUE_TITLE_LENGTH = 256
MAX_COMMENT_LENGTH = 65536

def validate_input_length(value: str, max_length: int) -> bool:
    """Validate input doesn't exceed maximum length."""
    return len(value) <= max_length
```

#### Injection Prevention

1. **SQL Injection** (if using database)
   ```python
   # Use parameterized queries ALWAYS
   cursor.execute("SELECT * FROM users WHERE id = ?", (user_id,))  # ✓ Correct
   # cursor.execute(f"SELECT * FROM users WHERE id = {user_id}")  # ❌ WRONG
   ```

2. **Command Injection**
   ```python
   # Never use shell=True with user input
   # subprocess.run(f"git log {user_input}", shell=True)  # ❌ WRONG
   
   # Use argument lists instead
   subprocess.run(["git", "log", user_input])  # ✓ Correct
   ```

3. **NoSQL Injection** (if using MongoDB/similar)
   ```python
   # Validate input types
   user_id = str(user_input)  # Ensure string type
   db.users.find({"id": user_id})  # Safe
   ```

### Output Sanitization

```python
def sanitize_for_discord(text: str) -> str:
    """
    Sanitize text for Discord output.
    
    - Escape Discord markdown
    - Remove @everyone/@here mentions
    - Limit length
    """
    # Escape markdown
    text = text.replace('*', '\\*').replace('_', '\\_')
    text = text.replace('`', '\\`').replace('~', '\\~')
    
    # Remove potentially abusive mentions
    text = text.replace('@everyone', '@​everyone')
    text = text.replace('@here', '@​here')
    
    # Truncate if too long
    if len(text) > 2000:
        text = text[:1997] + "..."
    
    return text
```

---

## Rate Limiting and Anti-Spam

### Per-User Rate Limiting

Implement cooldowns to prevent spam and abuse.

```python
from discord.ext import commands
from datetime import datetime, timedelta
from collections import defaultdict

class RateLimiter:
    """Rate limiter for bot commands."""
    
    def __init__(self, max_calls: int, period: timedelta):
        self.max_calls = max_calls
        self.period = period
        self.calls = defaultdict(list)
    
    def is_allowed(self, user_id: int) -> bool:
        """Check if user is within rate limit."""
        now = datetime.now()
        cutoff = now - self.period
        
        # Remove old calls
        self.calls[user_id] = [
            call_time for call_time in self.calls[user_id]
            if call_time > cutoff
        ]
        
        # Check if under limit
        if len(self.calls[user_id]) < self.max_calls:
            self.calls[user_id].append(now)
            return True
        
        return False
    
    def time_until_reset(self, user_id: int) -> timedelta:
        """Get time until rate limit resets."""
        if not self.calls[user_id]:
            return timedelta(0)
        
        oldest_call = min(self.calls[user_id])
        reset_time = oldest_call + self.period
        return max(reset_time - datetime.now(), timedelta(0))

# Usage
rate_limiter = RateLimiter(max_calls=5, period=timedelta(minutes=1))

@bot.tree.command(name="mis-issues")
async def mis_issues(interaction: discord.Interaction, username: str):
    if not rate_limiter.is_allowed(interaction.user.id):
        wait_time = rate_limiter.time_until_reset(interaction.user.id)
        await interaction.response.send_message(
            f"⏳ Por favor espera {wait_time.seconds} segundos antes de usar este comando nuevamente.",
            ephemeral=True
        )
        return
    
    # Process command...
```

### Global Rate Limiting

Protect against coordinated attacks.

```python
class GlobalRateLimiter:
    """Global rate limiter for all users."""
    
    def __init__(self, max_calls: int, period: timedelta):
        self.max_calls = max_calls
        self.period = period
        self.calls = []
        self.lock = asyncio.Lock()
    
    async def is_allowed(self) -> bool:
        """Check if global rate limit is not exceeded."""
        async with self.lock:
            now = datetime.now()
            cutoff = now - self.period
            
            # Remove old calls
            self.calls = [call_time for call_time in self.calls if call_time > cutoff]
            
            # Check if under limit
            if len(self.calls) < self.max_calls:
                self.calls.append(now)
                return True
            
            return False

# Usage
global_limiter = GlobalRateLimiter(max_calls=100, period=timedelta(minutes=1))
```

### GitHub API Rate Limiting

```python
import time
from functools import wraps

def github_rate_limit_handler(func):
    """Decorator to handle GitHub API rate limits."""
    @wraps(func)
    def wrapper(*args, **kwargs):
        max_retries = 3
        retry_count = 0
        
        while retry_count < max_retries:
            try:
                return func(*args, **kwargs)
            except RateLimitExceededException as e:
                retry_count += 1
                if retry_count >= max_retries:
                    raise
                
                # Get reset time from exception
                reset_time = e.reset_time
                wait_seconds = (reset_time - datetime.now()).total_seconds()
                
                logger.warning(f"GitHub rate limit hit. Waiting {wait_seconds}s")
                time.sleep(min(wait_seconds + 1, 60))  # Max 60s wait
        
        return None
    
    return wrapper
```

### Anti-Spam Measures

1. **Message Deduplication**
   ```python
   recent_messages = {}  # user_id: (message_hash, timestamp)
   
   def is_duplicate_message(user_id: int, message: str) -> bool:
       """Check if message is a duplicate within 5 seconds."""
       msg_hash = hash(message)
       now = time.time()
       
       if user_id in recent_messages:
           last_hash, last_time = recent_messages[user_id]
           if msg_hash == last_hash and (now - last_time) < 5:
               return True
       
       recent_messages[user_id] = (msg_hash, now)
       return False
   ```

2. **Exponential Backoff**
   ```python
   user_violations = defaultdict(int)
   
   def calculate_cooldown(user_id: int) -> int:
       """Calculate cooldown based on violation count."""
       violations = user_violations[user_id]
       return min(2 ** violations, 3600)  # Max 1 hour
   ```

---

## Access Control

### Role-Based Access Control (RBAC)

```python
from enum import Enum
from typing import Set

class BotPermission(Enum):
    """Bot permission levels."""
    USER = 1          # Basic user
    MODERATOR = 2     # Can manage issues
    ADMIN = 3         # Can configure bot
    OWNER = 4         # Full access

class AccessControl:
    """Manage role-based access control."""
    
    def __init__(self):
        self.role_permissions = {
            "Admin": BotPermission.ADMIN,
            "Moderator": BotPermission.MODERATOR,
            "Member": BotPermission.USER,
        }
        self.owner_ids = set()  # Bot owner Discord IDs
    
    def get_user_permission(self, member: discord.Member) -> BotPermission:
        """Get highest permission level for user."""
        if member.id in self.owner_ids:
            return BotPermission.OWNER
        
        highest_perm = BotPermission.USER
        for role in member.roles:
            if role.name in self.role_permissions:
                perm = self.role_permissions[role.name]
                if perm.value > highest_perm.value:
                    highest_perm = perm
        
        return highest_perm
    
    def require_permission(self, required: BotPermission):
        """Decorator to require specific permission level."""
        def decorator(func):
            @wraps(func)
            async def wrapper(interaction: discord.Interaction, *args, **kwargs):
                user_perm = self.get_user_permission(interaction.user)
                
                if user_perm.value < required.value:
                    await interaction.response.send_message(
                        "❌ No tienes permisos para usar este comando.",
                        ephemeral=True
                    )
                    return
                
                return await func(interaction, *args, **kwargs)
            
            return wrapper
        return decorator

# Usage
access_control = AccessControl()

@bot.tree.command(name="admin-command")
@access_control.require_permission(BotPermission.ADMIN)
async def admin_command(interaction: discord.Interaction):
    # Only admins can use this
    pass
```

### Channel Permissions

```python
async def check_channel_permissions(
    channel: discord.TextChannel,
    bot_user: discord.Member
) -> bool:
    """Verify bot has required permissions in channel."""
    required_permissions = discord.Permissions(
        send_messages=True,
        embed_links=True,
        read_message_history=True,
    )
    
    channel_permissions = channel.permissions_for(bot_user)
    
    if not channel_permissions.is_superset(required_permissions):
        missing = required_permissions - channel_permissions
        logger.warning(f"Missing permissions in {channel.name}: {missing}")
        return False
    
    return True
```

---

## Data Privacy and GDPR Compliance

### Data Collection Principles

1. **Data Minimization**
   - Only collect data necessary for bot functionality
   - Don't store message content unless required
   - Regularly purge unnecessary data

2. **Purpose Limitation**
   - Use data only for stated purposes
   - Document data usage in privacy policy
   - Obtain consent for secondary uses

### Personal Data Handling

```python
class UserDataManager:
    """Manage user data with privacy in mind."""
    
    def __init__(self):
        self.data_retention_days = 90
    
    def collect_user_data(self, user_id: int, data: dict):
        """
        Collect user data with consent.
        
        Log what data is collected and why.
        """
        logger.info(f"Collecting data for user {user_id}: {list(data.keys())}")
        # Store with timestamp
        data['collected_at'] = datetime.now().isoformat()
        # Store data...
    
    def purge_old_data(self):
        """Purge data older than retention period."""
        cutoff = datetime.now() - timedelta(days=self.data_retention_days)
        # Delete data older than cutoff
        logger.info(f"Purged data older than {cutoff}")
    
    def export_user_data(self, user_id: int) -> dict:
        """Export all data for a user (GDPR right to access)."""
        # Gather all data associated with user
        return {
            'user_id': user_id,
            'commands_used': [],
            'created_at': '',
            'last_activity': '',
        }
    
    def delete_user_data(self, user_id: int):
        """Delete all data for a user (GDPR right to erasure)."""
        logger.info(f"Deleting all data for user {user_id}")
        # Remove all user data from database
        # Anonymize logs containing user ID
```

### GDPR Compliance Commands

```python
@bot.tree.command(name="privacy")
async def privacy_command(interaction: discord.Interaction, action: str):
    """
    Handle GDPR data requests.
    
    Actions: export, delete, info
    """
    if action == "export":
        # Export user data
        data = data_manager.export_user_data(interaction.user.id)
        # Send data as DM
        await interaction.user.send(f"Your data: ```json\n{json.dumps(data, indent=2)}\n```")
        await interaction.response.send_message("✅ Datos enviados por mensaje directo.", ephemeral=True)
    
    elif action == "delete":
        # Confirm deletion
        await interaction.response.send_message(
            "⚠️ Esto eliminará todos tus datos. Usa `/privacy confirm-delete` para confirmar.",
            ephemeral=True
        )
    
    elif action == "info":
        # Explain data collection
        await interaction.response.send_message(
            "📊 Recopilamos: ID de usuario, comandos utilizados, marca de tiempo. "
            "No almacenamos mensajes ni datos personales adicionales.",
            ephemeral=True
        )
```

### Data Encryption

```python
from cryptography.fernet import Fernet
import base64
import os

class DataEncryption:
    """Encrypt sensitive data at rest."""
    
    def __init__(self):
        # Load encryption key from secure location
        key = os.getenv('ENCRYPTION_KEY')
        if not key:
            # Generate new key (only for first run)
            key = Fernet.generate_key()
            logger.warning("Generated new encryption key - store securely!")
        
        self.cipher = Fernet(key)
    
    def encrypt(self, data: str) -> str:
        """Encrypt data."""
        return self.cipher.encrypt(data.encode()).decode()
    
    def decrypt(self, encrypted_data: str) -> str:
        """Decrypt data."""
        return self.cipher.decrypt(encrypted_data.encode()).decode()
```

---

## Logging and Monitoring

### Secure Logging Practices

```python
import logging
from logging.handlers import RotatingFileHandler
import re

class SecureFormatter(logging.Formatter):
    """Logging formatter that redacts sensitive information."""
    
    # Patterns to redact
    SENSITIVE_PATTERNS = [
        (r'(DISCORD_BOT_TOKEN=)[^\s]+', r'\1[REDACTED]'),
        (r'(GITHUB_TOKEN=)[^\s]+', r'\1[REDACTED]'),
        (r'(Authorization: Bearer )[^\s]+', r'\1[REDACTED]'),
        (r'(["\']token["\']: ?["\'])[^"\']+', r'\1[REDACTED]'),
    ]
    
    def format(self, record):
        """Format log record and redact sensitive data."""
        message = super().format(record)
        
        for pattern, replacement in self.SENSITIVE_PATTERNS:
            message = re.sub(pattern, replacement, message)
        
        return message

def setup_logging():
    """Configure secure logging."""
    logger = logging.getLogger('discord_bot')
    logger.setLevel(logging.INFO)
    
    # Console handler
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(SecureFormatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    ))
    logger.addHandler(console_handler)
    
    # File handler with rotation
    file_handler = RotatingFileHandler(
        'bot.log',
        maxBytes=10*1024*1024,  # 10MB
        backupCount=5
    )
    file_handler.setFormatter(SecureFormatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    ))
    logger.addHandler(file_handler)
    
    return logger
```

### Security Event Logging

```python
class SecurityLogger:
    """Log security-relevant events."""
    
    def __init__(self, logger):
        self.logger = logger
    
    def log_auth_attempt(self, user_id: int, success: bool):
        """Log authentication attempts."""
        status = "SUCCESS" if success else "FAILURE"
        self.logger.warning(f"AUTH_{status}: user_id={user_id}")
    
    def log_rate_limit_hit(self, user_id: int, command: str):
        """Log rate limit violations."""
        self.logger.warning(f"RATE_LIMIT: user_id={user_id}, command={command}")
    
    def log_permission_denied(self, user_id: int, command: str, required_perm: str):
        """Log permission denials."""
        self.logger.warning(
            f"PERMISSION_DENIED: user_id={user_id}, command={command}, "
            f"required={required_perm}"
        )
    
    def log_suspicious_activity(self, user_id: int, reason: str):
        """Log suspicious activity."""
        self.logger.error(f"SUSPICIOUS: user_id={user_id}, reason={reason}")
```

### Monitoring Alerts

```python
class SecurityMonitor:
    """Monitor for security issues and send alerts."""
    
    def __init__(self, webhook_url: str):
        self.webhook_url = webhook_url
        self.alert_threshold = {
            'failed_auth': 5,
            'rate_limit_hits': 10,
            'permission_denials': 3,
        }
        self.counters = defaultdict(int)
    
    async def check_and_alert(self, event_type: str, user_id: int):
        """Check if threshold exceeded and send alert."""
        key = f"{event_type}:{user_id}"
        self.counters[key] += 1
        
        if self.counters[key] >= self.alert_threshold.get(event_type, 999):
            await self.send_alert(
                f"⚠️ Security Alert: {event_type} threshold exceeded for user {user_id}. "
                f"Count: {self.counters[key]}"
            )
            self.counters[key] = 0  # Reset counter
    
    async def send_alert(self, message: str):
        """Send alert to webhook."""
        # Send to Discord webhook, Slack, email, etc.
        logger.critical(f"SECURITY_ALERT: {message}")
```

---

## Incident Response

### Incident Response Plan

#### 1. Detection and Identification

**Indicators of Compromise:**
- Unusual command patterns
- Excessive API calls
- Authentication failures
- Unexpected errors
- Bot sending unexpected messages
- Permission escalation attempts

**Monitoring Tools:**
- Log aggregation (ELK stack, Splunk)
- Alerting system (PagerDuty, Opsgenie)
- Metrics dashboard (Grafana)

#### 2. Containment

**Immediate Actions:**

```python
class IncidentResponse:
    """Handle security incidents."""
    
    async def emergency_shutdown(self, reason: str):
        """Emergency bot shutdown."""
        logger.critical(f"EMERGENCY_SHUTDOWN: {reason}")
        
        # Notify admins
        await self.notify_admins(
            "🚨 Bot shutdown initiated",
            f"Reason: {reason}\nTime: {datetime.now()}"
        )
        
        # Graceful shutdown
        await bot.close()
        sys.exit(1)
    
    async def ban_user(self, user_id: int, reason: str):
        """Ban user from using bot."""
        logger.warning(f"BANNED_USER: {user_id}, reason: {reason}")
        # Add to ban list
        banned_users.add(user_id)
        
        # Notify user
        user = await bot.fetch_user(user_id)
        await user.send(
            f"Has sido bloqueado del bot. Razón: {reason}\n"
            "Contacta con un administrador si crees que esto es un error."
        )
    
    async def rotate_tokens(self):
        """Emergency token rotation procedure."""
        logger.critical("TOKEN_ROTATION: Initiating emergency rotation")
        
        # 1. Generate new tokens
        # 2. Update environment variables
        # 3. Restart bot with new credentials
        # 4. Revoke old tokens
        
        await self.notify_admins(
            "🔑 Token Rotation",
            "Emergency token rotation completed. Please verify bot functionality."
        )
```

#### 3. Investigation

**Data to Collect:**
- Full logs from incident timeframe
- User activity logs
- API call logs
- System metrics
- Network traffic logs (if available)

**Analysis Questions:**
- What was compromised?
- How did the breach occur?
- What data was accessed?
- Who was affected?
- What is the extent of the damage?

#### 4. Eradication

- Remove malicious code/backdoors
- Patch vulnerabilities
- Update dependencies
- Rotate all credentials
- Review and update access controls

#### 5. Recovery

```python
async def recovery_checklist():
    """Post-incident recovery steps."""
    steps = [
        "✓ Verify all credentials rotated",
        "✓ Confirm no unauthorized code changes",
        "✓ Test all bot functionality",
        "✓ Review and update security policies",
        "✓ Notify affected users (if applicable)",
        "✓ Update documentation with lessons learned",
        "✓ Resume normal operations",
    ]
    
    for step in steps:
        print(step)
```

#### 6. Lessons Learned

**Post-Incident Report Template:**

```markdown
# Security Incident Report

**Incident ID:** INC-YYYY-MM-DD-XXX
**Date:** YYYY-MM-DD
**Severity:** Critical / High / Medium / Low
**Status:** Resolved / Ongoing

## Summary
[Brief description of the incident]

## Timeline
- **HH:MM** - First indication of issue
- **HH:MM** - Incident confirmed
- **HH:MM** - Containment initiated
- **HH:MM** - Incident resolved

## Impact
- Users affected: X
- Data compromised: Yes/No
- Service downtime: X hours
- Financial impact: $X

## Root Cause
[Detailed analysis of what caused the incident]

## Response Actions
[What was done to address the incident]

## Lessons Learned
[What we learned and how to prevent recurrence]

## Action Items
- [ ] Update security policy
- [ ] Implement additional monitoring
- [ ] Train team on new procedures
- [ ] Schedule follow-up review
```

---

## Security Checklist

### Pre-Deployment Security Checklist

- [ ] **Authentication**
  - [ ] All tokens stored in environment variables
  - [ ] `.env` file added to `.gitignore`
  - [ ] File permissions restricted (600 for .env)
  - [ ] Token validation implemented
  - [ ] Different tokens for dev/staging/prod

- [ ] **Input Validation**
  - [ ] All user inputs validated
  - [ ] Input length limits enforced
  - [ ] Special characters sanitized
  - [ ] SQL/Command injection prevention
  - [ ] Output sanitization implemented

- [ ] **Rate Limiting**
  - [ ] Per-user rate limiting configured
  - [ ] Global rate limiting enabled
  - [ ] GitHub API rate limit handling
  - [ ] Anti-spam measures active

- [ ] **Access Control**
  - [ ] Role-based permissions implemented
  - [ ] Admin commands restricted
  - [ ] Channel permissions verified
  - [ ] Owner IDs configured

- [ ] **Data Privacy**
  - [ ] Data collection minimized
  - [ ] Privacy policy documented
  - [ ] GDPR compliance commands available
  - [ ] Data retention policy defined
  - [ ] Data encryption for sensitive info

- [ ] **Logging**
  - [ ] Secure logging configured
  - [ ] Sensitive data redacted from logs
  - [ ] Log rotation enabled
  - [ ] Security events logged
  - [ ] Monitoring alerts configured

- [ ] **Dependencies**
  - [ ] All dependencies up to date
  - [ ] Security vulnerabilities checked
  - [ ] Dependency pinning enabled
  - [ ] Regular update schedule defined

- [ ] **Deployment**
  - [ ] Running as non-root user
  - [ ] Firewall configured
  - [ ] HTTPS/TLS enforced
  - [ ] Backup procedures in place
  - [ ] Disaster recovery plan documented

### Regular Security Maintenance

**Weekly:**
- [ ] Review security logs
- [ ] Check for failed authentication attempts
- [ ] Monitor rate limit violations
- [ ] Verify backup integrity

**Monthly:**
- [ ] Update dependencies
- [ ] Review access control lists
- [ ] Audit user permissions
- [ ] Test incident response procedures

**Quarterly:**
- [ ] Rotate credentials
- [ ] Security policy review
- [ ] Penetration testing (if applicable)
- [ ] Team security training

**Annually:**
- [ ] Comprehensive security audit
- [ ] Third-party security assessment
- [ ] Disaster recovery drill
- [ ] Update security documentation

---

## Dependency Management

### Dependency Security

```bash
# Check for known vulnerabilities
pip install safety
safety check

# Audit dependencies
pip-audit

# Keep dependencies updated
pip list --outdated
pip install --upgrade package-name
```

### Requirements Pinning

```txt
# requirements.txt - Pin exact versions
discord.py==2.3.2
PyGithub==2.1.1
python-dotenv==1.0.0
cryptography==41.0.7

# Use hash checking for extra security
discord.py==2.3.2 \
    --hash=sha256:abc123...
```

### Automated Dependency Updates

Use Dependabot or Renovate:

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "pip"
    directory: "/tools/discord-bot"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5
    labels:
      - "dependencies"
      - "security"
```

---

## Deployment Security

### Running as Non-Root

```bash
# Create dedicated user
sudo useradd -r -s /bin/false discord-bot

# Set file ownership
sudo chown -R discord-bot:discord-bot /opt/discord-bot

# Run as dedicated user
sudo -u discord-bot python bot.py
```

### Systemd Service Security

```ini
[Unit]
Description=Discord Bot Service
After=network.target

[Service]
Type=simple
User=discord-bot
Group=discord-bot
WorkingDirectory=/opt/discord-bot
EnvironmentFile=/opt/discord-bot/.env

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/opt/discord-bot/logs

# Resource limits
MemoryLimit=512M
CPUQuota=50%

ExecStart=/usr/bin/python3 /opt/discord-bot/bot.py
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### Firewall Configuration

```bash
# Allow only necessary outbound connections
sudo ufw default deny incoming
sudo ufw default deny outgoing

# Allow Discord and GitHub APIs
sudo ufw allow out 443/tcp  # HTTPS

# Enable firewall
sudo ufw enable
```

### Environment Isolation

```bash
# Use virtual environment
python -m venv venv
source venv/bin/activate

# Install dependencies in isolation
pip install -r requirements.txt

# Deactivate when done
deactivate
```

---

## Conclusion

Security is an ongoing process, not a one-time task. Regularly review and update security measures, stay informed about new vulnerabilities, and always follow the principle of least privilege.

**Security Contact:**  
For security issues, please contact: [security@yourdomain.com]

**Last Updated:** 2026-06-18  
**Version:** 1.0.0
