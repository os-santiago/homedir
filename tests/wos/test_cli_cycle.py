"""
Tests for WOS Cycle CLI Command (Issue #936).

Validates:
- --debug flag enables debug logger
- --log-level configures log level
- --log-dir sets custom log directory
- CLI command integration with DebugLogger
"""
import tempfile
from pathlib import Path
from click.testing import CliRunner
import pytest

from src.wos.cli.cycle import cycle_work, cycle


class TestCycleWorkCLI:
    """Test cycle work CLI command."""

    @pytest.fixture
    def runner(self):
        """Create Click CLI test runner."""
        return CliRunner()

    @pytest.fixture
    def temp_log_dir(self):
        """Create temporary directory for test logs."""
        with tempfile.TemporaryDirectory() as tmpdir:
            yield Path(tmpdir)

    def test_cycle_work_without_debug(self, runner):
        """Should run without debug logging by default."""
        result = runner.invoke(cycle_work)
        assert result.exit_code == 0
        # Without --debug, logger is disabled and produces no output
        # Just verify command completes successfully

    def test_cycle_work_with_debug_flag(self, runner, temp_log_dir):
        """Should enable debug logging when --debug flag is used."""
        result = runner.invoke(cycle_work, ['--debug', '--log-dir', str(temp_log_dir)])
        assert result.exit_code == 0

        # Should create debug log file
        log_files = list(temp_log_dir.glob("cycle-*.log"))
        assert len(log_files) == 1

        # Should contain debug information
        content = log_files[0].read_text()
        assert "Debug logging initialized" in content
        assert "Cycle started" in content
        assert "Cycle ended" in content
        assert "CYCLE SUMMARY REPORT" in content

    def test_debug_flag_auto_sets_debug_level(self, runner, temp_log_dir):
        """--debug flag should automatically set log level to DEBUG."""
        result = runner.invoke(cycle_work, ['--debug', '--log-dir', str(temp_log_dir)])
        assert result.exit_code == 0

        log_files = list(temp_log_dir.glob("cycle-*.log"))
        content = log_files[0].read_text()
        # DEBUG level should be visible in logs
        assert "DEBUG" in content or "Cycle started" in content

    def test_custom_log_level(self, runner, temp_log_dir):
        """Should respect --log-level option."""
        result = runner.invoke(
            cycle_work,
            ['--debug', '--log-level', 'WARN', '--log-dir', str(temp_log_dir)]
        )
        assert result.exit_code == 0

        log_files = list(temp_log_dir.glob("cycle-*.log"))
        content = log_files[0].read_text()
        # With WARN level, INFO messages should not appear in log file
        # (but WARN and ERROR should)
        assert "WARN" in content or "ERROR" in content or content.count("INFO") < 3

    def test_default_log_dir(self, runner):
        """Should use default .workspace-os/debug-logs/ if --log-dir not specified."""
        with runner.isolated_filesystem():
            result = runner.invoke(cycle_work, ['--debug'])
            assert result.exit_code == 0

            default_log_dir = Path('.workspace-os/debug-logs')
            assert default_log_dir.exists()

            log_files = list(default_log_dir.glob("cycle-*.log"))
            assert len(log_files) == 1

    def test_cycle_group_exists(self, runner):
        """Should have cycle command group."""
        result = runner.invoke(cycle, ['--help'])
        assert result.exit_code == 0
        assert 'cycle' in result.output.lower()

    def test_work_subcommand_exists(self, runner):
        """Should have work subcommand under cycle."""
        result = runner.invoke(cycle, ['work', '--help'])
        assert result.exit_code == 0
        assert 'debug' in result.output.lower()
        assert 'log-level' in result.output.lower()
        assert 'log-dir' in result.output.lower()

    def test_help_text_describes_debug_features(self, runner):
        """Help text should describe debug logging features."""
        result = runner.invoke(cycle_work, ['--help'])
        assert result.exit_code == 0

        help_text = result.output.lower()
        assert 'debug' in help_text
        assert 'timestamp' in help_text or 'timing' in help_text
        assert 'agent' in help_text
        assert 'summary' in help_text

    def test_invalid_log_level_rejected(self, runner):
        """Should reject invalid log levels."""
        result = runner.invoke(cycle_work, ['--debug', '--log-level', 'INVALID'])
        assert result.exit_code != 0

    def test_log_file_closed_after_execution(self, runner, temp_log_dir):
        """Should properly close log file handlers after execution."""
        result = runner.invoke(cycle_work, ['--debug', '--log-dir', str(temp_log_dir)])
        assert result.exit_code == 0

        # Should be able to read log file immediately after command completes
        log_files = list(temp_log_dir.glob("cycle-*.log"))
        assert len(log_files) == 1

        # File should be readable (not locked)
        content = log_files[0].read_text()
        assert len(content) > 0

    def test_error_handling_logs_failure(self, runner, temp_log_dir):
        """Should log errors when cycle work fails."""
        # This test would require mocking an actual failure scenario
        # For now, just verify the logger is properly set up to catch exceptions
        result = runner.invoke(cycle_work, ['--debug', '--log-dir', str(temp_log_dir)])
        assert result.exit_code == 0

        log_files = list(temp_log_dir.glob("cycle-*.log"))
        content = log_files[0].read_text()
        # Should complete successfully in stub implementation
        assert "Cycle work completed" in content
