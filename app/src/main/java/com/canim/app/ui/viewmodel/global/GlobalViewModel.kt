package com.canim.app.ui.viewmodel.global

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.CharacterCastItem
import com.canim.app.data.model.MalUser
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StaffMemberItem
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.remote.LimiterEvent
import com.canim.app.data.remote.util.ApiErrorFormatter
import com.canim.app.domain.usecase.CheckApiHealthUseCase
import com.canim.app.domain.usecase.ClearCacheUseCase
import com.canim.app.domain.usecase.GetMalUserUseCase
import com.canim.app.domain.usecase.HandleMalOAuthCallbackUseCase
import com.canim.app.domain.usecase.LoginMalUseCase
import com.canim.app.domain.usecase.LogoutMalUseCase
import com.canim.app.domain.usecase.SyncMalUseCase
import com.canim.app.ui.navigation.ScreenRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalViewModel @Inject constructor(
    private val getMalUserUseCase: GetMalUserUseCase,
    private val loginMalUseCase: LoginMalUseCase,
    private val handleMalOAuthCallbackUseCase: HandleMalOAuthCallbackUseCase,
    private val logoutMalUseCase: LogoutMalUseCase,
    private val syncMalUseCase: SyncMalUseCase,
    private val checkApiHealthUseCase: CheckApiHealthUseCase,
    private val clearCacheUseCase: ClearCacheUseCase
) : ViewModel() {

    private val _globalState = MutableStateFlow(
        GlobalUiState(
            malUser = getMalUserUseCase(),
            appMode = if (getMalUserUseCase().isLoggedIn) "online_sync" else "offline"
        )
    )
    val globalState: StateFlow<GlobalUiState> = _globalState.asStateFlow()

    private val _screenStack = MutableStateFlow<List<ScreenRoute>>(emptyList())
    val screenStack: StateFlow<List<ScreenRoute>> = _screenStack.asStateFlow()

    private val _isPushNavigation = MutableStateFlow(true)
    val isPushNavigation: StateFlow<Boolean> = _isPushNavigation.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.BUFFERED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    private var statsScrollIndex: Int = 0
    private var statsScrollOffset: Int = 0

    private var throttleCountdownJob: Job? = null
    private var hasShownColdStartOutage = false
    private var coldStartBannerJob: Job? = null

    init {
        // Collect Rate Limiter events from ApiClient limiters
        viewModelScope.launch {
            merge(
                ApiClient.aniListLimiter.eventFlow,
                ApiClient.malLimiter.eventFlow
            ).collect { event ->
                val displayHost = if (event.host == "anilist") "AniList" else "MyAnimeList"

                when (event) {
                    is LimiterEvent.CooldownStarted -> {
                        startThrottleCountdown(event.host, event.durationMs)
                        val cooldownSec = (event.durationMs + 999L) / 1000L
                        val errorText = ApiErrorFormatter.formatHttpCode(429, event.host, cooldownSec)
                        showSnackbar(errorText)
                    }
                    is LimiterEvent.Recovered -> {
                        throttleCountdownJob?.cancel()
                        _globalState.update { it.copy(throttleNotification = null) }
                        showSnackbar("✅ Koneksi ke $displayHost pulih.")
                    }
                    is LimiterEvent.Throttled, is LimiterEvent.Retrying -> {
                        // Silent token-bucket smoothing and background retry; RateLimitBanner handles UI countdown if cooled down
                    }
                }
            }
        }

        // Memory Cache Auto-Pruning every 15 minutes
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15 * 60 * 1000L)
                clearCacheUseCase.prune()
            }
        }

        // Real-time API outage check on cold start (AniList & MAL)
        checkApiHealth(isColdStart = true)
    }

    private fun startThrottleCountdown(host: String, durationMs: Long) {
        // Pop out notification at top of screen capped at maximum 5 seconds
        val totalSeconds = minOf(5L, maxOf(1L, (durationMs + 999L) / 1000L))
        throttleCountdownJob?.cancel()
        throttleCountdownJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0 && isActive) {
                _globalState.update {
                    it.copy(
                        throttleNotification = ThrottleNotificationState(
                            host = host,
                            totalSeconds = totalSeconds,
                            remainingSeconds = remaining,
                            isActive = true
                        )
                    )
                }
                delay(1000L)
                remaining--
            }
            _globalState.update { it.copy(throttleNotification = null) }
        }
    }

    // --- Screen Stack Navigation ---
    fun pushScreen(route: ScreenRoute) {
        _isPushNavigation.value = true
        _screenStack.update { it + route }
    }

    fun popScreen(): Boolean {
        var popped = false
        _isPushNavigation.value = false
        _screenStack.update { stack ->
            if (stack.isNotEmpty()) {
                popped = true
                stack.dropLast(1)
            } else {
                stack
            }
        }
        return popped
    }

    fun clearScreenStack() {
        _isPushNavigation.value = false
        _screenStack.value = emptyList()
    }

    fun openDetail(item: Any, type: MediaType) {
        pushScreen(ScreenRoute.Detail(item, type))
    }

    fun openCastCrewProfile(id: Int, isStaff: Boolean) {
        pushScreen(ScreenRoute.CastCrew(id, isStaff))
    }

    fun openFullCastList(
        mediaTitle: String,
        castList: List<CharacterCastItem>,
        staffList: List<StaffMemberItem>,
        isCrewInitial: Boolean = false
    ) {
        pushScreen(ScreenRoute.FullCastList(mediaTitle, castList, staffList, isCrewInitial))
    }

    fun openStats() {
        resetStatsScrollPosition()
        pushScreen(ScreenRoute.Stats)
    }

    fun closeStats() {
        if (_screenStack.value.lastOrNull() is ScreenRoute.Stats) {
            popScreen()
        }
    }

    fun openAddTitleSheet() {
        pushScreen(ScreenRoute.AddTitleSheet)
    }

    fun closeAddTitleSheet() {
        if (_screenStack.value.lastOrNull() is ScreenRoute.AddTitleSheet) {
            popScreen()
        }
    }

    fun openStudio(studioId: Int, studioName: String) {
        pushScreen(ScreenRoute.StudioFilmography(studioId, studioName))
    }

    fun openFlashcard() {
        pushScreen(ScreenRoute.Flashcard)
    }

    fun openAiringCalendar() {
        pushScreen(ScreenRoute.AiringCalendar)
    }

    // --- Tab & App Mode ---
    fun setTab(tab: String) {
        clearScreenStack()
        _globalState.update { it.copy(activeTab = tab) }
    }

    fun setAppMode(mode: String) {
        _globalState.update { it.copy(appMode = mode) }
    }

    // --- Stats Scroll Position Preservation ---
    fun saveStatsScrollPosition(index: Int, offset: Int) {
        statsScrollIndex = index
        statsScrollOffset = offset
    }

    fun getStatsScrollPosition(): Pair<Int, Int> = Pair(statsScrollIndex, statsScrollOffset)

    fun resetStatsScrollPosition() {
        statsScrollIndex = 0
        statsScrollOffset = 0
    }

    // --- API Health Monitoring ---
    fun checkApiHealth(isColdStart: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val (aniDown, malDown) = checkApiHealthUseCase()
            _globalState.update { it.copy(isAniListDown = aniDown, isMalDown = malDown) }
            // Notification pop-out only appears on cold start and lasts at most 5 seconds
            if (isColdStart && aniDown && !hasShownColdStartOutage) {
                hasShownColdStartOutage = true
                _globalState.update { it.copy(showColdStartOutageBanner = true) }
                coldStartBannerJob?.cancel()
                coldStartBannerJob = viewModelScope.launch {
                    delay(5000L)
                    _globalState.update { it.copy(showColdStartOutageBanner = false) }
                }
            }
        }
    }

    fun dismissColdStartOutageBanner() {
        coldStartBannerJob?.cancel()
        _globalState.update { it.copy(showColdStartOutageBanner = false) }
    }

    // --- MAL OAuth & Sync ---
    fun loginWithMal(context: Context) {
        try {
            val url = loginMalUseCase()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showSnackbar("Gagal membuka browser untuk login: ${e.message}")
        }
    }

    fun handleOAuthCallback(code: String, state: String?, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _globalState.update { it.copy(isExchangingToken = true) }
            val result = handleMalOAuthCallbackUseCase(code, state)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                _globalState.update {
                    it.copy(
                        malUser = user,
                        appMode = "online_sync",
                        isExchangingToken = false
                    )
                }
                showSnackbar("Login MAL berhasil! Memuat library...")
                onSuccess?.invoke()
            } else {
                _globalState.update { it.copy(isExchangingToken = false) }
                showSnackbar("Gagal login MyAnimeList: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun logoutMal(onLogout: (() -> Unit)? = null) {
        logoutMalUseCase()
        _globalState.update {
            it.copy(
                malUser = MalUser(),
                appMode = "offline"
            )
        }
        showSnackbar("Akun MyAnimeList telah logout.")
        onLogout?.invoke()
    }

    fun syncWithMal(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _globalState.update { it.copy(isSyncingMal = true) }
            val result = syncMalUseCase()
            _globalState.update { it.copy(isSyncingMal = false) }
            if (result.isSuccess) {
                showSnackbar("Sync MAL selesai: ${result.animeSynced} anime & ${result.mangaSynced} manga")
                onSuccess?.invoke()
            } else {
                showSnackbar("Sync gagal: ${result.errorMessage}")
            }
        }
    }

    // --- Cache Management ---
    // Rule: Penghapusan cache hanya bisa untuk gambar, metriks, api, metadata, dll tidak boleh dihapus.
    fun clearImageCache(context: Context) {
        viewModelScope.launch {
            clearCacheUseCase.clearImageCache(context)
            showSnackbar("Cache gambar telah dibersihkan.")
        }
    }

    fun clearMetadataCache() {
        clearCacheUseCase.clearMetadataCache()
        showSnackbar("Cache metadata lokal berhasil dibersihkan.")
    }

    fun clearAllCache(context: Context) {
        // Only clear image cache per requirement
        viewModelScope.launch {
            clearCacheUseCase.clearImageCache(context)
            showSnackbar("Cache gambar telah dibersihkan.")
        }
    }

    // --- Snackbar Controls ---
    fun showSnackbar(message: String) {
        _globalState.update { it.copy(snackbarMessage = message) }
        viewModelScope.launch {
            _snackbarEvent.send(message)
        }
    }

    fun dismissSnackbar() {
        _globalState.update { it.copy(snackbarMessage = null) }
    }

    // --- Global Event Dispatcher ---
    fun onGlobalEvent(event: GlobalEvent) {
        when (event) {
            is GlobalEvent.SetActiveTab -> setTab(event.tab)
            is GlobalEvent.SetAppMode -> setAppMode(event.mode)
            is GlobalEvent.ShowSnackbar -> showSnackbar(event.message)
            is GlobalEvent.DismissSnackbar -> dismissSnackbar()
            is GlobalEvent.RefreshHealth -> checkApiHealth()
            is GlobalEvent.DismissColdStartOutageBanner -> dismissColdStartOutageBanner()
        }
    }
}
