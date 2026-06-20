#!/usr/bin/env bash
set -euo pipefail

#############################################################################
# Discord Bot Deployment Script
#
# This script handles the deployment of the Discord bot, including:
# - Dependency installation
# - Environment variable validation
# - Optional systemd service creation
# - Bot startup
#
# Usage:
#   ./deploy.sh                    # Basic deployment
#   ./deploy.sh --install-service  # Install as systemd service
#   ./deploy.sh --dev              # Development mode (no service)
#   ./deploy.sh --help             # Show help
#
# Author: HomeDir Team
# Version: 1.0.0
#############################################################################

# Color codes for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Default configuration
INSTALL_SERVICE=false
DEV_MODE=false
SKIP_DEPS=false
VENV_DIR="venv"
SERVICE_NAME="discord-bot"
SERVICE_USER="${SERVICE_USER:-discord-bot}"

#############################################################################
# Helper Functions
#############################################################################

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

show_help() {
    cat << EOF
Discord Bot Deployment Script

Usage: ./deploy.sh [OPTIONS]

Options:
    --install-service    Install bot as systemd service (requires sudo)
    --dev                Development mode (run in foreground)
    --skip-deps          Skip dependency installation
    --help               Show this help message

Examples:
    ./deploy.sh                      # Basic deployment
    ./deploy.sh --install-service    # Install as service
    ./deploy.sh --dev                # Development mode

Environment Variables:
    SERVICE_USER         User to run the service (default: discord-bot)

EOF
    exit 0
}

check_python() {
    log_info "Checking Python installation..."

    if ! command -v python3 &> /dev/null; then
        log_error "Python 3 is not installed. Please install Python 3.8 or higher."
        exit 1
    fi

    # Check Python version
    python_version=$(python3 --version | cut -d' ' -f2)
    python_major=$(echo "$python_version" | cut -d'.' -f1)
    python_minor=$(echo "$python_version" | cut -d'.' -f2)

    if [[ "$python_major" -lt 3 ]] || [[ "$python_major" -eq 3 && "$python_minor" -lt 8 ]]; then
        log_error "Python 3.8 or higher is required. Found: $python_version"
        exit 1
    fi

    log_success "Python $python_version detected"
}

create_virtualenv() {
    log_info "Setting up Python virtual environment..."

    if [[ -d "$VENV_DIR" ]]; then
        log_warning "Virtual environment already exists at $VENV_DIR"
        read -p "Recreate it? (y/N): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            rm -rf "$VENV_DIR"
            python3 -m venv "$VENV_DIR"
            log_success "Virtual environment recreated"
        fi
    else
        python3 -m venv "$VENV_DIR"
        log_success "Virtual environment created"
    fi
}

install_dependencies() {
    if [[ "$SKIP_DEPS" == "true" ]]; then
        log_info "Skipping dependency installation"
        return
    fi

    log_info "Installing dependencies..."

    # Activate virtual environment
    # shellcheck source=/dev/null
    source "$VENV_DIR/bin/activate"

    # Upgrade pip
    pip install --upgrade pip setuptools wheel

    # Check if requirements.txt exists
    if [[ ! -f "requirements.txt" ]]; then
        log_warning "requirements.txt not found. Creating minimal requirements..."
        cat > requirements.txt << 'EOF'
discord.py>=2.3.2
PyGithub>=2.1.1
python-dotenv>=1.0.0
EOF
    fi

    # Install requirements
    pip install -r requirements.txt

    log_success "Dependencies installed successfully"
}

