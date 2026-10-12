# Smart Stock Analyzer — Database Schema Notes

Based on the project reports; verify names against the actual schema used by your Java application.

| Table | Purpose | Typical fields described in reports |
|---|---|---|
| `Users` | Authentication | `user_id` (PK), `name`, `email` (UNIQUE), `password_hash`, `created_at` |
| `Stocks` | Stock master data | `stock_id` (PK), `symbol` (UNIQUE), `company`, `current_price`, `available_qty` |
| `Portfolio` | Current user holdings | `user_id` (FK), `stock_id` (FK), `quantity`, `avg_buy_price` |
| `Transactions` | Buy/sell ledger | `txn_id` (PK), `user_id` (FK), `stock_id` (FK), `type`, `quantity`, `price`, `txn_time` |
| `Logs` | Audit events/risk decisions | `log_id` (PK), `user_id` (FK), `event`, `risk_score`, `created_at` |
| `price_history` | Historical prices, if persisted | Confirm whether this table exists and its actual fields |

## Relationships and integrity
- One user can have many portfolio rows and transactions.
- One stock can appear in many portfolios and transactions.
- Foreign keys should prevent references to missing users/stocks.
- Email and stock symbol should be unique.
- Quantities and prices should be positive where applicable.
- Available stock and portfolio quantities should never become negative.
- Trade, portfolio update and ledger insertion should commit or roll back together.

This file documents the design only; it does not create or migrate tables. Update it to match the actual Java database configuration.
