package com.canim.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.canim.app.data.local.MalSecureStorage
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
        CanimViewModelFactory(repository)
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
                        NavigationBar(
                            containerColor = CardBg,
                            contentColor = TextPrimary,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .height(64.dp)
                                .testTag("main_bottom_nav")
                        ) {
                            navItems.forEach { item ->
                                val selected = uiState.activeTab == item.route
                                val isSearch = item.route == "search"

                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.setTab(item.route) },
                                    alwaysShowLabel = false,
                                    icon = {
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
                                                        color = if (selected) Color(0xFF60A5FA) else CardBorder,
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
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = if (isSearch) Color.Transparent else AccentBlue.copy(alpha = 0.16f),
                                        selectedIconColor = AccentBlue,
                                        unselectedIconColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("nav_tab_${item.route}")
                                )
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
                        when (uiState.activeTab) {
                            "dashboard" -> {
                                DashboardScreen(
                                    state = uiState,
                                    onQuickAddEpisode = { viewModel.quickIncrementAnime(it) },
                                    onQuickAddChapter = { viewModel.quickIncrementManga(it) },
                                    onSelectItem = { item, type -> viewModel.openDetail(item, type) },
                                    onLoadDemoData = { viewModel.loadDemoData() },
                                    onNavigateTab = { viewModel.setTab(it) },
                                    onLoginMal = { viewModel.loginWithMal(context) },
                                    onSyncMal = { viewModel.syncWithMal() },
                                    onOpenStats = { viewModel.openStats() }
                                )
                            }
                            "library" -> {
                                LibraryScreen(
                                    state = uiState,
                                    onSelectMediaType = { viewModel.setLibraryFilterType(it) },
                                    onSelectStatusFilter = { viewModel.setLibraryStatusFilter(it) },
                                    onSelectSort = { viewModel.setLibrarySort(it) },
                                    onSearchQueryChange = { viewModel.setLibrarySearch(it) },
                                    onQuickAddAnime = { viewModel.quickIncrementAnime(it) },
                                    onQuickDecrementAnime = { viewModel.quickDecrementAnime(it) },
                                    onQuickAddManga = { viewModel.quickIncrementManga(it) },
                                    onQuickDecrementManga = { viewModel.quickDecrementManga(it) },
                                    onSelectItem = { item, type -> viewModel.openDetail(item, type) }
                                )
                            }
                            "search" -> {
                                SearchScreen(
                                    state = uiState,
                                    onSearch = { query, type -> viewModel.search(query, type) },
                                    onAddMedia = { item, status -> viewModel.addFromCatalog(item, status) },
                                    onSelectItem = { item, type -> viewModel.openDetail(item, type) },
                                    onSaveAnime = { viewModel.saveAnime(it) },
                                    onSaveManga = { viewModel.saveManga(it) }
                                )
                            }
                            "discover" -> {
                                DiscoverScreen(
                                    state = uiState,
                                    onSelectCategory = { cat, filter -> viewModel.loadDiscoverCategory(cat, filter) },
                                    onAddMedia = { item, status -> viewModel.addFromCatalog(item, status) },
                                    onSelectItem = { item, type -> viewModel.openDetail(item, type) },
                                    onRandomize = { filter -> viewModel.randomizeAnime(filter) },
                                    onRandomizeManga = { filter -> viewModel.randomizeManga(filter) },
                                    onLoadMore = { viewModel.loadMoreDiscover() },
                                    onSaveAnime = { viewModel.saveAnime(it) },
                                    onSaveManga = { viewModel.saveManga(it) },
                                    onOpenStudio = { studioId, studioName -> viewModel.openStudio(studioId, studioName) }
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
                                    onClearAllCache = { viewModel.clearAllCache(context) }
                                )
                            }
                        }

                        // Top-level modal/overlay stack rendering
                        when (val currentScreen = screenStack.lastOrNull()) {
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
                                val detailItem = uiState.selectedDetailItem ?: currentScreen.item
                                val detailTitle = (detailItem as? com.canim.app.data.model.UserMediaItem)?.title
                                    ?: (detailItem as? com.canim.app.data.model.MediaItem)?.title
                                    ?: ""
                                MediaDetailScreen(
                                    item = detailItem,
                                    type = uiState.detailMediaType,
                                    extendedDetail = uiState.extendedDetail,
                                    isLoadingExtendedDetail = uiState.isLoadingExtendedDetail,
                                    onSaveAnime = { viewModel.saveAnime(it) },
                                    onSaveManga = { viewModel.saveManga(it) },
                                    onDeleteAnime = { viewModel.deleteAnime(it) },
                                    onDeleteManga = { viewModel.deleteManga(it) },
                                    onOpenCastCrew = { id, isStaff -> viewModel.openCastCrewProfile(id, isStaff) },
                                    onOpenFullCast = { isCrew ->
                                        viewModel.openFullCastList(
                                            mediaTitle = detailTitle,
                                            castList = uiState.extendedDetail?.cast ?: emptyList(),
                                            staffList = uiState.extendedDetail?.crew ?: emptyList(),
                                            isCrewInitial = isCrew
                                        )
                                    },
                                    onOpenStudio = { studioId, studioName -> viewModel.openStudio(studioId, studioName) },
                                    onDismiss = { viewModel.popScreen() }
                                )
                            }
                            is ScreenRoute.StudioFilmography -> {
                                StudioFilmographyScreen(
                                    studioId = currentScreen.studioId,
                                    studioName = currentScreen.studioName,
                                    items = uiState.studioFilmographyItems,
                                    isLoading = uiState.isStudioFilmographyLoading,
                                    isLoadingMore = uiState.isStudioFilmographyLoadingMore,
                                    canLoadMore = uiState.canLoadMoreStudioFilmography,
                                    onLoadMore = { viewModel.loadMoreStudioFilmography() },
                                    onOpenDetail = { media, type -> viewModel.openDetail(media, type) },
                                    onBack = { viewModel.popScreen() }
                                )
                            }
                            is ScreenRoute.Stats -> {
                                StatsScreen(
                                    state = uiState,
                                    onBack = { viewModel.popScreen() },
                                    onSelectItem = { item, type -> viewModel.openDetail(item, type) }
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
                                        onSaveManga = { viewModel.saveManga(it) }
                                    )
                                }
                            }
                            null -> { /* No overlay active */ }
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
