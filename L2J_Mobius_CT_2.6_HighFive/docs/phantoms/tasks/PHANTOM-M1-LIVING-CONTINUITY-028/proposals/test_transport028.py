"""Focused tests of the real PowerShell transport contract. No mailbox dispatch."""
from pathlib import Path
import subprocess
import unittest

MODULE = Path(__file__).resolve().parents[5]
LIBRARY = MODULE / 'tools/phantom-local-play/LocalPlay-Pilot.ps1'


class ReceiptContract(unittest.TestCase):
    def run_contract(self, run_id, expected_exit):
        command = (f". '{LIBRARY}'; "
                   "$r=@{requestId='request';sessionId='session';sequence='7';"
                   f"operation='MOVE_SELF';runId='{run_id}'}}; "
                   "try { Assert-PilotResultIdentity $r 'request' 'session' 'run' 7 'MOVE_SELF'; exit 0 } "
                   "catch { Write-Output $_.Exception.Message; exit 3 }")
        result = subprocess.run(['pwsh', '-NoProfile', '-Command', command],
                                capture_output=True, text=True, encoding='utf-8')
        self.assertEqual(expected_exit, result.returncode, result.stdout + result.stderr)
        return result.stdout

    def test_exact_receipt_is_accepted(self):
        self.run_contract('run', 0)

    def test_wrong_run_is_rejected(self):
        self.assertIn('identity mismatch', self.run_contract('other-run', 3))


if __name__ == '__main__':
    unittest.main()
