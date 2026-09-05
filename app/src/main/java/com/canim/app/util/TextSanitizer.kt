package com.canim.app.util

import android.os.Build
import android.text.Html

object TextSanitizer {

    fun sanitize(text: String?): String {
        if (text.isNullOrBlank()) return ""

        var cleaned = text
            // Replace HTML line breaks with real newlines
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            // Remove AniList spoiler tags: ~!spoiler content!~ -> spoiler content
            .replace(Regex("""~!([\s\S]*?)!~"""), "$1")
            // Strip markdown bold: __text__ or **text**
            .replace(Regex("""__(.*?)__"""), "$1")
            .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
            // Strip markdown italic: *text* or _text_
            .replace(Regex("""\*(.*?)\*"""), "$1")
            .replace(Regex("""_(.*?)_"""), "$1")
            // Strip markdown strikethrough: ~~text~~
            .replace(Regex("""~{1,2}([^~]+?)~{1,2}"""), "$1")
            // Strip markdown link: [text](url) -> text
            .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1")
            // Remove literal backslash escapes before punctuation/brackets
            .replace(Regex("""\\([()\[\]{}*~_`#+\-.!])"""), "$1")
            // Strip remaining HTML tags
            .replace(Regex("""(?i)</?[a-z0-9]+[^>]*>"""), "")

        // Decode HTML entities
        cleaned = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(cleaned).toString()
            }
        } catch (_: Exception) {
            decodeManualEntities(cleaned)
        }

        // Clean up redundant line breaks (max 2 consecutive newlines)
        return cleaned.replace(Regex("\n{3,}"), "\n\n").trim()
    }

    fun decodeManualEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&#60;", "<")
            .replace("&gt;", ">")
            .replace("&#62;", ">")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
    }
}
