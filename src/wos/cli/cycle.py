"""WOS Cycle Work CLI Command."""
import click
from pathlib import Path
from typing import Optional

from src.wos.logging.debug_logger import DebugLogger, LogLevel


@click.command('work')
@click.option(
    '--debug',
    is_flag=True,
    default=False,
    help='Enable detailed debug logging with operation timing and traceability'
)
@click.option(
    '--log-level',
    type=click.Choice(['DEBUG', 'INFO', 'WARN', 'ERROR'], case_sensitive=False),
    default='INFO',
    help='Set logging level (default: INFO, auto DEBUG when --debug enabled)'
)
@click.option(
    '--log-dir',
    type=click.Path(path_type=Path),
    default=None,
    help='Custom directory for debug logs (default: .workspace-os/debug-logs/)'
)
def cycle_work(debug: bool, log_level: str, log_dir: Optional[Path]):
    """
    Execute WOS cycle work with optional debug tracing.

    When --debug is enabled, detailed logs are written to timestamped files
    under .workspace-os/debug-logs/ with:
    - Timestamps for each operation
    - Agent assignments per work item
    - Operation timing (git, API, file I/O)
    - Queue state transitions
    - Checkpoint pass/fail details
    - End-of-cycle summary report
    """
    # Auto-set DEBUG level when --debug flag is used
    if debug and log_level == 'INFO':
        log_level = 'DEBUG'

    # Initialize debug logger
    logger = DebugLogger(
        enabled=debug,
        log_level=LogLevel[log_level],
        log_dir=log_dir,
        stream_to_stdout=True
    )

    try:
        logger.start_cycle()

        # TODO: Actual cycle work implementation will be integrated here
        # For now, this is a stub that demonstrates the logger integration

        logger.log(
            LogLevel.INFO,
            "Cycle work command invoked",
            operation="cycle_start"
        )

        # Example usage of debug logger methods:
        # logger.record_agent_assignment(work_item_id="123", agent="claude")
        # logger.start_operation(
        #     operation_id="op-1",
        #     operation_type="git_commit",
        #     agent="claude",
        #     work_item="123"
        # )
        # logger.end_operation(operation_id="op-1", success=True)
        # logger.record_checkpoint("validation", passed=True, reason="All tests passed")
        # logger.record_work_item_outcome(work_item_id="123", succeeded=True)

        logger.log(
            LogLevel.INFO,
            "Cycle work completed (stub implementation)",
            operation="cycle_end"
        )

        logger.end_cycle()
    except Exception as e:
        logger.log(LogLevel.ERROR, f"Cycle work failed: {e}")
        raise
    finally:
        logger.close()


@click.group('cycle')
def cycle():
    """WOS cycle management commands."""
    pass


# Register the work subcommand
cycle.add_command(cycle_work)
