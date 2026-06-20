# Discord Bot - HomeDir Community Manager

A Discord bot designed to help manage community interactions and GitHub issue tracking within Discord servers. The bot provides slash commands for users to get help and track their GitHub issues.

## Features

- **Slash Commands**: Modern Discord slash commands for intuitive user interaction
- **GitHub Integration**: Seamless integration with GitHub API for issue tracking
- **Bilingual Support**: Spanish user interface with English technical documentation
- **Rate Limiting**: Built-in protection against spam and abuse
- **Error Handling**: Comprehensive error handling with user-friendly messages
- **Logging**: Detailed logging for monitoring and debugging

## Discord Slash Commands

### `/ayuda` - Help Command
Displays comprehensive help information about available bot commands and features.

**Usage:**
```
/ayuda
```

**Response:** Provides a formatted embed with:
- List of available commands
- Brief description of each command
- How to use the bot effectively

### `/mis-issues` - My Issues Command
Retrieves and displays GitHub issues assigned to or created by the user.

**Usage:**
```
/mis-issues [username]
```

**Parameters:**
- `username` (optional): GitHub username to query. If not provided, the bot may use a configured default or prompt for input.

**Response:** Returns an embed containing:
- List of open issues
- Issue titles and numbers
- Direct links to GitHub issues
- Summary statistics

## Installation and Setup

### Prerequisites

- Python 3.8 or higher
- Discord bot token (from Discord Developer Portal)
- GitHub Personal Access Token (with repo access)
- A Discord server with permission to add bots

### Step 1: Clone or Navigate to the Repository

```bash
cd /path/to/homedir/tools/discord-bot
```

### Step 2: Install Dependencies

```bash
pip install -r requirements.txt
```

Required packages:
- `discord.py` - Discord API wrapper
- `PyGithub` - GitHub API wrapper
- `python-dotenv` - Environment variable management

### Step 3: Create Environment Configuration

Create a `.env` file in the bot directory:

```bash
# Discord Configuration
DISCORD_BOT_TOKEN=your_discord_bot_token_here
DISCORD_GUILD_ID=your_guild_id_here  # Optional, for guild-specific commands

# GitHub Configuration
GITHUB_TOKEN=your_github_personal_access_token
GITHUB_REPO_OWNER=your_organization_or_username
GITHUB_REPO_NAME=your_repository_name

# Bot Configuration (Optional)
BOT_PREFIX=!  # Fallback prefix for non-slash commands
LOG_LEVEL=INFO  # DEBUG, INFO, WARNING, ERROR, CRITICAL
COMMAND_COOLDOWN=5  # Seconds between command uses per user
```

### Step 4: Configure Discord Bot Permissions

Required Discord permissions:
- Send Messages
- Embed Links
- Read Message History
- Use Slash Commands
- View Channels

Recommended permission integer: `274877991936`

Bot invite URL format:
```
https://discord.com/api/oauth2/authorize?client_id=YOUR_CLIENT_ID&permissions=274877991936&scope=bot%20applications.commands
```

### Step 5: Run the Bot

#### Manual Execution
```bash
python bot.py
```

#### Using the Deployment Script
```bash
./deploy.sh
```

#### As a Systemd Service (Linux)
```bash
./deploy.sh --install-service
sudo systemctl start discord-bot
sudo systemctl enable discord-bot  # Auto-start on boot
```

### Step 6: Verify Setup

Run the test script to verify all configurations:

```bash
python test_setup.py
```

This will check:
- Environment variables
- Python dependencies
- GitHub API connectivity
- Discord token format validation

## Configuration Details

### Discord Bot Token

1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application or select existing
3. Navigate to "Bot" section
4. Click "Reset Token" to generate a new token
5. Copy the token to your `.env` file

**Security Note:** Never commit your bot token to version control!

### GitHub Personal Access Token

1. Go to GitHub Settings > Developer settings > Personal access tokens
2. Generate a new token (classic)
3. Select scopes:
   - `repo` - Full control of private repositories
   - `read:user` - Read user profile data
4. Copy the token to your `.env` file

**Token Expiration:** Consider using tokens with expiration dates and rotate them regularly.

### Guild-Specific Commands

To deploy commands to a specific guild (faster updates during development):
1. Enable Developer Mode in Discord (User Settings > Advanced)
2. Right-click your server and "Copy ID"
3. Add `DISCORD_GUILD_ID` to your `.env` file

**Production Tip:** Remove `DISCORD_GUILD_ID` to deploy commands globally (takes up to 1 hour).

## Architecture Overview

```
┌─────────────────┐
│  Discord User   │
└────────┬────────┘
         │ /ayuda or /mis-issues
         ▼
┌─────────────────────────┐
│   Discord.py Client     │
│  - Command Handler      │
│  - Event Listeners      │
│  - Rate Limiting        │
└──────────┬──────────────┘
           │
           ▼
┌──────────────────────────┐
│   Business Logic Layer   │
│  - Command Processing    │
│  - Data Formatting       │
│  - Error Handling        │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│   GitHub API Client      │
│  - Issue Queries         │
│  - Authentication        │
│  - Response Parsing      │
└──────────────────────────┘
```

### Key Components

