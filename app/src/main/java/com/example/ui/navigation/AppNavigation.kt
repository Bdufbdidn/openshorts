package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.ui.aishorts.AiShortsScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.automation.AutomationQueueScreen
import com.example.ui.captions.CaptionExportInitArgs
import com.example.ui.captions.CaptionExportScreen
import com.example.ui.clips.ClipGeneratorScreen
import com.example.ui.export.BatchExportQueueScreen
import com.example.ui.gallery.UgcGalleryScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.youtube.YouTubeStudioScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable

@Serializable
object ClipsRoute

@Serializable
object AiShortsRoute

@Serializable
object GalleryRoute

@Serializable
object YouTubeStudioRoute

@Serializable
object SettingsRoute

@Serializable
object AutomationQueueRoute

@Serializable
object BatchExportQueueRoute

@Serializable
data class CaptionExportRoute(
    val initialVideoUri: String = "",
    val jobId: String = "",
    val clipId: String = "",
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = -1L,
    val initialTranscript: String = "",
    val initialTitle: String = "",
    val initialHook: String = "",
    val subtitlePosition: String = "bottom",
    val subtitleAnimation: String = "word-highlight",
    val subtitleFont: String = "SpaceGrotesk",
    val subtitleColorHex: String = "#FFFFFF",
    val subtitleHighlightHex: String = "#3B82F6",
    val initialFilterStackCsv: String = "",
    val filterContrast: Float = 0f,
    val filterSaturation: Float = 0f,
    val filterBrightness: Float = 0f,
    val localSessionId: String = ""
)

private data class NavDestinationItem(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun AppNavigation(
    widthSizeClass: WindowWidthSizeClass
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        val uid = user.uid
        key(uid) {
            MainShell(
                currentUserId = uid,
                widthSizeClass = widthSizeClass
            )
        }
    }
}

