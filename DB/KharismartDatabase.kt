package com.byteall.kharismart.data

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Kharismart - Room entities (SQLite), SRS Revision B, 10 tables.
 *
 *  - Ids are autoincrement Long, money/quantities are Int (whole Rupiah).
 *  - Booleans are Int 0/1; dates are String "yyyy-MM-dd HH:mm:ss" (WIB).
 *  - Foreign keys: onUpdate = CASCADE, onDelete = RESTRICT (SRS 3.3.2).
 *    Room turns PRAGMA foreign_keys = ON by itself.
 *  - Room cannot declare CHECK constraints in annotations. The rules that
 *    protect the data are repeated as triggers in [KharismartDatabase.CALLBACK]
 *    (stock >= 0, single settings row); the remaining CHECK rules in
 *    kharismart_schema.sql (method/paid/change, subtotal, enums, ...) must be
 *    validated in the repository/use-case layer before every insert or update.
 *  - Multi-table saves (complete sale, restock, adjustment, debt payment)
 *    must use @Transaction or db.withTransaction { } (SRS NF08).
 */

// ---------------------------------------------------------------- D4 users
@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "user_id") val userId: Long = 0,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "pin_hash") val pinHash: String,          // salted hash, never the PIN
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "role") val role: String,                 // "admin" | "cashier"
    @ColumnInfo(name = "status", defaultValue = "1") val status: Int = 1,
    @ColumnInfo(name = "recovery_hash") val recoveryHash: String? = null
)

// ------------------------------------------------------------ D7 categories
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "category_id") val categoryId: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "status", defaultValue = "1") val status: Int = 1
)

// -------------------------------------------------------------- D1 products
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["category_id"], childColumns = ["category_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["barcode"], unique = true),               // NULL allowed (no barcode)
        Index(value = ["category_id"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "product_id") val productId: Long = 0,
    @ColumnInfo(name = "barcode") val barcode: String? = null,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "price") val price: Int,                  // > 0
    @ColumnInfo(name = "cost_price", defaultValue = "0") val costPrice: Int = 0,   // >= 0, hidden from cashier
    @ColumnInfo(name = "stock", defaultValue = "0") val stock: Int = 0,            // >= 0
    @ColumnInfo(name = "min_stock") val minStock: Int? = null,   // null = settings.default_min_stock
    @ColumnInfo(name = "photo") val photo: String? = null,
    @ColumnInfo(name = "status", defaultValue = "1") val status: Int = 1
)

// ---------------------------------------------------------- D2 transactions
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"], childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["receipt_no"], unique = true),
        Index(value = ["date"]),
        Index(value = ["user_id"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "transaction_id") val transactionId: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "receipt_no") val receiptNo: String,      // TRX-yyyymmdd-nnn
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "method") val method: String,             // "cash" | "qris" | "credit"
    @ColumnInfo(name = "total") val total: Int,
    @ColumnInfo(name = "paid") val paid: Int,                    // credit: always 0
    @ColumnInfo(name = "change_due", defaultValue = "0") val changeDue: Int = 0,
    @ColumnInfo(name = "total_cost", defaultValue = "0") val totalCost: Int = 0,
    @ColumnInfo(name = "gross_profit", defaultValue = "0") val grossProfit: Int = 0, // total - total_cost
    @ColumnInfo(name = "note") val note: String? = null
)

// ----------------------------------------------------- D3 transaction_items
@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"], childColumns = ["transaction_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["product_id"], childColumns = ["product_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["transaction_id"]), Index(value = ["product_id"])]
)
data class TransactionItemEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "item_id") val itemId: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "quantity") val quantity: Int,            // > 0
    @ColumnInfo(name = "price") val price: Int,                  // copied at sale time (BR-06)
    @ColumnInfo(name = "cost_price", defaultValue = "0") val costPrice: Int = 0,
    @ColumnInfo(name = "subtotal") val subtotal: Int             // price x quantity
)

// ------------------------------------------------------ D8 stock_movements
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["product_id"], childColumns = ["product_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"], childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"], childColumns = ["transaction_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["product_id", "date"]),
        Index(value = ["user_id"]),
        Index(value = ["transaction_id"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "movement_id") val movementId: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,  // sale only
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "type") val type: String,                 // initial | restock | sale | adjustment
    @ColumnInfo(name = "quantity_change") val quantityChange: Int,         // signed, never 0
    @ColumnInfo(name = "stock_after") val stockAfter: Int,
    @ColumnInfo(name = "unit_cost") val unitCost: Int? = null,   // restock only
    @ColumnInfo(name = "total_cost") val totalCost: Int? = null, // restock only
    @ColumnInfo(name = "note") val note: String? = null          // required for adjustment
)

