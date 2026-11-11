package com.byteall.kharismart.data.repository

import androidx.room.withTransaction
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.MovementType
import com.byteall.kharismart.data.local.dao.CategoryDao
import com.byteall.kharismart.data.local.dao.ProductDao
import com.byteall.kharismart.data.local.dao.SettingsDao
import com.byteall.kharismart.data.local.entity.CategoryEntity
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.StockMovementEntity
import com.byteall.kharismart.util.DomainException
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

enum class StockStatus { SAFE, LOW, OUT }

fun ProductEntity.stockStatus(defaultMin: Int): StockStatus {
    return when {
        stock <= 0 -> StockStatus.OUT
        stock <= (minStock ?: defaultMin) -> StockStatus.LOW
        else -> StockStatus.SAFE
    }
}

class InventoryRepository(
    private val database: KharismartDatabase,
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val settingsDao: SettingsDao
) {

    fun observeActiveProducts(): Flow<List<ProductEntity>> = productDao.observeActiveProducts()

    fun observeAllProducts(): Flow<List<ProductEntity>> = productDao.observeAllProducts()

    fun observeActiveByCategory(categoryId: Long): Flow<List<ProductEntity>> =
        productDao.observeActiveByCategory(categoryId)

    fun searchProducts(query: String): Flow<List<ProductEntity>> = productDao.searchActive(query)

    fun observeLowStock(): Flow<List<ProductEntity>> {
        return kotlinx.coroutines.flow.flow {
            val defaultMin = settingsDao.getSettings()?.defaultMinStock ?: 5
            emit(defaultMin)
        }.let { minFlow ->
            kotlinx.coroutines.flow.channelFlow {
                minFlow.collect { defaultMin ->
                    productDao.observeLowStock(defaultMin).collect { send(it) }
                }
            }
        }
    }

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeActive()

    fun observeAllCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun findByBarcode(barcode: String): ProductEntity? =
        productDao.getActiveByBarcode(barcode.trim())

    suspend fun saveProduct(product: ProductEntity, userId: Long): Result<Long> {
        val cleanName = product.name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(DomainException("Nama produk tidak boleh kosong"))
        }
        if (product.price <= 0) {
            return Result.failure(DomainException("Harga jual harus lebih besar dari 0"))
        }
        if (product.costPrice < 0) {
            return Result.failure(DomainException("Harga modal tidak boleh negatif"))
        }
        if (product.stock < 0) {
            return Result.failure(DomainException("Jumlah stok tidak boleh negatif"))
        }
        if (product.minStock != null && product.minStock < 0) {
            return Result.failure(DomainException("Batas stok minimum tidak boleh negatif"))
        }

        val cleanBarcode = product.barcode?.trim()?.ifBlank { null }
        if (cleanBarcode != null && productDao.barcodeExists(cleanBarcode, product.productId)) {
            return Result.failure(DomainException("Barcode '$cleanBarcode' sudah digunakan produk lain"))
        }

        if (categoryDao.getById(product.categoryId) == null) {
            return Result.failure(DomainException("Kategori produk tidak ditemukan"))
        }

        return try {
            database.withTransaction {
                if (product.productId == 0L) {
                    val toInsert = product.copy(name = cleanName, barcode = cleanBarcode)
                    val newId = productDao.insert(toInsert)
                    if (product.stock > 0) {
                        val movement = StockMovementEntity(
                            productId = newId,
                            userId = userId,
                            transactionId = null,
                            date = WibClock.nowString(),
                            type = MovementType.INITIAL,
                            quantityChange = product.stock,
                            stockAfter = product.stock,
                            unitCost = product.costPrice,
                            totalCost = null, // Initial stock is not considered a purchase
                            note = "Stok awal produk baru"
                        )
                        productDao.insertStockMovement(movement)
                    }
                    Result.success(newId)
                } else {
                    val existing = productDao.getById(product.productId)
                        ?: throw DomainException("Produk tidak ditemukan")
                    // Catalog editing NEVER alters the current stock count
                    val toUpdate = product.copy(
                        name = cleanName,
                        barcode = cleanBarcode,
                        stock = existing.stock
                    )
                    productDao.update(toUpdate)
                    Result.success(product.productId)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restock(
        productId: Long,
        quantity: Int,
        unitCost: Long,
        updateCostPrice: Boolean,
        userId: Long
    ): Result<Unit> {
        if (quantity <= 0) {
            return Result.failure(DomainException("Jumlah restock harus lebih besar dari 0"))
        }
        if (unitCost < 0) {
            return Result.failure(DomainException("Harga modal per unit tidak boleh negatif"))
        }

        return try {
            database.withTransaction {
                val product = productDao.getById(productId)
                    ?: throw DomainException("Produk tidak ditemukan")

                val newStock = product.stock + quantity
                productDao.updateStock(productId, newStock)

                if (updateCostPrice) {
                    productDao.updateCostPrice(productId, unitCost)
                }

                val totalCost = quantity.toLong() * unitCost
                val movement = StockMovementEntity(
                    productId = productId,
                    userId = userId,
                    transactionId = null,
                    date = WibClock.nowString(),
                    type = MovementType.RESTOCK,
                    quantityChange = quantity,
                    stockAfter = newStock,
                    unitCost = unitCost,
                    totalCost = totalCost,
                    note = "Restock pasokan barang"
                )
                productDao.insertStockMovement(movement)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adjustStock(
        productId: Long,
        countedStock: Int,
        reason: String,
        userId: Long
    ): Result<Int> {
        if (countedStock < 0) {
            return Result.failure(DomainException("Hasil perhitungan fisik stok tidak boleh negatif"))
        }
        val cleanReason = reason.trim()
        if (cleanReason.isBlank()) {
            return Result.failure(DomainException("Alasan penyesuaian stok opname wajib diisi"))
        }

        return try {
            val delta = database.withTransaction {
                val product = productDao.getById(productId)
                    ?: throw DomainException("Produk tidak ditemukan")

                val difference = countedStock - product.stock
                if (difference != 0) {
                    productDao.updateStock(productId, countedStock)
                    // BR-12: Stock opname adjustments are inventory reconciliation only (neither expense nor income)
                    val movement = StockMovementEntity(
                        productId = productId,
                        userId = userId,
                        transactionId = null,
                        date = WibClock.nowString(),
                        type = MovementType.ADJUSTMENT,
                        quantityChange = difference,
                        stockAfter = countedStock,
                        unitCost = null,
                        totalCost = null,
                        note = cleanReason
                    )
                    productDao.insertStockMovement(movement)
                }
                difference
            }
            Result.success(delta)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setProductActive(productId: Long, active: Boolean): Result<Unit> {
        productDao.updateStatus(productId, if (active) 1 else 0)
        return Result.success(Unit)
    }

    suspend fun addCategory(name: String): Result<Long> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(DomainException("Nama kategori tidak boleh kosong"))
        }
        if (categoryDao.getByName(cleanName) != null) {
            return Result.failure(DomainException("Kategori '$cleanName' sudah ada"))
        }
        val id = categoryDao.insert(CategoryEntity(name = cleanName, status = 1))
        return Result.success(id)
    }

    suspend fun renameCategory(categoryId: Long, name: String): Result<Unit> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            return Result.failure(DomainException("Nama kategori tidak boleh kosong"))
        }
        val existing = categoryDao.getByName(cleanName)
        if (existing != null && existing.categoryId != categoryId) {
            return Result.failure(DomainException("Kategori '$cleanName' sudah digunakan"))
        }
        val category = categoryDao.getById(categoryId)
            ?: return Result.failure(DomainException("Kategori tidak ditemukan"))
        categoryDao.update(category.copy(name = cleanName))
        return Result.success(Unit)
    }

    suspend fun setCategoryActive(categoryId: Long, active: Boolean): Result<Unit> {
        categoryDao.updateStatus(categoryId, if (active) 1 else 0)
        return Result.success(Unit)
    }
}