package com.example.ui.navigation

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.TikTokOAuthCallbackBus
import com.example.auth.YouTubeAuthHelper
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.StudioClip
import com.example.data.remote.YouTubeMetadataService
import com.example.data.remote.YouTubeRetrofitClient
import com.example.data.repository.AiStudioRepository
import com.example.data.repository.StudioRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatbotScreen
import com.example.ui.screens.ConnectAccountsScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PublishScreen
import com.example.ui.screens.SelectVideoScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.ElectricVioletLight
import com.example.ui.theme.SpaceNavy850
import com.example.ui.theme.SpaceNavy900
import com.example.ui.theme.SpaceNavy950
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_HOME = "home"
const val ROUTE_EDITOR = "editor"
const val ROUTE_TEMPLATES = "templates"
const val ROUTE_STATS = "stats"
const val ROUTE_PROFILE = "profile"

const val ROUTE_CONNECT = "connect"
const val ROUTE_SELECT_VIDEO = "select_video"
const val ROUTE_PUBLISH = "publish"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_CHAT = "chat"

private data class TopLevelNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelItems = listOf(
    TopLevelNavItem(ROUTE_HOME, "Início", Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
    TopLevelNavItem(ROUTE_EDITOR, "Editor", Icons.Rounded.ContentCut, Icons.Outlined.ContentCut, "nav_editor"),
    TopLevelNavItem(ROUTE_TEMPLATES, "Templates", Icons.Rounded.GridView, Icons.Outlined.GridView, "nav_templates"),
    TopLevelNavItem(ROUTE_STATS, "Estatísticas", Icons.Rounded.BarChart, Icons.Outlined.BarChart, "nav_stats"),
    TopLevelNavItem(ROUTE_PROFILE, "Perfil", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedStudioShell(
                currentUserId = user.uid,
                userEmail = user.email,
                userName = user.displayName
            )
        }
    }
}

