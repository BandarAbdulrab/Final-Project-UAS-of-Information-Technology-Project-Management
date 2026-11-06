-- ============================================================
-- Kharismart Cashier, Finance and Stock Management System
-- Toko Kharisma  (SRS Revision B)
-- Target: SQLite (Android, accessed through Room) | 10 tables
--
-- Conventions (SRS 3.3.2):
--   * Primary keys: INTEGER PRIMARY KEY AUTOINCREMENT
--   * Foreign keys enforced: ON UPDATE CASCADE ON DELETE RESTRICT
--   * Money and quantities: whole INTEGER (Rupiah, no decimals)
--   * Booleans: INTEGER 0/1 | Dates: TEXT 'yyyy-MM-dd HH:mm:ss' (WIB)
--   * Restricted values: TEXT + CHECK
--
-- Use this script to inspect/test the database in "DB Browser for
-- SQLite" or the sqlite3 shell. In the app itself the same schema is
-- created by Room from the entity classes (KharismartDatabase.kt).
-- ============================================================

PRAGMA foreign_keys = ON;

DROP TABLE IF EXISTS debt_payments;
DROP TABLE IF EXISTS debts;
DROP TABLE IF EXISTS stock_movements;
DROP TABLE IF EXISTS transaction_items;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS finance;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS settings;
DROP TABLE IF EXISTS users;

-- ------------------------------------------------------------
-- D4  users
-- ------------------------------------------------------------
CREATE TABLE users (
  user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
  username      TEXT    NOT NULL UNIQUE,
  pin_hash      TEXT    NOT NULL,                 -- salted hash of the 4-6 digit PIN
  name          TEXT    NOT NULL,
  role          TEXT    NOT NULL CHECK (role IN ('admin','cashier')),
  status        INTEGER NOT NULL DEFAULT 1 CHECK (status IN (0,1)),
  recovery_hash TEXT                              -- admin only, one-time recovery code hash
);

-- ------------------------------------------------------------
-- D7  categories
-- ------------------------------------------------------------
CREATE TABLE categories (
  category_id INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT    NOT NULL UNIQUE,
  status      INTEGER NOT NULL DEFAULT 1 CHECK (status IN (0,1))
);

