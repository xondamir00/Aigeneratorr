package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.firebase.GoogleAuthManager
import com.example.firebase.configureAppCheck
import com.example.ui.DuskLoreViewModel
import com.example.ui.screens.ArchiveScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.ShortDetailScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.DuskLoreTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

private const val ROUTE_STUDIO = "studio"
private const val ROUTE_ARCHIVE = "archive"
private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_DETAIL = "detail/{shortId}"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    NavDestination(
        route = ROUTE_STUDIO,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_studio"
    ),
    NavDestination(
        route = ROUTE_ARCHIVE,
        labelRes = R.string.nav_archive,
        selectedIcon = Icons.Filled.VideoLibrary,
        unselectedIcon = Icons.Outlined.VideoLibrary,
        testTag = "nav_archive"
    ),
    NavDestination(
        route = ROUTE_SCHEDULE,
        labelRes = R.string.nav_schedule,
        selectedIcon = Icons.Filled.Schedule,
        unselectedIcon = Icons.Outlined.Schedule,
        testTag = "nav_schedule"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            DuskLoreTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = currentUser!!.uid
        key(uid) {
            AuthenticatedStudioShell(currentUserId = uid)
        }
    }
}

@Composable
private fun AuthenticatedStudioShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: DuskLoreViewModel = viewModel(
        key = currentUserId,
        factory = DuskLoreViewModel.provideFactory(context)
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_STUDIO
    val showTopLevelNav = currentRoute in setOf(ROUTE_STUDIO, ROUTE_ARCHIVE, ROUTE_SCHEDULE)

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val pendingConsent by viewModel.pendingConsentIntent.collectAsStateWithLifecycle()

    // Google Play Services AuthorizationClient consent sheet launcher for YouTube OAuth
    val youtubeConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.onYouTubeConsentActivityResult(activityResult.resultCode, activityResult.data)
    }

    LaunchedEffect(pendingConsent) {
        val pending = pendingConsent ?: return@LaunchedEffect
        viewModel.consumePendingConsentIntent()
        youtubeConsentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (showTopLevelNav && !useNavigationRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(id = dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useNavigationRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(id = dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_STUDIO,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_STUDIO) {
                        StudioScreen(
                            viewModel = viewModel,
                            onOpenShortDetail = { shortId ->
                                navController.navigate("detail/$shortId")
                            }
                        )
                    }
                    composable(ROUTE_ARCHIVE) {
                        ArchiveScreen(
                            viewModel = viewModel,
                            onOpenShortDetail = { shortId ->
                                navController.navigate("detail/$shortId")
                            }
                        )
                    }
                    composable(ROUTE_SCHEDULE) {
                        ScheduleScreen(
                            viewModel = viewModel,
                            onSignOutClick = {
                                GoogleAuthManager.signOut(credentialManager, scope)
                            }
                        )
                    }
                    composable(
                        route = ROUTE_DETAIL,
                        arguments = listOf(navArgument("shortId") { type = NavType.StringType })
                    ) { entry ->
                        val shortId = entry.arguments?.getString("shortId").orEmpty()
                        ShortDetailScreen(
                            shortId = shortId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
