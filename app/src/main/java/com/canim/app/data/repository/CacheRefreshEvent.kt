package com.canim.app.data.repository

enum class CacheRefreshType {
    SEARCH,
    DISCOVER,
    DETAIL
}

data class CacheRefreshEvent(
    val key: String,
    val type: CacheRefreshType
)
