# Economy Module (Iteration 1)

This iteration introduces the backend foundation for rewards, shop purchases, inventory, and transaction history.

## Scope

- `HCoin` wallet per user.
- Shop catalog with purchasable items.
- User inventory with quantity and acquisition timestamps.
- Transaction ledger (`REWARD`, `PURCHASE`, `REFUND`, `ADJUSTMENT`).
- Guardrails for memory and persistent storage with alerting.

## Persistence model

- File-based state: `${homedir.data.dir}/economy-state.json`
- Uses existing `PersistenceService` with synchronous save for economic mutations.
- In-memory cache keeps only a bounded recent transaction window.
- Deep history is loaded on demand from disk when pagination requests older entries.

## Guardrails

Configured in `application.properties`:

- `economy.transactions.persisted-max`
- `economy.transactions.cache-max`
- `economy.storage.max-bytes`
- `economy.memory.max-users`
- `economy.memory.max-inventory-entries`
- `economy.inventory.user-max-items`
- `economy.guard.strict`
- `economy.guard.alert-cooldown`

When `economy.guard.strict=true`, mutation operations are blocked once a limit is hit and a warning alert is emitted.

## API endpoints

- `GET /api/economy/catalog`
- `GET /api/economy/wallet` (auth required)
- `GET /api/economy/inventory?limit=&offset=` (auth required)
- `GET /api/economy/transactions?limit=&offset=` (auth required)
- `POST /api/economy/purchase` body `{ "itemId": "profile-glow" }` (auth required)
- `GET /api/economy/stats` (admin required)

## Gamification integration

- Each successful XP award also credits HCoin using:
  - `economy.rewards.xp-to-hcoin-ratio`
  - `economy.rewards.min-hcoin`

If guardrails are active, XP awarding remains functional and only HCoin credit is skipped.
