#!/usr/bin/env python3
"""
Validation script for endpoint authorization matrix.
Verifies structure, completeness, and data quality.
"""
import sys
import yaml
from pathlib import Path
from collections import Counter

REQUIRED_FIELDS = {'route', 'method', 'method_name', 'security_annotation', 'risk_level', 'source_file'}
VALID_RISK_LEVELS = {
    'LECTURA_PUBLICA',
    'LECTURA_AUTENTICADA',
    'ESCRITURA_USUARIO',
    'ESCRITURA_ADMIN',
    'DATOS_SENSIBLES',
    'CRITICAL_ADMIN_UNPROTECTED'
}

def validate_matrix(matrix_path: Path):
    """Validate the endpoint authorization matrix."""
    errors = []

    if not matrix_path.exists():
        return False, [f"Matrix file not found: {matrix_path}"]

    try:
        with open(matrix_path, 'r', encoding='utf-8') as f:
            data = yaml.safe_load(f)
    except Exception as e:
        return False, [f"Failed to parse YAML: {e}"]

    # Validate structure
    if not isinstance(data, dict):
        errors.append("Root element must be a dictionary")
        return False, errors

    if 'meta' not in data:
        errors.append("Missing 'meta' section")

    if 'endpoints' not in data:
        errors.append("Missing 'endpoints' section")
        return False, errors

    endpoints = data['endpoints']
    if not isinstance(endpoints, list):
        errors.append("'endpoints' must be a list")
        return False, errors

    if len(endpoints) == 0:
        errors.append("No endpoints found")
        return False, errors

    # Validate each endpoint
    risk_levels = Counter()
    for idx, endpoint in enumerate(endpoints):
        prefix = f"Endpoint {idx + 1}"

        # Check required fields
        missing = REQUIRED_FIELDS - set(endpoint.keys())
        if missing:
            errors.append(f"{prefix}: Missing fields: {missing}")

        # Validate risk level
        risk_level = endpoint.get('risk_level')
        if risk_level:
            risk_levels[risk_level] += 1
            if risk_level not in VALID_RISK_LEVELS:
                errors.append(f"{prefix}: Invalid risk level '{risk_level}'")

        # Validate route
        route = endpoint.get('route')
        if route and not route.startswith('/'):
            errors.append(f"{prefix}: Route must start with '/' (got: {route})")

        # Validate method
        method = endpoint.get('method')
        if method and method not in ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS']:
            errors.append(f"{prefix}: Invalid HTTP method '{method}'")

    # Report statistics
    print(f"✓ Total endpoints: {len(endpoints)}")
    print(f"✓ Risk level distribution:")
    for risk, count in sorted(risk_levels.items()):
        print(f"  - {risk}: {count}")

    return len(errors) == 0, errors

def main():
    """Main validation entry point."""
    matrix_path = Path(__file__).parent.parent / 'docs' / 'security' / 'endpoint-authorization-matrix.yaml'

    print(f"Validating authorization matrix: {matrix_path}")
    print("-" * 80)

    success, errors = validate_matrix(matrix_path)

    if errors:
        print("\n❌ Validation errors:")
        for error in errors:
            print(f"  - {error}")

    if success:
        print("\n✅ Validation passed!")
        return 0
    else:
        print("\n❌ Validation failed!")
        return 1

if __name__ == '__main__':
    sys.exit(main())
