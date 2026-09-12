package com.canim.app.ui.viewmodel.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.*
import com.canim.app.data.remote.ApiClient
import com.canim.app.data.repository.CacheRefreshType
import com.canim.app.domain.usecase.GetCastCrewProfileUseCase
import com.canim.app.domain.usecase.GetExtendedDetailUseCase
import com.canim.app.domain.usecase.ObserveCacheRefreshUseCase
import com.canim.app.util.LogRedactor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val getExtendedDetailUseCase: GetExtendedDetailUseCase,
    private val getCastCrewProfileUseCase: GetCastCrewProfileUseCase,
    private val observeCacheRefreshUseCase: ObserveCacheRefreshUseCase
) : ViewModel() {

    private val _detailState = MutableStateFlow(DetailUiState())
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private var detailJob: Job? = null
    private var detailRequestToken = 0L

    private val detailScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private val detailCache = mutableMapOf<String, ExtendedMediaDetail>()

    init {
        viewModelScope.launch {
            observeCacheRefreshUseCase.events.collect { event ->
                if (event.type == CacheRefreshType.DETAIL) {
                    val token = detailRequestToken
                    val state = _detailState.value
                    if (state.isOpen && token == detailRequestToken) {
                        val selected = state.selectedItem
                        val ext = state.extendedDetail
                        val aniId = ext?.anilistId ?: (selected as? MediaItem)?.anilistId ?: (selected as? UserMediaItem)?.anilistId
                        val malId = ext?.malId ?: (selected as? MediaItem)?.malId ?: (selected as? UserMediaItem)?.malId
                        val matchesDetail = getExtendedDetailUseCase.matchesDetailKey(event.key, aniId, malId)
                        if (matchesDetail) {
                            val fresh = getExtendedDetailUseCase.getCachedDetail(event.key)
                            if (fresh != null && token == detailRequestToken) {
                                selected?.let { cacheDetail(it, fresh) }
                                _detailState.update {
                                    it.copy(
                                        extendedDetail = fresh,
                                        isLoadingExtendedDetail = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun onDetailEvent(event: DetailEvent) {
        when (event) {
            is DetailEvent.OpenDetail -> openDetail(event.item, event.type)
            is DetailEvent.CloseDetail -> closeDetail()
            is DetailEvent.OpenCastCrewProfile -> openCastCrewProfile(event.id, event.isStaff)
            is DetailEvent.CloseCastCrewProfile -> closeCastCrewProfile()
            is DetailEvent.ItemUpdated -> {
                _detailState.update { it.copy(selectedItem = event.updatedItem) }
            }
        }
    }

    fun cacheDetail(item: Any, detail: ExtendedMediaDetail) {
        val key = getMediaKey(item)
        detailCache[key] = detail
        detail.anilistId?.let { detailCache[it.toString()] = detail }
        detail.malId?.let { detailCache[it.toString()] = detail }
        (item as? MediaItem)?.malId?.let { detailCache[it.toString()] = detail }
        (item as? MediaItem)?.anilistId?.let { detailCache[it.toString()] = detail }
        (item as? UserMediaItem)?.malId?.let { detailCache[it.toString()] = detail }
        (item as? UserMediaItem)?.anilistId?.let { detailCache[it.toString()] = detail }
    }

    fun getCachedDetail(item: Any): ExtendedMediaDetail? {
        val key = getMediaKey(item)
        val direct = detailCache[key]
        if (direct != null) return direct

        val aniId = when (item) {
            is UserMediaItem -> item.anilistId
            is MediaItem -> item.anilistId
            else -> null
        }
        val malId = when (item) {
            is UserMediaItem -> item.malId
            is MediaItem -> item.malId
            else -> null
        }
        val type = when (item) {
            is UserMediaItem -> item.metadata.type
            is MediaItem -> item.type
            else -> null
        }

        return (aniId?.let { detailCache[it.toString()] })
            ?: (malId?.let { detailCache[it.toString()] })
            ?: getExtendedDetailUseCase.getCachedExtendedDetail(aniId, malId, type)
    }

    fun saveDetailScrollPosition(key: String, index: Int, offset: Int) {
        detailScrollPositions[key] = Pair(index, offset)
    }

    fun getDetailScrollPosition(key: String): Pair<Int, Int> {
        return detailScrollPositions[key] ?: Pair(0, 0)
    }

    fun getMediaKey(item: Any): String {
        return when (item) {
            is UserMediaItem -> item.anilistId?.toString() ?: item.malId?.toString() ?: item.title
            is MediaItem -> item.anilistId?.toString() ?: item.malId?.toString() ?: item.title
            is AiringAnimeItem -> item.anilistId?.toString() ?: item.malId?.toString() ?: item.id
            else -> item.toString()
        }
    }

    fun getAniListIdForMalId(malId: Int): Int? =
        getExtendedDetailUseCase.getAniListIdForMalId(malId)

    fun openDetail(item: Any, type: MediaType) {
        val resolvedItem: Any = when (item) {
            is AiringAnimeItem -> MediaItem(
                malId = item.malId,
                anilistId = item.anilistId ?: item.id.toIntOrNull(),
                title = item.title,
                titleEnglish = item.titleEnglish,
                imageUrl = item.imageUrl,
                type = MediaType.ANIME,
                score = item.score,
                episodes = item.episodes,
                genres = item.genres,
                studio = item.studio
            )
            else -> item
        }

        val anilistId = when (resolvedItem) {
            is UserMediaItem -> resolvedItem.anilistId
            is MediaItem -> resolvedItem.anilistId
            else -> null
        }
        val malId = when (resolvedItem) {
            is UserMediaItem -> resolvedItem.malId
            is MediaItem -> resolvedItem.malId
            else -> null
        }

        val mediaKey = getMediaKey(resolvedItem)
        val inMemoryDetail = detailCache[mediaKey]
        val cachedDetail = inMemoryDetail
            ?: getExtendedDetailUseCase.getCachedExtendedDetail(anilistId, malId, type)

        val initialDetail = cachedDetail ?: when (resolvedItem) {
            is UserMediaItem -> ExtendedMediaDetail(
                anilistId = resolvedItem.anilistId,
                malId = resolvedItem.malId,
                title = resolvedItem.title,
                titleEnglish = resolvedItem.metadata.titleEnglish,
                coverImage = resolvedItem.imageUrl,
                synopsis = resolvedItem.synopsis,
                studio = resolvedItem.metadata.studio,
                source = resolvedItem.metadata.format,
                airingStatus = resolvedItem.metadata.status,
                genres = resolvedItem.metadata.genres,
                malScore = if (resolvedItem.score > 0) resolvedItem.score.toDouble() else resolvedItem.metadata.score
            )
            is MediaItem -> ExtendedMediaDetail(
                anilistId = resolvedItem.anilistId,
                malId = resolvedItem.malId,
                title = resolvedItem.title,
                titleEnglish = resolvedItem.titleEnglish,
                coverImage = resolvedItem.imageUrl,
                synopsis = resolvedItem.synopsis,
                studio = resolvedItem.studio,
                source = resolvedItem.format,
                airingStatus = resolvedItem.status,
                genres = resolvedItem.genres,
                malScore = null,
                averageScore = resolvedItem.score?.toDouble()
            )
            else -> null
        }

        if (initialDetail != null) {
            cacheDetail(resolvedItem, initialDetail)
            cacheDetail(item, initialDetail)
        }

        _detailState.update {
            it.copy(
                selectedItem = resolvedItem,
                mediaType = type,
                isOpen = true,
                selectedCastCrewProfile = null,
                isLoadingCastCrewProfile = false,
                extendedDetail = initialDetail,
                isLoadingExtendedDetail = cachedDetail == null,
                isAniListUnavailable = false
            )
        }

        detailJob?.cancel()
        val token = ++detailRequestToken
        detailJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200L)
            if (token != detailRequestToken) return@launch
            try {
                val initialMalId = malId ?: (anilistId?.let { getExtendedDetailUseCase.getMalIdForAniListId(it, type) })
                var effectiveMalId = initialMalId

                val malDeferred = if (initialMalId != null) {
                    async {
                        getExtendedDetailUseCase.getMalExtendedDetailFallback(initialMalId, type)
                    }
                } else null

                val aniDeferred = async {
                    com.canim.app.data.remote.AniListClient.getExtendedDetails(anilistId, initialMalId, type)
                }

                if (malDeferred != null) {
                    launch {
                        try {
                            val malDetail = malDeferred.await()
                            if (malDetail != null && token == detailRequestToken) {
                                _detailState.update { current ->
                                    val currentExt = current.extendedDetail
                                    val merged = (currentExt ?: malDetail).copy(
                                        coverImage = malDetail.coverImage?.takeIf { it.isNotBlank() } ?: currentExt?.coverImage ?: "",
                                        malScore = malDetail.malScore ?: currentExt?.malScore,
                                        malRank = malDetail.malRank ?: currentExt?.malRank,
                                        malPopularity = malDetail.malPopularity ?: currentExt?.malPopularity,
                                        malMembers = malDetail.malMembers ?: currentExt?.malMembers,
                                        synopsis = malDetail.synopsis?.takeIf { it.isNotBlank() } ?: currentExt?.synopsis ?: "",
                                        airingStatus = malDetail.airingStatus ?: currentExt?.airingStatus,
                                        studio = malDetail.studio ?: currentExt?.studio,
                                        studioId = malDetail.studioId ?: currentExt?.studioId,
                                        publisher = malDetail.publisher ?: currentExt?.publisher,
                                        source = malDetail.source ?: currentExt?.source,
                                        startDate = malDetail.startDate ?: currentExt?.startDate,
                                        endDate = malDetail.endDate ?: currentExt?.endDate,
                                        durationMinutes = malDetail.durationMinutes ?: currentExt?.durationMinutes,
                                        genres = if (malDetail.genres.isNotEmpty()) malDetail.genres else (currentExt?.genres ?: emptyList()),
                                        relations = if (malDetail.relations.isNotEmpty()) malDetail.relations else (currentExt?.relations ?: emptyList()),
                                        recommendations = if (malDetail.recommendations.isNotEmpty()) malDetail.recommendations else (currentExt?.recommendations ?: emptyList()),
                                        isFromFallback = currentExt == null || currentExt.isFromFallback
                                    )
                                    cacheDetail(resolvedItem, merged)
                                    cacheDetail(item, merged)
                                    current.copy(
                                        extendedDetail = merged,
                                        isLoadingExtendedDetail = false
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                val aniDetail = try {
                    aniDeferred.await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("DetailViewModel", "AniList detail fetch failed: ${LogRedactor.redact(e.message ?: "")}")
                    null
                }

                if (aniDetail != null && token == detailRequestToken) {
                    if (effectiveMalId == null && aniDetail.malId != null) {
                        effectiveMalId = aniDetail.malId
                        launch {
                            try {
                                val malDetail = getExtendedDetailUseCase.getMalExtendedDetailFallback(aniDetail.malId, type)
                                if (malDetail != null && token == detailRequestToken) {
                                    _detailState.update { current ->
                                        val currentExt = current.extendedDetail ?: aniDetail
                                        val merged = currentExt.copy(
                                            coverImage = malDetail.coverImage?.takeIf { it.isNotBlank() } ?: currentExt.coverImage,
                                            malScore = malDetail.malScore ?: currentExt.malScore,
                                            malRank = malDetail.malRank ?: currentExt.malRank,
                                            malPopularity = malDetail.malPopularity ?: currentExt.malPopularity,
                                            malMembers = malDetail.malMembers ?: currentExt.malMembers,
                                            synopsis = malDetail.synopsis?.takeIf { it.isNotBlank() } ?: currentExt.synopsis,
                                            airingStatus = malDetail.airingStatus ?: currentExt.airingStatus,
                                            studio = currentExt.studio ?: malDetail.studio,
                                            studioId = currentExt.studioId ?: malDetail.studioId,
                                            publisher = currentExt.publisher ?: malDetail.publisher,
                                            source = currentExt.source ?: malDetail.source,
                                            startDate = currentExt.startDate ?: malDetail.startDate,
                                            endDate = currentExt.endDate ?: malDetail.endDate,
                                            durationMinutes = currentExt.durationMinutes ?: malDetail.durationMinutes,
                                            genres = if (currentExt.genres.isNotEmpty()) currentExt.genres else malDetail.genres,
                                            relations = if (currentExt.relations.isNotEmpty()) currentExt.relations else malDetail.relations,
                                            recommendations = if (currentExt.recommendations.isNotEmpty()) currentExt.recommendations else malDetail.recommendations
                                        )
                                        cacheDetail(resolvedItem, merged)
                                        cacheDetail(item, merged)
                                        current.copy(extendedDetail = merged)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    _detailState.update { current ->
                        val currentExt = current.extendedDetail
                        val merged = aniDetail.copy(
                            coverImage = currentExt?.coverImage?.takeIf { it.isNotBlank() } ?: aniDetail.coverImage,
                            malScore = currentExt?.malScore ?: aniDetail.malScore,
                            malRank = currentExt?.malRank ?: aniDetail.rank,
                            malPopularity = currentExt?.malPopularity ?: aniDetail.popularity,
                            malMembers = currentExt?.malMembers ?: aniDetail.watchers,
                            synopsis = currentExt?.synopsis?.takeIf { it.isNotBlank() } ?: aniDetail.synopsis,
                            airingStatus = currentExt?.airingStatus ?: aniDetail.airingStatus,
                            studio = aniDetail.studio ?: currentExt?.studio,
                            studioId = aniDetail.studioId ?: currentExt?.studioId,
                            publisher = aniDetail.publisher ?: currentExt?.publisher,
                            source = aniDetail.source ?: currentExt?.source,
                            startDate = aniDetail.startDate ?: currentExt?.startDate,
                            endDate = aniDetail.endDate ?: currentExt?.endDate,
                            durationMinutes = aniDetail.durationMinutes ?: currentExt?.durationMinutes,
                            genres = if (aniDetail.genres.isNotEmpty()) aniDetail.genres else (currentExt?.genres ?: emptyList()),
                            relations = if (aniDetail.relations.isNotEmpty()) aniDetail.relations else (currentExt?.relations ?: emptyList()),
                            recommendations = if (aniDetail.recommendations.isNotEmpty()) aniDetail.recommendations else (currentExt?.recommendations ?: emptyList())
                        )
                        cacheDetail(resolvedItem, merged)
                        cacheDetail(item, merged)
                        current.copy(
                            extendedDetail = merged,
                            isLoadingExtendedDetail = false,
                            isAniListUnavailable = false
                        )
                    }
                } else if (aniDetail == null && token == detailRequestToken) {
                    val malDetail = if (effectiveMalId != null && malDeferred != null) {
                        malDeferred.await()
                    } else null

                    val isThrottledOrCooldown = ApiClient.aniListLimiter.isCooldownActive()

                    if (malDetail != null) {
                        // Only cache if AniList was NOT throttled or in cooldown (ANTI-FALSE CACHE)
                        if (!isThrottledOrCooldown) {
                            cacheDetail(resolvedItem, malDetail)
                            cacheDetail(item, malDetail)
                        }
                        _detailState.update {
                            it.copy(
                                extendedDetail = malDetail,
                                isLoadingExtendedDetail = isThrottledOrCooldown,
                                isAniListUnavailable = !isThrottledOrCooldown
                            )
                        }
                    } else {
                        _detailState.update {
                            it.copy(
                                isLoadingExtendedDetail = isThrottledOrCooldown,
                                isAniListUnavailable = !isThrottledOrCooldown
                            )
                        }
                    }

                    // DO NOT GIVE UP on throttling/cooldown: retry AniList request after cooldown
                    if (isThrottledOrCooldown && token == detailRequestToken) {
                        launch {
                            val waitCooldownMs = ApiClient.aniListLimiter.remainingCooldownMs()
                            if (waitCooldownMs > 0L) {
                                delay(waitCooldownMs + 500L)
                            }
                            if (token == detailRequestToken && isActive) {
                                try {
                                    val retriedAni = com.canim.app.data.remote.AniListClient.getExtendedDetails(
                                        anilistId,
                                        effectiveMalId,
                                        type,
                                        forceRefresh = true
                                    )
                                    if (retriedAni != null && token == detailRequestToken) {
                                        _detailState.update { current ->
                                            val currentExt = current.extendedDetail
                                            val merged = (currentExt ?: retriedAni).copy(
                                                cast = if (retriedAni.cast.isNotEmpty()) retriedAni.cast else (currentExt?.cast ?: emptyList()),
                                                crew = if (retriedAni.crew.isNotEmpty()) retriedAni.crew else (currentExt?.crew ?: emptyList()),
                                                studio = retriedAni.studio ?: currentExt?.studio,
                                                studioId = retriedAni.studioId ?: currentExt?.studioId,
                                                publisher = retriedAni.publisher ?: currentExt?.publisher,
                                                durationMinutes = retriedAni.durationMinutes ?: currentExt?.durationMinutes,
                                                isFromFallback = false
                                            )
                                            cacheDetail(resolvedItem, merged)
                                            cacheDetail(item, merged)
                                            current.copy(
                                                extendedDetail = merged,
                                                isLoadingExtendedDetail = false,
                                                isAniListUnavailable = false
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("DetailViewModel", "AniList retry after cooldown failed: ${e.message}")
                                }
                            }
                        }
                    }
                }

                if (resolvedItem !is UserMediaItem && effectiveMalId != null) {
                    try {
                        val tracking = getExtendedDetailUseCase.getMalTrackingStatus(effectiveMalId, type)
                        if (tracking != null) {
                            val currentExt = _detailState.value.extendedDetail
                            val media = resolvedItem as? MediaItem
                            val itemTitle = media?.title ?: currentExt?.title ?: ""
                            val itemImageUrl = currentExt?.coverImage?.takeIf { it.isNotBlank() } ?: media?.imageUrl ?: ""
                            val metadata = MediaMetadata(
                                title = itemTitle,
                                titleEnglish = media?.titleEnglish ?: currentExt?.titleEnglish,
                                titleNative = currentExt?.nativeTitle,
                                imageUrl = itemImageUrl,
                                type = type,
                                score = currentExt?.malScore ?: media?.score,
                                synopsis = media?.synopsis ?: currentExt?.synopsis,
                                totalEpisodes = media?.episodes,
                                totalChapters = media?.chapters,
                                status = media?.status ?: currentExt?.airingStatus,
                                year = media?.year ?: currentExt?.startDate?.take(4)?.toIntOrNull(),
                                season = media?.season,
                                genres = if (media?.genres?.isNotEmpty() == true) media.genres else (currentExt?.genres ?: emptyList()),
                                format = media?.format ?: currentExt?.source,
                                studio = media?.studio ?: currentExt?.studio
                            )
                            val newUserItem = UserMediaItem(
                                identity = MediaRef(
                                    anilistId = anilistId ?: currentExt?.anilistId,
                                    malId = effectiveMalId
                                ),
                                metadata = metadata,
                                tracking = tracking
                            )
                            _detailState.update { it.copy(selectedItem = newUserItem) }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("DetailViewModel", "Detail fetch failed: ${LogRedactor.redact(e.message ?: "")}")
            } finally {
                if (token == detailRequestToken) {
                    _detailState.update { it.copy(isLoadingExtendedDetail = false) }
                    if (_detailState.value.extendedDetail == null) {
                        showSnackbar("Gagal memuat detail media. Periksa koneksi internet Anda.")
                    }
                }
            }
        }
    }

    fun openCastCrewProfile(id: Int, isStaff: Boolean) {
        _detailState.update {
            it.copy(
                selectedCastCrewProfile = null,
                isLoadingCastCrewProfile = true
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val profile = getCastCrewProfileUseCase(id, isStaff)
            _detailState.update {
                it.copy(
                    selectedCastCrewProfile = profile,
                    isLoadingCastCrewProfile = false
                )
            }
        }
    }

    fun closeCastCrewProfile() {
        _detailState.update {
            it.copy(
                selectedCastCrewProfile = null,
                isLoadingCastCrewProfile = false
            )
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _detailState.update {
            it.copy(
                selectedItem = null,
                isOpen = false,
                extendedDetail = null,
                isLoadingExtendedDetail = false,
                selectedCastCrewProfile = null,
                isLoadingCastCrewProfile = false
            )
        }
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(message)
        }
    }
}
