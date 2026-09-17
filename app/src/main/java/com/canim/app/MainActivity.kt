package com.canim.app

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import dagger.hilt.android.AndroidEntryPoint
import com.canim.app.data.model.MediaType
import com.canim.app.ui.components.AdaptiveNavigationRail
import com.canim.app.ui.components.RateLimitBanner
import com.canim.app.ui.navigation.PredictiveBackOverlayContainer
import com.canim.app.ui.navigation.ScreenRoute
import com.canim.app.ui.screens.*
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.update.UpdateViewModel
import com.canim.app.ui.viewmodel.gacha.GachaViewModel
import com.canim.app.ui.viewmodel.detail.DetailViewModel
import com.canim.app.ui.viewmodel.studio.StudioViewModel
import com.canim.app.ui.viewmodel.search.SearchViewModel
import com.canim.app.ui.viewmodel.discover.DiscoverViewModel
import com.canim.app.ui.viewmodel.library.LibraryViewModel
import com.canim.app.ui.viewmodel.global.GlobalViewModel
import com.canim.app.ui.viewmodel.calendar.AiringCalendarViewModel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

data class NavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val updateViewModel: UpdateViewModel by viewModels()
    private val gachaViewModel: GachaViewModel by viewModels()
    private val detailViewModel: DetailViewModel by viewModels()
    private val studioViewModel: StudioViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val discoverViewModel: DiscoverViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val globalViewModel: GlobalViewModel by viewModels()
    private val calendarViewModel: AiringCalendarViewModel by viewModels()

    @javax.inject.Inject
    lateinit var airingAlertManager: com.canim.app.notification.AiringAlertManager

    @javax.inject.Inject
    lateinit var notificationManager: com.canim.app.notification.CanimNotificationManager

    private var notificationSoundTitle by mutableStateOf("Default Sistem")

    private val ringtonePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            notificationManager.setNotificationSoundUri(uri)
            updateSoundTitle()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun updateSoundTitle() {
        val soundUri = notificationManager.getNotificationSoundUri()
        notificationSoundTitle = try {
            val ringtone = RingtoneManager.getRingtone(this, soundUri)
            ringtone?.getTitle(this) ?: "Default Sistem"
        } catch (_: Exception) {
            "Default Sistem"
        }
    }

    private fun pickNotificationSound() {
        val currentUri = notificationManager.getNotificationSoundUri()
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pilih Suara Notifikasi")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        }
        ringtonePickerLauncher.launch(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateSoundTitle()

        // Request notification permission on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle OAuth Deep Link callback on initial launch
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }
        handleNotificationIntent(intent)

        setContent {
            CanimTheme {
                val updateState by updateViewModel.updateState.collectAsState()
                val gachaState by gachaViewModel.gachaState.collectAsState()
                val detailState by detailViewModel.detailState.collectAsState()
                val studioState by studioViewModel.studioState.collectAsState()
                val searchState by searchViewModel.searchState.collectAsState()
                val discoverState by discoverViewModel.discoverState.collectAsState()
                val libraryState by libraryViewModel.libraryState.collectAsState()
                val globalState by globalViewModel.globalState.collectAsState()
                val screenStack by globalViewModel.screenStack.collectAsState()
                val calendarState by calendarViewModel.calendarState.collectAsState()
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }

                // Periodic or on-load airing episode alert check for watching anime
                val watchingAnime = remember(libraryState.animeList) {
                    libraryState.animeList.filter { it.status == "watching" }
                }
                LaunchedEffect(watchingAnime) {
                    if (watchingAnime.isNotEmpty()) {
                        airingAlertManager.checkAndDispatchAiringAlerts(watchingAnime)
                    }
                }



                LaunchedEffect(Unit) {
                    merge(
                        globalViewModel.snackbarEvent,
                        updateViewModel.snackbarEvent,
                        gachaViewModel.snackbarEvent,
                        detailViewModel.snackbarEvent,
                        studioViewModel.snackbarEvent,
                        searchViewModel.snackbarEvent,
                        discoverViewModel.snackbarEvent,
                        libraryViewModel.snackbarEvent
                    ).collect { message ->
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short
                        )
                    }
                }

                // Reordered Navigation Items with Search placed strictly in the center (Index 2)
                val navItems = remember {
                    listOf(
                        NavItem("dashboard", "Dasbor", Icons.Filled.Home, Icons.Outlined.Home),
                        NavItem("library", "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
                        NavItem("search", "Cari", Icons.Filled.Search, Icons.Outlined.Search), // CENTER (Index 2)
                        NavItem("discover", "Discover", Icons.Filled.Explore, Icons.Outlined.Explore),
                        NavItem("settings", "Pengaturan", Icons.Filled.Settings, Icons.Outlined.Settings)
                    )
                }

                var isColdStarting by rememberSaveable { mutableStateOf(true) }

                if (isColdStarting) {
                    ColdStartSplashScreen(
                        onFinished = {
                            isColdStarting = false
                        }
                    )
                } else if (!globalState.malUser.isLoggedIn) {
                    LoginScreen(
                        onLoginMal = {
                            globalViewModel.loginWithMal(context)
                        },
                        isExchangingToken = globalState.isExchangingToken
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWideScreen = maxWidth >= 600.dp
                        val hasOverlay = screenStack.isNotEmpty()

                        Row(modifier = Modifier.fillMaxSize()) {
                            if (isWideScreen) {
                                AdaptiveNavigationRail(
                                    navItems = navItems,
                                    activeTab = globalState.activeTab,
                                    onTabSelected = { globalViewModel.setTab(it) }
                                )
                            }

                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Scaffold(
                                    modifier = Modifier.fillMaxSize(),
                                    containerColor = BlackBg,
                                    snackbarHost = {
                                        SnackbarHost(
                                            hostState = snackbarHostState,
                                            modifier = Modifier.padding(bottom = if (isWideScreen) 16.dp else 80.dp)
                                        ) { data ->
                                            Snackbar(
                                                snackbarData = data,
                                                containerColor = CardElevated,
                                                contentColor = TextPrimary,
                                                actionColor = AccentBlue
                                            )
                                        }
                                    },
                                    floatingActionButton = {},
                                    bottomBar = {
                                        if (!isWideScreen) {
                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = !hasOverlay,
                                                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                                                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
                                            ) {
                                                val activeIndex = navItems.indexOfFirst { it.route == globalState.activeTab }.coerceAtLeast(0)

                                            Surface(
                                                color = CardBg,
                                                contentColor = TextPrimary,
                                                tonalElevation = 8.dp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("main_bottom_nav")
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .navigationBarsPadding()
                                                        .height(64.dp)
                                                ) {
                                                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                                        val count = navItems.size
                                                        val itemWidth = maxWidth / count
                                                        val indicatorOffset by androidx.compose.animation.core.animateDpAsState(
                                                            targetValue = itemWidth * activeIndex,
                                                            animationSpec = androidx.compose.animation.core.spring(
                                                                dampingRatio = 0.8f,
                                                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                                            ),
                                                            label = "nav_indicator_offset"
                                                        )

                                                        // Sliding Highlight Pill
                                                        Box(
                                                            modifier = Modifier
                                                                .offset(x = indicatorOffset)
                                                                .width(itemWidth)
                                                                .fillMaxHeight(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (activeIndex == 2) {
                                                                // Center search tab glowing aura
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(48.dp)
                                                                        .clip(CircleShape)
                                                                        .background(AccentBlue.copy(alpha = 0.2f))
                                                                )
                                                            } else {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .height(36.dp)
                                                                        .width(54.dp)
                                                                        .clip(RoundedCornerShape(18.dp))
                                                                        .background(AccentBlue.copy(alpha = 0.16f))
                                                                        .border(1.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                                                                )
                                                            }
                                                        }

                                                        // Interactive Tab Items Row
                                                        Row(
                                                            modifier = Modifier.fillMaxSize(),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            navItems.forEachIndexed { _, item ->
                                                                val selected = globalState.activeTab == item.route
                                                                val isSearch = item.route == "search"

                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxHeight()
                                                                        .clickable(
                                                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                                            indication = null
                                                                        ) {
                                                                            globalViewModel.setTab(item.route)
                                                                        }
                                                                        .testTag("nav_tab_${item.route}"),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    if (isSearch) {
                                                                        // Unique Cyber Floating Search Button
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(46.dp)
                                                                                .clip(CircleShape)
                                                                                .background(
                                                                                    if (selected) {
                                                                                        Brush.linearGradient(listOf(AccentBlue, Color(0xFF1D4ED8)))
                                                                                    } else {
                                                                                        Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
                                                                                    }
                                                                                )
                                                                                .border(
                                                                                    width = if (selected) 2.dp else 1.dp,
                                                                                    color = if (selected) Color(0xFF60A5FA) else CardBorderSubtle,
                                                                                    shape = CircleShape
                                                                                ),
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Icon(
                                                                                imageVector = Icons.Filled.Search,
                                                                                contentDescription = "Cari",
                                                                                tint = if (selected) Color.White else AccentBlueLight,
                                                                                modifier = Modifier.size(22.dp)
                                                                            )
                                                                        }
                                                                    } else {
                                                                        Icon(
                                                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                                            contentDescription = item.title,
                                                                            tint = if (selected) AccentBlue else TextMuted,
                                                                            modifier = Modifier.size(24.dp)
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
                                }
                                ) { innerPadding ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                            .background(BlackBg)
                                    ) {
                        // Stable hoisted callbacks to ensure 100% skippable recomposition during scrolling
                        val onQuickAddEpisode: (String) -> Unit = remember { {
                            libraryViewModel.quickIncrementAnime(it)
                        } }
                        val onQuickAddChapter: (String) -> Unit = remember { {
                            libraryViewModel.quickIncrementManga(it)
                        } }
                        val onSelectItem: (Any, MediaType) -> Unit = remember { { item: Any, type: MediaType ->
                            globalViewModel.openDetail(item, type)
                            detailViewModel.openDetail(item, type)
                        } }
                        val onLoadDemoData: () -> Unit = remember { {
                            libraryViewModel.loadDemoData()
                        } }
                        val onNavigateTab: (String) -> Unit = remember { {
                            globalViewModel.setTab(it)
                        } }
                        val onLoginMal: () -> Unit = remember(context) { {
                            globalViewModel.loginWithMal(context)
                        } }
                        val onSyncMal: () -> Unit = remember { {
                            globalViewModel.syncWithMal {
                                libraryViewModel.loadUserLibrary(forceRefresh = true)
                            }
                        } }
                        val onOpenStats: () -> Unit = remember { {
                            globalViewModel.openStats()
                        } }
                        val onOpenFlashcard: () -> Unit = remember { {
                            globalViewModel.openFlashcard()
                            gachaViewModel.openFlashcard()
                        } }

                        val onSelectMediaType: (MediaType) -> Unit = remember { {
                            libraryViewModel.setLibraryFilterType(it)
                        } }
                        val onSelectStatusFilter: (String?) -> Unit = remember { {
                            libraryViewModel.setLibraryStatusFilter(it)
                        } }
                        val onSelectSort: (String) -> Unit = remember { {
                            libraryViewModel.setLibrarySort(it)
                        } }
                        val onSearchQueryChange: (String) -> Unit = remember { {
                            libraryViewModel.setLibrarySearch(it)
                        } }
                        val onQuickDecrementAnime: (String) -> Unit = remember { {
                            libraryViewModel.quickDecrementAnime(it)
                        } }
                        val onQuickDecrementManga: (String) -> Unit = remember { {
                            libraryViewModel.quickDecrementManga(it)
                        } }

                        AnimatedContent(
                            targetState = globalState.activeTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)).togetherWith(fadeOut(animationSpec = tween(180)))
                            },
                            label = "TabContent"
                        ) { activeTab ->
                            when (activeTab) {
                                "dashboard" -> {
                                    DashboardScreen(
                                        libraryState = libraryState,
                                        globalState = globalState,
                                        gachaState = gachaState,
                                        onQuickAddEpisode = onQuickAddEpisode,
                                        onQuickAddChapter = onQuickAddChapter,
                                        onSelectItem = onSelectItem,
                                        onLoadDemoData = onLoadDemoData,
                                        onNavigateTab = onNavigateTab,
                                        onLoginMal = onLoginMal,
                                        onSyncMal = onSyncMal,
                                        onOpenStats = onOpenStats,
                                        onOpenFlashcard = onOpenFlashcard
                                    )
                                }
                                "library" -> {
                                    LibraryScreen(
                                        libraryState = libraryState,
                                        onSelectMediaType = onSelectMediaType,
                                        onSelectStatusFilter = onSelectStatusFilter,
                                        onSelectSort = onSelectSort,
                                        onSearchQueryChange = onSearchQueryChange,
                                        onQuickAddAnime = onQuickAddEpisode,
                                        onQuickDecrementAnime = onQuickDecrementAnime,
                                        onQuickAddManga = onQuickAddChapter,
                                        onQuickDecrementManga = onQuickDecrementManga,
                                        onSelectItem = onSelectItem,
                                        onOpenCalendar = {
                                            globalViewModel.openAiringCalendar()
                                        },
                                        onRefresh = onSyncMal
                                    )
                                }
                                "search" -> {
                                    SearchScreen(
                                        searchState = searchState,
                                        libraryState = libraryState,
                                        onSearch = { query, type ->
                                            searchViewModel.search(query, type)
                                        },
                                        onAddMedia = { item, status ->
                                            libraryViewModel.addFromCatalog(item, status)
                                        },
                                        onSelectItem = onSelectItem,
                                        onSaveAnime = {
                                            libraryViewModel.saveAnime(it)
                                        },
                                        onSaveManga = {
                                            libraryViewModel.saveManga(it)
                                        },
                                        onApplyFilters = { genres, year, format ->
                                            searchViewModel.applySearchFilters(genres, year, format)
                                        },
                                        onResetFilters = {
                                            searchViewModel.resetSearchFilters()
                                        },
                                        onLoadMore = {
                                            searchViewModel.loadMore()
                                        }
                                    )
                                }
                                "discover" -> {
                                    DiscoverScreen(
                                        discoverState = discoverState,
                                        libraryState = libraryState,
                                        studioViewModel = studioViewModel,
                                        isAniListDown = globalState.isAniListDown,
                                        onSelectCategory = { cat, filter, type ->
                                            discoverViewModel.loadDiscoverCategory(cat, filter, mediaType = type)
                                        },
                                        onAddMedia = { item, status ->
                                            libraryViewModel.addFromCatalog(item, status)
                                        },
                                        onSelectItem = onSelectItem,
                                        onLoadMore = {
                                            discoverViewModel.loadMoreDiscover()
                                        },
                                        onSaveAnime = {
                                            libraryViewModel.saveAnime(it)
                                        },
                                        onSaveManga = {
                                            libraryViewModel.saveManga(it)
                                        },
                                        onOpenStudio = { studioId, studioName ->
                                            studioViewModel.openStudio(studioId, studioName)
                                            globalViewModel.openStudio(studioId, studioName)
                                        },
                                        onGetStudioInfo = { studioId, studioName -> studioViewModel.getStudioInfo(studioId, studioName) },
                                        onRefresh = {
                                            discoverViewModel.loadDiscoverCategory(
                                                discoverState.selectedCategory,
                                                discoverState.filter,
                                                forceRefresh = true
                                            )
                                        }
                                    )
                                }
                                "settings" -> {
                                    SettingsScreen(
                                        globalState = globalState,
                                        updateState = updateState,
                                        onLoginMal = {
                                            globalViewModel.loginWithMal(context)
                                        },
                                        onSyncMal = {
                                            globalViewModel.syncWithMal {
                                                libraryViewModel.loadUserLibrary(forceRefresh = true)
                                            }
                                        },
                                        onLogoutMal = {
                                            globalViewModel.logoutMal {
                                                libraryViewModel.loadDemoData()
                                            }
                                        },
                                        onSetAppMode = {
                                            globalViewModel.setAppMode(it)
                                        },
                                        onLoadDemoData = {
                                            libraryViewModel.loadDemoData()
                                        },
                                        onClearAllData = {
                                            libraryViewModel.clearAllData()
                                        },
                                        onClearImageCache = {
                                            globalViewModel.clearImageCache(context)
                                        },
                                        onClearMetadataCache = {
                                            globalViewModel.clearMetadataCache()
                                        },
                                        onCheckForUpdates = { updateViewModel.checkForUpdates(manual = true) },
                                        onSetAutoUpdateCheck = { updateViewModel.setAutoUpdateCheck(it) },
                                        onDismissUpdateDialog = { updateViewModel.dismissUpdateDialog() },
                                        onStartDownloadUpdate = { updateViewModel.startDownloadUpdate(context) },
                                        onInstallDownloadedUpdate = { updateViewModel.installDownloadedUpdate(context) },
                                        notificationSoundTitle = notificationSoundTitle,
                                        onPickNotificationSound = { pickNotificationSound() }
                                    )
                                }
                            }
                        }

                        LaunchedEffect(screenStack.isEmpty()) {
                            if (screenStack.isEmpty()) {
                                detailViewModel.closeDetail()
                                studioViewModel.closeStudio()
                            }
                        }

                        // Top-level modal/overlay stack rendering with Animite-inspired predictive back gestures & two-layer stack
                        PredictiveBackOverlayContainer(
                            screenStack = screenStack,
                            onPopScreen = { globalViewModel.popScreen() },
                            modifier = Modifier.fillMaxSize()
                        ) { currentScreen, isTop ->
                            when (currentScreen) {
                                is ScreenRoute.CastCrew -> {
                                    val isCurrentProfile = detailState.selectedCastCrewProfile?.id == currentScreen.id &&
                                        detailState.selectedCastCrewProfile?.isStaff == currentScreen.isStaff
                                    val effectiveProfile = if (isCurrentProfile) {
                                        detailState.selectedCastCrewProfile
                                    } else {
                                        detailViewModel.getCachedCastCrewProfile(currentScreen.id, currentScreen.isStaff)
                                    }

                                    LaunchedEffect(currentScreen.id, currentScreen.isStaff, isTop) {
                                        if (isTop && effectiveProfile == null) {
                                            detailViewModel.openCastCrewProfile(currentScreen.id, currentScreen.isStaff)
                                        }
                                    }

                                    CastCrewProfileScreen(
                                        profile = effectiveProfile,
                                        isLoading = if (effectiveProfile != null) false else detailState.isLoadingCastCrewProfile,
                                        onBack = {
                                            globalViewModel.popScreen()
                                        },
                                        onSelectMedia = { mediaItem ->
                                            detailViewModel.openDetail(mediaItem, mediaItem.type)
                                            globalViewModel.openDetail(mediaItem, mediaItem.type)
                                        },
                                        onSaveScrollPosition = { key, index, offset ->
                                            detailViewModel.saveDetailScrollPosition(key, index, offset)
                                        },
                                        onGetScrollPosition = { key ->
                                            detailViewModel.getDetailScrollPosition(key)
                                        }
                                    )
                                }
                                is ScreenRoute.FullCastList -> {
                                    FullCastListScreen(
                                        mediaTitle = currentScreen.mediaTitle,
                                        castList = currentScreen.castList,
                                        staffList = currentScreen.staffList,
                                        isCrewInitial = currentScreen.isCrewInitial,
                                        onBack = {
                                            globalViewModel.popScreen()
                                        },
                                        onOpenCastCrew = { id, isStaff ->
                                            detailViewModel.openCastCrewProfile(id, isStaff)
                                            globalViewModel.openCastCrewProfile(id, isStaff)
                                        },
                                        onSaveScrollPosition = { key, index, offset ->
                                            detailViewModel.saveDetailScrollPosition(key, index, offset)
                                        },
                                        onGetScrollPosition = { key ->
                                            detailViewModel.getDetailScrollPosition(key)
                                        }
                                    )
                                }
                                is ScreenRoute.Detail -> {
                                    val currentMal = (currentScreen.item as? com.canim.app.data.model.UserMediaItem)?.malId
                                        ?: (currentScreen.item as? com.canim.app.data.model.MediaItem)?.malId
                                        ?: (currentScreen.item as? com.canim.app.data.model.AiringAnimeItem)?.malId
                                    val currentAni = (currentScreen.item as? com.canim.app.data.model.UserMediaItem)?.anilistId
                                        ?: (currentScreen.item as? com.canim.app.data.model.MediaItem)?.anilistId
                                        ?: (currentScreen.item as? com.canim.app.data.model.AiringAnimeItem)?.anilistId

                                    val selectedMal = (detailState.selectedItem as? com.canim.app.data.model.UserMediaItem)?.malId
                                        ?: (detailState.selectedItem as? com.canim.app.data.model.MediaItem)?.malId
                                        ?: (detailState.selectedItem as? com.canim.app.data.model.AiringAnimeItem)?.malId
                                    val selectedAni = (detailState.selectedItem as? com.canim.app.data.model.UserMediaItem)?.anilistId
                                        ?: (detailState.selectedItem as? com.canim.app.data.model.MediaItem)?.anilistId
                                        ?: (detailState.selectedItem as? com.canim.app.data.model.AiringAnimeItem)?.anilistId

                                    val isCurrentDetailSelected = (currentMal != null && currentMal == selectedMal) ||
                                        (currentAni != null && currentAni == selectedAni) ||
                                        (detailState.selectedItem != null && currentMal == null && currentAni == null)

                                    LaunchedEffect(currentScreen.item, currentScreen.type, isTop) {
                                        if (isTop && !isCurrentDetailSelected) {
                                            detailViewModel.openDetail(currentScreen.item, currentScreen.type)
                                        }
                                    }

                                    val detailItem = if (isCurrentDetailSelected) {
                                        detailState.selectedItem ?: currentScreen.item
                                    } else {
                                        currentScreen.item
                                    }
                                    val detailExtended = (if (isCurrentDetailSelected) detailState.extendedDetail else null)
                                        ?: detailViewModel.getCachedDetail(currentScreen.item)
                                    val detailIsLoading = if (isCurrentDetailSelected) {
                                        detailState.isLoadingExtendedDetail
                                    } else {
                                        detailExtended == null
                                    }
                                    val detailTitle = (detailItem as? com.canim.app.data.model.UserMediaItem)?.title
                                        ?: (detailItem as? com.canim.app.data.model.MediaItem)?.title
                                        ?: (detailItem as? com.canim.app.data.model.AiringAnimeItem)?.title
                                        ?: ""
                                    MediaDetailScreen(
                                        item = detailItem,
                                        type = currentScreen.type,
                                        extendedDetail = detailExtended,
                                        isLoadingExtendedDetail = detailIsLoading,
                                        libraryViewModel = libraryViewModel,
                                        onSaveAnime = {
                                            libraryViewModel.saveAnime(it)
                                        },
                                        onSaveManga = {
                                            libraryViewModel.saveManga(it)
                                        },
                                        onDeleteAnime = {
                                            libraryViewModel.deleteAnime(it)
                                        },
                                        onDeleteManga = {
                                            libraryViewModel.deleteManga(it)
                                        },
                                        onOpenCastCrew = { id, isStaff ->
                                            detailViewModel.openCastCrewProfile(id, isStaff)
                                            globalViewModel.openCastCrewProfile(id, isStaff)
                                        },
                                        onOpenFullCast = { isCrew ->
                                            globalViewModel.openFullCastList(
                                                mediaTitle = detailTitle,
                                                castList = detailExtended?.cast ?: emptyList(),
                                                staffList = detailExtended?.crew ?: emptyList(),
                                                isCrewInitial = isCrew
                                            )
                                        },
                                        onOpenStudio = { studioId, studioName ->
                                            studioViewModel.openStudio(studioId, studioName)
                                            globalViewModel.openStudio(studioId, studioName)
                                        },
                                        onOpenMediaDetail = { media, mediaType ->
                                            detailViewModel.openDetail(media, mediaType)
                                            globalViewModel.openDetail(media, mediaType)
                                        },
                                        onResolveAniListId = { malId -> detailViewModel.getAniListIdForMalId(malId) },
                                        onSaveScrollPosition = { key, index, offset ->
                                            detailViewModel.saveDetailScrollPosition(key, index, offset)
                                        },
                                        onGetScrollPosition = { key -> detailViewModel.getDetailScrollPosition(key) },
                                        onGenreClick = { genre ->
                                            detailViewModel.closeDetail()
                                            globalViewModel.clearScreenStack()
                                            searchViewModel.setSearchType(currentScreen.type)
                                            searchViewModel.applySearchFilters(listOf(genre), null, null)
                                            globalViewModel.setTab("search")
                                        },
                                        onRankClick = { mediaType ->
                                            detailViewModel.closeDetail()
                                            globalViewModel.clearScreenStack()
                                            val category = if (mediaType == MediaType.MANGA) {
                                                com.canim.app.data.model.DiscoverCategory.TOP_MANGA
                                            } else {
                                                com.canim.app.data.model.DiscoverCategory.TOP_ANIME
                                            }
                                            val filter = if (mediaType == MediaType.MANGA) {
                                                com.canim.app.data.model.DiscoverFilter(format = "MANGA")
                                            } else {
                                                com.canim.app.data.model.DiscoverFilter()
                                            }
                                            discoverViewModel.onDiscoverEvent(
                                                com.canim.app.ui.viewmodel.discover.DiscoverEvent.CategorySelected(
                                                    category = category,
                                                    filter = filter,
                                                    mediaType = mediaType
                                                )
                                            )
                                            globalViewModel.setTab("discover")
                                        },
                                        onRefresh = {
                                            detailViewModel.openDetail(detailItem, currentScreen.type)
                                        },
                                        onDismiss = {
                                            globalViewModel.popScreen()
                                        }
                                    )
                                }
                                is ScreenRoute.StudioFilmography -> {
                                    StudioFilmographyScreen(
                                        studioId = currentScreen.studioId,
                                        studioName = currentScreen.studioName,
                                        items = studioState.items,
                                        totalEntries = studioState.totalEntries,
                                        isLoading = studioState.isLoading,
                                        isLoadingMore = studioState.isLoadingMore,
                                        canLoadMore = studioState.canLoadMore,
                                        onLoadMore = { studioViewModel.loadMoreStudioFilmography() },
                                        onOpenDetail = { media, type ->
                                            detailViewModel.openDetail(media, type)
                                            globalViewModel.openDetail(media, type)
                                        },
                                        onBack = {
                                            globalViewModel.popScreen()
                                        },
                                        bioInfo = studioState.bio,
                                        sort = studioState.sort,
                                        onSortChanged = { studioViewModel.setStudioFilmographySort(it) },
                                        onRefresh = {
                                            studioViewModel.openStudio(currentScreen.studioId, currentScreen.studioName)
                                        },
                                        onSaveScrollPosition = { key, index, offset ->
                                            studioViewModel.saveScrollPosition(key, index, offset)
                                        },
                                        onGetScrollPosition = { key ->
                                            studioViewModel.getScrollPosition(key)
                                        }
                                    )
                                }
                                is ScreenRoute.Flashcard -> {
                                    FlashcardScreen(
                                        deck = gachaState.deck,
                                        credits = gachaState.credits,
                                        isLoading = gachaState.isLoading,
                                        onBack = { globalViewModel.popScreen() },
                                        onConsumeCredit = { gachaViewModel.consumeGachaCredit() },
                                        onSwipeCard = { gachaViewModel.swipeDismissFlashcard(it) },
                                        onOpenDetail = { media, type ->
                                            detailViewModel.openDetail(media, type)
                                            globalViewModel.openDetail(media, type)
                                        },
                                        onRefreshDeck = { gachaViewModel.loadFlashcardDeck() },
                                        onSavePlanToWatch = { media, cb -> libraryViewModel.saveFlashcardPlanToWatch(media, cb) },
                                        onCardRevealed = { gachaViewModel.onCardRevealed(it) }
                                    )
                                }
                                is ScreenRoute.Stats -> {
                                    StatsScreen(
                                        libraryState = libraryState,
                                        globalState = globalState,
                                        onBack = { globalViewModel.closeStats() },
                                        onSelectItem = { item, type ->
                                            detailViewModel.openDetail(item, type)
                                            globalViewModel.openDetail(item, type)
                                        },
                                        onSaveScrollPosition = { index, offset ->
                                            globalViewModel.saveStatsScrollPosition(index, offset)
                                        },
                                        onGetScrollPosition = { globalViewModel.getStatsScrollPosition() }
                                    )
                                }
                                is ScreenRoute.AddTitleSheet -> {
                                    ModalBottomSheet(
                                        onDismissRequest = { globalViewModel.closeAddTitleSheet() },
                                        containerColor = BlackBg,
                                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                        dragHandle = { BottomSheetDefaults.DragHandle(color = CardBorder) },
                                        modifier = Modifier.fillMaxHeight(0.92f)
                                    ) {
                                        SearchScreen(
                                            searchState = searchState,
                                            libraryState = libraryState,
                                            onSearch = { query, type -> searchViewModel.search(query, type) },
                                            onAddMedia = { item, status -> libraryViewModel.addFromCatalog(item, status) },
                                            onSelectItem = { item, type ->
                                                detailViewModel.openDetail(item, type)
                                                globalViewModel.openDetail(item, type)
                                            },
                                            onSaveAnime = { libraryViewModel.saveAnime(it) },
                                            onSaveManga = { libraryViewModel.saveManga(it) },
                                            onApplyFilters = { genres, year, format ->
                                                searchViewModel.applySearchFilters(genres, year, format)
                                            },
                                            onResetFilters = { searchViewModel.resetSearchFilters() }
                                        )
                                    }
                                }
                                is ScreenRoute.AiringCalendar -> {
                                    val watchingMalIds = remember(libraryState.animeList) {
                                        libraryState.animeList
                                            .filter { it.status == "watching" && it.malId != null }
                                            .mapNotNull { it.malId }
                                            .toSet()
                                    }
                                    AiringCalendarScreen(
                                        state = calendarState,
                                        watchingMalIds = watchingMalIds,
                                        onSelectDay = { calendarViewModel.selectDay(it) },
                                        onToggleFilterOnlyWatching = { calendarViewModel.toggleFilterOnlyWatching() },
                                        onRefresh = { calendarViewModel.loadAiringCalendar(forceRefresh = true) },
                                        onOpenDetail = { media, type ->
                                            detailViewModel.openDetail(media, type)
                                            globalViewModel.openDetail(media, type)
                                        },
                                        onBack = { globalViewModel.popScreen() }
                                    )
                                }
                            }
                        }

                        // Floating Top Notification Banners (Rate Limit, Syncing, & Cold-Start Outage)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                        ) {
                            RateLimitBanner(
                                throttleState = globalState.throttleNotification,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Floating Library Sync Top Notification Popup
                            val isSyncing = globalState.syncStatus == com.canim.app.data.model.SyncStatus.SYNCING || globalState.isSyncingMal
                            AnimatedVisibility(
                                visible = isSyncing,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = CardBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Brush.horizontalGradient(
                                            listOf(AccentBlue.copy(alpha = 0.8f), Color(0xFF60A5FA).copy(alpha = 0.5f))
                                        )
                                    ),
                                    shadowElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.2.dp,
                                            color = AccentBlue,
                                            trackColor = CardElevated
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Menyinkronkan Library...",
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Memperbarui anime & manga dari akun MyAnimeList",
                                                color = TextSecondary,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // AniList Outage Notification Banner (Cold-start only, auto-dismissed in 5 seconds)
                            AnimatedVisibility(
                                visible = globalState.showColdStartOutageBanner,
                                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E1408),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        Brush.horizontalGradient(
                                            listOf(StatusOnHoldColor.copy(alpha = 0.8f), Color(0xFFD97706).copy(alpha = 0.5f))
                                        )
                                    ),
                                    shadowElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(StatusOnHoldColor.copy(alpha = 0.2f))
                                                .border(1.dp, StatusOnHoldColor.copy(alpha = 0.5f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudOff,
                                                contentDescription = "AniList Outage",
                                                tint = StatusOnHoldColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Server AniList Sedang Gangguan (HTTP 403)",
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Karakter, Seiyuu, Kru, & Tren dialihkan / dinonaktifkan sementara.",
                                                color = TextSecondary,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp
                                            )
                                        }

                                        IconButton(
                                            onClick = { globalViewModel.dismissColdStartOutageBanner() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Tutup",
                                                tint = StatusOnHoldColor,
                                                modifier = Modifier.size(16.dp)
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
    }
}
}
}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { uri ->
            handleDeepLink(uri)
        }
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(com.canim.app.notification.CanimNotificationManager.EXTRA_OPEN_UPDATE, false) == true) {
            intent.removeExtra(com.canim.app.notification.CanimNotificationManager.EXTRA_OPEN_UPDATE)
            globalViewModel.setTab("settings")
            updateViewModel.checkForUpdates(manual = true)
        }
        val airingMalId = intent?.getIntExtra(com.canim.app.notification.CanimNotificationManager.EXTRA_OPEN_AIRING_MAL_ID, -1) ?: -1
        if (airingMalId > 0) {
            intent?.removeExtra(com.canim.app.notification.CanimNotificationManager.EXTRA_OPEN_AIRING_MAL_ID)
            val matchingAnime = libraryViewModel.libraryState.value.animeList.firstOrNull { it.malId == airingMalId }
            if (matchingAnime != null) {
                globalViewModel.openDetail(matchingAnime, com.canim.app.data.model.MediaType.ANIME)
                detailViewModel.openDetail(matchingAnime, com.canim.app.data.model.MediaType.ANIME)
            } else {
                globalViewModel.openAiringCalendar()
            }
        }
        if (intent?.getBooleanExtra("extra_open_airing_calendar", false) == true) {
            intent.removeExtra("extra_open_airing_calendar")
            globalViewModel.openAiringCalendar()
        }
        val widgetMalId = (intent?.getIntExtra("extra_open_mal_id", -1) ?: -1)
            .let { if (it > 0) it else (intent?.getIntExtra("extra_mal_id", -1) ?: -1) }
        if (widgetMalId > 0) {
            intent?.removeExtra("extra_open_mal_id")
            intent?.removeExtra("extra_mal_id")
            val matchingAnime = libraryViewModel.libraryState.value.animeList.firstOrNull { it.malId == widgetMalId }
            if (matchingAnime != null) {
                globalViewModel.openDetail(matchingAnime, com.canim.app.data.model.MediaType.ANIME)
                detailViewModel.openDetail(matchingAnime, com.canim.app.data.model.MediaType.ANIME)
            }
        }
    }

    private fun handleDeepLink(uri: android.net.Uri) {
        if (uri.scheme == "canim" && uri.host == "oauth" && uri.path == "/callback") {
            intent?.data = null // Clear to prevent double processing on recreation / orientation change
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")
            if (!error.isNullOrEmpty()) {
                globalViewModel.showSnackbar("Login MAL dibatalkan: ${errorDescription ?: error}")
                return
            }

            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            if (!code.isNullOrEmpty()) {
                globalViewModel.handleOAuthCallback(code, state) {
                    libraryViewModel.loadUserLibrary(forceRefresh = true)
                }
            }
        }
    }
}
