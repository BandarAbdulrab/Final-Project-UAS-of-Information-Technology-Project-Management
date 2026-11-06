-- ============================================================
-- Kharismart - DEMO DATA (2+ rows per table)
-- Run AFTER kharismart_schema.sql (same SQLite database).
-- Demo only: replace with real data through the app.
-- Demo PINs: admin 'sumarti' = 123456 | cashier 'rizki' = 4321
--            admin recovery code = RECOVERY-0001
-- Money is whole Rupiah; dates are WIB.
-- ============================================================
PRAGMA foreign_keys = ON;

-- 1. users (hashes are bcrypt; the real app may use PBKDF2/bcrypt)
INSERT INTO users (user_id, username, pin_hash, name, role, status, recovery_hash) VALUES
  (1, 'sumarti', '$2b$10$b1RalILC85le4K/uR6a6ce2Naoup1FZEs/9UI2Kcf/2BjTZF7q3yq', 'Ibu Sumarti', 'admin',   1,
      '$2b$10$a8ZWJOGleKUYfnbTJWvvfeHYy4WnFIXOgqnIzXpVnGc5V2Li2rofC'),
  (2, 'rizki',   '$2b$10$8unIpEns1os5qsxmFFxsO.8GmUXqS8ApggwwbM9hHejD8e2xpzhIy', 'Rizki (Son)', 'cashier', 1, NULL);

-- 2. categories: the 4 defaults already exist (schema seed): 1 Sembako, 2 Jajanan, 3 Rokok, 4 Kebutuhan Harian

-- 3. products (final stock = result of the stock movements below)
INSERT INTO products (product_id, barcode, name, category_id, price, cost_price, stock, min_stock, photo, status) VALUES
  (1, '8990000000011', 'Indomie Goreng',        1,  3500,  3000,  97, NULL, NULL, 1),
  (2, '8990000000028', 'Beras 5 kg',            1, 68000, 62000,  19,    5, NULL, 1),
  (3, '8990000000035', 'Chitato 68 g',          2, 11000,  9500,  28, NULL, NULL, 1),
  (4, '8990000000042', 'Teh Botol Sosro 450 ml',4,  5000,  3800,  33, NULL, NULL, 1);

-- 4. transactions: cash, QRIS and credit (BR-03, BR-04, BR-04b, BR-10)
INSERT INTO transactions
  (transaction_id, user_id, receipt_no, date, method, total, paid, change_due, total_cost, gross_profit, note)
VALUES
  (1, 2, 'TRX-20261105-001', '2026-11-05 10:05:12', 'cash',   18000, 20000, 2000, 15500,  2500, NULL),
  (2, 2, 'TRX-20261105-002', '2026-11-05 10:30:40', 'qris',   68000, 68000,    0, 62000,  6000, NULL),
  (3, 2, 'TRX-20261105-003', '2026-11-05 11:15:05', 'credit', 18500,     0,    0, 14400,  4100, 'Bu Ani - due 12/11/2026');

-- 5. transaction_items (price and cost copied at sale time)
INSERT INTO transaction_items (item_id, transaction_id, product_id, quantity, price, cost_price, subtotal) VALUES
  (1, 1, 1, 2,  3500,  3000,  7000),
  (2, 1, 3, 1, 11000,  9500, 11000),
  (3, 2, 2, 1, 68000, 62000, 68000),
  (4, 3, 4, 3,  5000,  3800, 15000),
  (5, 3, 1, 1,  3500,  3000,  3500);

-- 6. stock_movements (after transactions, because sales refer to them): initial, restock, adjustment, then sales
INSERT INTO stock_movements
  (movement_id, product_id, user_id, transaction_id, date, type, quantity_change, stock_after, unit_cost, total_cost, note)
VALUES
  ( 1, 1, 1, NULL, '2026-11-01 08:00:00', 'initial',     100, 100, NULL, NULL, 'Initial stock'),
  ( 2, 2, 1, NULL, '2026-11-01 08:00:00', 'initial',      20,  20, NULL, NULL, 'Initial stock'),
  ( 3, 3, 1, NULL, '2026-11-01 08:00:00', 'initial',      30,  30, NULL, NULL, 'Initial stock'),
  ( 4, 4, 1, NULL, '2026-11-01 08:00:00', 'initial',      24,  24, NULL, NULL, 'Initial stock'),
  ( 5, 4, 1, NULL, '2026-11-05 08:00:00', 'restock',      12,  36, 3600, 43200, 'Delivery from supplier'),
  ( 6, 3, 1, NULL, '2026-11-05 08:10:00', 'adjustment',   -1,  29, NULL, NULL, 'Damaged pack found during count'),
  ( 7, 1, 2,    1, '2026-11-05 10:05:12', 'sale',         -2,  98, NULL, NULL, NULL),
  ( 8, 3, 2,    1, '2026-11-05 10:05:12', 'sale',         -1,  28, NULL, NULL, NULL),
  ( 9, 2, 2,    2, '2026-11-05 10:30:40', 'sale',         -1,  19, NULL, NULL, NULL),
  (10, 4, 2,    3, '2026-11-05 11:15:05', 'sale',         -3,  33, NULL, NULL, NULL),
  (11, 1, 2,    3, '2026-11-05 11:15:05', 'sale',         -1,  97, NULL, NULL, NULL);

