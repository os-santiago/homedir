#!/usr/bin/env python3
"""
Homedir Local Administrative CLI Tool.
Integrated with the Homedir Backoffice/Management backend API.
"""

import os
import sys
import click
import requests

# Set formatting helpers or fallbacks if rich is not installed
try:
    from rich.console import Console
    from rich.table import Table
    console = Console()
except ImportError:
    # Minimal fallback print system
    class SimpleConsole:
        def print(self, msg, *args, **kwargs):
            if isinstance(msg, str) and msg.startswith("[bold"):
                # Clean simple tags
                msg = msg.replace("[bold green]", "").replace("[/]", "").replace("[bold red]", "")
            print(msg)
    console = SimpleConsole()

SESSION = requests.Session()

@click.group()
@click.option('--api-url', default='http://localhost:8080', help='URL of the Homedir backend API.')
@click.option('--token', envvar='HOMEDIR_ADMIN_TOKEN', help='Administrative API bearer token.')
@click.pass_context
def cli(ctx, api_url, token):
    """Homedir Administrative CLI Tool.
    
    Allows managing events, CFP submissions, volunteer applications, and viewing metrics locally.
    """
    ctx.ensure_object(dict)
    ctx.obj['API_URL'] = api_url.rstrip('/')
    ctx.obj['TOKEN'] = token
    ctx.obj['HEADERS'] = {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
    }
    if token:
        ctx.obj['HEADERS']['Authorization'] = f'Bearer {token}'

def handle_request(ctx, method, endpoint, **kwargs):
    api_url = ctx.obj['API_URL']
    headers = ctx.obj['HEADERS']
    token = ctx.obj['TOKEN']
    url = f"{api_url}{endpoint}"
    
    # Dev mode auto-login if hitting localhost without a token
    if not token and ("localhost" in api_url or "127.0.0.1" in api_url):
        if "quarkus-credential" not in SESSION.cookies:
            try:
                SESSION.post(
                    f"{api_url}/j_security_check",
                    data={"username": "admin@example.org", "password": "adminpass"},
                    timeout=5
                )
            except Exception:
                pass
                
    try:
        response = SESSION.request(method, url, headers=headers, **kwargs)
        if response.status_code == 401:
            console.print("[bold red]Error: Unauthorized. Please check your admin token.[/]")
            sys.exit(1)
        elif response.status_code == 403:
            console.print("[bold red]Error: Forbidden. Insufficient permissions.[/]")
            sys.exit(1)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.RequestException as e:
        console.print(f"[bold red]HTTP Request Failed:[/] {e}")
        sys.exit(1)

@cli.command('status')
@click.pass_context
def status(ctx):
    """Retrieve platform capacity, health status and auth verification."""
    data = handle_request(ctx, 'GET', '/api/private/admin/status')
    console.print(f"[bold green]Connected successfully![/]")
    console.print(f"Authenticated as: {data.get('email', 'N/A')}")
    console.print(f"Write permissions (canManage): {data.get('canManage', False)}")
    
    health = data.get('health', {})
    console.print(f"\n[bold]Health Status:[/] {health.get('estado', 'UNKNOWN')}")
    console.print(f"Total Discards: {health.get('totalDiscards', 0)}")

@cli.command('events')
@click.pass_context
def events(ctx):
    """List all registered events on the platform."""
    data = handle_request(ctx, 'GET', '/api/private/admin/events')
    if not data:
        console.print("No events found.")
        return
        
    console.print(f"[bold green]Loaded {len(data)} events:[/]\n")
    # If rich is present, format beautifully, else simple text
    if 'Console' in globals():
        table = Table(title="Homedir Events")
        table.add_column("ID", style="cyan")
        table.add_column("Name", style="magenta")
        table.add_column("Date / Period", style="green")
        table.add_column("Status", style="yellow")
        
        for event in data:
            table.add_row(
                str(event.get('id', '')),
                str(event.get('name', '')),
                str(event.get('period', '')),
                str(event.get('status', ''))
            )
        console.print(table)
    else:
        for event in data:
            console.print(f"- [{event.get('id')}] {event.get('name')} ({event.get('period')}) - Status: {event.get('status')}")

@cli.command('cfp')
@click.pass_context
def cfp(ctx):
    """List CFP submissions."""
    data = handle_request(ctx, 'GET', '/api/private/admin/cfp')
    if not data:
        console.print("No CFP submissions found.")
        return
        
    console.print(f"[bold green]Loaded {len(data)} CFP submissions:[/]\n")
    if 'Console' in globals():
        table = Table(title="CFP Submissions")
        table.add_column("ID", style="cyan")
        table.add_column("Title", style="magenta")
        table.add_column("Speaker", style="green")
        table.add_column("Track", style="yellow")
        table.add_column("Status", style="blue")
        
        for sub in data:
            table.add_row(
                str(sub.get('id', '')),
                str(sub.get('title', '')),
                str(sub.get('speakerName', '')),
                str(sub.get('track', '')),
                str(sub.get('status', ''))
            )
        console.print(table)
    else:
        for sub in data:
            console.print(f"- [{sub.get('id')}] {sub.get('title')} by {sub.get('speakerName')} - Status: {sub.get('status')}")