@Composable
private fun AuthenticatedStudioShell(
    currentUserId: String,
    userEmail: String?,
    userName: String?
) {
    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(
                    currentUserId = currentUserId,
                    studioRepo = StudioRepository(db),
                    aiRepo = AiStudioRepository(),
                    ytService = YouTubeMetadataService()
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val sourcesState by viewModel.sourcesState.collectAsStateWithLifecycle()
    val clipsState by viewModel.clipsState.collectAsStateWithLifecycle()
    val ytLibraryState by viewModel.ytLibraryState.collectAsStateWithLifecycle()
    val autopilotBusyId by viewModel.autopilotBusySourceId.collectAsStateWithLifecycle()
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycle()
    val chatState by viewModel.chatState.collectAsStateWithLifecycle()
    val imageGenState by viewModel.imageGenState.collectAsStateWithLifecycle()
    val selectedImageSize by viewModel.selectedImageSize.collectAsStateWithLifecycle()
    val selectedImageAspect by viewModel.selectedImageAspect.collectAsStateWithLifecycle()
    val selectedImageModel by viewModel.selectedImageModel.collectAsStateWithLifecycle()
    val localVideoInfo by viewModel.localVideoInfo.collectAsStateWithLifecycle()
    val renderedClipIds by viewModel.renderedClipIds.collectAsStateWithLifecycle()
    val shortExportState by viewModel.shortExportState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val clipVisualStyles by viewModel.clipVisualStyles.collectAsStateWithLifecycle()
    val tikTokAccount by viewModel.tikTokAccount.collectAsStateWithLifecycle()
    val aiEditBusy by viewModel.aiEditBusy.collectAsStateWithLifecycle()
    val youTubeUploadBusy by viewModel.youTubeUploadBusy.collectAsStateWithLifecycle()
    val dualPublishStatus by viewModel.dualPublishStatus.collectAsStateWithLifecycle()
    val exportQuality by viewModel.exportQuality.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()

    var selectVideoInitialTab by remember { mutableIntStateOf(0) }
    var pendingYouTubeUploadClip by remember { mutableStateOf<StudioClip?>(null) }
    var pendingSendYouTube by remember { mutableStateOf(true) }
    var pendingSendTikTok by remember { mutableStateOf(true) }
    var pendingDescription by remember { mutableStateOf("") }

    val youtubeConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.data == null && result.resultCode != Activity.RESULT_OK) {
            viewModel.onYouTubeAuthError("Permissão do YouTube cancelada pelo utilizador.")
            return@rememberLauncherForActivityResult
        }
        YouTubeAuthHelper.parseAuthorizationResult(context, result.data).fold(
            onSuccess = { token -> viewModel.onYouTubeTokenGranted(token, context) },
            onFailure = { e -> viewModel.onYouTubeAuthError(e.localizedMessage ?: "Falha na autorização do YouTube.") }
        )
    }

    val youtubeUploadConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val clipToRetry = pendingYouTubeUploadClip
        val retryYt = pendingSendYouTube
        val retryTt = pendingSendTikTok
        val retryDesc = pendingDescription
        pendingYouTubeUploadClip = null
        if (clipToRetry == null) return@rememberLauncherForActivityResult

        if (result.data == null && result.resultCode != Activity.RESULT_OK) {
            viewModel.publishWithSelectedPlatforms(
                context = context,
                clip = clipToRetry,
                sendToYouTube = retryYt,
                sendToTikTok = retryTt,
                description = retryDesc
            )
            return@rememberLauncherForActivityResult
        }
        YouTubeAuthHelper.parseAuthorizationResult(context, result.data).fold(
            onSuccess = {
                viewModel.publishWithSelectedPlatforms(
                    context = context,
                    clip = clipToRetry,
                    sendToYouTube = retryYt,
                    sendToTikTok = retryTt,
                    description = retryDesc
                )
            },
            onFailure = {
                viewModel.publishWithSelectedPlatforms(
                    context = context,
                    clip = clipToRetry,
                    sendToYouTube = retryYt,
                    sendToTikTok = retryTt,
                    description = retryDesc
                )
            }
        )
    }

    fun connectYouTubeLibrary() {
        val activity = context.findActivity()
        if (activity == null) {
            viewModel.onYouTubeAuthError("Atividade indisponível para iniciar consentimento Google.")
            return
        }
        YouTubeAuthHelper.authorizeWithConsent(
            activity = activity,
            onAuthorized = { token -> viewModel.onYouTubeTokenGranted(token, context) },
            onNeedsConsent = { request -> youtubeConsentLauncher.launch(request) },
            onError = { msg -> viewModel.onYouTubeAuthError(msg) }
        )
    }

    val tikTokCallbackUri by TikTokOAuthCallbackBus.pendingCallbackUri.collectAsStateWithLifecycle()

    LaunchedEffect(currentUserId) {
        viewModel.hydrateRenderedShorts(context)
        viewModel.checkSilentYouTubeAuth(context)
    }

    LaunchedEffect(tikTokCallbackUri) {
        val uri = tikTokCallbackUri
        if (uri != null) {
            TikTokOAuthCallbackBus.consume()
            viewModel.handleTikTokOAuthCallback(context, uri)
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val currentRoute = currentDestination?.route
    val isHomeSubRoute = currentRoute == ROUTE_SELECT_VIDEO || currentRoute == ROUTE_CONNECT
    val isEditorSubRoute = currentRoute == ROUTE_PUBLISH
    val isStatsSubRoute = currentRoute == ROUTE_CHAT
    val isProfileSubRoute = currentRoute == ROUTE_SETTINGS

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = SpaceNavy950,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = SpaceNavy900,
                        contentColor = TextPrimary
                    ) {
                        TopLevelItems.forEach { item ->
                            val selected = when {
                                item.route == ROUTE_HOME && isHomeSubRoute -> true
                                item.route == ROUTE_EDITOR && isEditorSubRoute -> true
                                item.route == ROUTE_STATS && isStatsSubRoute -> true
                                item.route == ROUTE_PROFILE && isProfileSubRoute -> true
                                else -> currentDestination?.hierarchy?.any { it.route == item.route } == true
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricVioletLight,
                                    selectedTextColor = ElectricVioletLight,
                                    indicatorColor = ElectricViolet.copy(alpha = 0.22f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
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
                if (useRail) {
                    NavigationRail(
                        containerColor = SpaceNavy900,
                        contentColor = TextPrimary
                    ) {
                        TopLevelItems.forEach { item ->
                            val selected = when {
                                item.route == ROUTE_HOME && isHomeSubRoute -> true
                                item.route == ROUTE_EDITOR && isEditorSubRoute -> true
                                item.route == ROUTE_STATS && isStatsSubRoute -> true
                                item.route == ROUTE_PROFILE && isProfileSubRoute -> true
                                else -> currentDestination?.hierarchy?.any { it.route == item.route } == true
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricVioletLight,
                                    selectedTextColor = ElectricVioletLight,
                                    indicatorColor = SpaceNavy850,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            ytLibraryState = ytLibraryState,
                            clipsState = clipsState,
                            autopilotBusySourceId = autopilotBusyId,
                            onOpenVideosList = {
                                selectVideoInitialTab = 0
                                navController.navigate(ROUTE_SELECT_VIDEO)
                            },
                            onOpenLivesList = {
                                selectVideoInitialTab = 1
                                navController.navigate(ROUTE_SELECT_VIDEO)
                            },
                            onOpenVideoInEditor = { video ->
                                viewModel.importSelectedLibraryVideosAndOpenEditor(
                                    videos = listOf(video),
                                    context = context,
                                    onComplete = { navigateTopLevel(ROUTE_EDITOR) }
                                )
                            },
                            onOpenClipInEditor = { clipId ->
                                viewModel.selectClip(clipId)
                                navigateTopLevel(ROUTE_EDITOR)
                            },
                            onOpenConnectAccounts = { navController.navigate(ROUTE_CONNECT) },
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                            onRefreshYouTube = { viewModel.refreshYouTubeLibrary(context) }
                        )
                    }

                    composable(ROUTE_SELECT_VIDEO) {
                        SelectVideoScreen(
                            ytLibraryState = ytLibraryState,
                            initialTabIndex = selectVideoInitialTab,
                            autopilotBusySourceId = autopilotBusyId,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                            onRefreshYouTube = { viewModel.refreshYouTubeLibrary(context) },
                            onLoadPublicChannel = { hint ->
                                viewModel.loadPublicChannelLibrary(hint, context.applicationContext)
                            },
                            onSearchQueryChange = viewModel::updateYouTubeSearchQuery,
                            onLoadMoreYouTube = viewModel::loadMoreYouTubeVideos,
                            onContinueWithSelected = { selectedVideos ->
                                viewModel.importSelectedLibraryVideosAndOpenEditor(
                                    videos = selectedVideos,
                                    context = context,
                                    onComplete = { navigateTopLevel(ROUTE_EDITOR) }
                                )
                            },
                            onPickLocalVideo = { uri ->
                                viewModel.generateAutoMp4ShortsFromLocalVideo(context, uri) {
                                    navigateTopLevel(ROUTE_EDITOR)
                                }
                            }
                        )
                    }

                    composable(ROUTE_CONNECT) {
                        ConnectAccountsScreen(
                            ytLibraryState = ytLibraryState,
                            tikTokAccount = tikTokAccount,
                            onConnectYouTube = ::connectYouTubeLibrary,
                            onDisconnectYouTube = { viewModel.disconnectYouTube(context) },
                            onLoadPublicChannel = { hint ->
                                viewModel.loadPublicChannelLibrary(hint, context.applicationContext)
                            },
                            onStartTikTokOAuth = { viewModel.startTikTokOAuth(context) },
                            onConnectTikTokHandle = { handle -> viewModel.connectTikTokAccount(context, handle) },
                            onDisconnectTikTok = { viewModel.disconnectTikTokAccount(context) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(ROUTE_EDITOR) {
                        EditorScreen(
                            clipsState = clipsState,
                            sourcesState = sourcesState,
                            selectedClipId = selectedClipId,
                            renderedClipIds = renderedClipIds,
                            clipVisualStyles = clipVisualStyles,
                            aiEditBusy = aiEditBusy,
                            localVideoInfo = localVideoInfo,
                            shortExportState = shortExportState,
                            imageGenState = imageGenState,
                            selectedImageSize = selectedImageSize,
                            selectedImageAspect = selectedImageAspect,
                            selectedImageModel = selectedImageModel,
                            onSelectClip = viewModel::selectClip,
                            onCreateManualClip = viewModel::createManualClip,
                            onSaveClipEdits = viewModel::saveClipEdits,
                            onUpdateVisualStyle = { clipId, transform ->
                                viewModel.updateClipVisualStyle(context, clipId, transform)
                            },
                            onRunAiAutoEdit = { clip -> viewModel.runAiAutoEditForClip(context, clip) },
                            onTranscribeClipSpeech = { clip -> viewModel.transcribeClipSpeechWithAi(context, clip) },
                            onAiOptimizeAndCutScene = { clip, start, end, ratio, panX ->
                                viewModel.runAiCutAndOptimizeScene(
                                    context = context,
                                    clip = clip,
                                    startLabel = start,
                                    endLabel = end,
                                    aspectRatio = ratio,
                                    manualPanX = panX,
                                    autoPublishOverride = false
                                )
                            },
                            onLoadLocalVideoForEditor = { uri, clip ->
                                viewModel.loadLocalVideoIntoEditor(context, uri, clip)
                            },
                            onCreateMp4FromPhotosForClip = { uris, targetClip ->
                                viewModel.createShortMp4FromPhotos(
                                    context = context,
                                    uris = uris,
                                    title = targetClip?.title ?: "Short de Fotos 9:16",
                                    targetClip = targetClip
                                )
                            },
                            onRenderClipToMp4 = { clip, aspect, start, end, panX ->
                                viewModel.renderClipToMp4(
                                    context = context,
                                    clip = clip,
                                    aspectRatio = aspect,
                                    startLabel = start,
                                    endLabel = end,
                                    manualPanX = panX,
                                    triggerAutoPublish = false
                                )
                            },
                            onCancelShortExport = viewModel::cancelShortExport,
                            onSelectImageSize = viewModel::selectImageSize,
                            onSelectImageAspect = viewModel::selectImageAspect,
                            onSelectImageModel = viewModel::selectImageModel,
                            onGenerateCoverImage = { prompt, headline, emoji, filter, photoUri, youtubeVideoId, activeClipId, preferLocalStudio ->
                                viewModel.generateCoverImage(
                                    context = context,
                                    prompt = prompt,
                                    headline = headline,
                                    emojiBadge = emoji,
                                    filterPreset = filter,
                                    backgroundPhotoUri = photoUri,
                                    youtubeVideoId = youtubeVideoId,
                                    activeClipId = activeClipId,
                                    preferLocalStudio = preferLocalStudio
                                )
                            },
                            onApplyCoverToClip = { clipId, bitmap ->
                                viewModel.applyCoverToActiveClip(context, clipId, bitmap)
                            },
                            onSaveCoverToGallery = { bitmap, title ->
                                viewModel.saveCoverToGallery(context, bitmap, title)
                            },
                            onNextToPublish = { clip ->
                                viewModel.saveClipEdits(clip)
                                viewModel.selectClip(clip.id)
                                navController.navigate(ROUTE_PUBLISH)
                            },
                            onOpenSelectVideo = {
                                selectVideoInitialTab = 0
                                navController.navigate(ROUTE_SELECT_VIDEO)
                            },
                            onBack = { navigateTopLevel(ROUTE_HOME) }
                        )
                    }

                    composable(ROUTE_PUBLISH) {
                        PublishScreen(
                            clipsState = clipsState,
                            sourcesState = sourcesState,
                            selectedClipId = selectedClipId,
                            clipVisualStyles = clipVisualStyles,
                            tikTokAccount = tikTokAccount,
                            isYouTubeConnected = ytLibraryState.isConnected,
                            shortExportState = shortExportState,
                            youTubeUploadBusy = youTubeUploadBusy,
                            dualPublishStatus = dualPublishStatus,
                            onPublishNow = { clip, sendYt, sendTt, desc ->
                                viewModel.publishWithSelectedPlatforms(
                                    context = context,
                                    clip = clip,
                                    sendToYouTube = sendYt,
                                    sendToTikTok = sendTt,
                                    description = desc,
                                    activity = context.findActivity(),
                                    onNeedsUploadConsent = { req ->
                                        pendingYouTubeUploadClip = clip
                                        pendingSendYouTube = sendYt
                                        pendingSendTikTok = sendTt
                                        pendingDescription = desc
                                        youtubeUploadConsentLauncher.launch(req)
                                    }
                                )
                            },
                            onSaveToGallery = { clip -> viewModel.saveClipToGallery(context, clip) },
                            onShareYouTubeFallback = { clip -> viewModel.launchYouTubeShareFallback(context, clip) },
                            onShareTikTokFallback = { clip -> viewModel.launchTikTokShareFallback(context, clip) },
                            onDismissDualPublish = viewModel::clearDualPublishStatus,
                            onOpenConnectAccounts = { navController.navigate(ROUTE_CONNECT) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(ROUTE_TEMPLATES) {
                        TemplatesScreen(
                            onUseTemplate = { template ->
                                viewModel.applyTemplateToActiveClip(context, template) {
                                    navigateTopLevel(ROUTE_EDITOR)
                                }
                            },
                            onBack = { navigateTopLevel(ROUTE_HOME) }
                        )
                    }

                    composable(ROUTE_STATS) {
                        StatsScreen(
                            ytLibraryState = ytLibraryState,
                            sourcesState = sourcesState,
                            clipsState = clipsState,
                            renderedClipIds = renderedClipIds,
                            onOpenClipInEditor = { clipId ->
                                viewModel.selectClip(clipId)
                                navigateTopLevel(ROUTE_EDITOR)
                            },
                            onOpenAiProducerChat = { navController.navigate(ROUTE_CHAT) },
                            onOpenConnectAccounts = { navController.navigate(ROUTE_CONNECT) }
                        )
                    }

                    composable(ROUTE_CHAT) {
                        ChatbotScreen(
                            chatState = chatState,
                            onSelectRole = viewModel::selectChatRole,
                            onSelectModel = viewModel::selectChatModel,
                            onSendMessage = viewModel::sendChatMessage,
                            onResetChat = viewModel::resetChat
                        )
                    }

                    composable(ROUTE_PROFILE) {
                        SettingsScreen(
                            userEmail = userEmail,
                            userName = userName,
                            ytLibraryState = ytLibraryState,
                            tikTokAccount = tikTokAccount,
                            exportQuality = exportQuality,
                            notificationsEnabled = notificationsEnabled,
                            showBackButton = false,
                            onOpenConnectAccounts = { navController.navigate(ROUTE_CONNECT) },
                            onSelectExportQuality = { q -> viewModel.setExportQuality(context, q) },
                            onToggleNotifications = { en -> viewModel.setNotificationsEnabled(context, en) },
                            onSignOut = {
                                YouTubeRetrofitClient.setAccessToken(null)
                                signOutUser(credentialManager, scope)
                            },
                            onBack = { navigateTopLevel(ROUTE_HOME) }
                        )
                    }

                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            userEmail = userEmail,
                            userName = userName,
                            ytLibraryState = ytLibraryState,
                            tikTokAccount = tikTokAccount,
                            exportQuality = exportQuality,
                            notificationsEnabled = notificationsEnabled,
                            showBackButton = true,
                            onOpenConnectAccounts = { navController.navigate(ROUTE_CONNECT) },
                            onSelectExportQuality = { q -> viewModel.setExportQuality(context, q) },
                            onToggleNotifications = { en -> viewModel.setNotificationsEnabled(context, en) },
                            onSignOut = {
                                YouTubeRetrofitClient.setAccessToken(null)
                                signOutUser(credentialManager, scope)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
