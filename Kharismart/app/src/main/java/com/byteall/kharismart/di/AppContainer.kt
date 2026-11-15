package com.byteall.kharismart.di

import android.content.Context
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.repository.AnalyticsRepository
import com.byteall.kharismart.data.repository.DebtRepository
import com.byteall.kharismart.data.repository.FinanceRepository
import com.byteall.kharismart.data.repository.InventoryRepository
import com.byteall.kharismart.data.repository.SaleRepository
import com.byteall.kharismart.data.repository.SettingsRepository
import com.byteall.kharismart.data.repository.UserRepository

class AppContainer(val appContext: Context) {

    val database: KharismartDatabase by lazy {
        KharismartDatabase.getInstance(appContext)
    }

    val userRepository: UserRepository by lazy {
        UserRepository(database.userDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.settingsDao())
    }

    val financeRepository: FinanceRepository by lazy {
        FinanceRepository(database.financeDao())
    }

    val debtRepository: DebtRepository by lazy {
        DebtRepository(database, database.debtDao())
    }

    val inventoryRepository: InventoryRepository by lazy {
        InventoryRepository(
            database = database,
            productDao = database.productDao(),
            categoryDao = database.categoryDao(),
            settingsDao = database.settingsDao()
        )
    }

    val saleRepository: SaleRepository by lazy {
        SaleRepository(
            database = database,
            saleDao = database.saleDao(),
            productDao = database.productDao(),
            debtDao = database.debtDao(),
            settingsDao = database.settingsDao()
        )
    }

    val analyticsRepository: AnalyticsRepository by lazy {
        AnalyticsRepository(database.analyticsDao())
    }
}