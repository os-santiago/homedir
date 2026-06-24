"""
WOS Debug Logger Module.

Provides detailed logging for cycle work traceability with timestamps,
agent tracking, operation timing, and cycle summaries (Issue #936).
"""
import logging
import time
from pathlib import Path
from typing import Optional, Dict, Any
from datetime import datetime
from dataclasses import dataclass, field
from enum import Enum


class LogLevel(Enum):
    """Log levels for debug logger."""
    DEBUG = "DEBUG"
    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"


@dataclass
class OperationMetrics:
    """Track timing and metadata for a single operation."""
    operation_type: str
    start_time: float
    end_time: Optional[float] = None
    agent_name: Optional[str] = None
    work_item_id: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    @property
    def duration(self) -> Optional[float]:
        """Calculate operation duration in seconds."""
        if self.end_time is None:
            return None
        return self.end_time - self.start_time


@dataclass
class CycleSummary:
    """Aggregated metrics for a complete cycle."""
    cycle_start: float
    cycle_end: Optional[float] = None
    operations: list = field(default_factory=list)
    work_items_processed: int = 0
    work_items_succeeded: int = 0
    work_items_failed: int = 0
    checkpoints_passed: int = 0
    checkpoints_failed: int = 0
    api_calls: int = 0

    def time_by_operation_type(self) -> Dict[str, float]:
        """Aggregate time spent by operation type."""
        result = {}
        for op in self.operations:
            if op.duration:
                result[op.operation_type] = result.get(op.operation_type, 0.0) + op.duration
        return result

    def time_by_agent(self) -> Dict[str, float]:
        """Aggregate time spent by agent."""
        result = {}
        for op in self.operations:
            if op.duration and op.agent_name:
                result[op.agent_name] = result.get(op.agent_name, 0.0) + op.duration
        return result

    @property
    def total_duration(self) -> Optional[float]:
        """Total cycle duration in seconds."""
        if self.cycle_end is None:
            return None
        return self.cycle_end - self.cycle_start


