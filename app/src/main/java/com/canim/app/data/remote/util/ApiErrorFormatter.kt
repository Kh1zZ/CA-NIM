package com.canim.app.data.remote.util

import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import com.canim.app.data.remote.AniListResult
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Formats API errors (HTTP codes, timeouts, GraphQL, network) from MyAnimeList and AniList
 * into clear, user-friendly plain text in Indonesian (Jenis Error + Maksud Error) for Snackbars.
 */
object ApiErrorFormatter {

    fun format(throwable: Throwable?, host: String = "Layanan"): String {
        if (throwable == null) return "Terjadi kendala jaringan yang tidak diketahui."

        val displayHost = when (host.lowercase()) {
            "anilist" -> "AniList"
            "myanimelist", "mal" -> "MyAnimeList"
            else -> host
        }

        return when (throwable) {
            is SocketTimeoutException -> {
                "Koneksi Bermasalah (Timeout): Waktu permintaan ke $displayHost habis. Periksa kestabilan internet Anda."
            }
            is UnknownHostException -> {
                "Koneksi Terputus: Tidak dapat menemukan server $displayHost. Periksa koneksi internet Anda."
            }
            is ApolloNetworkException -> {
                if (throwable.cause is SocketTimeoutException) {
                    "Koneksi Bermasalah (Timeout): Waktu permintaan ke $displayHost habis. Periksa koneksi internet Anda."
                } else {
                    "Koneksi Jaringan Gagal: Gagal terhubung ke $displayHost. Pastikan koneksi internet aktif."
                }
            }
            is ApolloHttpException -> {
                formatHttpCode(throwable.statusCode, displayHost)
            }
            is HttpException -> {
                formatHttpCode(throwable.code(), displayHost)
            }
            is IOException -> {
                "Gangguan Jaringan: Gagal berkomunikasi dengan $displayHost (${throwable.localizedMessage ?: "I/O error"})."
            }
            else -> {
                val msg = throwable.localizedMessage ?: throwable.message ?: "Kesalahan tidak diketahui"
                if (msg.contains("429") || msg.contains("Too Many Requests", ignoreCase = true)) {
                    formatHttpCode(429, displayHost)
                } else if (msg.contains("timeout", ignoreCase = true)) {
                    "Koneksi Bermasalah (Timeout): Permintaan ke $displayHost melampaui batas waktu."
                } else {
                    "Kendala Layanan ($displayHost): $msg"
                }
            }
        }
    }

    fun formatHttpCode(statusCode: Int, host: String, cooldownSeconds: Long? = null): String {
        val displayHost = when (host.lowercase()) {
            "anilist" -> "AniList"
            "myanimelist", "mal" -> "MyAnimeList"
            else -> host
        }

        return when (statusCode) {
            429 -> {
                val secText = if (cooldownSeconds != null && cooldownSeconds > 0) " selama ${cooldownSeconds} detik" else ""
                "Batas Permintaan (HTTP 429): Terlalu banyak permintaan ke $displayHost. Sistem mendinginkan koneksi$secText agar akun aman."
            }
            401 -> {
                "Sesi Berakhir (HTTP 401): Autentikasi $displayHost kedaluwarsa. Silakan masuk ulang melalui Pengaturan."
            }
            403 -> {
                "Akses Ditolak (HTTP 403): Permintaan ke $displayHost ditolak oleh server."
            }
            404 -> {
                "Tidak Ditemukan (HTTP 404): Data yang dicari tidak tersedia di $displayHost."
            }
            500, 502, 503, 504 -> {
                "Gangguan Server (HTTP $statusCode): Server $displayHost sedang mengalami kendala atau pemeliharaan berkala."
            }
            else -> {
                "Kesalahan HTTP ($statusCode): Server $displayHost mengembalikan kode error $statusCode."
            }
        }
    }

    fun formatAniListResult(result: AniListResult<*>): String? {
        return when (result) {
            is AniListResult.RateLimited -> {
                val sec = result.retryAfterSeconds ?: 30L
                formatHttpCode(429, "AniList", sec)
            }
            is AniListResult.HttpError -> {
                formatHttpCode(result.code, "AniList")
            }
            is AniListResult.Timeout -> {
                "Koneksi Bermasalah (Timeout): Waktu permintaan ke AniList habis. Periksa koneksi internet Anda."
            }
            is AniListResult.NetworkError -> {
                "Koneksi Jaringan Gagal: Gagal terhubung ke AniList. Pastikan internet aktif."
            }
            is AniListResult.GraphQLError -> {
                val detail = result.errors.firstOrNull()?.message ?: "Terjadi kesalahan kueri GraphQL"
                "Kendala Layanan (AniList GraphQL): $detail"
            }
            is AniListResult.NotFound -> {
                "Tidak Ditemukan: Informasi anime/manga tidak ditemukan di AniList."
            }
            is AniListResult.ParseError -> {
                "Kesalahan Format Data: Gagal memproses respons dari server AniList."
            }
            is AniListResult.Success -> null
        }
    }
}
