"""
Tests for WOS Debug Logger (Issue #936).

Validates:
- Debug flag enables detailed logging
- Logs written to timestamped file
- Log entries have correct structure (timestamp, context)
- End-of-cycle summary format
- Log level configuration
"""
import time
import tempfile
from pathlib import Path
import pytest

from src.wos.logging import DebugLogger, LogLevel, OperationMetrics, CycleSummary


class TestOperationMetrics:
    """Test OperationMetrics dataclass."""

    def test_duration_calculation(self):
        """Verify duration is calculated correctly."""
        op = OperationMetrics(
            operation_type="git_commit",
            start_time=100.0,
            end_time=105.5
        )
        assert op.duration == 5.5

    def test_duration_none_when_incomplete(self):
        """Duration should be None if operation not ended."""
        op = OperationMetrics(
            operation_type="api_call",
            start_time=100.0
        )
        assert op.duration is None

    def test_metadata_default(self):
        """Metadata should default to empty dict."""
        op = OperationMetrics(
            operation_type="file_write",
            start_time=100.0
        )
        assert op.metadata == {}


class TestCycleSummary:
    """Test CycleSummary aggregation."""

    def test_time_by_operation_type(self):
        """Should aggregate time by operation type."""
        summary = CycleSummary(cycle_start=100.0)
        summary.operations = [
            OperationMetrics("git_commit", 100.0, 102.0),
            OperationMetrics("git_commit", 102.0, 103.5),
            OperationMetrics("api_call", 103.5, 105.0),
        ]

        result = summary.time_by_operation_type()
        assert result["git_commit"] == 3.5
        assert result["api_call"] == 1.5

    def test_time_by_agent(self):
        """Should aggregate time by agent."""
        summary = CycleSummary(cycle_start=100.0)
        summary.operations = [
            OperationMetrics("git_commit", 100.0, 102.0, agent_name="opencode"),
            OperationMetrics("api_call", 102.0, 103.5, agent_name="opencode"),
            OperationMetrics("file_write", 103.5, 105.0, agent_name="claude"),
        ]

        result = summary.time_by_agent()
        assert result["opencode"] == 3.5
        assert result["claude"] == 1.5

    def test_total_duration(self):
        """Should calculate total cycle duration."""
        summary = CycleSummary(cycle_start=100.0, cycle_end=110.5)
        assert summary.total_duration == 10.5

    def test_total_duration_none_when_incomplete(self):
        """Duration should be None if cycle not ended."""
        summary = CycleSummary(cycle_start=100.0)
        assert summary.total_duration is None