validate_environment() {
    log_info "Validating environment variables..."

    if [[ ! -f ".env" ]]; then
        log_error ".env file not found!"
        log_info "Creating .env template..."

        cat > .env << 'EOF'
# Discord Configuration
DISCORD_BOT_TOKEN=your_discord_bot_token_here
DISCORD_GUILD_ID=  # Optional: Guild ID for faster command sync during development

# GitHub Configuration
GITHUB_TOKEN=your_github_personal_access_token
GITHUB_REPO_OWNER=your_organization_or_username
GITHUB_REPO_NAME=your_repository_name

# Bot Configuration (Optional)
BOT_PREFIX=!
LOG_LEVEL=INFO
COMMAND_COOLDOWN=5

# Service Configuration (Optional)
SERVICE_USER=discord-bot
EOF

        log_warning ".env template created. Please edit it with your credentials."
        log_error "Deployment cannot continue without valid credentials."
        exit 1
    fi

    # Source .env file
    set -a
    # shellcheck source=/dev/null
    source .env
    set +a

    # Validate required variables
    local required_vars=(
        "DISCORD_BOT_TOKEN"
        "GITHUB_TOKEN"
    )

    local missing_vars=()

    for var in "${required_vars[@]}"; do
        if [[ -z "${!var:-}" ]] || [[ "${!var}" == *"your_"* ]]; then
            missing_vars+=("$var")
        fi
    done

    if [[ ${#missing_vars[@]} -gt 0 ]]; then
        log_error "Missing or invalid environment variables:"
        for var in "${missing_vars[@]}"; do
            echo "  - $var"
        done
        log_info "Please edit .env file with valid credentials"
        exit 1
    fi

    log_success "Environment variables validated"
}

create_systemd_service() {
    log_info "Creating systemd service..."

    # Check if running as root or with sudo
    if [[ $EUID -ne 0 ]]; then
        log_error "Installing systemd service requires sudo privileges"
        log_info "Please run: sudo ./deploy.sh --install-service"
        exit 1
    fi

    # Create service user if it doesn't exist
    if ! id "$SERVICE_USER" &>/dev/null; then
        log_info "Creating service user: $SERVICE_USER"
        useradd -r -s /bin/false "$SERVICE_USER"
    fi

    # Set ownership
    chown -R "$SERVICE_USER:$SERVICE_USER" "$SCRIPT_DIR"

    # Protect .env file
    chmod 600 .env

    # Create systemd service file
    local service_file="/etc/systemd/system/${SERVICE_NAME}.service"

    cat > "$service_file" << EOF
[Unit]
Description=Discord Bot Service
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_USER
WorkingDirectory=$SCRIPT_DIR
EnvironmentFile=$SCRIPT_DIR/.env

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$SCRIPT_DIR/logs

# Resource limits
MemoryLimit=512M
CPUQuota=50%

# Restart policy
Restart=on-failure
RestartSec=10
StartLimitBurst=5
StartLimitIntervalSec=300

# Execution
ExecStart=$SCRIPT_DIR/$VENV_DIR/bin/python $SCRIPT_DIR/bot.py

# Logging
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$SERVICE_NAME

[Install]
WantedBy=multi-user.target
EOF

    # Reload systemd
    systemctl daemon-reload

    log_success "Systemd service created: $service_file"
    log_info "To manage the service:"
    echo "  Start:   sudo systemctl start $SERVICE_NAME"
    echo "  Stop:    sudo systemctl stop $SERVICE_NAME"
    echo "  Restart: sudo systemctl restart $SERVICE_NAME"
    echo "  Status:  sudo systemctl status $SERVICE_NAME"
    echo "  Enable:  sudo systemctl enable $SERVICE_NAME  # Auto-start on boot"
    echo "  Logs:    sudo journalctl -u $SERVICE_NAME -f"
}

create_log_directory() {
    log_info "Creating log directory..."

    mkdir -p logs

    # Set permissions if running as root
    if [[ $EUID -eq 0 ]] && id "$SERVICE_USER" &>/dev/null; then
        chown "$SERVICE_USER:$SERVICE_USER" logs
    fi

    log_success "Log directory created"
}

check_bot_file() {
    log_info "Checking for bot.py..."

    if [[ ! -f "bot.py" ]]; then
        log_warning "bot.py not found!"
        log_info "Creating minimal bot.py template..."

        cat > bot.py << 'EOF'
#!/usr/bin/env python3
"""
Discord Bot - HomeDir Community Manager

A Discord bot for managing GitHub issues and community interactions.
"""

import os
import logging
from dotenv import load_dotenv
import discord
from discord import app_commands
from github import Github, GithubException

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(
    level=os.getenv('LOG_LEVEL', 'INFO'),
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('logs/bot.log')
    ]
)
logger = logging.getLogger('discord_bot')

# Discord bot setup
intents = discord.Intents.default()
intents.message_content = True

bot = discord.Client(intents=intents)
tree = app_commands.CommandTree(bot)

# GitHub client setup
github_token = os.getenv('GITHUB_TOKEN')
github_client = Github(github_token) if github_token else None

@bot.event
async def on_ready():
    """Called when bot is ready."""
    logger.info(f'Bot connected as {bot.user}')

    # Sync commands
    try:
        guild_id = os.getenv('DISCORD_GUILD_ID')
        if guild_id:
            guild = discord.Object(id=int(guild_id))
            tree.copy_global_to(guild=guild)
            await tree.sync(guild=guild)
            logger.info(f'Commands synced to guild {guild_id}')
        else:
            await tree.sync()
            logger.info('Commands synced globally')
    except Exception as e:
        logger.error(f'Failed to sync commands: {e}')

@tree.command(name="ayuda", description="Muestra información de ayuda")
async def ayuda_command(interaction: discord.Interaction):
    """Help command."""
    embed = discord.Embed(
        title="🤖 Ayuda del Bot",
        description="Comandos disponibles:",
        color=discord.Color.blue()
    )

    embed.add_field(
        name="/ayuda",
        value="Muestra esta información de ayuda",
        inline=False
    )

    embed.add_field(
        name="/mis-issues [username]",
        value="Muestra tus issues de GitHub",
        inline=False
    )

    embed.set_footer(text="HomeDir Community Bot")

    await interaction.response.send_message(embed=embed)

@tree.command(name="mis-issues", description="Muestra tus issues de GitHub")
async def mis_issues_command(
    interaction: discord.Interaction,
    username: str = None
):
    """GitHub issues command."""
    if not github_client:
        await interaction.response.send_message(
            "❌ GitHub integration not configured",
            ephemeral=True
        )
        return

    if not username:
        await interaction.response.send_message(
            "❌ Por favor proporciona un nombre de usuario de GitHub",
            ephemeral=True
        )
        return

    await interaction.response.defer()

    try:
        user = github_client.get_user(username)
        issues = list(user.get_issues(state='open'))[:10]

        if not issues:
            await interaction.followup.send(
                f"✅ {username} no tiene issues abiertas"
            )
            return

        embed = discord.Embed(
            title=f"📋 Issues de {username}",
            color=discord.Color.green()
        )

        for issue in issues:
            embed.add_field(
                name=f"#{issue.number} - {issue.title[:50]}",
                value=f"[Ver issue]({issue.html_url})",
                inline=False
            )

        embed.set_footer(text=f"Total: {len(issues)} issues")

        await interaction.followup.send(embed=embed)

    except GithubException as e:
        logger.error(f"GitHub API error: {e}")
        await interaction.followup.send(
            "❌ Error al obtener issues de GitHub",
            ephemeral=True
        )
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        await interaction.followup.send(
            "❌ Error inesperado",
            ephemeral=True
        )

def main():
    """Main entry point."""
    token = os.getenv('DISCORD_BOT_TOKEN')

    if not token:
        logger.error("DISCORD_BOT_TOKEN not found in environment")
        return

    try:
        bot.run(token)
    except Exception as e:
        logger.error(f"Failed to start bot: {e}")

if __name__ == '__main__':
    main()
EOF

        chmod +x bot.py
        log_success "bot.py template created"
    else
        log_success "bot.py found"
    fi
}

start_bot() {
    log_info "Starting Discord bot..."

    # Activate virtual environment
    # shellcheck source=/dev/null
    source "$VENV_DIR/bin/activate"

    if [[ "$DEV_MODE" == "true" ]]; then
        log_info "Running in development mode (foreground)"
        python bot.py
    else
        log_info "Starting bot in background..."
        nohup python bot.py > logs/bot.out 2>&1 &
        local bot_pid=$!
        echo "$bot_pid" > logs/bot.pid
        log_success "Bot started with PID: $bot_pid"
        log_info "Logs: tail -f logs/bot.out"
        log_info "Stop: kill \$(cat logs/bot.pid)"
    fi
}

#############################################################################
# Main Deployment Flow
#############################################################################

main() {
    echo "======================================"
    echo "  Discord Bot Deployment Script"
    echo "======================================"
    echo

    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --install-service)
                INSTALL_SERVICE=true
                shift
                ;;
            --dev)
                DEV_MODE=true
                shift
                ;;
            --skip-deps)
                SKIP_DEPS=true
                shift
                ;;
            --help)
                show_help
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                ;;
        esac
    done

    # Deployment steps
    check_python
    check_bot_file
    create_virtualenv
    install_dependencies
    validate_environment
    create_log_directory

    if [[ "$INSTALL_SERVICE" == "true" ]]; then
        create_systemd_service
        log_info "Service installed. Start it with: sudo systemctl start $SERVICE_NAME"
    else
        start_bot
    fi

    echo
    log_success "Deployment completed successfully!"
    echo
}

# Run main function
main "$@"
