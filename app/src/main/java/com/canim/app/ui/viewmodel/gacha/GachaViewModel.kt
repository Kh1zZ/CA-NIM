package com.canim.app.ui.viewmodel.gacha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.MediaItem
import com.canim.app.domain.usecase.ConsumeGachaCreditUseCase
import com.canim.app.domain.usecase.LoadFlashcardDeckUseCase
import com.canim.app.data.local.GachaCooldownManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GachaViewModel @Inject constructor(
    private val consumeGachaCreditUseCase: ConsumeGachaCreditUseCase,
    private val loadFlashcardDeckUseCase: LoadFlashcardDeckUseCase,
    private val cooldownManager: GachaCooldownManager? = null
) : ViewModel() {

    private val _gachaState = MutableStateFlow(GachaUiState())
    val gachaState: StateFlow<GachaUiState> = _gachaState.asStateFlow()

    private val _snackbarEvent = Channel<String>(Channel.BUFFERED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()

    init {
        val currentCredits = consumeGachaCreditUseCase.getCredits()
        _gachaState.update { it.copy(credits = currentCredits) }
        viewModelScope.launch {
            consumeGachaCreditUseCase.observeCredits().collect { updatedCredits ->
                _gachaState.update { it.copy(credits = updatedCredits) }
            }
        }
    }

    fun onGachaEvent(event: GachaEvent) {
        when (event) {
            is GachaEvent.OpenGacha -> {
                if (_gachaState.value.deck.isEmpty() && _gachaState.value.credits > 0) {
                    onGachaEvent(GachaEvent.LoadDeck)
                }
            }
            is GachaEvent.ConsumeCredit -> {
                consumeGachaCredit()
            }
            is GachaEvent.SwipeDismiss -> {
                swipeDismissFlashcard(event.item)
            }
            is GachaEvent.LoadDeck -> {
                loadFlashcardDeck()
            }
            is GachaEvent.UpdateCredits -> {
                consumeGachaCreditUseCase.setCredits(event.newCredits)
                _gachaState.update { it.copy(credits = event.newCredits) }
            }
        }
    }

    fun openFlashcard(excludedIds: Set<Int>? = null) {
        if (_gachaState.value.deck.isEmpty() && _gachaState.value.credits > 0) {
            loadFlashcardDeck(excludedIds)
        }
    }

    fun consumeGachaCredit(): Boolean {
        val success = consumeGachaCreditUseCase()
        if (success) {
            val updated = consumeGachaCreditUseCase.getCredits()
            _gachaState.update { it.copy(credits = updated) }
        }
        return success
    }

    fun swipeDismissFlashcard(item: MediaItem) {
        val mId = item.malId
        if (mId != null && mId > 0) {
            cooldownManager?.recordGachaDrawn(mId)
        }
        _gachaState.update {
            val updatedDeck = it.deck.filter { card -> card.id != item.id }
            it.copy(deck = updatedDeck)
        }
        if (_gachaState.value.deck.isEmpty() && _gachaState.value.credits > 0) {
            loadFlashcardDeck()
        }
    }

    fun loadFlashcardDeck(customExcludedIds: Set<Int>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _gachaState.update { it.copy(isLoading = true) }
            val pool = if (customExcludedIds != null) {
                loadFlashcardDeckUseCase(customExcludedIds)
            } else {
                loadFlashcardDeckUseCase()
            }
            _gachaState.update {
                it.copy(
                    deck = pool,
                    isLoading = false
                )
            }
        }
    }

    fun updateCredits(newCredits: Int) {
        onGachaEvent(GachaEvent.UpdateCredits(newCredits))
    }

    fun refreshCredits() {
        val currentCredits = consumeGachaCreditUseCase.getCredits()
        _gachaState.update { it.copy(credits = currentCredits) }
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.send(message)
        }
    }
}