-- ------------------------------------------------------------
-- D1  products
-- ------------------------------------------------------------
CREATE TABLE products (
  product_id  INTEGER PRIMARY KEY AUTOINCREMENT,
  barcode     TEXT    UNIQUE,                     -- NULL for products without barcode
  name        TEXT    NOT NULL,
  category_id INTEGER NOT NULL,
  price       INTEGER NOT NULL CHECK (price > 0),
  cost_price  INTEGER NOT NULL DEFAULT 0 CHECK (cost_price >= 0),
  stock       INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
  min_stock   INTEGER CHECK (min_stock IS NULL OR min_stock >= 0),  -- NULL = settings.default_min_stock
  photo       TEXT,                               -- file name in app private storage
  status      INTEGER NOT NULL DEFAULT 1 CHECK (status IN (0,1)),
  FOREIGN KEY (category_id) REFERENCES categories (category_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
);

-- ------------------------------------------------------------
-- D2  transactions
-- ------------------------------------------------------------
CREATE TABLE transactions (
  transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL,                -- user who served the sale
  receipt_no     TEXT    NOT NULL UNIQUE,         -- TRX-yyyymmdd-nnn, sequential per day
  date           TEXT    NOT NULL,
  method         TEXT    NOT NULL CHECK (method IN ('cash','qris','credit')),
  total          INTEGER NOT NULL CHECK (total > 0),
  paid           INTEGER NOT NULL CHECK (paid >= 0),
  change_due     INTEGER NOT NULL DEFAULT 0 CHECK (change_due >= 0),
  total_cost     INTEGER NOT NULL DEFAULT 0 CHECK (total_cost >= 0),
  gross_profit   INTEGER NOT NULL DEFAULT 0,      -- total - total_cost, may be negative
  note           TEXT,
  FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  -- BR-03 / BR-04 / BR-04b
  CHECK (method <> 'cash'   OR (paid >= total AND change_due = paid - total)),
  CHECK (method <> 'qris'   OR (paid = total  AND change_due = 0)),
  CHECK (method <> 'credit' OR (paid = 0      AND change_due = 0)),
  -- BR-10
  CHECK (gross_profit = total - total_cost)
);

-- ------------------------------------------------------------
-- D3  transaction_items
--     price and cost_price are copied at sale time (BR-06)
-- ------------------------------------------------------------
CREATE TABLE transaction_items (
  item_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id INTEGER NOT NULL,
  product_id     INTEGER NOT NULL,
  quantity       INTEGER NOT NULL CHECK (quantity > 0),
  price          INTEGER NOT NULL CHECK (price > 0),
  cost_price     INTEGER NOT NULL DEFAULT 0 CHECK (cost_price >= 0),
  subtotal       INTEGER NOT NULL,
  FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (product_id) REFERENCES products (product_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CHECK (subtotal = price * quantity)             -- BR-02
);

-- ------------------------------------------------------------
-- D8  stock_movements (every stock change, BR-08)
-- ------------------------------------------------------------
CREATE TABLE stock_movements (
  movement_id     INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id      INTEGER NOT NULL,
  user_id         INTEGER NOT NULL,
  transaction_id  INTEGER,                        -- only for type = 'sale'
  date            TEXT    NOT NULL,
  type            TEXT    NOT NULL CHECK (type IN ('initial','restock','sale','adjustment')),
  quantity_change INTEGER NOT NULL CHECK (quantity_change <> 0),
  stock_after     INTEGER NOT NULL CHECK (stock_after >= 0),
  unit_cost       INTEGER CHECK (unit_cost IS NULL OR unit_cost >= 0),   -- restock only
  total_cost      INTEGER CHECK (total_cost IS NULL OR total_cost >= 0), -- restock only = quantity x unit_cost
  note            TEXT,                           -- required for an adjustment
  FOREIGN KEY (product_id) REFERENCES products (product_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CHECK (type <> 'sale' OR (transaction_id IS NOT NULL AND quantity_change < 0)),
  CHECK (type <> 'restock' OR quantity_change > 0),
  CHECK (type <> 'adjustment' OR (note IS NOT NULL AND length(trim(note)) > 0))
);

-- ------------------------------------------------------------
-- D9  debts (customer debt notes)
--     transaction_id filled = created by a POS credit sale (max 1 per
--     transaction, UNIQUE); NULL = manual debt note (BR-13)
-- ------------------------------------------------------------
CREATE TABLE debts (
  debt_id        INTEGER PRIMARY KEY AUTOINCREMENT,
  transaction_id INTEGER UNIQUE,
  user_id        INTEGER NOT NULL,
  customer_name  TEXT    NOT NULL CHECK (length(trim(customer_name)) > 0),
  date           TEXT    NOT NULL,
  total_amount   INTEGER NOT NULL CHECK (total_amount > 0),
  paid_amount    INTEGER NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
  status         TEXT    NOT NULL DEFAULT 'unpaid' CHECK (status IN ('unpaid','paid')),
  note           TEXT,
  FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CHECK (paid_amount <= total_amount),
  CHECK ((status = 'paid') = (paid_amount = total_amount))
);

-- ------------------------------------------------------------
-- D10  debt_payments
-- ------------------------------------------------------------
CREATE TABLE debt_payments (
  payment_id INTEGER PRIMARY KEY AUTOINCREMENT,
  debt_id    INTEGER NOT NULL,
  user_id    INTEGER NOT NULL,                    -- user who received the payment
  date       TEXT    NOT NULL,
  method     TEXT    NOT NULL CHECK (method IN ('cash','qris')),
  amount     INTEGER NOT NULL CHECK (amount > 0),
  note       TEXT,
  FOREIGN KEY (debt_id) REFERENCES debts (debt_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
);

-- ------------------------------------------------------------
-- D6  finance (manual other income and operating expenses)
--     sales, debt payments and restock purchases are NOT entered here
-- ------------------------------------------------------------
CREATE TABLE finance (
  finance_id  INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL,
  date        TEXT    NOT NULL,
  type        TEXT    NOT NULL CHECK (type IN ('income','expense')),
  category    TEXT    NOT NULL,
  amount      INTEGER NOT NULL CHECK (amount > 0),
  description TEXT,
  FOREIGN KEY (user_id) REFERENCES users (user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
);

-- ------------------------------------------------------------
-- D5  settings (single configuration row, settings_id = 1)
-- ------------------------------------------------------------
CREATE TABLE settings (
  settings_id       INTEGER PRIMARY KEY CHECK (settings_id = 1),
  store_name        TEXT    NOT NULL,
  store_address     TEXT,
  receipt_footer    TEXT,
  default_min_stock INTEGER NOT NULL DEFAULT 5 CHECK (default_min_stock >= 0),
  qris_bank         TEXT,
  qris_owner_name   TEXT,
  qris_image        TEXT,                         -- NULL = QRIS not configured
  target_daily      INTEGER CHECK (target_daily   IS NULL OR target_daily   >= 0),
  target_weekly     INTEGER CHECK (target_weekly  IS NULL OR target_weekly  >= 0),
  target_monthly    INTEGER CHECK (target_monthly IS NULL OR target_monthly >= 0),
  auto_lock_minutes INTEGER NOT NULL DEFAULT 5 CHECK (auto_lock_minutes > 0),
  backup_folder     TEXT,                         -- Android folder URI, NULL = no auto backup
  last_backup_at    TEXT
);

-- ------------------------------------------------------------
-- Indexes (SRS 3.3.2 + foreign-key columns)
-- ------------------------------------------------------------
CREATE INDEX idx_transactions_date           ON transactions (date);
CREATE INDEX idx_transactions_user           ON transactions (user_id);
CREATE INDEX idx_finance_date                ON finance (date);
CREATE INDEX idx_finance_user                ON finance (user_id);
CREATE INDEX idx_stock_movements_product_date ON stock_movements (product_id, date);
CREATE INDEX idx_stock_movements_user        ON stock_movements (user_id);
CREATE INDEX idx_stock_movements_transaction ON stock_movements (transaction_id);
CREATE INDEX idx_transaction_items_trx       ON transaction_items (transaction_id);
CREATE INDEX idx_transaction_items_product   ON transaction_items (product_id);
CREATE INDEX idx_products_category           ON products (category_id);
CREATE INDEX idx_debts_status                ON debts (status);
CREATE INDEX idx_debts_user                  ON debts (user_id);
CREATE INDEX idx_debt_payments_debt          ON debt_payments (debt_id);
CREATE INDEX idx_debt_payments_user          ON debt_payments (user_id);

-- ============================================================
-- INITIAL DATA (Appendix D)
-- The admin account is created by the first-run setup screen
-- (PIN + one-time recovery code), so users starts empty.
-- ============================================================
INSERT INTO categories (name, status) VALUES
  ('Sembako', 1), ('Jajanan', 1), ('Rokok', 1), ('Kebutuhan Harian', 1);

INSERT INTO settings
  (settings_id, store_name, store_address, receipt_footer, default_min_stock, auto_lock_minutes)
VALUES
  (1, 'Toko Kharisma', NULL, 'Thank you', 5, 5);