// ------------------------------------------------------------------ D9 debts
@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["transaction_id"], childColumns = ["transaction_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"], childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["transaction_id"], unique = true),        // max 1 debt per transaction
        Index(value = ["status"]),
        Index(value = ["user_id"])
    ]
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "debt_id") val debtId: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,  // null = manual debt note
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "customer_name") val customerName: String,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "total_amount") val totalAmount: Int,     // > 0
    @ColumnInfo(name = "paid_amount", defaultValue = "0") val paidAmount: Int = 0,
    @ColumnInfo(name = "status", defaultValue = "'unpaid'") val status: String = "unpaid", // unpaid | paid
    @ColumnInfo(name = "note") val note: String? = null
)

// ------------------------------------------------------- D10 debt_payments
@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["debt_id"], childColumns = ["debt_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"], childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["debt_id"]), Index(value = ["user_id"])]
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "payment_id") val paymentId: Long = 0,
    @ColumnInfo(name = "debt_id") val debtId: Long,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "method") val method: String,             // "cash" | "qris"
    @ColumnInfo(name = "amount") val amount: Int,                // > 0, <= remaining debt
    @ColumnInfo(name = "note") val note: String? = null
)

// ------------------------------------------------------------- D6 finance
@Entity(
    tableName = "finance",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"], childColumns = ["user_id"],
            onUpdate = ForeignKey.CASCADE, onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["date"]), Index(value = ["user_id"])]
)
data class FinanceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "finance_id") val financeId: Long = 0,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "type") val type: String,                 // "income" | "expense"
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "amount") val amount: Int,                // > 0
    @ColumnInfo(name = "description") val description: String? = null
)

// ------------------------------------------------------------- D5 settings
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey @ColumnInfo(name = "settings_id") val settingsId: Int = 1,   // always 1
    @ColumnInfo(name = "store_name") val storeName: String,
    @ColumnInfo(name = "store_address") val storeAddress: String? = null,
    @ColumnInfo(name = "receipt_footer") val receiptFooter: String? = null,
    @ColumnInfo(name = "default_min_stock", defaultValue = "5") val defaultMinStock: Int = 5,
    @ColumnInfo(name = "qris_bank") val qrisBank: String? = null,
    @ColumnInfo(name = "qris_owner_name") val qrisOwnerName: String? = null,
    @ColumnInfo(name = "qris_image") val qrisImage: String? = null,
    @ColumnInfo(name = "target_daily") val targetDaily: Int? = null,
    @ColumnInfo(name = "target_weekly") val targetWeekly: Int? = null,
    @ColumnInfo(name = "target_monthly") val targetMonthly: Int? = null,
    @ColumnInfo(name = "auto_lock_minutes", defaultValue = "5") val autoLockMinutes: Int = 5,
    @ColumnInfo(name = "backup_folder") val backupFolder: String? = null,
    @ColumnInfo(name = "last_backup_at") val lastBackupAt: String? = null
)

// ----------------------------------------------------------------- database
@Database(
    entities = [
        UserEntity::class, CategoryEntity::class, ProductEntity::class,
        TransactionEntity::class, TransactionItemEntity::class,
        StockMovementEntity::class, DebtEntity::class, DebtPaymentEntity::class,
        FinanceEntity::class, SettingsEntity::class
    ],
    version = 1,
    exportSchema = true          // keep the schema JSON in version control for migrations
)
abstract class KharismartDatabase : RoomDatabase() {

    // abstract fun userDao(): UserDao  ... (one DAO per aggregate)

    companion object {
        const val NAME = "kharismart.db"

        /** Pass to Room.databaseBuilder(...).addCallback(CALLBACK). */
        val CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // Appendix D: default categories and the single settings row
                db.execSQL(
                    "INSERT INTO categories (name, status) VALUES " +
                        "('Sembako',1),('Jajanan',1),('Rokok',1),('Kebutuhan Harian',1)"
                )
                db.execSQL(
                    "INSERT INTO settings (settings_id, store_name, receipt_footer, " +
                        "default_min_stock, auto_lock_minutes) " +
                        "VALUES (1,'Toko Kharisma','Thank you',5,5)"
                )
                // BR-08: stock can never be negative
                db.execSQL(
                    "CREATE TRIGGER trg_products_stock_ins BEFORE INSERT ON products " +
                        "WHEN NEW.stock < 0 BEGIN SELECT RAISE(ABORT,'stock must be >= 0'); END"
                )
                db.execSQL(
                    "CREATE TRIGGER trg_products_stock_upd BEFORE UPDATE OF stock ON products " +
                        "WHEN NEW.stock < 0 BEGIN SELECT RAISE(ABORT,'stock must be >= 0'); END"
                )
                // settings holds exactly one row (settings_id = 1)
                db.execSQL(
                    "CREATE TRIGGER trg_settings_single BEFORE INSERT ON settings " +
                        "WHEN NEW.settings_id <> 1 OR (SELECT COUNT(*) FROM settings) >= 1 " +
                        "BEGIN SELECT RAISE(ABORT,'settings is a single-row table'); END"
                )
            }
        }
    }
}
