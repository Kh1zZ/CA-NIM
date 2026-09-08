package com.canim.app.util

/**
 * Utility for sanitizing and redacting sensitive data (tokens, secrets, credentials, PII)
 * from logs, error messages, and metrics.
 */
object LogRedactor {

    private val BEARER_TOKEN_REGEX = Regex("(?i)Bearer\\s+([a-zA-Z0-9_.-]+)")
    private val ACCESS_TOKEN_REGEX = Regex("(?i)(access_token|refresh_token|token|secret|code_verifier|code|client_secret)=([^&\\s]+)")
    private val CLIENT_ID_REGEX = Regex("(?i)(client_id)=([^&\\s]+)")
    private val STATE_PARAM_REGEX = Regex("(?i)(state)=([^&\\s]+)")
    private val SEARCH_QUERY_REGEX = Regex("(?i)(query|q)=([^&\\s]+)")

    /**
     * Sanitizes a log string or error message by masking sensitive tokens, secrets,
     * and query strings.
     */
    fun redact(input: String?): String {
        if (input.isNullOrBlank()) return ""

        var sanitized = input
        sanitized = BEARER_TOKEN_REGEX.replace(sanitized) { match ->
            "Bearer [REDACTED]"
        }
        sanitized = ACCESS_TOKEN_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        sanitized = CLIENT_ID_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        sanitized = STATE_PARAM_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        sanitized = SEARCH_QUERY_REGEX.replace(sanitized) { match ->
            "${match.groupValues[1]}=[REDACTED_QUERY]"
        }
        return sanitized
    }

    /**
     * Safely redacts an exception's message and stacktrace summary.
     */
    fun redactException(throwable: Throwable?): String {
        if (throwable == null) return "Unknown error"
        val type = throwable.javaClass.simpleName
        val msg = redact(throwable.message)
        return if (msg.isNotBlank()) "$type: $msg" else type
    }
}