@cli.command('volunteers')
@click.pass_context
def volunteers(ctx):
    """List volunteer applications."""
    data = handle_request(ctx, 'GET', '/api/private/admin/volunteers')
    if not data:
        console.print("No volunteer applications found.")
        return
        
    console.print(f"[bold green]Loaded {len(data)} volunteer applications:[/]\n")
    if 'Console' in globals():
        table = Table(title="Volunteer Applications")
        table.add_column("ID", style="cyan")
        table.add_column("Name", style="magenta")
        table.add_column("Email", style="green")
        table.add_column("Status", style="blue")
        
        for vol in data:
            table.add_row(
                str(vol.get('id', '')),
                str(vol.get('name', '')),
                str(vol.get('email', '')),
                str(vol.get('status', ''))
            )
        console.print(table)
    else:
        for vol in data:
            console.print(f"- [{vol.get('id')}] {vol.get('name')} ({vol.get('email')}) - Status: {vol.get('status')}")

@cli.command('metrics')
@click.pass_context
def metrics(ctx):
    """Retrieve platform metrics summary."""
    data = handle_request(ctx, 'GET', '/api/private/admin/metrics')
    console.print("[bold green]Usage Metrics Summary:[/]\n")
    console.print(f"Events Viewed: {data.get('eventsViewed', 0)}")
    console.print(f"Talks Viewed: {data.get('talksViewed', 0)}")
    console.print(f"Talks Registered: {data.get('talksRegistered', 0)}")
    console.print(f"Stage Visits: {data.get('stageVisits', 0)}")
    console.print(f"Expected Attendees: {data.get('expectedAttendees', 0)}")

@cli.group('users')
def users():
    """Manage final user profiles, XP, and Quest classes."""
    pass

@users.command('list')
@click.option('--query', default='', help='Search term to filter user profiles.')
@click.pass_context
def list_users(ctx, query):
    """List and search all user profiles."""
    data = handle_request(ctx, 'GET', '/api/private/admin/users')
    if not data:
        console.print("No users found.")
        return
        
    query = query.lower()
    if query:
        filtered = []
        for u in data:
            searchable = f"{u.get('userId','')} {u.get('name','')} {u.get('email','')}".lower()
            if query in searchable:
                filtered.append(u)
        data = filtered
        
    console.print(f"[bold green]Loaded {len(data)} user profiles:[/]\n")
    if 'Console' in globals():
        table = Table(title="User Profiles")
        table.add_column("User ID", style="cyan")
        table.add_column("Name", style="magenta")
        table.add_column("Email", style="green")
        table.add_column("Quest Class", style="yellow")
        table.add_column("XP", style="blue")
        
        for u in data:
            table.add_row(
                str(u.get('userId', '')),
                str(u.get('name', '')),
                str(u.get('email', '')),
                str(u.get('questClass', 'NONE')),
                str(u.get('currentXp', 0))
            )
        console.print(table)
    else:
        for u in data:
            console.print(f"- [{u.get('userId')}] {u.get('name')} ({u.get('email')}) - Class: {u.get('questClass', 'NONE')} - XP: {u.get('currentXp', 0)}")

@users.command('get')
@click.argument('user_id')
@click.pass_context
def get_user(ctx, user_id):
    """Get details of a specific user profile."""
    data = handle_request(ctx, 'GET', f'/api/private/admin/users/{user_id}')
    console.print(f"[bold green]User Details for {user_id}:[/]\n")
    console.print(f"Name: {data.get('name')}")
    console.print(f"Email: {data.get('email')}")
    console.print(f"Quest Class: {data.get('questClass', 'NONE')}")
    console.print(f"Current XP: {data.get('currentXp', 0)}")
    history = data.get('history', [])
    if history:
        console.print(f"\n[bold]XP History ({len(history)} items):[/]")
        for item in history:
            console.print(f"  - {item.get('date')}: {item.get('xp', 0):+d} XP - {item.get('reason')}")

@users.command('add-xp')
@click.argument('user_id')
@click.argument('amount', type=int)
@click.argument('reason')
@click.option('--quest-class', help='Quest class to associate with the XP (e.g. DEVELOPER, DESIGNER).')
@click.pass_context
def add_xp(ctx, user_id, amount, reason, quest_class):
    """Reward or subtract XP points from a user."""
    payload = {"amount": amount, "reason": reason}
    if quest_class:
        payload["questClass"] = quest_class.upper()
    data = handle_request(ctx, 'POST', f'/api/private/admin/users/{user_id}/xp', json=payload)
    console.print(f"[bold green]XP updated successfully for {user_id}.[/]")
    console.print(f"New XP: {data.get('currentXp', 0)}")

@users.command('set-class')
@click.argument('user_id')
@click.argument('quest_class')
@click.pass_context
def set_class(ctx, user_id, quest_class):
    """Assign or update a user's Quest Class."""
    payload = {"questClass": quest_class.upper()}
    data = handle_request(ctx, 'POST', f'/api/private/admin/users/{user_id}/quest-class', json=payload)
    console.print(f"[bold green]Quest Class updated successfully for {user_id}.[/]")
    console.print(f"New Class: {data.get('questClass', 'NONE')}")

if __name__ == '__main__':
    cli()
