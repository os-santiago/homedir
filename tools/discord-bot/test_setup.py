#!/usr/bin/env python3
"""
Discord Bot Setup Validation Script

This script validates the bot's environment and configuration:
- Environment variable checks
- Python dependency validation
- GitHub API connection test
- Discord token format validation
- Comprehensive test summary

Usage:
    python test_setup.py
    python test_setup.py --verbose
    python test_setup.py --fix  # Attempt to fix issues

Author: HomeDir Team
Version: 1.0.0
"""

import os
import sys
import re
import argparse
from typing import Dict, List, Tuple, Optional
from pathlib import Path

# ANSI color codes
class Colors:
    """ANSI color codes for terminal output."""
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    BLUE = '\033[0;34m'
    MAGENTA = '\033[0;35m'
    CYAN = '\033[0;36m'
    WHITE = '\033[1;37m'
    NC = '\033[0m'  # No Color
    BOLD = '\033[1m'


def print_header(text: str):
    """Print a formatted header."""
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*70}{Colors.NC}")
    print(f"{Colors.BOLD}{Colors.BLUE}{text.center(70)}{Colors.NC}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*70}{Colors.NC}\n")


def print_section(text: str):
    """Print a section header."""
    print(f"\n{Colors.CYAN}{Colors.BOLD}[{text}]{Colors.NC}")


def print_success(text: str):
    """Print success message."""
    print(f"{Colors.GREEN}✓{Colors.NC} {text}")


def print_error(text: str):
    """Print error message."""
    print(f"{Colors.RED}✗{Colors.NC} {text}")


def print_warning(text: str):
    """Print warning message."""
    print(f"{Colors.YELLOW}⚠{Colors.NC} {text}")


def print_info(text: str):
    """Print info message."""
    print(f"{Colors.BLUE}ℹ{Colors.NC} {text}")


