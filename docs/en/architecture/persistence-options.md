# Persistence options - Eventflow

## Option A - Sidecar by Pod
### Pros
- Simplicity when unfolding along with each replica.
- Minimum latency when operating locally.
### cons
- Duplicate resources and status in each pod.
- Complex synchronization between replicas.
### When I would apply
- Low -scale deployments or development environments.

## Option B - Centralized Service
### Pros
- Strong consistency and concurrence control.
- Eventflow independent scale.
### cons
- Add a network latency.
- Requires high availability.
### When I would apply
- When multiple replicas are needed with immediate shared.

## Option C - Object Storage
### Pros
- Scalability practically unlimited.
- Reduced operating cost.
### cons
- Eventual consistency.
- Superior latency for reading/writing operations.
### Target State
- Long -term destination for mass loads and durability.

## Comparison
| Criteria | Option A - Sidecar | Option B - Centralized Service | Option C - Object Storage |
| --- | --- | --- | --- |
| Complexity | Baja | Average | Average |
| Latency | Minimal | Moderate | High |
| Consistency | Local, not shared | Strong | Eventual |
| Scalability | Linked to Pods | Independent | Unlimited |
| Operation | POD management | Unique Service | Managed Service |

## Decision
The ** option B - centralized service ** is adopted as an immediate solution because it offers strong consistency and allows you to climb without duplicating the State as in option A.