class TestDebugLogger:
    """Test DebugLogger functionality."""

    @pytest.fixture
    def temp_log_dir(self):
        """Create temporary directory for test logs."""
        with tempfile.TemporaryDirectory() as tmpdir:
            yield Path(tmpdir)

    @pytest.fixture
    def logger(self, temp_log_dir):
        """Create debug logger instance."""
        logger_instance = DebugLogger(
            enabled=True,
            log_level=LogLevel.DEBUG,
            log_dir=temp_log_dir,
            stream_to_stdout=False
        )
        yield logger_instance
        logger_instance.close()

    def test_logger_disabled_by_default(self, temp_log_dir):
        """Logger should be disabled by default."""
        logger = DebugLogger(enabled=False, log_dir=temp_log_dir)
        logger.start_cycle()
        logger.log(LogLevel.INFO, "test message")

        log_files = list(temp_log_dir.glob("*.log"))
        assert len(log_files) == 0

    def test_log_file_created(self, logger, temp_log_dir):
        """Should create timestamped log file."""
        log_files = list(temp_log_dir.glob("cycle-*.log"))
        assert len(log_files) == 1
        assert log_files[0].name.startswith("cycle-")
        assert log_files[0].suffix == ".log"

    def test_log_entry_structure(self, logger, temp_log_dir):
        """Log entries should have timestamp and structured context."""
        logger.log(
            LogLevel.INFO,
            "Test message",
            agent="opencode",
            work_item="issue-123",
            operation="git_commit"
        )

        log_file = list(temp_log_dir.glob("cycle-*.log"))[0]
        content = log_file.read_text()

        assert "INFO" in content
        assert "Test message" in content
        assert "agent=opencode" in content
        assert "work_item=issue-123" in content
        assert "op=git_commit" in content

    def test_start_end_cycle(self, logger):
        """Should track cycle start and end."""
        logger.start_cycle()
        assert logger.current_cycle is not None
        assert logger.current_cycle.cycle_start > 0
        assert logger.current_cycle.cycle_end is None

        time.sleep(0.01)
        logger.end_cycle()
        assert logger.current_cycle.cycle_end > logger.current_cycle.cycle_start

    def test_operation_tracking(self, logger):
        """Should track operation start and end with timing."""
        logger.start_cycle()

        logger.start_operation(
            "op1",
            "git_commit",
            agent="opencode",
            work_item="issue-123"
        )
        assert "op1" in logger.active_operations

        time.sleep(0.01)
        logger.end_operation("op1", success=True)

        assert "op1" not in logger.active_operations
        assert len(logger.current_cycle.operations) == 1

        op = logger.current_cycle.operations[0]
        assert op.operation_type == "git_commit"
        assert op.agent_name == "opencode"
        assert op.work_item_id == "issue-123"
        assert op.duration > 0

    def test_agent_assignment_logged(self, logger, temp_log_dir):
        """Should log agent assignments."""
        logger.record_agent_assignment("issue-456", "claude")

        log_file = list(temp_log_dir.glob("cycle-*.log"))[0]
        content = log_file.read_text()

        assert "Assigned to claude" in content
        assert "work_item=issue-456" in content

    def test_queue_transition_logged(self, logger, temp_log_dir):
        """Should log queue state transitions."""
        logger.record_queue_transition("pending", "in_progress", "issue-789")

        log_file = list(temp_log_dir.glob("cycle-*.log"))[0]
        content = log_file.read_text()

        assert "Queue transition: pending -> in_progress" in content
        assert "work_item=issue-789" in content

    def test_checkpoint_pass_recorded(self, logger):
        """Should record checkpoint pass."""
        logger.start_cycle()
        logger.record_checkpoint("validation", passed=True, reason="All checks passed")

        assert logger.current_cycle.checkpoints_passed == 1
        assert logger.current_cycle.checkpoints_failed == 0

    def test_checkpoint_fail_recorded(self, logger):
        """Should record checkpoint failure."""
        logger.start_cycle()
        logger.record_checkpoint("tests", passed=False, reason="3 tests failed")

        assert logger.current_cycle.checkpoints_passed == 0
        assert logger.current_cycle.checkpoints_failed == 1

    def test_work_item_outcome_tracking(self, logger):
        """Should track work item success/failure."""
        logger.start_cycle()

        logger.record_work_item_outcome("issue-111", succeeded=True)
        logger.record_work_item_outcome("issue-222", succeeded=False)
        logger.record_work_item_outcome("issue-333", succeeded=True)

        assert logger.current_cycle.work_items_processed == 3
        assert logger.current_cycle.work_items_succeeded == 2
        assert logger.current_cycle.work_items_failed == 1

    def test_api_call_counting(self, logger):
        """Should count API calls."""
        logger.start_cycle()

        logger.start_operation("api1", "api_call")
        logger.end_operation("api1")

        logger.start_operation("api2", "api_call")
        logger.end_operation("api2")

        assert logger.current_cycle.api_calls == 2

    def test_cycle_summary_generated(self, logger, temp_log_dir):
        """Should generate complete cycle summary."""
        logger.start_cycle()

        logger.start_operation("op1", "git_commit", agent="opencode")
        time.sleep(0.01)
        logger.end_operation("op1")

        logger.record_work_item_outcome("issue-123", succeeded=True)
        logger.record_checkpoint("validation", passed=True)

        logger.end_cycle()

        log_file = list(temp_log_dir.glob("cycle-*.log"))[0]
        content = log_file.read_text()

        assert "CYCLE SUMMARY REPORT" in content
        assert "Total Duration:" in content
        assert "Work Items:" in content
        assert "Checkpoints:" in content
        assert "Time by Operation Type:" in content
        assert "Time by Agent:" in content

    def test_log_level_filtering(self, temp_log_dir):
        """Should respect log level configuration."""
        logger = DebugLogger(
            enabled=True,
            log_level=LogLevel.WARN,
            log_dir=temp_log_dir,
            stream_to_stdout=False
        )

        try:
            logger.log(LogLevel.DEBUG, "Debug message")
            logger.log(LogLevel.INFO, "Info message")
            logger.log(LogLevel.WARN, "Warning message")

            log_file = list(temp_log_dir.glob("cycle-*.log"))[0]
            content = log_file.read_text()

            assert "Debug message" not in content
            assert "Info message" not in content
            assert "Warning message" in content
        finally:
            logger.close()

    def test_metadata_in_operations(self, logger):
        """Should store operation metadata."""
        logger.start_cycle()

        metadata = {"file": "test.py", "lines_changed": 42}
        logger.start_operation(
            "op1",
            "file_write",
            metadata=metadata
        )
        logger.end_operation("op1")

        op = logger.current_cycle.operations[0]
        assert op.metadata == metadata