-- 7. debts: #1 created automatically by credit sale #3, #2 manual note
INSERT INTO debts (debt_id, transaction_id, user_id, customer_name, date, total_amount, paid_amount, status, note) VALUES
  (1,    3, 2, 'Bu Ani',   '2026-11-05 11:15:05', 18500, 10000, 'unpaid', 'Due 12/11/2026'),
  (2, NULL, 1, 'Pak Budi', '2026-10-28 16:00:00', 50000, 50000, 'paid',   'Old IOU from last month');

-- 8. debt_payments
INSERT INTO debt_payments (payment_id, debt_id, user_id, date, method, amount, note) VALUES
  (1, 1, 2, '2026-11-05 15:00:00', 'cash', 10000, 'Partial payment'),
  (2, 2, 1, '2026-11-03 09:30:00', 'qris', 50000, 'Paid in full');

-- 9. finance: manual expense and other income
INSERT INTO finance (finance_id, user_id, date, type, category, amount, description) VALUES
  (1, 1, '2026-11-05 09:00:00', 'expense', 'Electricity', 150000, 'October electricity bill'),
  (2, 1, '2026-11-05 09:05:00', 'income',  'Other income', 20000, 'Sale of empty sacks');

-- 10. settings: single row, filled in with the owner's data
UPDATE settings
SET store_address   = 'Toko Kharisma, [address to be filled in]',
    receipt_footer  = 'Thank you, see you again!',
    qris_bank       = 'BRI',
    qris_owner_name = 'Sumarti',
    qris_image      = 'qris.png',
    target_daily    = 500000,
    target_weekly   = 3500000,
    target_monthly  = 15000000,
    last_backup_at  = '2026-11-05 07:00:00'
WHERE settings_id = 1;

-- ============================================================
-- CHECK QUERIES (optional)
-- ============================================================

-- A. NF08: stock = last stock_after = sum of movements (every row must show 1)
SELECT p.product_id, p.name, p.stock,
       p.stock = (SELECT SUM(quantity_change) FROM stock_movements WHERE product_id = p.product_id) AS sum_ok,
       p.stock = (SELECT stock_after FROM stock_movements WHERE product_id = p.product_id
                  ORDER BY date DESC, movement_id DESC LIMIT 1)                                     AS last_ok
FROM products p;

-- B. Stock status (BR-09): Habis / Menipis / Aman
SELECT p.name, p.stock,
       CASE WHEN p.stock = 0 THEN 'Habis'
            WHEN p.stock <= COALESCE(p.min_stock, s.default_min_stock) THEN 'Menipis'
            ELSE 'Aman' END AS status
FROM products p, settings s;

-- C. Transaction totals match their items (every row must show 1)
SELECT t.receipt_no,
       t.total      = (SELECT SUM(subtotal) FROM transaction_items WHERE transaction_id = t.transaction_id) AS total_ok,
       t.total_cost = (SELECT SUM(cost_price * quantity) FROM transaction_items WHERE transaction_id = t.transaction_id) AS cost_ok
FROM transactions t;

-- D. Report for 2026-11-05 (BR-14): revenue, gross profit, cash/QRIS received, outstanding debt
SELECT
  (SELECT SUM(total)        FROM transactions WHERE date(date) = '2026-11-05')                         AS revenue,        -- 104500
  (SELECT SUM(gross_profit) FROM transactions WHERE date(date) = '2026-11-05')                         AS gross_profit,   -- 12600
  (SELECT SUM(paid - change_due) FROM transactions WHERE method = 'cash' AND date(date) = '2026-11-05')
   + (SELECT COALESCE(SUM(amount),0) FROM debt_payments WHERE method = 'cash' AND date(date) = '2026-11-05') AS cash_received, -- 28000
  (SELECT SUM(paid)         FROM transactions WHERE method = 'qris' AND date(date) = '2026-11-05')     AS qris_received,  -- 68000
  (SELECT SUM(total_amount - paid_amount) FROM debts WHERE status = 'unpaid')                          AS outstanding_debt; -- 8500

-- E. Debts with their origin (F13)
SELECT d.debt_id, d.customer_name, d.total_amount, d.paid_amount, d.status,
       CASE WHEN d.transaction_id IS NULL THEN 'Manual Entry'
            ELSE 'POS Sale (' || t.receipt_no || ')' END AS origin
FROM debts d LEFT JOIN transactions t ON t.transaction_id = d.transaction_id;

-- F. Row counts
SELECT 'users' t, COUNT(*) n FROM users UNION ALL SELECT 'categories', COUNT(*) FROM categories
UNION ALL SELECT 'products', COUNT(*) FROM products UNION ALL SELECT 'transactions', COUNT(*) FROM transactions
UNION ALL SELECT 'transaction_items', COUNT(*) FROM transaction_items
UNION ALL SELECT 'stock_movements', COUNT(*) FROM stock_movements
UNION ALL SELECT 'debts', COUNT(*) FROM debts UNION ALL SELECT 'debt_payments', COUNT(*) FROM debt_payments
UNION ALL SELECT 'finance', COUNT(*) FROM finance UNION ALL SELECT 'settings', COUNT(*) FROM settings;
