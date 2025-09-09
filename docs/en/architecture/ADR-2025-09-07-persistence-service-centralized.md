# ADR 2025-09-07: centralized persistence

## Context
Eventflow needs to climb multiple replicas and persist shared. Three options were evaluated: Sidecar, Centralized Service and Object Storage.

## Decision
Adopt a ** centralized persistence service ** (solution b) in this phase.

## Consequences
- ** Positive: ** Strong consistency and simpler operation.
- ** Positive: ** Independent Scale of Eventflow.
- ** Negative: ** Extra network latency.
- ** Future: ** Planned Migration to Object Storage (S3/Minio).

## State
Accepted