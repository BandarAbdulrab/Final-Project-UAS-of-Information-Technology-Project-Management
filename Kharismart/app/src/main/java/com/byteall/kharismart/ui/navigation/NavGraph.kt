package com.byteall.kharismart.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.byteall.kharismart.R
import com.byteall.kharismart.data.local.entity.UserEntity
import com.byteall.kharismart.data.repository.UserRepository
import com.byteall.kharismart.di.appViewModel
import com.byteall.kharismart.session.AppSession
import com.byteall.kharismart.session.SessionUser
import com.byteall.kharismart.session.UserRole
import com.byteall.kharismart.ui.common.PlaceholderScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LockPlaceholderViewModel(private val userRepository: UserRepository) : ViewModel() {
    val activeUsers: StateFlow<List<UserEntity>> = userRepository.observeActiveUsers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

@Composable
fun KharismartRoot() {
    val currentUser by AppSession.currentUser.collectAsStateWithLifecycle()

    if (currentUser == null) {
        val viewModel: LockPlaceholderViewModel = appViewModel { container ->
            LockPlaceholderViewModel(container.userRepository)
        }
        val users by viewModel.activeUsers.collectAsStateWithLifecycle()

        DatabaseLockPlaceholderScreen(
            users = users,
            onUserSelected = { user ->
                AppSession.signIn(
                    SessionUser(
                        userId = user.userId,
                        username = user.username,
                        name = user.name,
                        role = UserRole.fromDb(user.role)
                    )
                )
            }
        )
    } else {
        MainAppShell()
    }
}

@Composable
fun DatabaseLockPlaceholderScreen(
    users: List<UserEntity>,
    onUserSelected: (UserEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Data Langsung dari Database SQLite (Day 3)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.width(380.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pilih Akun Kasir / Admin (DEV: Tanpa PIN)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (users.isEmpty()) {
                    Text(
                        text = "Memuat data pengguna dari database...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(users, key = { it.userId }) { user ->
                            val isAdmin = user.role == "admin"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onUserSelected(user) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAdmin) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = user.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "@${user.username}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        text = if (isAdmin) "ADMIN" else "KASIR",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isAdmin) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell() {
    var currentRoute by remember { mutableStateOf(Routes.POS) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val currentUserRole = AppSession.currentUser.value?.role
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
                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                NavigationRailItem(
                    selected = false,
                    onClick = { AppSession.signOut() },
                    icon = { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.nav_lock)) },
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
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
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
                        icon = { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.nav_lock)) },
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
            val screenTitle = if (currentDest != null) stringResource(currentDest.labelRes) else currentRoute

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(screenTitle) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
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
    val currentUserRole = AppSession.currentUser.value?.role
    if (!Routes.isRouteAllowed(route, currentUserRole)) {
        PlaceholderScreen(
            title = stringResource(R.string.access_denied_title),
            subtitle = stringResource(R.string.access_denied_message)
        )
    } else {
        val currentDest = APP_DESTINATIONS.find { it.route == route }
        val title = if (currentDest != null) stringResource(currentDest.labelRes) else route
        PlaceholderScreen(
            title = title,
            subtitle = stringResource(R.string.placeholder_under_construction)
        )
    }
}