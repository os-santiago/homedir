# Centralized persistence service

The implemented solution exposes an HTTP service dedicated to storing and recovering the state of Eventflow.

## Endpoints
- `Get /State /{key}`: obtains the state associated with `Key`.
- `Put /State /{Key}`: Create or replace the State.
- `Delete /State /{Key}`: Eliminates the State.
- `Post /locks /{key}` and `delete /locks /{key}`: Lock administration.

## ETAG and concurrence control
Each response Updates must send `if-mmetch` with the` eTag` received. If it does not coincide, the service returns `412 precondition failed` to avoid overwhelming.

## Locks
Optimistic and pessimistic locks are offered. Customers can request an explicit lock on a key to long -term operations. Locks automatically expire to prevent permanent blockages.

## Write-Ahead Log (Wal)
All mutations are recorded in a Wal. In case of failure, the service can reproduce tickets to restore the State. The Wal also serves as a source for replication.

## Events
When the State changes, the service publishes an `state.updated` event. This allows other Eventflow components to react to the modification and maintain materialized caches or views.