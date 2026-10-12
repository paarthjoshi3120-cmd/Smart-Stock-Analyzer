# Smart Stock Analyzer — API Documentation Template

The Phase-II report mentions `/api/prices` and says broader REST endpoints were pending. Verify routes in the actual Java code before marking any endpoint implemented.

| Feature | Example method/path | Status |
|---|---|---|
| View stock prices | `GET /api/prices` | Mentioned in Phase-II report; verify Java route |
| Register | `POST /api/register` | Verify actual route |
| Login | `POST /api/login` | Verify actual route |
| Buy shares | `POST /api/trades/buy` | Example only; confirm route |
| Sell shares | `POST /api/trades/sell` | Example only; confirm route |
| Portfolio | `GET /api/portfolio` | Example only; confirm route |
| Transaction history | `GET /api/transactions` | Example only; confirm route |

## Example success response
```json
{
  "success": true,
  "message": "Trade completed",
  "data": {}
}
```

## Example validation error
```json
{
  "success": false,
  "message": "Quantity must be greater than zero"
}
```

These JSON bodies are examples, not claims about the current response format.

## API checklist
- Validate inputs on the server.
- Require authentication for portfolio, trade and history operations.
- Ensure users can access only their own portfolio and transactions.
- Perform trade and ledger updates atomically.
- Return useful errors without exposing stack traces or secrets.
- Document request fields, response fields, status codes and authentication.