@Composable
private fun MainShell(
    currentUserId: String,
    widthSizeClass: WindowWidthSizeClass
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val items = remember {
        listOf(
            NavDestinationItem(
                route = ClipsRoute,
                labelRes = R.string.nav_clips,
                selectedIcon = Icons.Filled.ContentCut,
                unselectedIcon = Icons.Outlined.ContentCut,
                testTag = "nav_clips_tab"
            ),
            NavDestinationItem(
                route = AiShortsRoute,
                labelRes = R.string.nav_ai_shorts,
                selectedIcon = Icons.Filled.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                testTag = "nav_ai_shorts_tab"
            ),
            NavDestinationItem(
                route = GalleryRoute,
                labelRes = R.string.nav_gallery,
                selectedIcon = Icons.Filled.PhotoLibrary,
                unselectedIcon = Icons.Outlined.PhotoLibrary,
                testTag = "nav_gallery_tab"
            ),
            NavDestinationItem(
                route = YouTubeStudioRoute,
                labelRes = R.string.nav_youtube,
                selectedIcon = Icons.Filled.VideoLibrary,
                unselectedIcon = Icons.Outlined.VideoLibrary,
                testTag = "nav_youtube_tab"
            ),
            NavDestinationItem(
                route = SettingsRoute,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings_tab"
            )
        )
    }

    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isAutomationQueue = currentDestination?.hasRoute(AutomationQueueRoute::class) == true
    val isCaptionExport = currentDestination?.hasRoute(CaptionExportRoute::class) == true
    val isBatchExport = currentDestination?.hasRoute(BatchExportQueueRoute::class) == true
    val hideMainChrome = isAutomationQueue || isCaptionExport || isBatchExport

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (!useNavigationRail && !hideMainChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    items.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelRes),
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag(item.testTag)
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
            if (useNavigationRail && !hideMainChrome) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    items.forEach { item ->
                        val selected = currentDestination?.hasRoute(item.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelRes),
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = ClipsRoute
                ) {
                    composable<ClipsRoute> { entry ->
                        val pendingResumeId by entry.savedStateHandle
                            .getStateFlow<String?>("resume_local_session_id", null)
                            .collectAsStateWithLifecycle()
                        ClipGeneratorScreen(
                            currentUserId = currentUserId,
                            pendingResumeSessionId = pendingResumeId,
                            onConsumedPendingResume = {
                                entry.savedStateHandle["resume_local_session_id"] = null
                            },
                            onOpenAutomationQueue = { navController.navigate(AutomationQueueRoute) },
                            onOpenBatchExportQueue = { navController.navigate(BatchExportQueueRoute) },
                            onOpenCaptionExport = { args ->
                                navController.navigate(
                                    CaptionExportRoute(
                                        initialVideoUri = args.initialVideoUri,
                                        jobId = args.jobId,
                                        clipId = args.clipId,
                                        clipStartMs = args.clipStartMs,
                                        clipEndMs = args.clipEndMs,
                                        initialTranscript = args.initialTranscript,
                                        initialTitle = args.initialTitle,
                                        initialHook = args.initialHook,
                                        subtitlePosition = args.subtitlePosition,
                                        subtitleAnimation = args.subtitleAnimation,
                                        subtitleFont = args.subtitleFont,
                                        subtitleColorHex = args.subtitleColorHex,
                                        subtitleHighlightHex = args.subtitleHighlightHex,
                                        initialFilterStackCsv = args.initialFilterStackCsv,
                                        filterContrast = args.filterContrast,
                                        filterSaturation = args.filterSaturation,
                                        filterBrightness = args.filterBrightness,
                                        localSessionId = args.localSessionId
                                    )
                                )
                            }
                        )
                    }
                    composable<AiShortsRoute> {
                        AiShortsScreen(
                            currentUserId = currentUserId,
                            onOpenAutomationQueue = { navController.navigate(AutomationQueueRoute) }
                        )
                    }
                    composable<GalleryRoute> {
                        UgcGalleryScreen(currentUserId = currentUserId)
                    }
                    composable<YouTubeStudioRoute> {
                        YouTubeStudioScreen(
                            currentUserId = currentUserId,
                            onOpenAutomationQueue = { navController.navigate(AutomationQueueRoute) }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            currentUserId = currentUserId,
                            onOpenAutomationQueue = { navController.navigate(AutomationQueueRoute) }
                        )
                    }
                    composable<AutomationQueueRoute> {
                        AutomationQueueScreen(
                            currentUserId = currentUserId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<CaptionExportRoute> { entry ->
                        val parsed = entry.toRoute<CaptionExportRoute>()
                        CaptionExportScreen(
                            currentUserId = currentUserId,
                            args = CaptionExportInitArgs(
                                initialVideoUri = parsed.initialVideoUri,
                                jobId = parsed.jobId,
                                clipId = parsed.clipId,
                                clipStartMs = parsed.clipStartMs,
                                clipEndMs = parsed.clipEndMs,
                                initialTranscript = parsed.initialTranscript,
                                initialTitle = parsed.initialTitle,
                                initialHook = parsed.initialHook,
                                subtitlePosition = parsed.subtitlePosition,
                                subtitleAnimation = parsed.subtitleAnimation,
                                subtitleFont = parsed.subtitleFont,
                                subtitleColorHex = parsed.subtitleColorHex,
                                subtitleHighlightHex = parsed.subtitleHighlightHex,
                                initialFilterStackCsv = parsed.initialFilterStackCsv,
                                filterContrast = parsed.filterContrast,
                                filterSaturation = parsed.filterSaturation,
                                filterBrightness = parsed.filterBrightness,
                                localSessionId = parsed.localSessionId
                            ),
                            onResumeClipStudioSession = { sessionId ->
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("resume_local_session_id", sessionId)
                                navController.popBackStack()
                            },
                            onOpenBatchExportQueue = { navController.navigate(BatchExportQueueRoute) },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable<BatchExportQueueRoute> {
                        BatchExportQueueScreen(
                            currentUserId = currentUserId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
