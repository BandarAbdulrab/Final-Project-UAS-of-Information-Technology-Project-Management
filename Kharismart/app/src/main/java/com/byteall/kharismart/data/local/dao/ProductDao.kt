package com.byteall.kharismart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE status = 1 ORDER BY name ASC")
    fun observeActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE status = 1 AND category_id = :categoryId ORDER BY name ASC")
    fun observeActiveByCategory(categoryId: Long): Flow<List<ProductEntity>>

    @Query(
        """
        SELECT * FROM products 
        WHERE status = 1 AND (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') 
        ORDER BY name ASC
        """
    )
    fun searchActive(query: String): Flow<List<ProductEntity>>

    @Query(
        """
        SELECT * FROM products 
        WHERE status = 1 AND stock <= COALESCE(min_stock, :defaultMin) 
        ORDER BY stock ASC, name ASC
        """
    )
    fun observeLowStock(defaultMin: Int): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE product_id = :productId")
    suspend fun getById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE status = 1 AND barcode = :barcode")
    suspend fun getActiveByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE product_id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ProductEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM products WHERE barcode = :barcode AND product_id != :excludeProductId)")
    suspend fun barcodeExists(barcode: String, excludeProductId: Long): Boolean

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("UPDATE products SET status = :status WHERE product_id = :productId")
    suspend fun updateStatus(productId: Long, status: Int)

    @Query("UPDATE products SET stock = :newStock WHERE product_id = :productId")
    suspend fun updateStock(productId: Long, newStock: Int)

    @Query("UPDATE products SET cost_price = :costPrice WHERE product_id = :productId")
    suspend fun updateCostPrice(productId: Long, costPrice: Long)

    @Query("UPDATE products SET stock = stock - :quantity WHERE product_id = :productId AND stock >= :quantity")
    suspend fun decrementStockIfAvailable(productId: Long, quantity: Int): Int

    @Insert
    suspend fun insertStockMovement(movement: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY date DESC, movement_id DESC")
    fun observeMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>
}
