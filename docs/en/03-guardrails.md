# Security rails

## Units
- Core does not matter other Core.
- app does not depend on adaptrs.
- Core is Framework-Free.
- [Archunit] (https://www.archunit.org/) mandatory.

## Resources per module
- Datasources, Pools, Caches and Threads dedicated.
- Strictly configurable limits.

## Resilience
- Fault Tolerance (Timeouts, Person, Circuit Breaker).
-Rate-Limit and Kill-Switches per module.

## Operation
- Health and Readiness per module.
- Metrics and traces labeled with `Module`.