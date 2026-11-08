package com.byteall.kharismart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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

        /**
         * Returns the singleton instance of [KharismartDatabase].
         *
         * NOTE: Never use fallbackToDestructiveMigration().
         * Any future schema updates must be handled via explicit Migration objects
         * to protect business transaction and inventory data from loss.
         */
        fun getInstance(context: Context): KharismartDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KharismartDatabase::class.java,
                    DATABASE_NAME
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addCallback(seedCallback())
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Closes the database instance and resets [INSTANCE] to null.
         * Used by database restore and test tear-downs.
         */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        /**
         * Builds an in-memory database with seeds and triggers for instrumentation tests.
         */
        fun buildInMemory(context: Context): KharismartDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                KharismartDatabase::class.java
            )
                .addCallback(seedCallback())
                .allowMainThreadQueries()
                .build()
        }

        private fun seedCallback(): Callback {
            return object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)

                    // 1. Seed categories
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Sembako', 1)")
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Jajanan', 1)")
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Rokok', 1)")
                    db.execSQL("INSERT INTO categories (name, status) VALUES ('Kebutuhan Harian', 1)")

                    // 2. Seed settings (settings_id 1)
                    db.execSQL(
                        """
                        INSERT INTO settings (settings_id, store_name, receipt_footer, default_min_stock, auto_lock_minutes)
                        VALUES (1, 'Toko Kharisma', 'Thank you', 5, 5)
                        """.trimIndent()
                    )

                    // 3. Seed demo users with hashed PINs and recovery code
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

                    // 4. Create database triggers
                    // Stock must be >= 0 on insert
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

                    // Stock must be >= 0 on update
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

                    // Settings is a single-row table
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
                }
            }
        }
    }
}
