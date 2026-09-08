# Smart Stock Analyzer - Database Schema

## 1. Overview

The Smart Stock Analyzer uses a relational database to store and manage
user information, stock details, portfolios, and trading transactions.

The database is designed to maintain proper relationships between different
entities and support stock analysis and portfolio management.

## 2. Main Database Entities

The system contains the following major entities:

- Users
- Stocks
- Portfolios
- Transactions

## 3. Users Table

The Users table stores information about registered users.

| Column | Data Type | Description |
|---|---|---|
| user_id | INT | Unique ID of the user |
| name | VARCHAR(100) | User's name |
| email | VARCHAR(150) | User's email address |
| password | VARCHAR(255) | User login password |

**Primary Key:** `user_id`

## 4. Stocks Table

The Stocks table stores information about stocks available for analysis.

| Column | Data Type | Description |
|---|---|---|
| stock_id | INT | Unique ID of the stock |
| stock_name | VARCHAR(100) | Name of the stock |
| symbol | VARCHAR(20) | Stock market symbol |
| current_price | DECIMAL(10,2) | Current stock price |

**Primary Key:** `stock_id`

## 5. Portfolios Table

The Portfolios table stores the portfolios created by users.

| Column | Data Type | Description |
|---|---|---|
| portfolio_id | INT | Unique ID of the portfolio |
| user_id | INT | ID of the portfolio owner |
| portfolio_name | VARCHAR(100) | Name of the portfolio |

**Primary Key:** `portfolio_id`

**Foreign Key:** `user_id` references `Users(user_id)`

## 6. Transactions Table

The Transactions table records stock buying and selling activities.

| Column | Data Type | Description |
|---|---|---|
| transaction_id | INT | Unique transaction ID |
| user_id | INT | ID of the user |
| stock_id | INT | ID of the stock |
| portfolio_id | INT | ID of the portfolio |
| transaction_type | VARCHAR(10) | BUY or SELL |
| quantity | INT | Number of shares |
| price | DECIMAL(10,2) | Price per share |
| transaction_date | DATE | Date of transaction |

**Primary Key:** `transaction_id`

**Foreign Keys:**

- `user_id` references `Users(user_id)`
- `stock_id` references `Stocks(stock_id)`
- `portfolio_id` references `Portfolios(portfolio_id)`

## 7. Relationships

The main relationships are:

```text
Users
  |
  +---- Portfolios
  |
  +---- Transactions
             |
             +---- Stocks
