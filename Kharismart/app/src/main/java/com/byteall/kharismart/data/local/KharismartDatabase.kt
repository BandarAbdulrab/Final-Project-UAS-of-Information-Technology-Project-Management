package com.byteall.kharismart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.byteall.kharismart.BuildConfig
import com.byteall.kharismart.data.local.dao.AnalyticsDao
import com.byteall.kharismart.data.local.dao.CategoryDao
import com.byteall.kharismart.data.local.dao.DebtDao
import com.byteall.kharismart.data.local.dao.FinanceDao
import com.byteall.kharismart.data.local.dao.ProductDao
import com.byteall.kharismart.data.local.dao.SaleDao
import com.byteall.kharismart.data.local.dao.SettingsDao
import com.byteall.kharismart.data.local.dao.UserDao
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.DebtEntity
import com.byteall.kharismart.data.local.entity.DebtPaymentEntity
import com.byteall.kharismart.data.local.entity.FinanceEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.SettingsEntity
import com.byteall.kharismart.data.local.entity.StockMovementEntity
import com.byteall.kharismart.data.local.entity.TransactionEntity
import com.byteall.kharismart.data.local.entity.TransactionItemEntity
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.util.security.PasswordHasher
import com.byteall.kharismart.util.time.WibClock

@Database(
    entities = [
        CategoryEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        FinanceEntity::class,
        ProductEntity::class,
        SettingsEntity::class,
        StockMovementEntity::class,
        TransactionEntity::class,
        TransactionItemEntity::class,
        UserEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KharismartDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun debtDao(): DebtDao
    abstract fun financeDao(): FinanceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        const val DATABASE_NAME = "kharismart.db"

        @Volatile
        private var INSTANCE: KharismartDatabase? = null

        fun getInstance(context: Context): KharismartDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KharismartDatabase::class.java,
                    DATABASE_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(seedCallback(seedDemoData = BuildConfig.SEED_DEMO_DATA))
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun buildInMemory(
            context: Context,
            seedDemoData: Boolean = false
        ): KharismartDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                KharismartDatabase::class.java
            )
                .addCallback(seedCallback(seedDemoData = seedDemoData))
                .allowMainThreadQueries()
                .build()
        }

        private fun seedCallback(seedDemoData: Boolean): Callback {
            return object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)

                    // 1. Seed default categories
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Sembako', 1)")
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Jajanan', 1)")
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Rokok', 1)")
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Kebutuhan Harian', 1)")

                    // 2. Seed single settings row (settings_id = 1)
                    db.execSQL(
                        """
                        INSERT INTO settings (settings_id, store_name, receipt_footer, default_min_stock, auto_lock_minutes)
                        VALUES (1, 'Toko Kharisma', 'Thank you', 5, 5)
                        """.trimIndent()
                    )

                    // 3. Seed 2 starter users (Admin + Cashier)
                    val adminPinHash = PasswordHasher.hashPin(DemoSeed.ADMIN_PIN)
                    val adminRecoveryHash = PasswordHasher.hashRecoveryCode(DemoSeed.ADMIN_RECOVERY_CODE)
                    val cashierPinHash = PasswordHasher.hashPin(DemoSeed.CASHIER_PIN)

                    db.execSQL(
                        "INSERT INTO users (username, pin_hash, name, role, status, recovery_hash) VALUES (?, ?, ?, ?, 1, ?)",
                        arrayOf<Any?>(
                            DemoSeed.ADMIN_USERNAME,
                            adminPinHash,
                            DemoSeed.ADMIN_NAME,
                            Role.ADMIN,
                            adminRecoveryHash
                        )
                    )

                    db.execSQL(
                        "INSERT INTO users (username, pin_hash, name, role, status, recovery_hash) VALUES (?, ?, ?, ?, 1, NULL)",
                        arrayOf<Any?>(
                            DemoSeed.CASHIER_USERNAME,
                            cashierPinHash,
                            DemoSeed.CASHIER_NAME,
                            Role.CASHIER
                        )
                    )

                    // 4. Create database integrity triggers
                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS trg_products_stock_ins
                        BEFORE INSERT ON products
                        FOR EACH ROW
                        WHEN NEW.stock < 0
                        BEGIN
                            SELECT RAISE(ABORT, 'stock must be >= 0');
                        END;
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS trg_products_stock_upd
                        BEFORE UPDATE ON products
                        FOR EACH ROW
                        WHEN NEW.stock < 0
                        BEGIN
                            SELECT RAISE(ABORT, 'stock must be >= 0');
                        END;
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        CREATE TRIGGER IF NOT EXISTS trg_settings_single
                        BEFORE INSERT ON settings
                        FOR EACH ROW
                        WHEN NEW.settings_id != 1 OR (SELECT COUNT(*) FROM settings) > 0
                        BEGIN
                            SELECT RAISE(ABORT, 'settings is a single-row table');
                        END;
                        """.trimIndent()
                    )

                    // 5. Prefill demonstration dataset ONLY in the 'demo' flavor
                    if (seedDemoData) {
                        seedDemonstrationData(db)
                    }
                }
            }
        }

        private fun seedDemonstrationData(db: SupportSQLiteDatabase) {
            val now = WibClock.nowString()
            val receiptDate = WibClock.receiptDatePart()

            // Update settings with demo identity, QRIS metadata, and revenue targets
            db.execSQL(
                """
                UPDATE settings
                SET store_name = 'Toko Kharisma (Demo)',
                    store_address = 'Jl. Raya Purbalingga No. 12, Jawa Tengah',
                    receipt_footer = 'Terima kasih telah berbelanja di Toko Kharisma!',
                    qris_bank = 'BCA',
                    qris_owner_name = 'Toko Kharisma',
                    target_daily = 500000,
                    target_weekly = 3500000,
                    target_monthly = 15000000
                WHERE settings_id = 1
                """.trimIndent()
            )

            // Seed 8 demo products across all 4 categories and all 3 stock statuses (Aman, Menipis, Habis)
            val products = listOf(
                arrayOf<Any?>("8991001000001", "Beras Rojolele 5kg", 1L, 68000L, 58000L, 18, 5),
                arrayOf<Any?>("8991001000002", "Minyak Goreng Bimoli 1L", 1L, 18000L, 15000L, 4, 5),
                arrayOf<Any?>("8991001000003", "Gula Pasir Gulaku 1kg", 1L, 17500L, 14500L, 0, 5),
                arrayOf<Any?>("8991002000004", "Indomie Goreng Original", 2L, 3500L, 2800L, 46, 10),
                arrayOf<Any?>("8991002000005", "Teh Pucuk Harum 350ml", 2L, 4000L, 2900L, 30, 10),
                arrayOf<Any?>("8991002000006", "Chitato Sapi Panggang 68g", 2L, 11000L, 8500L, 3, 5),
                arrayOf<Any?>("8991003000007", "Gudang Garam Surya 12", 3L, 26000L, 23500L, 22, 5),
                arrayOf<Any?>("8991004000008", "Sabun Lifebuoy Cair 450ml", 4L, 24000L, 19000L, 12, 4)
            )
            products.forEachIndexed { idx, p ->
                db.execSQL(
                    """
                    INSERT INTO products (barcode, name, category_id, price, cost_price, stock, min_stock, photo, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 1)
                    """.trimIndent(),
                    p
                )
                val productId = idx + 1L
                val stock = p[5] as Int
                val cost = p[4] as Long
                if (stock > 0) {
                    db.execSQL(
                        """
                        INSERT INTO stock_movements (product_id, user_id, transaction_id, date, type, quantity_change, stock_after, unit_cost, total_cost, note)
                        VALUES (?, 1, NULL, ?, ?, ?, ?, ?, NULL, 'Stok awal demo')
                        """.trimIndent(),
                        arrayOf<Any?>(productId, now, MovementType.INITIAL, stock, stock, cost)
                    )
                }
            }

            // Sample restock movement (10 units of Indomie Goreng @ Rp2.800 = Rp28.000)
            db.execSQL(
                """
                INSERT INTO stock_movements (product_id, user_id, transaction_id, date, type, quantity_change, stock_after, unit_cost, total_cost, note)
                VALUES (4, 1, NULL, ?, ?, 10, 46, 2800, 28000, 'Restock mingguan supplier')
                """.trimIndent(),
                arrayOf<Any?>(now, MovementType.RESTOCK)
            )

            // Transaction 1: Cash sale (Cashier Rizki, user_id = 2)
            val trx1No = "TRX-$receiptDate-001"
            db.execSQL(
                """
                INSERT INTO transactions (user_id, receipt_no, date, method, total, paid, change_due, total_cost, gross_profit, note)
                VALUES (2, ?, ?, ?, 22000, 50000, 28000, 17000, 5000, NULL)
                """.trimIndent(),
                arrayOf<Any?>(trx1No, now, PaymentMethod.CASH)
            )
            db.execSQL(
                "INSERT INTO transaction_items (transaction_id, product_id, quantity, price, cost_price, subtotal) VALUES (1, 4, 4, 3500, 2800, 14000)"
            )
            db.execSQL(
                "INSERT INTO transaction_items (transaction_id, product_id, quantity, price, cost_price, subtotal) VALUES (1, 5, 2, 4000, 2900, 8000)"
            )

            // Transaction 2: QRIS sale (Admin Ibu Sumarti, user_id = 1)
            val trx2No = "TRX-$receiptDate-002"
            db.execSQL(
                """
                INSERT INTO transactions (user_id, receipt_no, date, method, total, paid, change_due, total_cost, gross_profit, note)
                VALUES (1, ?, ?, ?, 86000, 86000, 0, 73000, 13000, 'Pembayaran QRIS lunas')
                """.trimIndent(),
                arrayOf<Any?>(trx2No, now, PaymentMethod.QRIS)
            )
            db.execSQL(
                "INSERT INTO transaction_items (transaction_id, product_id, quantity, price, cost_price, subtotal) VALUES (2, 1, 1, 68000, 58000, 68000)"
            )
            db.execSQL(
                "INSERT INTO transaction_items (transaction_id, product_id, quantity, price, cost_price, subtotal) VALUES (2, 2, 1, 18000, 15000, 18000)"
            )

            // Transaction 3: Credit sale (Admin Ibu Sumarti, user_id = 1, Customer: Bu Hirah)
            val trx3No = "TRX-$receiptDate-003"
            db.execSQL(
                """
                INSERT INTO transactions (user_id, receipt_no, date, method, total, paid, change_due, total_cost, gross_profit, note)
                VALUES (1, ?, ?, ?, 76000, 0, 0, 66000, 10000, 'Bayar akhir minggu')
                """.trimIndent(),
                arrayOf<Any?>(trx3No, now, PaymentMethod.CREDIT)
            )
            db.execSQL(
                "INSERT INTO transaction_items (transaction_id, product_id, quantity, price, cost_price, subtotal) VALUES (3, 7, 2, 26000, 23500, 52000)"
            )
            db.execSQL(
                "INSERT INTO transaction_items (transaction_id, product_id, quantity, price, cost_price, subtotal) VALUES (3, 8, 1, 24000, 19000, 24000)"
            )

            // Debts: 1 POS-linked (partially paid), 1 Manual unpaid, 1 Manual paid
            db.execSQL(
                """
                INSERT INTO debts (transaction_id, user_id, customer_name, date, total_amount, paid_amount, status, note)
                VALUES (3, 1, 'Bu Hirah', ?, 76000, 26000, ?, 'Bayar akhir minggu')
                """.trimIndent(),
                arrayOf<Any?>(now, DebtStatus.UNPAID)
            )
            db.execSQL(
                """
                INSERT INTO debt_payments (debt_id, user_id, date, method, amount, note)
                VALUES (1, 1, ?, ?, 26000, 'Cicilan pertama tunai')
                """.trimIndent(),
                arrayOf<Any?>(now, PaymentMethod.CASH)
            )

            db.execSQL(
                """
                INSERT INTO debts (transaction_id, user_id, customer_name, date, total_amount, paid_amount, status, note)
                VALUES (NULL, 1, 'Pak Joko', ?, 45000, 0, ?, 'Bon sembako rutin')
                """.trimIndent(),
                arrayOf<Any?>(now, DebtStatus.UNPAID)
            )

            db.execSQL(
                """
                INSERT INTO debts (transaction_id, user_id, customer_name, date, total_amount, paid_amount, status, note)
                VALUES (NULL, 1, 'Ibu Siti', ?, 15000, 15000, ?, 'Lunas via QRIS')
                """.trimIndent(),
                arrayOf<Any?>(now, DebtStatus.PAID)
            )
            db.execSQL(
                """
                INSERT INTO debt_payments (debt_id, user_id, date, method, amount, note)
                VALUES (3, 1, ?, ?, 15000, 'Pelunasan QRIS')
                """.trimIndent(),
                arrayOf<Any?>(now, PaymentMethod.QRIS)
            )

            // Manual Cash Ledger (1 operating expense + 1 other income)
            db.execSQL(
                """
                INSERT INTO finance (user_id, date, type, category, amount, description)
                VALUES (1, ?, ?, 'Listrik & Air', 35000, 'Token listrik toko')
                """.trimIndent(),
                arrayOf<Any?>(now, FinanceType.EXPENSE)
            )
            db.execSQL(
                """
                INSERT INTO finance (user_id, date, type, category, amount, description)
                VALUES (1, ?, ?, 'Pemasukan Lainnya', 12000, 'Penjualan kardus bekas')
                """.trimIndent(),
                arrayOf<Any?>(now, FinanceType.INCOME)
            )
        }
    }
}