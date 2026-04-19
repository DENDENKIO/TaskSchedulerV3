package com.example.taskschedulerv3.util

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * LLM応答のJSON、またはOCR生テキストから予定情報を抽出する。
 */
object OcrTextParser {

    data class ParsedInfo(
        val title: String?,
        val date: String?,          // YYYY-MM-DD
        val startTime: String?,     // HH:mm
        val endTime: String?,       // HH:mm
        val summary: String?
    )

    /**
     * LLMの応答文字列からJSONを抽出してパースする。
     * JSONが見つからない、またはパース失敗した場合は null を返す。
     */
    fun parseFromLlmResponse(response: String): ParsedInfo? {
        return try {
            // レスポンスからJSON部分を抽出（前後に余計なテキストがある場合に対応）
            val jsonStr = extractJsonFromText(response) ?: return null
            val json = JSONObject(jsonStr)

            ParsedInfo(
                title = json.optStringOrNull("title"),
                date = json.optStringOrNull("date"),
                startTime = json.optStringOrNull("start_time"),
                endTime = json.optStringOrNull("end_time"),
                summary = json.optStringOrNull("summary")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * JSONパース失敗時のフォールバック: 正規表現でOCR生テキストから日付を抽出。
     */
    fun fallbackParseFromOcr(ocrText: String): ParsedInfo {
        val date = extractDateFromText(ocrText)
        val times = extractTimesFromText(ocrText)
        // タイトルは最初の非空白行を使用
        val title = ocrText.lines()
            .firstOrNull { it.isNotBlank() && it.length in 2..50 }
            ?.trim()
        // 要約は先頭500文字
        val summary = if (ocrText.length > 500) ocrText.take(500) + "…" else ocrText

        return ParsedInfo(
            title = title,
            date = date,
            startTime = times.first,
            endTime = times.second,
            summary = summary
        )
    }

    // ===== private helpers =====

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key, "")
        return value.ifBlank { null }
    }

    /**
     * テキストから最初の {...} ブロックを抽出
     */
    private fun extractJsonFromText(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    /**
     * 日付パターンを複数サポート:
     * - 2026年5月1日 / 2026/5/1 / 2026-05-01
     * - 令和8年5月1日
     * - 5月1日（年なし → 今年を補完）
     */
    private fun extractDateFromText(text: String): String? {
        // パターン1: 2026年5月1日 or 2026/5/1 or 2026-5-1
        val p1 = Regex("""(20\d{2})\s*[年/\-]\s*(\d{1,2})\s*[月/\-]\s*(\d{1,2})""")
        p1.find(text)?.let { m ->
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            return formatDate(y, mo, d)
        }

        // パターン2: 令和X年Y月Z日
        val p2 = Regex("""令和\s*(\d{1,2})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
        p2.find(text)?.let { m ->
            val y = 2018 + m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            return formatDate(y, mo, d)
        }

        // パターン3: X月Y日（年なし）
        val p3 = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日""")
        p3.find(text)?.let { m ->
            val y = LocalDate.now().year
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            return formatDate(y, mo, d)
        }

        return null
    }

    /**
     * 時刻パターン:
     * - 14:00〜16:00 / 14:00-16:00 / 14:00～16:00
     * - 午後2時〜午後4時 / 午前10時30分
     * - 14時00分
     */
    private fun extractTimesFromText(text: String): Pair<String?, String?> {
        // パターン1: HH:mm〜HH:mm or HH:mm-HH:mm
        val p1 = Regex("""(\d{1,2}):(\d{2})\s*[〜～\-~]\s*(\d{1,2}):(\d{2})""")
        p1.find(text)?.let { m ->
            val start = "%02d:%02d".format(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            val end = "%02d:%02d".format(m.groupValues[3].toInt(), m.groupValues[4].toInt())
            return Pair(start, end)
        }

        // パターン2: 単独の HH:mm
        val p2 = Regex("""(\d{1,2}):(\d{2})""")
        p2.find(text)?.let { m ->
            val start = "%02d:%02d".format(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            return Pair(start, null)
        }

        // パターン3: 午前/午後X時Y分
        val p3 = Regex("""(午前|午後)\s*(\d{1,2})\s*時\s*(\d{1,2})?\s*分?""")
        val matches = p3.findAll(text).toList()
        if (matches.isNotEmpty()) {
            val times = matches.map { m ->
                var h = m.groupValues[2].toInt()
                val min = m.groupValues[3].toIntOrNull() ?: 0
                if (m.groupValues[1] == "午後" && h < 12) h += 12
                if (m.groupValues[1] == "午前" && h == 12) h = 0
                "%02d:%02d".format(h, min)
            }
            return Pair(times.getOrNull(0), times.getOrNull(1))
        }

        // パターン4: X時Y分（午前/午後なし）
        val p4 = Regex("""(\d{1,2})\s*時\s*(\d{1,2})?\s*分?""")
        p4.find(text)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toIntOrNull() ?: 0
            return Pair("%02d:%02d".format(h, min), null)
        }

        return Pair(null, null)
    }

    private fun formatDate(year: Int, month: Int, day: Int): String? {
        return try {
            LocalDate.of(year, month, day)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            null
        }
    }
}