class DebugLogger:
    """
    Debug logger for WOS cycle work with detailed traceability.

    Features:
    - Timestamped logging to file and stdout
    - Agent assignment tracking
    - Operation timing
    - Queue state transitions
    - Checkpoint pass/fail recording
    - End-of-cycle summary report
    """

    def __init__(
        self,
        enabled: bool = False,
        log_level: LogLevel = LogLevel.INFO,
        log_dir: Optional[Path] = None,
        stream_to_stdout: bool = True
    ):
        """
        Initialize debug logger.

        Args:
            enabled: Whether debug logging is enabled
            log_level: Minimum log level to record
            log_dir: Directory for log files (default: .workspace-os/debug-logs)
            stream_to_stdout: Whether to also output to stdout
        """
        self.enabled = enabled
        self.log_level = log_level
        self.stream_to_stdout = stream_to_stdout
        self.current_cycle: Optional[CycleSummary] = None
        self.active_operations: Dict[str, OperationMetrics] = {}

        if log_dir is None:
            log_dir = Path('.workspace-os/debug-logs')
        self.log_dir = log_dir

        if self.enabled:
            self._setup_logging()

    def _setup_logging(self):
        """Initialize logging infrastructure."""
        self.log_dir.mkdir(parents=True, exist_ok=True)

        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        log_file = self.log_dir / f'cycle-{timestamp}.log'

        self.logger = logging.getLogger('wos.debug')
        self.logger.setLevel(getattr(logging, self.log_level.value))
        self.logger.handlers.clear()

        file_handler = logging.FileHandler(log_file)
        file_handler.setLevel(logging.DEBUG)

        formatter = logging.Formatter(
            '%(asctime)s | %(levelname)-5s | %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
        file_handler.setFormatter(formatter)
        self.logger.addHandler(file_handler)

        if self.stream_to_stdout:
            stream_handler = logging.StreamHandler()
            stream_handler.setLevel(getattr(logging, self.log_level.value))
            stream_handler.setFormatter(formatter)
            self.logger.addHandler(stream_handler)

        self.log_file_path = log_file
        self.log(LogLevel.INFO, f"Debug logging initialized: {log_file}")

    def log(
        self,
        level: LogLevel,
        message: str,
        agent: Optional[str] = None,
        work_item: Optional[str] = None,
        operation: Optional[str] = None
    ):
        """
        Log a message with structured context.

        Args:
            level: Log level
            message: Log message
            agent: Agent name (optional)
            work_item: Work item ID (optional)
            operation: Operation type (optional)
        """
        if not self.enabled:
            return

        context_parts = []
        if agent:
            context_parts.append(f"agent={agent}")
        if work_item:
            context_parts.append(f"work_item={work_item}")
        if operation:
            context_parts.append(f"op={operation}")

        context = f" [{', '.join(context_parts)}]" if context_parts else ""
        full_message = f"{message}{context}"

        # Map WARN to warning to avoid deprecation warning
        level_name = 'warning' if level == LogLevel.WARN else level.value.lower()
        log_method = getattr(self.logger, level_name)
        log_method(full_message)

    def start_cycle(self):
        """Begin a new cycle and initialize metrics tracking."""
        if not self.enabled:
            return

        self.current_cycle = CycleSummary(cycle_start=time.time())
        self.log(LogLevel.INFO, "Cycle started")

    def end_cycle(self):
        """Complete the cycle and generate summary report."""
        if not self.enabled or not self.current_cycle:
            return

        self.current_cycle.cycle_end = time.time()
        self._print_summary()
        self.log(LogLevel.INFO, "Cycle ended")

    def close(self):
        """Close all handlers and release file locks."""
        if not self.enabled:
            return

        for handler in self.logger.handlers[:]:
            handler.close()
            self.logger.removeHandler(handler)

    def start_operation(
        self,
        operation_id: str,
        operation_type: str,
        agent: Optional[str] = None,
        work_item: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None
    ):
        """
        Start tracking an operation.

        Args:
            operation_id: Unique identifier for this operation
            operation_type: Type of operation (e.g., 'git_commit', 'api_call')
            agent: Agent performing the operation
            work_item: Associated work item ID
            metadata: Additional operation metadata
        """
        if not self.enabled:
            return

        op_metrics = OperationMetrics(
            operation_type=operation_type,
            start_time=time.time(),
            agent_name=agent,
            work_item_id=work_item,
            metadata=metadata or {}
        )
        self.active_operations[operation_id] = op_metrics

        self.log(
            LogLevel.DEBUG,
            f"Operation started: {operation_type}",
            agent=agent,
            work_item=work_item,
            operation=operation_type
        )

    def end_operation(self, operation_id: str, success: bool = True):
        """
        Complete an operation and record metrics.

        Args:
            operation_id: Unique identifier of the operation
            success: Whether operation succeeded
        """
        if not self.enabled or operation_id not in self.active_operations:
            return

        op = self.active_operations.pop(operation_id)
        op.end_time = time.time()

        if self.current_cycle:
            self.current_cycle.operations.append(op)
            if op.operation_type == 'api_call':
                self.current_cycle.api_calls += 1

        status = "succeeded" if success else "failed"
        self.log(
            LogLevel.DEBUG,
            f"Operation {status}: {op.operation_type} ({op.duration:.3f}s)",
            agent=op.agent_name,
            work_item=op.work_item_id,
            operation=op.operation_type
        )

    def record_agent_assignment(self, work_item_id: str, agent: str):
        """Record agent assignment for a work item."""
        if not self.enabled:
            return

        self.log(
            LogLevel.INFO,
            f"Assigned to {agent}",
            agent=agent,
            work_item=work_item_id
        )

    def record_queue_transition(self, from_state: str, to_state: str, work_item_id: Optional[str] = None):
        """Record queue state transition."""
        if not self.enabled:
            return

        self.log(
            LogLevel.DEBUG,
            f"Queue transition: {from_state} -> {to_state}",
            work_item=work_item_id
        )

    def record_checkpoint(self, checkpoint_name: str, passed: bool, reason: Optional[str] = None):
        """
        Record checkpoint pass/fail.

        Args:
            checkpoint_name: Name of the checkpoint
            passed: Whether checkpoint passed
            reason: Reason for pass/fail
        """
        if not self.enabled:
            return

        if self.current_cycle:
            if passed:
                self.current_cycle.checkpoints_passed += 1
            else:
                self.current_cycle.checkpoints_failed += 1

        status = "PASS" if passed else "FAIL"
        message = f"Checkpoint {checkpoint_name}: {status}"
        if reason:
            message += f" - {reason}"

        level = LogLevel.INFO if passed else LogLevel.WARN
        self.log(level, message)

    def record_work_item_outcome(self, work_item_id: str, succeeded: bool):
        """Record work item completion outcome."""
        if not self.enabled or not self.current_cycle:
            return

        self.current_cycle.work_items_processed += 1
        if succeeded:
            self.current_cycle.work_items_succeeded += 1
        else:
            self.current_cycle.work_items_failed += 1

        status = "succeeded" if succeeded else "failed"
        self.log(
            LogLevel.INFO,
            f"Work item {status}",
            work_item=work_item_id
        )

    def _print_summary(self):
        """Print end-of-cycle summary report."""
        if not self.current_cycle:
            return

        summary = self.current_cycle

        self.log(LogLevel.INFO, "=" * 60)
        self.log(LogLevel.INFO, "CYCLE SUMMARY REPORT")
        self.log(LogLevel.INFO, "=" * 60)

        if summary.total_duration:
            self.log(LogLevel.INFO, f"Total Duration: {summary.total_duration:.2f}s")

        self.log(LogLevel.INFO, f"Work Items: {summary.work_items_processed} processed, "
                              f"{summary.work_items_succeeded} succeeded, "
                              f"{summary.work_items_failed} failed")

        self.log(LogLevel.INFO, f"Checkpoints: {summary.checkpoints_passed} passed, "
                              f"{summary.checkpoints_failed} failed")

        self.log(LogLevel.INFO, f"API Calls: {summary.api_calls}")

        time_by_op = summary.time_by_operation_type()
        if time_by_op:
            self.log(LogLevel.INFO, "\nTime by Operation Type:")
            for op_type, duration in sorted(time_by_op.items(), key=lambda x: -x[1]):
                self.log(LogLevel.INFO, f"  {op_type}: {duration:.2f}s")

        time_by_agent = summary.time_by_agent()
        if time_by_agent:
            self.log(LogLevel.INFO, "\nTime by Agent:")
            for agent, duration in sorted(time_by_agent.items(), key=lambda x: -x[1]):
                self.log(LogLevel.INFO, f"  {agent}: {duration:.2f}s")

        self.log(LogLevel.INFO, "=" * 60)
