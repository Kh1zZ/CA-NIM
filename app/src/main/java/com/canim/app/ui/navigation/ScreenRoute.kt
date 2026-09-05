package com.canim.app.ui.navigation

import com.canim.app.data.model.CharacterCastItem
import com.canim.app.data.model.MediaType
import com.canim.app.data.model.StaffMemberItem

sealed class ScreenRoute {
    data class Detail(val item: Any, val type: MediaType) : ScreenRoute()
    data class CastCrew(val id: Int, val isStaff: Boolean) : ScreenRoute()
    data class FullCastList(
        val mediaTitle: String,
        val castList: List<CharacterCastItem>,
        val staffList: List<StaffMemberItem>,
        val isCrewInitial: Boolean = false
    ) : ScreenRoute()
    object Stats : ScreenRoute()
    object AddTitleSheet : ScreenRoute()
}
