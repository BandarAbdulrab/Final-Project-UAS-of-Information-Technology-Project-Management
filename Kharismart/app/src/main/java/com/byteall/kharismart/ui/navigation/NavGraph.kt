package com.byteall.kharismart.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.byteall.kharismart.R
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.session.SessionUser
import com.byteall.kharismart.session.UserRole
import com.byteall.kharismart.ui.common.PlaceholderScreen
import kotlinx.coroutines.launch

@Composable
fun KharismartRoot() {
    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()

    if (currentUser == null) {
        DevLockScreen()
    } else {
        AuthenticatedShell(user = currentUser!!)
    }
}

@Composable
private fun DevLockScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.dev_lock_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.width(360.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.dev_lock_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        AppSession.signIn(
                            SessionUser(
                                userId = 1L,
                                username = "sumarti",
                                name = "Ibu Sumarti",
                                role = UserRole.ADMIN
                            )
                        )
                    }
                ) {
                    Text("DEV: Masuk sebagai Admin (Ibu Sumarti)")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        AppSession.signIn(
                            SessionUser(
                                userId = 2L,
                                username = "rizki",
                                name = "Rizki",
                                role = UserRole.CASHIER
                            )
                        )
                    }
                ) {
                    Text("DEV: Masuk sebagai Kasir (Rizki)")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(user: SessionUser) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Routes.POS
    val allowedDestinations = APP_DESTINATIONS.filter { destination ->
        if (user.role == UserRole.ADMIN) true else !destination.adminOnly
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLandscape = maxWidth >= 600.dp

        if (isTabletLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    header = {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                ) {
                    allowedDestinations.forEach { destination ->
                        NavigationRailItem(
                            selected = currentRoute == destination.route,
                            onClick = { navigateToRoute(navController, destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationRailItem(
                        selected = false,
                        onClick = { AppSession.signOut() },
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_lock)) }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    KharismartNavHost(
                        navController = navController,
                        userRole = user.role
                    )
                }
            }
        } else {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${user.name} (${if (user.role == UserRole.ADMIN) stringResource(R.string.role_admin) else stringResource(R.string.role_cashier)})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        allowedDestinations.forEach { destination ->
                            NavigationDrawerItem(
                                label = { Text(stringResource(destination.labelRes)) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                selected = currentRoute == destination.route,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    navigateToRoute(navController, destination.route)
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.nav_lock)) },
                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                AppSession.signOut()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                val currentDest = APP_DESTINATIONS.firstOrNull { it.route == currentRoute }
                                Text(currentDest?.let { stringResource(it.labelRes) } ?: stringResource(R.string.app_name))
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        KharismartNavHost(
                            navController = navController,
                            userRole = user.role
                        )
                    }
                }
            }
        }
    }
}

private fun navigateToRoute(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun KharismartNavHost(
    navController: NavHostController,
    userRole: UserRole,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.POS,
        modifier = modifier
    ) {
        composable(Routes.POS) { PlaceholderScreen(stringResource(R.string.nav_pos)) }
        composable(Routes.HISTORY) { PlaceholderScreen(stringResource(R.string.nav_history)) }
        composable(Routes.INVENTORY) { PlaceholderScreen(stringResource(R.string.nav_inventory)) }
        composable(Routes.DEBTS) { PlaceholderScreen(stringResource(R.string.nav_debts)) }

        composable(Routes.FINANCE) {
            RoleGuardedRoute(userRole, Routes.FINANCE, R.string.nav_finance)
        }
        composable(Routes.ANALYTICS) {
            RoleGuardedRoute(userRole, Routes.ANALYTICS, R.string.nav_analytics)
        }
        composable(Routes.USERS) {
            RoleGuardedRoute(userRole, Routes.USERS, R.string.nav_users)
        }
        composable(Routes.SETTINGS) {
            RoleGuardedRoute(userRole, Routes.SETTINGS, R.string.nav_settings)
        }
        composable(Routes.BACKUP) {
            RoleGuardedRoute(userRole, Routes.BACKUP, R.string.nav_backup)
        }
    }
}

@Composable
private fun RoleGuardedRoute(
    userRole: UserRole,
    route: String,
    titleRes: Int
) {
    if (!Routes.isRouteAllowed(route, userRole)) {
        PlaceholderScreen(
            title = stringResource(R.string.access_denied_title),
            subtitle = stringResource(R.string.access_denied_message)
        )
    } else {
        PlaceholderScreen(title = stringResource(titleRes))
    }
}