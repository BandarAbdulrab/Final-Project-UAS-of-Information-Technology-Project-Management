package com.byteall.kharismart.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.byteall.kharismart.session.UserRole
import com.byteall.kharismart.ui.common.PlaceholderScreen
import com.byteall.kharismart.ui.debt.DebtListScreen
import com.byteall.kharismart.ui.lock.LockScreen
import com.byteall.kharismart.ui.users.UserManagementScreen
import kotlinx.coroutines.launch

@Composable
fun KharismartRoot() {
    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (currentUser == null) {
            LockScreen()
        } else {
            MainAppShell()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell() {
    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()
    val currentUserRole = currentUser?.role
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.POS

    val visibleDestinations = APP_DESTINATIONS.filter { destination ->
        !destination.adminOnly || currentUserRole == UserRole.ADMIN
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        visibleDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateTopLevel(navController, dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) }
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .width(48.dp)
                        )
                        NavigationRailItem(
                            selected = false,
                            onClick = { AppSession.signOut() },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = stringResource(R.string.nav_lock)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_lock)) }
                        )
                    }
                }

                KharismartNavHost(
                    navController = navController,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val activeLabelRes = visibleDestinations.find { it.route == currentRoute }?.labelRes
                ?: R.string.app_name

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp)
                        ) {
                            currentUser?.let { user ->
                                Text(
                                    text = "${user.name} (${
                                        stringResource(
                                            if (user.role == UserRole.ADMIN) R.string.role_admin else R.string.role_cashier
                                        )
                                    })",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                            }

                            visibleDestinations.forEach { dest ->
                                NavigationDrawerItem(
                                    label = { Text(stringResource(dest.labelRes)) },
                                    selected = currentRoute == dest.route,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        navigateTopLevel(navController, dest.route)
                                    },
                                    icon = { Icon(dest.icon, contentDescription = null) },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            NavigationDrawerItem(
                                label = { Text(stringResource(R.string.nav_lock)) },
                                selected = false,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    AppSession.signOut()
                                },
                                icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(activeLabelRes)) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.nav_open_drawer_cd)
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    KharismartNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun KharismartNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.POS,
        modifier = modifier.fillMaxSize()
    ) {
        APP_DESTINATIONS.forEach { dest ->
            composable(dest.route) {
                RenderScreenContent(route = dest.route)
            }
        }
    }
}

@Composable
fun RenderScreenContent(route: String) {
    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()
    val currentUserRole = currentUser?.role
    if (!Routes.isRouteAllowed(route, currentUserRole)) {
        PlaceholderScreen(
            title = stringResource(R.string.access_denied_title),
            subtitle = stringResource(R.string.access_denied_message)
        )
    } else {
        when (route) {
            Routes.DEBTS -> DebtListScreen()
            Routes.USERS -> UserManagementScreen()
            else -> {
                val currentDest = APP_DESTINATIONS.find { it.route == route }
                val title = if (currentDest != null) stringResource(currentDest.labelRes) else route
                PlaceholderScreen(
                    title = title,
                    subtitle = stringResource(R.string.placeholder_under_construction)
                )
            }
        }
    }
}

private fun navigateTopLevel(navController: NavHostController, route: String) {
    AppSession.touch()
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}