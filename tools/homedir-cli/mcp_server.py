#!/usr/bin/env python3
"""
Homedir User Administration MCP Server.
Implements the Model Context Protocol (MCP) over stdin/stdout for local agents.

This server can operate in two modes:
1. OIDC mode: Uses standard authentication (requires login via web)
2. Localhost admin mode: Uses bearer token (LOCALHOST_ADMIN_TOKEN) for direct API access
"""

import sys
import json
import os
import requests

# Retrieve config from environment variables
API_URL = os.environ.get('HOMEDIR_API_URL', 'http://localhost:8080').rstrip('/')
TOKEN = os.environ.get('HOMEDIR_ADMIN_TOKEN')
LOCALHOST_TOKEN = os.environ.get('LOCALHOST_ADMIN_TOKEN')

# Determine which API mode to use
USE_LOCALHOST_API = bool(LOCALHOST_TOKEN)

SESSION = requests.Session()

def get_headers():
    headers = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    }
    if USE_LOCALHOST_API and LOCALHOST_TOKEN:
        headers['Authorization'] = f'Bearer {LOCALHOST_TOKEN}'
    elif TOKEN:
        headers['Authorization'] = f'Bearer {TOKEN}'
    return headers

def make_request(method, endpoint, json_data=None):
    url = f"{API_URL}{endpoint}"
    headers = get_headers()

    # Dev mode auto-login if hitting localhost without a token (only for non-localhost-API mode)
    if not USE_LOCALHOST_API and not TOKEN and ("localhost" in API_URL or "127.0.0.1" in API_URL):
        if "quarkus-credential" not in SESSION.cookies:
            try:
                SESSION.post(
                    f"{API_URL}/j_security_check",
                    data={"username": "admin@example.org", "password": "adminpass"},
                    timeout=5
                )
            except Exception:
                pass
                
    try:
        response = SESSION.request(method, url, headers=headers, json=json_data, timeout=10)
        if response.status_code == 401:
            token_hint = "LOCALHOST_ADMIN_TOKEN" if USE_LOCALHOST_API else "HOMEDIR_ADMIN_TOKEN"
            return {"error": f"Unauthorized. Please set/check your {token_hint}."}
        elif response.status_code == 403:
            return {"error": "Forbidden. Insufficient administrative privileges."}
        elif response.status_code == 404:
            return {"error": "Not Found."}
        if not response.ok:
            try:
                error_body = response.json()
                return error_body
            except Exception:
                pass
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        return {"error": f"HTTP Request failed: {str(e)}"}

# Define MCP tools schemas
TOOLS = [
    {
        "name": "search_users",
        "description": "Search final user profiles in Homedir. Supports querying by name, email, github handle, etc.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "Optional search term to filter users. If empty, lists all users."
                }
            }
        }
    },
    {
        "name": "get_user_details",
        "description": "Retrieve the complete profile details of a user by their user ID.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "user_id": {
                    "type": "string",
                    "description": "The unique ID of the user."
                }
            },
            "required": ["user_id"]
        }
    },
    {
        "name": "add_user_xp",
        "description": "Reward or subtract XP points from a user's profile with a specific reason.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "user_id": {
                    "type": "string",
                    "description": "The unique ID of the user."
                },
                "amount": {
                    "type": "integer",
                    "description": "Number of XP points to add (positive) or deduct (negative)."
                },
                "reason": {
                    "type": "string",
                    "description": "Description of why the XP is being changed."
                },
                "quest_class": {
                    "type": "string",
                    "description": "Optional Quest class to associate the XP with (e.g. DEVELOPER, DESIGNER, WRITER, ORGANIZER)."
                }
            },
            "required": ["user_id", "amount", "reason"]
        }
    },
    {
        "name": "update_user_class",
        "description": "Assign or update a user's Quest Class (gremio).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "user_id": {
                    "type": "string",
                    "description": "The unique ID of the user."
                },
                "quest_class": {
                    "type": "string",
                    "description": "The target Quest class (e.g. DEVELOPER, DESIGNER, WRITER, ORGANIZER)."
                }
            },
            "required": ["user_id", "quest_class"]
        }
    },
    {
        "name": "get_status",
        "description": "Retrieve the current system health and status summary."
    },
    {
        "name": "list_events",
        "description": "List all configured events on the platform."
    },
    {
        "name": "list_cfp",
        "description": "Retrieve all CFP (Call for Papers) submissions across events."
    },
    {
        "name": "get_cfp_submission",
        "description": "Get detailed information about a specific CFP submission by ID and event ID.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "event_id": {
                    "type": "string",
                    "description": "The event ID (e.g., devopsdays-santiago-2026)."
                },
                "cfp_id": {
                    "type": "string",
                    "description": "The CFP submission UUID."
                }
            },
            "required": ["event_id", "cfp_id"]
        }
    },
    {
        "name": "update_cfp_status",
        "description": "Update the status of a CFP submission (e.g., to ACCEPTED, REJECTED, UNDER_REVIEW).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "event_id": {
                    "type": "string",
                    "description": "The event ID (e.g., devopsdays-santiago-2026)."
                },
                "cfp_id": {
                    "type": "string",
                    "description": "The CFP submission UUID."
                },
                "status": {
                    "type": "string",
                    "description": "The new status: accepted, rejected, under_review, or waitlisted.",
                    "enum": ["accepted", "rejected", "under_review", "waitlisted"]
                },
                "note": {
                    "type": "string",
                    "description": "Optional note explaining the status change."
                }
            },
            "required": ["event_id", "cfp_id", "status"]
        }
    },
    {
        "name": "list_volunteers",
        "description": "Retrieve all volunteer applications across events."
    },
    {
        "name": "get_metrics",
        "description": "Retrieve global usage and traffic metrics summary."
    }
]

