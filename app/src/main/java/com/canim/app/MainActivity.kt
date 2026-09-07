package com.canim.app

import android.content.Intent
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
import com.canim.app.data.local.GachaCreditManager
import com.canim.app.data.local.MalSecureStorage
import com.canim.app.data.model.MediaType
import com.canim.app.data.repository.CanimRepository
import com.canim.app.data.repository.MalAuthManager
import com.canim.app.ui.navigation.ScreenRoute
import com.canim.app.ui.screens.*
import com.canim.app.ui.theme.*
import com.canim.app.ui.viewmodel.CanimViewModel
import com.canim.app.ui.viewmodel.CanimViewModelFactory

data class NavItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {

    private val viewModel: CanimViewModel by viewModels {
        val secureStorage = MalSecureStorage(applicationContext)
        val malAuthManager = MalAuthManager(secureStorage = secureStorage)
        val repository = CanimRepository(malAuthManager = malAuthManager)
        val gachaCreditManager = GachaCreditManager.getInstance(applicationContext)
        CanimViewModelFactory(repository, gachaCreditManager)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle OAuth Deep Link callback on initial launch
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }

        setContent {
            CanimTheme {
                val uiState by viewModel.uiState.collectAsState()
                val screenStack by viewModel.screenStack.collectAsState()
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }

                // Single centralized top-level BackHandler
                BackHandler(enabled = screenStack.isNotEmpty()) {
                    viewModel.popScreen()
                }

                LaunchedEffect(uiState.snackbarMessage) {
                    val msg = uiState.snackbarMessage
                    if (msg != null) {
                        snackbarHostState.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Short
                        )
                        viewModel.dismissSnackbar()
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

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BlackBg,
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = 80.dp)
                        ) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = CardElevated,
                                contentColor = TextPrimary,
                                actionColor = AccentBlue
                            )
                        }
                    },
                    floatingActionButton = {
                        if (uiState.activeTab == "library" && screenStack.isEmpty()) {
                            ExtendedFloatingActionButton(
                                onClick = { viewModel.openAddTitleSheet() },
                                containerColor = AccentBlue,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .testTag("main_quick_fab")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Cari & Tambah",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tambah Judul",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    },
                    bottomBar = {
                        val activeIndex = navItems.indexOfFirst { it.route == uiState.activeTab }.coerceAtLeast(0)

                        Surface(
                            color = CardBg,
                            contentColor = TextPrimary,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("main_bottom_nav")
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
                                        val selected = uiState.activeTab == item.route
                                        val isSearch = item.route == "search"

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clickable(
                                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                    indication = null
                                                ) { viewModel.setTab(item.route) }
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
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(BlackBg)
                    ) {
                        // Stable hoisted callbacks to ensure 100% skippable recomposition during scrolling
                        val onQuickAddEpisode: (String) -> Unit = remember { { viewModel.quickIncrementAnime(it) } }
                        val onQuickAddChapter: (String) -> Unit = remember { { viewModel.quickIncrementManga(it) } }
                        val onSelectItem: (Any, MediaType) -> Unit = remember { { item: Any, type: MediaType -> viewModel.openDetail(item, type) } }
                        val onLoadDemoData: () -> Unit = remember { { viewModel.loadDemoData() } }
                        val onNavigateTab: (String) -> Unit = remember { { viewModel.setTab(it) } }
                        val onLoginMal: () -> Unit = remember(context) { { viewModel.loginWithMal(context) } }
                        val onSyncMal: () -> Unit = remember { { viewModel.syncWithMal() } }
                        val onOpenStats: () -> Unit = remember { { viewModel.openStats() } }
                        val onOpenFlashcard: () -> Unit = remember { { viewModel.openFlashcard() } }

                        val onSelectMediaType: (MediaType) -> Unit = remember { { viewModel.setLibraryFilterType(it) } }
                        val onSelectStatusFilter: (String?) -> Unit = remember { { viewModel.setLibraryStatusFilter(it) } }
                        val onSelectSort: (String) -> Unit = remember { { viewModel.setLibrarySort(it) } }
                        val onSearchQueryChange: (String) -> Unit = remember { { viewModel.setLibrarySearch(it) } }
                        val onQuickDecrementAnime: (String) -> Unit = remember { { viewModel.quickDecrementAnime(it) } }
                        val onQuickDecrementManga: (String) -> Unit = remember { { viewModel.quickDecrementManga(it) } }

                        AnimatedContent(
                            targetState = uiState.activeTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)).togetherWith(fadeOut(animationSpec = tween(180)))
                            },
                            label = "TabContent"
                        ) { activeTab ->
                            when (activeTab) {
                                "dashboard" -> {
                                    DashboardScreen(
                                        state = uiState,
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
                                        state = uiState,
                                        onSelectMediaType = onSelectMediaType,
                                        onSelectStatusFilter = onSelectStatusFilter,
                                        onSelectSort = onSelectSort,
                                        onSearchQueryChange = onSearchQueryChange,
                                        onQuickAddAnime = onQuickAddEpisode,
                                        onQuickDecrementAnime = onQuickDecrementAnime,
                                        onQuickAddManga = onQuickAddChapter,
                                        onQuickDecrementManga = onQuickDecrementManga,
                                        onSelectItem = onSelectItem
                                    )
                                }
                                "search" -> {
                                    SearchScreen(
                                        state = uiState,
                                        onSearch = { query, type -> viewModel.search(query, type) },
                                        onAddMedia = { item, status -> viewModel.addFromCatalog(item, status) },
                                        onSelectItem = { item, type -> viewModel.openDetail(item, type) },
                                        onSaveAnime = { viewModel.saveAnime(it) },
                                        onSaveManga = { viewModel.saveManga(it) },
                                        onApplyFilters = { genres, year, format -> viewModel.applySearchFilters(genres, year, format) },
                                        onResetFilters = { viewModel.resetSearchFilters() }
                                    )
                                }
                                "discover" -> {
                                    DiscoverScreen(
                                        state = uiState,
                                        onSelectCategory = { cat, filter -> viewModel.loadDiscoverCategory(cat, filter) },
                                        onAddMedia = { item, status -> viewModel.addFromCatalog(item, status) },
                                        onSelectItem = { item, type -> viewModel.openDetail(item, type) },
                                        onLoadMore = { viewModel.loadMoreDiscover() },
                                        onSaveAnime = { viewModel.saveAnime(it) },
                                        onSaveManga = { viewModel.saveManga(it) },
                                        onOpenStudio = { studioId, studioName -> viewModel.openStudio(studioId, studioName) },
                                        onSearchStudio = { viewModel.searchStudios(it) }
                                    )
                                }
                                "settings" -> {
                                    SettingsScreen(
                                        state = uiState,
                                        onLoginMal = { viewModel.loginWithMal(context) },
                                        onSyncMal = { viewModel.syncWithMal() },
                                        onLogoutMal = { viewModel.logoutMal() },
                                        onSetAppMode = { viewModel.setAppMode(it) },
                                        onLoadDemoData = { viewModel.loadDemoData() },
                                        onClearAllData = { viewModel.clearAllData() },
                                        onClearImageCache = { viewModel.clearImageCache(context) },
                                        onClearMetadataCache = { viewModel.clearMetadataCache() },
                                        onClearAllCache = { viewModel.clearAllCache(context) },
                                        onCheckForUpdates = { viewModel.checkForUpdates(manual = true) },
                                        onSetAutoUpdateCheck = { viewModel.setAutoUpdateCheck(it) },
                                        onDismissUpdateDialog = { viewModel.dismissUpdateDialog() },
                                        onStartDownloadUpdate = { viewModel.startDownloadUpdate(context) },
                                        onInstallDownloadedUpdate = { viewModel.installDownloadedUpdate(context) }
                                    )
                                }
                            }
                        }

                        // Top-level modal/overlay stack rendering with unified smooth popup entry & popdown exit
                        AnimatedContent(
                            targetState = screenStack.lastOrNull(),
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                            transitionSpec = {
                                val noClipSizeTransform = SizeTransform(clip = false)
                                val isPush = if (initialState == null && targetState != null) {
                                    true
                                } else if (initialState != null && targetState == null) {
                                    false
                                } else if (targetState != null && !screenStack.contains(initialState)) {
                                    false
                                } else if (initialState != null && !screenStack.contains(targetState)) {
                                    false
                                } else {
                                    screenStack.indexOf(targetState) > screenStack.indexOf(initialState)
                                }

                                if (isPush) {
                                    // Smooth vertical popup entry: rises from bottom of screen to top, rapid fade
                                    (slideInVertically(
                                        initialOffsetY = { it },
                                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                                    ) + scaleIn(
                                        initialScale = 0.92f,
                                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                                    ) + fadeIn(animationSpec = tween(150, easing = LinearOutSlowInEasing)))
                                        .togetherWith(
                                            scaleOut(
                                                targetScale = 0.92f,
                                                animationSpec = tween(200, easing = FastOutSlowInEasing)
                                            ) + fadeOut(animationSpec = tween(140))
                                        )
                                        .using(noClipSizeTransform)
                                        .apply { targetContentZIndex = 1f }
                                } else {
                                    // Instant popdown exit: drops down immediately off bottom edge with zero still-image hesitation
                                    (scaleIn(
                                        initialScale = 0.94f,
                                        animationSpec = tween(200, easing = LinearOutSlowInEasing)
                                    ) + fadeIn(animationSpec = tween(150, easing = LinearOutSlowInEasing)))
                                        .togetherWith(
                                            slideOutVertically(
                                                targetOffsetY = { it },
                                                animationSpec = tween(180, easing = LinearEasing)
                                            ) + scaleOut(
                                                targetScale = 0.92f,
                                                animationSpec = tween(180, easing = LinearEasing)
                                            ) + fadeOut(animationSpec = tween(140, easing = LinearEasing))
                                        )
                                        .using(noClipSizeTransform)
                                        .apply { targetContentZIndex = -1f }
                                }
                            },
                            label = "ScreenOverlayTransition"
                        ) { currentScreen ->
                            when (currentScreen) {
                                is ScreenRoute.CastCrew -> {
                                    CastCrewProfileScreen(
                                        profile = uiState.selectedCastCrewProfile,
                                        isLoading = uiState.isLoadingCastCrewProfile,
                                        onBack = { viewModel.popScreen() },
                                        onSelectMedia = { mediaItem ->
                                            viewModel.openDetail(mediaItem, mediaItem.type)
                                        }
                                    )
                                }
                                is ScreenRoute.FullCastList -> {
                                    FullCastListScreen(
                                        mediaTitle = currentScreen.mediaTitle,
                                        castList = currentScreen.castList,
                                        staffList = currentScreen.staffList,
                                        isCrewInitial = currentScreen.isCrewInitial,
                                        onBack = { viewModel.popScreen() },
                                        onOpenCastCrew = { id, isStaff ->
                                            viewModel.openCastCrewProfile(id, isStaff)
                                        }
                                    )
                                }
                                is ScreenRoute.Detail -> {
                                    val currentMediaId = when (val item = currentScreen.item) {
                                        is com.canim.app.data.model.UserMediaItem -> item.id
                                        is com.canim.app.data.model.MediaItem -> item.id
                                        else -> null
                                    }
                                    val selectedMediaId = when (val item = uiState.selectedDetailItem) {
                                        is com.canim.app.data.model.UserMediaItem -> item.id
                                        is com.canim.app.data.model.MediaItem -> item.id
                                        else -> null
                                    }
                                    val isCurrentDetailSelected = currentMediaId != null && currentMediaId == selectedMediaId
                                    val detailItem = if (isCurrentDetailSelected) {
                                        uiState.selectedDetailItem ?: currentScreen.item
                                    } else {
                                        currentScreen.item
                                    }
                                    val detailExtended = if (isCurrentDetailSelected) {
                                        uiState.extendedDetail
                                    } else {
                                        viewModel.getCachedDetail(currentScreen.item)
                                    }
                                    val detailIsLoading = if (isCurrentDetailSelected) {
                                        uiState.isLoadingExtendedDetail
                                    } else {
                                        false
                                    }
                                    val detailTitle = (detailItem as? com.canim.app.data.model.UserMediaItem)?.title
                                        ?: (detailItem as? com.canim.app.data.model.MediaItem)?.title
                                        ?: ""
                                    MediaDetailScreen(
                                        item = detailItem,
                                        type = currentScreen.type,
                                        extendedDetail = detailExtended,
                                        isLoadingExtendedDetail = detailIsLoading,
                                        onSaveAnime = { viewModel.saveAnime(it) },
                                        onSaveManga = { viewModel.saveManga(it) },
                                        onDeleteAnime = { viewModel.deleteAnime(it) },
                                        onDeleteManga = { viewModel.deleteManga(it) },
                                        onOpenCastCrew = { id, isStaff -> viewModel.openCastCrewProfile(id, isStaff) },
                                        onOpenFullCast = { isCrew ->
                                            viewModel.openFullCastList(
                                                mediaTitle = detailTitle,
                                                castList = detailExtended?.cast ?: emptyList(),
                                                staffList = detailExtended?.crew ?: emptyList(),
                                                isCrewInitial = isCrew
                                            )
                                        },
                                        onOpenStudio = { studioId, studioName -> viewModel.openStudio(studioId, studioName) },
                                        onOpenMediaDetail = { media, mediaType -> viewModel.openDetail(media, mediaType) },
                                        onSaveScrollPosition = { key, index, offset -> viewModel.saveDetailScrollPosition(key, index, offset) },
                                        onGetScrollPosition = { key -> viewModel.getDetailScrollPosition(key) },
                                        onDismiss = { viewModel.popScreen() }
                                    )
                                }
                                is ScreenRoute.StudioFilmography -> {
                                    StudioFilmographyScreen(
                                        studioId = currentScreen.studioId,
                                        studioName = currentScreen.studioName,
                                        items = uiState.studioFilmographyItems,
                                        totalEntries = uiState.studioFilmographyTotalEntries,
                                        isLoading = uiState.isStudioFilmographyLoading,
                                        isLoadingMore = uiState.isStudioFilmographyLoadingMore,
                                        canLoadMore = uiState.canLoadMoreStudioFilmography,
                                        onLoadMore = { viewModel.loadMoreStudioFilmography() },
                                        onOpenDetail = { media, type -> viewModel.openDetail(media, type) },
                                        onBack = { viewModel.popScreen() },
                                        bioInfo = uiState.studioFilmographyBio,
                                        sort = uiState.studioFilmographySort,
                                        onSortChanged = { viewModel.setStudioFilmographySort(it) }
                                    )
                                }
                                is ScreenRoute.Flashcard -> {
                                    FlashcardScreen(
                                        deck = uiState.flashcardDeck,
                                        credits = uiState.gachaCredits,
                                        isLoading = uiState.isFlashcardLoading,
                                        onBack = { viewModel.popScreen() },
                                        onConsumeCredit = { viewModel.consumeGachaCredit() },
                                        onSwipeCard = { viewModel.swipeDismissFlashcard(it) },
                                        onOpenDetail = { media, type -> viewModel.openDetail(media, type) },
                                        onRefreshDeck = { viewModel.loadFlashcardDeck() },
                                        onSavePlanToWatch = { media, cb -> viewModel.saveFlashcardPlanToWatch(media, cb) }
                                    )
                                }
                                is ScreenRoute.Stats -> {
                                    StatsScreen(
                                        state = uiState,
                                        onBack = { viewModel.popScreen() },
                                        onSelectItem = { item, type -> viewModel.openDetail(item, type) },
                                        onSaveScrollPosition = { index, offset -> viewModel.saveStatsScrollPosition(index, offset) },
                                        onGetScrollPosition = { viewModel.getStatsScrollPosition() }
                                    )
                                }
                                is ScreenRoute.AddTitleSheet -> {
                                    ModalBottomSheet(
                                        onDismissRequest = { viewModel.popScreen() },
                                        containerColor = BlackBg,
                                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                        dragHandle = { BottomSheetDefaults.DragHandle(color = CardBorder) },
                                        modifier = Modifier.fillMaxHeight(0.92f)
                                    ) {
                                        SearchScreen(
                                            state = uiState,
                                            onSearch = { query, type -> viewModel.search(query, type) },
                                            onAddMedia = { item, status -> viewModel.addFromCatalog(item, status) },
                                            onSelectItem = { item, type -> viewModel.openDetail(item, type) },
                                            onSaveAnime = { viewModel.saveAnime(it) },
                                            onSaveManga = { viewModel.saveManga(it) },
                                            onApplyFilters = { genres, year, format -> viewModel.applySearchFilters(genres, year, format) },
                                            onResetFilters = { viewModel.resetSearchFilters() }
                                        )
                                    }
                                }
                                null -> {
                                    Spacer(modifier = Modifier.fillMaxSize())
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
    }

    private fun handleDeepLink(uri: android.net.Uri) {
        if (uri.scheme == "canim" && uri.host == "oauth" && uri.path == "/callback") {
            intent?.data = null // Clear to prevent double processing on recreation / orientation change
            val error = uri.getQueryParameter("error")
            val errorDescription = uri.getQueryParameter("error_description")
            if (!error.isNullOrEmpty()) {
                viewModel.showSnackbar("Login MAL dibatalkan: ${errorDescription ?: error}")
                return
            }

            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            if (!code.isNullOrEmpty()) {
                viewModel.handleOAuthCallback(code, state)
            }
        }
    }
}