class SetupValidator:
    """Validates Discord bot setup and configuration."""

    def __init__(self, verbose: bool = False, fix: bool = False):
        """
        Initialize the validator.

        Args:
            verbose: Enable verbose output
            fix: Attempt to fix issues automatically
        """
        self.verbose = verbose
        self.fix = fix
        self.results = {
            'passed': [],
            'warnings': [],
            'failed': []
        }

    def add_result(self, category: str, test_name: str, details: str = ""):
        """Add a test result."""
        self.results[category].append((test_name, details))

    def check_python_version(self) -> bool:
        """Check Python version is 3.8 or higher."""
        print_section("Python Version Check")

        major = sys.version_info.major
        minor = sys.version_info.minor
        patch = sys.version_info.micro

        version_str = f"{major}.{minor}.{patch}"

        if major >= 3 and minor >= 8:
            print_success(f"Python {version_str} (meets requirement: 3.8+)")
            self.add_result('passed', 'Python Version', version_str)
            return True
        else:
            print_error(f"Python {version_str} (requires 3.8+)")
            self.add_result('failed', 'Python Version', f"{version_str} < 3.8")
            return False

    def check_env_file(self) -> bool:
        """Check if .env file exists."""
        print_section("Environment File Check")

        env_path = Path('.env')

        if env_path.exists():
            print_success(".env file found")
            self.add_result('passed', '.env file', 'exists')

            # Check permissions
            if os.name != 'nt':  # Unix-like systems
                mode = oct(env_path.stat().st_mode)[-3:]
                if mode == '600':
                    print_success(f"File permissions are secure ({mode})")
                    self.add_result('passed', '.env permissions', mode)
                else:
                    print_warning(f"File permissions are {mode}, should be 600")
                    self.add_result('warnings', '.env permissions', f"{mode} (should be 600)")

                    if self.fix and os.name != 'nt':
                        try:
                            os.chmod('.env', 0o600)
                            print_info("Fixed: Set permissions to 600")
                        except Exception as e:
                            print_error(f"Failed to fix permissions: {e}")

            return True
        else:
            print_error(".env file not found")
            self.add_result('failed', '.env file', 'not found')

            if self.fix:
                self._create_env_template()

            return False

    def _create_env_template(self):
        """Create .env template file."""
        print_info("Creating .env template...")

        template = """# Discord Configuration
DISCORD_BOT_TOKEN=your_discord_bot_token_here
DISCORD_GUILD_ID=  # Optional: Guild ID for faster command sync

# GitHub Configuration
GITHUB_TOKEN=your_github_personal_access_token
GITHUB_REPO_OWNER=your_organization_or_username
GITHUB_REPO_NAME=your_repository_name

# Bot Configuration (Optional)
BOT_PREFIX=!
LOG_LEVEL=INFO
COMMAND_COOLDOWN=5
"""

        try:
            with open('.env', 'w') as f:
                f.write(template)
            print_success(".env template created")

            if os.name != 'nt':
                os.chmod('.env', 0o600)
                print_success("File permissions set to 600")
        except Exception as e:
            print_error(f"Failed to create .env template: {e}")

    def check_environment_variables(self) -> Dict[str, bool]:
        """Check required environment variables."""
        print_section("Environment Variables Check")

        # Load .env file
        try:
            from dotenv import load_dotenv
            load_dotenv()
        except ImportError:
            print_warning("python-dotenv not installed, skipping .env loading")

        required_vars = {
            'DISCORD_BOT_TOKEN': r'^[A-Za-z0-9_-]{50,}',
            'GITHUB_TOKEN': r'^(ghp_|github_pat_)[A-Za-z0-9_]{32,}',
        }

        optional_vars = {
            'DISCORD_GUILD_ID': r'^\d+$',
            'GITHUB_REPO_OWNER': r'^[a-zA-Z0-9-]+$',
            'GITHUB_REPO_NAME': r'^[a-zA-Z0-9_.-]+$',
            'LOG_LEVEL': r'^(DEBUG|INFO|WARNING|ERROR|CRITICAL)$',
        }

        results = {}

        # Check required variables
        for var_name, pattern in required_vars.items():
            value = os.getenv(var_name, '')

            if not value or 'your_' in value:
                print_error(f"{var_name}: Not set or using placeholder")
                self.add_result('failed', var_name, 'not configured')
                results[var_name] = False
            elif not re.match(pattern, value):
                print_warning(f"{var_name}: Set but format appears invalid")
                self.add_result('warnings', var_name, 'invalid format')
                results[var_name] = False
            else:
                # Mask the value for security
                masked = value[:8] + '*' * (len(value) - 12) + value[-4:]
                print_success(f"{var_name}: Set ({masked})")
                self.add_result('passed', var_name, 'configured')
                results[var_name] = True

        # Check optional variables
        for var_name, pattern in optional_vars.items():
            value = os.getenv(var_name, '')

            if not value:
                print_info(f"{var_name}: Not set (optional)")
                continue

            if not re.match(pattern, value):
                print_warning(f"{var_name}: Set but format appears invalid")
                self.add_result('warnings', var_name, 'invalid format')
                results[var_name] = False
            else:
                print_success(f"{var_name}: Set ({value})")
                self.add_result('passed', var_name, 'configured')
                results[var_name] = True

        return results

    def check_dependencies(self) -> Dict[str, bool]:
        """Check Python dependencies are installed."""
        print_section("Python Dependencies Check")

        required_packages = {
            'discord': 'discord.py',
            'github': 'PyGithub',
            'dotenv': 'python-dotenv',
        }

        optional_packages = {
            'aiohttp': 'aiohttp',
            'requests': 'requests',
        }

        results = {}

        # Check required packages
        for import_name, package_name in required_packages.items():
            try:
                module = __import__(import_name)
                version = getattr(module, '__version__', 'unknown')
                print_success(f"{package_name}: Installed (version {version})")
                self.add_result('passed', package_name, version)
                results[package_name] = True
            except ImportError:
                print_error(f"{package_name}: Not installed")
                self.add_result('failed', package_name, 'not installed')
                results[package_name] = False

                if self.fix:
                    print_info(f"Install with: pip install {package_name}")

        # Check optional packages
        for import_name, package_name in optional_packages.items():
            try:
                module = __import__(import_name)
                version = getattr(module, '__version__', 'unknown')
                print_success(f"{package_name}: Installed (version {version})")
                self.add_result('passed', package_name, version)
            except ImportError:
                print_info(f"{package_name}: Not installed (optional)")

        return results

    def check_github_connection(self) -> bool:
        """Test GitHub API connection."""
        print_section("GitHub API Connection Test")

        try:
            from github import Github, GithubException
        except ImportError:
            print_error("PyGithub not installed, skipping GitHub test")
            self.add_result('failed', 'GitHub Connection', 'PyGithub not installed')
            return False

        token = os.getenv('GITHUB_TOKEN', '')

        if not token or 'your_' in token:
            print_error("GITHUB_TOKEN not configured")
            self.add_result('failed', 'GitHub Connection', 'token not configured')
            return False

        try:
            g = Github(token)
            user = g.get_user()

            print_success(f"Connected to GitHub as: {user.login}")
            print_info(f"Name: {user.name or 'N/A'}")
            print_info(f"Public repos: {user.public_repos}")

            # Check rate limit
            rate_limit = g.get_rate_limit()
            core_remaining = rate_limit.core.remaining
            core_limit = rate_limit.core.limit

            if core_remaining > 100:
                print_success(f"Rate limit: {core_remaining}/{core_limit} remaining")
            else:
                print_warning(f"Rate limit low: {core_remaining}/{core_limit} remaining")

            self.add_result('passed', 'GitHub Connection', f'authenticated as {user.login}')
            return True

        except GithubException as e:
            print_error(f"GitHub API error: {e.data.get('message', str(e))}")
            self.add_result('failed', 'GitHub Connection', str(e))
            return False
        except Exception as e:
            print_error(f"Connection error: {e}")
            self.add_result('failed', 'GitHub Connection', str(e))
            return False

    def check_discord_token(self) -> bool:
        """Validate Discord token format."""
        print_section("Discord Token Validation")

        token = os.getenv('DISCORD_BOT_TOKEN', '')

        if not token or 'your_' in token:
            print_error("DISCORD_BOT_TOKEN not configured")
            self.add_result('failed', 'Discord Token', 'not configured')
            return False

        # Basic token format validation
        parts = token.split('.')

        if len(parts) != 3:
            print_error("Token format invalid (should have 3 parts separated by dots)")
            self.add_result('failed', 'Discord Token', 'invalid format')
            return False

        # Check each part is base64-like
        for i, part in enumerate(parts):
            if not re.match(r'^[A-Za-z0-9_-]+$', part):
                print_error(f"Token part {i+1} has invalid characters")
                self.add_result('failed', 'Discord Token', f'part {i+1} invalid')
                return False

        print_success("Token format appears valid")
        print_info(f"Token ID (first part): {parts[0]}")
        self.add_result('passed', 'Discord Token', 'format valid')

        print_warning("Note: Format validation doesn't guarantee the token is active")
        print_info("Run the bot to verify the token works with Discord API")

        return True

    def check_bot_file(self) -> bool:
        """Check if bot.py exists and is valid."""
        print_section("Bot File Check")

        bot_path = Path('bot.py')

        if not bot_path.exists():
            print_error("bot.py not found")
            self.add_result('failed', 'bot.py', 'not found')
            return False

        print_success("bot.py exists")

        # Check if executable (Unix-like systems)
        if os.name != 'nt':
            if os.access(bot_path, os.X_OK):
                print_success("bot.py is executable")
                self.add_result('passed', 'bot.py', 'executable')
            else:
                print_warning("bot.py is not executable")
                self.add_result('warnings', 'bot.py', 'not executable')

                if self.fix:
                    try:
                        os.chmod('bot.py', 0o755)
                        print_info("Fixed: Made bot.py executable")
                    except Exception as e:
                        print_error(f"Failed to make executable: {e}")

        # Basic syntax check
        try:
            with open('bot.py', 'r') as f:
                content = f.read()

            # Check for required imports
            required_imports = ['discord', 'github', 'dotenv']
            missing_imports = []

            for imp in required_imports:
                if f"import {imp}" not in content and f"from {imp}" not in content:
                    missing_imports.append(imp)

            if missing_imports:
                print_warning(f"Missing imports: {', '.join(missing_imports)}")
                self.add_result('warnings', 'bot.py imports', f"missing: {', '.join(missing_imports)}")
            else:
                print_success("All required imports present")
                self.add_result('passed', 'bot.py imports', 'complete')

        except Exception as e:
            print_error(f"Failed to read bot.py: {e}")
            self.add_result('failed', 'bot.py', f'read error: {e}')
            return False

        return True

    def check_directory_structure(self) -> bool:
        """Check directory structure is correct."""
        print_section("Directory Structure Check")

        required_files = ['bot.py', '.env']
        recommended_files = ['requirements.txt', 'deploy.sh', 'test_setup.py']
        required_dirs = ['logs']

        all_good = True

        # Check required files
        for filename in required_files:
            if Path(filename).exists():
                print_success(f"Required file exists: {filename}")
                self.add_result('passed', f'file:{filename}', 'exists')
            else:
                print_error(f"Required file missing: {filename}")
                self.add_result('failed', f'file:{filename}', 'missing')
                all_good = False

        # Check recommended files
        for filename in recommended_files:
            if Path(filename).exists():
                print_success(f"Recommended file exists: {filename}")
                self.add_result('passed', f'file:{filename}', 'exists')
            else:
                print_info(f"Recommended file missing: {filename}")

        # Check/create required directories
        for dirname in required_dirs:
            dir_path = Path(dirname)
            if dir_path.exists():
                print_success(f"Directory exists: {dirname}/")
                self.add_result('passed', f'dir:{dirname}', 'exists')
            else:
                print_warning(f"Directory missing: {dirname}/")

                if self.fix:
                    try:
                        dir_path.mkdir(parents=True, exist_ok=True)
                        print_info(f"Created directory: {dirname}/")
                        self.add_result('passed', f'dir:{dirname}', 'created')
                    except Exception as e:
                        print_error(f"Failed to create {dirname}/: {e}")
                        self.add_result('failed', f'dir:{dirname}', str(e))
                        all_good = False

        return all_good

    def print_summary(self):
        """Print test summary."""
        print_header("Test Summary")

        total_tests = sum(len(v) for v in self.results.values())
        passed = len(self.results['passed'])
        warnings = len(self.results['warnings'])
        failed = len(self.results['failed'])

        # Calculate percentage
        if total_tests > 0:
            success_rate = (passed / total_tests) * 100
        else:
            success_rate = 0

        # Summary statistics
        print(f"\n{Colors.BOLD}Total Tests:{Colors.NC} {total_tests}")
        print(f"{Colors.GREEN}Passed:{Colors.NC} {passed}")
        print(f"{Colors.YELLOW}Warnings:{Colors.NC} {warnings}")
        print(f"{Colors.RED}Failed:{Colors.NC} {failed}")
        print(f"{Colors.BOLD}Success Rate:{Colors.NC} {success_rate:.1f}%\n")

        # Show failures
        if failed > 0:
            print(f"\n{Colors.RED}{Colors.BOLD}Failed Tests:{Colors.NC}")
            for test_name, details in self.results['failed']:
                print(f"  {Colors.RED}✗{Colors.NC} {test_name}")
                if details and self.verbose:
                    print(f"    {Colors.WHITE}→ {details}{Colors.NC}")

        # Show warnings
        if warnings > 0:
            print(f"\n{Colors.YELLOW}{Colors.BOLD}Warnings:{Colors.NC}")
            for test_name, details in self.results['warnings']:
                print(f"  {Colors.YELLOW}⚠{Colors.NC} {test_name}")
                if details and self.verbose:
                    print(f"    {Colors.WHITE}→ {details}{Colors.NC}")

        # Overall status
        print()
        if failed == 0:
            if warnings == 0:
                print_success("All tests passed! Your setup is ready.")
                print_info("Start the bot with: python bot.py")
            else:
                print_success("Setup is functional but has some warnings.")
                print_info("Review warnings above and consider fixing them.")
        else:
            print_error("Setup has critical issues that must be fixed.")
            print_info("Fix the failed tests and run this script again.")

        print()

        # Return exit code
        return 0 if failed == 0 else 1

    def run_all_checks(self) -> int:
        """Run all validation checks."""
        print_header("Discord Bot Setup Validation")

        self.check_python_version()
        self.check_directory_structure()
        self.check_env_file()
        self.check_environment_variables()
        self.check_dependencies()
        self.check_bot_file()
        self.check_github_connection()
        self.check_discord_token()

        return self.print_summary()


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description='Validate Discord bot setup and configuration'
    )
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help='Enable verbose output'
    )
    parser.add_argument(
        '--fix',
        action='store_true',
        help='Attempt to fix issues automatically'
    )

    args = parser.parse_args()

    validator = SetupValidator(verbose=args.verbose, fix=args.fix)
    exit_code = validator.run_all_checks()

    sys.exit(exit_code)


if __name__ == '__main__':
    main()