def handle_tool_call(name, args):
    if name == "get_status":
        res = make_request("GET", "/api/private/admin/status")
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return json.dumps(res, indent=2)

    elif name == "list_events":
        res = make_request("GET", "/api/private/admin/events")
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return json.dumps(res, indent=2)

    elif name == "list_cfp":
        res = make_request("GET", "/api/private/admin/cfp")
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return json.dumps(res, indent=2)

    elif name == "list_volunteers":
        res = make_request("GET", "/api/private/admin/volunteers")
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return json.dumps(res, indent=2)

    elif name == "get_metrics":
        res = make_request("GET", "/api/private/admin/metrics")
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return json.dumps(res, indent=2)

    elif name == "search_users":
        query = args.get("query", "").lower()
        users = make_request("GET", "/api/private/admin/users")
        if isinstance(users, dict) and "error" in users:
            return users["error"]
        
        # Filter locally if query is provided
        if query:
            filtered = []
            for u in users:
                searchable = f"{u.get('userId','')} {u.get('name','')} {u.get('email','')}".lower()
                if query in searchable:
                    filtered.append(u)
            users = filtered
        return json.dumps(users, indent=2)

    elif name == "get_user_details":
        user_id = args.get("user_id")
        res = make_request("GET", f"/api/private/admin/users/{user_id}")
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return json.dumps(res, indent=2)

    elif name == "add_user_xp":
        user_id = args.get("user_id")
        payload = {
            "amount": args.get("amount"),
            "reason": args.get("reason")
        }
        if args.get("quest_class"):
            payload["questClass"] = args.get("quest_class").upper()
            
        res = make_request("POST", f"/api/private/admin/users/{user_id}/xp", payload)
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return f"Successfully updated XP for {user_id}. New profile state:\n{json.dumps(res, indent=2)}"

    elif name == "update_user_class":
        user_id = args.get("user_id")
        payload = {
            "questClass": args.get("quest_class").upper()
        }
        res = make_request("POST", f"/api/private/admin/users/{user_id}/quest-class", payload)
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return f"Successfully updated Quest Class for {user_id}. New profile state:\n{json.dumps(res, indent=2)}"

    elif name == "get_cfp_submission":
        event_id = args.get("event_id")
        cfp_id = args.get("cfp_id")

        if USE_LOCALHOST_API:
            endpoint = f"/api/localhost-admin/cfp/{event_id}/{cfp_id}"
        else:
            endpoint = f"/api/events/{event_id}/cfp/submissions/{cfp_id}"

        res = make_request("GET", endpoint)
        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return json.dumps(res, indent=2)

    elif name == "update_cfp_status":
        event_id = args.get("event_id")
        cfp_id = args.get("cfp_id")
        status = args.get("status")
        note = args.get("note", "Status updated via MCP admin tool")

        if USE_LOCALHOST_API:
            get_endpoint = f"/api/localhost-admin/cfp/{event_id}/{cfp_id}"
            put_endpoint = f"/api/localhost-admin/cfp/{event_id}/{cfp_id}/status"
        else:
            get_endpoint = f"/api/events/{event_id}/cfp/submissions/{cfp_id}"
            put_endpoint = f"/api/events/{event_id}/cfp/submissions/{cfp_id}/status"

        current = make_request("GET", get_endpoint)
        if isinstance(current, dict) and "error" in current:
            return current["error"]

        version = current.get("item", {}).get("version", 0)
        payload = {"status": status, "note": note, "version": version}
        res = make_request("PUT", put_endpoint, payload)

        if isinstance(res, dict) and "error" in res:
            return res["error"]
        return f"Successfully updated CFP {cfp_id} to status '{status}'.\n{json.dumps(res, indent=2)}"

    return f"Error: Tool '{name}' not found."

def send_response(req_id, result=None, error=None):
    res = {"jsonrpc": "2.0", "id": req_id}
    if error:
        res["error"] = error
    else:
        res["result"] = result
    sys.stdout.write(json.dumps(res) + "\n")
    sys.stdout.flush()

def main():
    # Loop reading standard input line by line (JSON-RPC)
    for line in sys.stdin:
        if not line.strip():
            continue
        try:
            req = json.loads(line)
            req_id = req.get("id")
            method = req.get("method")
            
            if method == "initialize":
                send_response(req_id, result={
                    "protocolVersion": "2024-11-05",
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": "homedir-user-admin-mcp", "version": "1.0"}
                })
            elif method == "tools/list":
                send_response(req_id, result={"tools": TOOLS})
            elif method == "tools/call":
                params = req.get("params", {})
                tool_name = params.get("name")
                arguments = params.get("arguments", {})
                
                output_text = handle_tool_call(tool_name, arguments)
                send_response(req_id, result={
                    "content": [{"type": "text", "text": output_text}]
                })
            else:
                send_response(req_id, error={"code": -32601, "message": f"Method {method} not found"})
        except Exception as e:
            # Send general RPC parse error
            sys.stdout.write(json.dumps({
                "jsonrpc": "2.0",
                "error": {"code": -32700, "message": f"Parse error: {str(e)}"}
            }) + "\n")
            sys.stdout.flush()

if __name__ == '__main__':
    main()