1. **Bot Client (`bot.py`)**: Main entry point, handles Discord connection and command registration
2. **Command Handlers**: Process slash commands and generate responses
3. **GitHub Integration**: Interfaces with GitHub API for issue management
4. **Error Handler**: Centralized error handling and logging
5. **Rate Limiter**: Prevents spam and API abuse

See [ARCHITECTURE.md](./ARCHITECTURE.md) for detailed technical architecture.

## Security Considerations Summary

### Critical Security Practices

1. **Token Management**
   - Store tokens in `.env` file (never in code)
   - Add `.env` to `.gitignore`
   - Use environment-specific tokens (dev/prod)
   - Rotate tokens regularly

2. **Input Validation**
   - Validate all user inputs
   - Sanitize data before API calls
   - Implement length limits on inputs

3. **Rate Limiting**
   - Per-user cooldowns on commands
   - Global rate limiting for API calls
   - Implement exponential backoff for retries

4. **Access Control**
   - Verify user permissions before executing commands
   - Implement role-based access control (RBAC)
   - Log all administrative actions

5. **Data Privacy**
   - Minimize data collection
   - Don't store sensitive user data
   - Comply with GDPR and data protection laws
   - Provide data deletion mechanisms

For comprehensive security guidelines, see [SECURITY.md](./SECURITY.md).

## Monitoring and Logging

### Log Levels

- **DEBUG**: Detailed diagnostic information
- **INFO**: General informational messages (default)
- **WARNING**: Warning messages for potential issues
- **ERROR**: Error messages for failures
- **CRITICAL**: Critical failures requiring immediate attention

### Log Output

Logs are written to:
- Console (stdout/stderr)
- Optional log file (configure via environment variable)

### Monitoring Metrics

Key metrics to monitor:
- Command usage frequency
- API response times
- Error rates
- Rate limit hits
- Active user count

## Scalability and Next Steps

### Current Limitations

- Single-instance deployment (no horizontal scaling)
- In-memory rate limiting (doesn't persist across restarts)
- Synchronous command processing

### Recommended Improvements

#### Short-term (1-3 months)
- [ ] Add caching layer (Redis) for GitHub API responses
- [ ] Implement database for persistent storage (PostgreSQL)
- [ ] Add health check endpoint
- [ ] Enhance error messages with suggested actions
- [ ] Add more slash commands (e.g., `/create-issue`, `/search-issues`)

#### Medium-term (3-6 months)
- [ ] Multi-server support with isolated configurations
- [ ] Add webhooks for GitHub events (issue updates, comments)
- [ ] Implement admin dashboard for bot management
- [ ] Add analytics and usage statistics
- [ ] Support for multiple GitHub repositories

#### Long-term (6-12 months)
- [ ] Horizontal scaling with load balancing
- [ ] Message queue integration (RabbitMQ/Kafka)
- [ ] ML-powered issue categorization
- [ ] Integration with other platforms (Jira, Slack, etc.)
- [ ] Plugin system for custom commands

### Performance Optimization

1. **Caching Strategy**
   - Cache GitHub API responses (5-15 minute TTL)
   - Use Discord's built-in caching
   - Implement LRU cache for frequently accessed data

2. **Database Optimization**
   - Index frequently queried fields
   - Use connection pooling
   - Implement read replicas for scaling

3. **Async Operations**
   - Already using async/await with discord.py
   - Consider background task queue for long-running operations
   - Implement webhook listeners instead of polling

## Troubleshooting

### Common Issues

**Bot doesn't respond to commands:**
- Verify bot is online in Discord
- Check bot has proper permissions in the channel
- Ensure slash commands are synced (may take up to 1 hour for global commands)
- Check logs for error messages

**GitHub API errors:**
- Verify GitHub token is valid and not expired
- Check token has required scopes (repo access)
- Ensure repository name and owner are correct
- Check GitHub API rate limits

**Permission errors:**
- Verify bot has required Discord permissions
- Check channel-specific permission overrides
- Ensure bot role is high enough in role hierarchy

**Rate limiting issues:**
- Reduce command usage frequency
- Implement exponential backoff
- Consider increasing cooldown timers

### Getting Help

1. Check logs for detailed error messages
2. Run `python test_setup.py` to verify configuration
3. Review [ARCHITECTURE.md](./ARCHITECTURE.md) for system understanding
4. Check [SECURITY.md](./SECURITY.md) for security-related issues
5. Create an issue in the GitHub repository

## Development

### Running Tests

```bash
# Run setup validation
python test_setup.py

# Run unit tests (if implemented)
pytest tests/

# Run with debug logging
LOG_LEVEL=DEBUG python bot.py
```

### Code Style

- Follow PEP 8 guidelines
- Use type hints where applicable
- Document functions with docstrings
- Keep functions focused and single-purpose

### Contributing

1. Create a feature branch
2. Make your changes
3. Test thoroughly
4. Submit a pull request
5. Ensure all checks pass

## License

[Specify your license here]

## Support

For support, please:
- Check the troubleshooting section above
- Review the documentation files
- Create an issue in the repository
- Contact the development team

---

**Version:** 1.0.0  
**Last Updated:** 2026-06-18  
**Maintainer:** HomeDir Team
