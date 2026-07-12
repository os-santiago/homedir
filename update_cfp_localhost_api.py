#!/usr/bin/env python3
"""
Script to update CFP submissions using the localhost admin API via SSH tunnel.
This uses the new /api/localhost-admin endpoint that only accepts localhost connections
with Bearer token authentication.
"""

import subprocess
import time
import sys
import signal
import requests
import os

# CFP IDs to update
CFP_IDS = [
    "36134e03-782d-4265-a4a0-832827ae10a2",
    "3be12da8-cf69-45ad-9754-ab5214381960",
    "d6aa685a-466b-4064-8176-55f1c81c6b3f",
]

EVENT_ID = os.environ.get('EVENT_ID', 'devopsdays-santiago-2026')
SSH_HOST = os.environ.get('SSH_TARGET')
SSH_KEY = os.environ.get('SSH_KEY_PATH')
LOCAL_PORT = int(os.environ.get('LOCAL_PORT', '18080'))
REMOTE_PORT = int(os.environ.get('REMOTE_PORT', '8080'))

# Get admin token from environment
ADMIN_TOKEN = os.environ.get('LOCALHOST_ADMIN_TOKEN')

def create_ssh_tunnel():
    """Create SSH tunnel to forward local port to remote server."""
    print(f"Creating SSH tunnel: localhost:{LOCAL_PORT} -> {SSH_HOST}:{REMOTE_PORT}")

    tunnel_cmd = [
        "ssh",
        "-i", SSH_KEY,
        "-o", "BatchMode=yes",
        "-o", "StrictHostKeyChecking=accept-new",
        "-L", f"{LOCAL_PORT}:localhost:{REMOTE_PORT}",
        "-N",
        "-f",
        SSH_HOST
    ]

    try:
        result = subprocess.run(tunnel_cmd, capture_output=True, text=True, timeout=10)
        if result.returncode == 0:
            print("✓ SSH tunnel created successfully")
            time.sleep(2)  # Give tunnel time to establish
            return True
        else:
            print(f"✗ Failed to create tunnel: {result.stderr}")
            return False
    except Exception as e:
        print(f"✗ Error creating tunnel: {e}")
        return False

def close_ssh_tunnel():
    """Close the SSH tunnel."""
    print("\nClosing SSH tunnel...")
    try:
        # Kill all SSH processes with the tunnel port
        subprocess.run([
            "pkill", "-f", f"-L {LOCAL_PORT}:localhost:{REMOTE_PORT}"
        ], timeout=5)
        print("✓ Tunnel closed")
    except Exception as e:
        print(f"Warning: Could not close tunnel: {e}")

def test_connection(api_url, headers):
    """Test the localhost admin API connection."""
    try:
        response = requests.get(f"{api_url}/api/localhost-admin/status", headers=headers, timeout=10)
        if response.status_code == 200:
            print(f"✓ Connected to localhost admin API")
            data = response.json()
            print(f"  Mode: {data.get('mode')}")
            return True
        else:
            print(f"✗ Connection failed: HTTP {response.status_code}")
            print(f"  Response: {response.text}")
            return False
    except Exception as e:
        print(f"✗ Connection error: {e}")
        return False

def update_cfp(api_url, headers, cfp_id):
    """Update CFP using localhost admin API."""
    print(f"\nProcessing {cfp_id}...")

    # Get submission details
    try:
        details_resp = requests.get(
            f"{api_url}/api/localhost-admin/cfp/{EVENT_ID}/{cfp_id}",
            headers=headers,
            timeout=10
        )

        if details_resp.status_code != 200:
            print(f"  ✗ Failed to get submission (HTTP {details_resp.status_code})")
            print(f"  Response: {details_resp.text}")
            return False

        details = details_resp.json()
        item = details.get('item', {})
        status = item.get('status')
        version = item.get('version', 0)
        title = item.get('title', 'N/A')[:50]

        print(f"  Title: {title}")
        print(f"  Current status: {status}")
        print(f"  Version: {version}")

        if status == 'ACCEPTED':
            print(f"  - Already ACCEPTED")
            return True

    except Exception as e:
        print(f"  ✗ Failed to get submission details: {e}")
        return False

    # Update status
    try:
        update_resp = requests.put(
            f"{api_url}/api/localhost-admin/cfp/{EVENT_ID}/{cfp_id}/status",
            json={
                "status": "accepted",
                "note": "Charla seleccionada para DevOpsDays Santiago 2026",
                "version": version
            },
            headers=headers,
            timeout=10
        )

        if update_resp.status_code == 200:
            print(f"  ✓ Successfully updated to ACCEPTED")
            return True
        else:
            print(f"  ✗ Failed with HTTP {update_resp.status_code}")
            print(f"  Response: {update_resp.text[:200]}")
            return False

    except Exception as e:
        print(f"  ✗ Update failed: {e}")
        return False

def main():
    global ADMIN_TOKEN, SSH_HOST, SSH_KEY

    missing = []
    if not ADMIN_TOKEN:
        missing.append("LOCALHOST_ADMIN_TOKEN")
    if not SSH_HOST:
        missing.append("SSH_TARGET")
    if not SSH_KEY:
        missing.append("SSH_KEY_PATH")
    if missing:
        for var in missing:
            print(f"Error: {var} environment variable not set")
        return 1

    print(f"Updating {len(CFP_IDS)} CFP submissions to ACCEPTED status...")
    print(f"{'='*60}\n")

    # Create SSH tunnel
    if not create_ssh_tunnel():
        print("Failed to create SSH tunnel. Exiting.")
        return 1

    try:
        api_url = f"http://localhost:{LOCAL_PORT}"
        headers = {
            "Authorization": f"Bearer {ADMIN_TOKEN}",
            "Content-Type": "application/json"
        }

        # Test connection
        if not test_connection(api_url, headers):
            print("Failed to connect to localhost admin API. Exiting.")
            return 1

        print()

        # Update CFPs
        success_count = 0
        for cfp_id in CFP_IDS:
            if update_cfp(api_url, headers, cfp_id):
                success_count += 1

        print(f"\n{'='*60}")
        print(f"Summary:")
        print(f"  Successfully updated: {success_count}/{len(CFP_IDS)}")
        print(f"{'='*60}")

        return 0 if success_count == len(CFP_IDS) else 1

    finally:
        close_ssh_tunnel()

if __name__ == "__main__":
    sys.exit(main())
