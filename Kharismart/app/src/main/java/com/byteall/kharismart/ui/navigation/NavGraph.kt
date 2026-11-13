package com.byteall.kharismart.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byteall.kharismart.R
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.ui.common.PlaceholderScreen
import com.byteall.kharismart.ui.lock.LockScreen
import kotlinx.coroutines.launch

@Composable
fun KharismartRoot() {
    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()

    if (currentUser == null) {
        LockScreen()
    } else {
        MainAppShell()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell() {
    var currentRoute by remember { mutableStateOf(Routes.POS) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()
    val currentUserRole = currentUser?.role
    val availableDestinations = APP_DESTINATIONS.filter { dest ->
        Routes.isRouteAllowed(dest.route, currentUserRole)
    }

    if (isTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                for (dest in availableDestinations) {
                    NavigationRailItem(
                        selected = currentRoute == dest.route,
                        onClick = { currentRoute = dest.route },
                        icon = {
                            Icon(
                                dest.icon,
                                contentDescription = stringResource(dest.labelRes)
                            )
                        },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                NavigationRailItem(
                    selected = false,
                    onClick = { AppSession.signOut() },
                    icon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.nav_lock)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_lock)) }
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                RenderScreenContent(route = currentRoute)
            }
        }
    } else {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        text = stringResource(R.string.app_name),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    for (dest in availableDestinations) {
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    dest.icon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            selected = currentRoute == dest.route,
                            onClick = {
                                currentRoute = dest.route
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.nav_lock)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_lock)) },
                        selected = false,
                        onClick = {
                            AppSession.signOut()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        ) {
            val currentDest = APP_DESTINATIONS.find { it.route == currentRoute }
            val screenTitle = if (currentDest != null) {
                stringResource(currentDest.labelRes)
            } else {
                currentRoute
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(screenTitle) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.cd_menu)
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    RenderScreenContent(route = currentRoute)
                }
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
        val currentDest = APP_DESTINATIONS.find { it.route == route }
        val title = if (currentDest != null) {
            stringResource(currentDest.labelRes)
        } else {
            route
        }
        PlaceholderScreen(
            title = title,
            subtitle = stringResource(R.string.placeholder_under_construction)
        )
    }
}