package com.byteall.kharismart.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.byteall.kharismart.R
import com.byteall.kharismart.session.UserRole

object Routes {
    const val LOCK = "lock"
    const val POS = "pos"
    const val HISTORY = "history"
    const val INVENTORY = "inventory"
    const val DEBTS = "debts"
    const val FINANCE = "finance"
    const val ANALYTICS = "analytics"
    const val USERS = "users"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"

    val ADMIN_ONLY_ROUTES = setOf(
        FINANCE,
        ANALYTICS,
        USERS,
        SETTINGS,
        BACKUP
    )

    fun isRouteAllowed(route: String, role: UserRole?): Boolean {
        if (role == null) return route == LOCK
        if (role == UserRole.ADMIN) return true
        return route !in ADMIN_ONLY_ROUTES
    }
}

data class NavigationDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val adminOnly: Boolean = false
)

val APP_DESTINATIONS = listOf(
    NavigationDestination(Routes.POS, R.string.nav_pos, Icons.Default.PointOfSale),
    NavigationDestination(Routes.HISTORY, R.string.nav_history, Icons.Default.History),
    NavigationDestination(Routes.INVENTORY, R.string.nav_inventory, Icons.Default.Inventory2),
    NavigationDestination(Routes.DEBTS, R.string.nav_debts, Icons.AutoMirrored.Filled.ReceiptLong),
    NavigationDestination(Routes.FINANCE, R.string.nav_finance, Icons.Default.Payments, adminOnly = true),
    NavigationDestination(Routes.ANALYTICS, R.string.nav_analytics, Icons.Default.Assessment, adminOnly = true),
    NavigationDestination(Routes.USERS, R.string.nav_users, Icons.Default.People, adminOnly = true),
    NavigationDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings, adminOnly = true),
    NavigationDestination(Routes.BACKUP, R.string.nav_backup, Icons.Default.Backup, adminOnly = true)
)