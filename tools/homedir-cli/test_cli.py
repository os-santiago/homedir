import unittest
from unittest.mock import patch, MagicMock
import sys
import os

# Add current directory to path so cli can be imported
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cli import cli

class TestAdminCLI(unittest.TestCase):
    
    def setUp(self):
        from click.testing import CliRunner
        self.runner = CliRunner()

    @patch('requests.Session.request')
    def test_status_command(self, mock_request):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            'email': 'admin@example.org',
            'canManage': True,
            'health': {'estado': 'OK', 'totalDiscards': 0}
        }
        mock_request.return_value = mock_response

        result = self.runner.invoke(cli, ['--token', 'dummy-token', 'status'])
        self.assertEqual(result.exit_code, 0)
        self.assertIn('Connected successfully', result.output)
        self.assertIn('admin@example.org', result.output)

    @patch('requests.Session.request')
    def test_events_command(self, mock_request):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = [
            {'id': 'event-1', 'name': 'DevOpsDays 2026', 'period': '2026', 'status': 'ACTIVE'}
        ]
        mock_request.return_value = mock_response

        result = self.runner.invoke(cli, ['--token', 'dummy-token', 'events'])
        self.assertEqual(result.exit_code, 0)
        self.assertIn('DevOpsDays 2026', result.output)

    @patch('requests.Session.request')
    def test_cfp_command(self, mock_request):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = [
            {'id': 'cfp-1', 'title': 'My Quarkus Journey', 'speakerName': 'Alice', 'track': 'Java', 'status': 'UNDER_REVIEW'}
        ]
        mock_request.return_value = mock_response

        result = self.runner.invoke(cli, ['--token', 'dummy-token', 'cfp'])
        self.assertEqual(result.exit_code, 0)
        self.assertIn('My Quarkus Journey', result.output)

    @patch('requests.Session.request')
    def test_users_list_command(self, mock_request):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = [
            {'userId': 'user-123', 'name': 'Bob', 'email': 'bob@example.com', 'questClass': 'DEVELOPER', 'currentXp': 100}
        ]
        mock_request.return_value = mock_response

        result = self.runner.invoke(cli, ['--token', 'dummy-token', 'users', 'list'])
        self.assertEqual(result.exit_code, 0)
        self.assertIn('user-123', result.output)
        self.assertIn('Bob', result.output)

    def test_mcp_initialize(self):
        import io
        from mcp_server import main
        
        stdin_mock = io.StringIO('{"jsonrpc": "2.0", "method": "initialize", "params": {"protocolVersion": "2024-11-05"}, "id": 1}\n')
        stdout_mock = io.StringIO()
        
        with patch('sys.stdin', stdin_mock), patch('sys.stdout', stdout_mock):
            main()
            
        output = stdout_mock.getvalue()
        self.assertIn('protocolVersion', output)
        self.assertIn('homedir-user-admin-mcp', output)

if __name__ == '__main__':
    unittest.main()
