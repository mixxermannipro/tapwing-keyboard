package com.tapwing.keyboard.dictionary

import kotlin.math.hypot
import kotlin.math.min

data class KeyCenter(
    val char: Char,
    val x: Float,
    val y: Float
)

data class SwipePoint(
    val x: Float,
    val y: Float
)

/**
 * Clean-room on-device geometric glide/swipe decoder.
 * Scores vocabulary + user-learned words against the spatial gesture path,
 * anchored by start key, end key, path alignment, and personal word/bigram frequency.
 */
object SwipeGestureDecoder {

    fun decodeSwipe(
        points: List<SwipePoint>,
        keyCenters: List<KeyCenter>,
        locale: String,
        previousWord: String? = null,
        dictionaryStore: AdaptiveDictionaryStore? = null,
        secondaryLocales: List<String> = emptyList(),
        atSentenceStart: Boolean = false
    ): List<String> {
        if (points.size < 2 || keyCenters.isEmpty()) return emptyList()

        val startPt = points.first()
        val endPt = points.last()
        val startCandidates = nearestKeys(startPt, keyCenters, count = 2)
        val endCandidates = nearestKeys(endPt, keyCenters, count = 2)
        if (startCandidates.isEmpty() || endCandidates.isEmpty()) return emptyList()

        val centerByChar = keyCenters.associateBy { it.char.lowercaseChar() }
        val resampled = resamplePath(points, targetCount = 24)
        val pathLength = computePathLength(resampled).coerceAtLeast(1f)

        val avgKeyGap = estimateKeyWidth(keyCenters).coerceAtLeast(40f)
        val pool = linkedSetOf<String>()
        dictionaryStore?.allKnownVocabularyWords()?.let { pool.addAll(it) }
        pool.addAll(LanguageVocabularies.vocabularyFor(locale))
        secondaryLocales.forEach { sec ->
            pool.addAll(LanguageVocabularies.vocabularyFor(sec))
        }

        val bigramNext = if (!previousWord.isNullOrBlank() && dictionaryStore != null) {
            dictionaryStore.predictNextWords(previousWord, limit = 10).toSet()
        } else {
            emptySet()
        }

        val scored = mutableListOf<Pair<String, Double>>()
        for (word in pool) {
            val clean = word.trim()
            if (clean.length < 2) continue
            val lower = clean.lowercase()
            if (!lower.all { centerByChar.containsKey(it) }) continue

            val firstChar = lower.first()
            val lastChar = lower.last()
            val startRank = startCandidates.indexOfFirst { it.first == firstChar }
            val endRank = endCandidates.indexOfFirst { it.first == lastChar }
            if (startRank == -1 || endRank == -1) continue

            val idealPoints = buildIdealWordPath(lower, centerByChar, resampled.size) ?: continue
            val dtwDist = normalizedPathDistance(resampled, idealPoints) / avgKeyGap
            if (dtwDist > 1.85) continue

            val idealLen = computePathLength(idealPoints)
            val lenRatioPenalty = kotlin.math.abs(pathLength - idealLen) / maxOf(pathLength, idealLen, 1f)

            val startEndPenalty = startCandidates[startRank].second / avgKeyGap +
                endCandidates[endRank].second / avgKeyGap

            val personalScore = (dictionaryStore?.wordScore(clean) ?: 0).coerceAtMost(200)
            val bigramBonus = if (clean in bigramNext || lower in bigramNext) 0.45 else 0.0
            val personalBonus = (personalScore / 200.0) * 0.35

            val totalCost = dtwDist * 1.0 + startEndPenalty * 0.55 + lenRatioPenalty * 0.35 - bigramBonus - personalBonus
            val finalWord = if (atSentenceStart) {
                clean.replaceFirstChar { it.uppercaseChar() }
            } else if (LanguageVocabularies.isNeverAutoCapitalizeMidSentence(clean, locale)) {
                clean.lowercase()
            } else {
                clean
            }
            scored += finalWord to totalCost
        }

        return scored
            .sortedBy { it.second }
            .map { it.first }
            .distinct()
            .take(3)
    }

    private fun nearestKeys(pt: SwipePoint, centers: List<KeyCenter>, count: Int): List<Pair<Char, Float>> {
        return centers
            .map { it.char.lowercaseChar() to hypot(pt.x - it.x, pt.y - it.y) }
            .sortedBy { it.second }
            .take(count)
    }

    private fun estimateKeyWidth(centers: List<KeyCenter>): Float {
        if (centers.size < 2) return 80f
        var minGap = Float.MAX_VALUE
        for (i in 0 until min(centers.size, 10)) {
            for (j in i + 1 until min(centers.size, 10)) {
                val d = hypot(centers[i].x - centers[j].x, centers[i].y - centers[j].y)
                if (d > 10f && d < minGap) minGap = d
            }
        }
        return if (minGap == Float.MAX_VALUE) 80f else minGap
    }

    private fun buildIdealWordPath(
        word: String,
        centers: Map<Char, KeyCenter>,
        samples: Int
    ): List<SwipePoint>? {
        val raw = word.map { ch ->
            val kc = centers[ch] ?: return null
            SwipePoint(kc.x, kc.y)
        }
        return resamplePath(raw, samples)
    }

    private fun resamplePath(points: List<SwipePoint>, targetCount: Int): List<SwipePoint> {
        if (points.size <= 1) return List(targetCount) { points.firstOrNull() ?: SwipePoint(0f, 0f) }
        val totalLen = computePathLength(points)
        if (totalLen < 1f) return List(targetCount) { points.first() }

        val out = ArrayList<SwipePoint>(targetCount)
        val step = totalLen / (targetCount - 1)
        var accumulated = 0f
        var idx = 1
        var prev = points[0]
        out += prev

        while (out.size < targetCount - 1 && idx < points.size) {
            val curr = points[idx]
            val seg = hypot(curr.x - prev.x, curr.y - prev.y)
            if (accumulated + seg >= step) {
                val t = ((step - accumulated) / seg).coerceIn(0f, 1f)
                val nx = prev.x + t * (curr.x - prev.x)
                val ny = prev.y + t * (curr.y - prev.y)
                val np = SwipePoint(nx, ny)
                out += np
                prev = np
                accumulated = 0f
            } else {
                accumulated += seg
                prev = curr
                idx++
            }
        }
        while (out.size < targetCount) {
            out += points.last()
        }
        return out
    }

    private fun computePathLength(points: List<SwipePoint>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
        }
        return total
    }

    private fun normalizedPathDistance(a: List<SwipePoint>, b: List<SwipePoint>): Double {
        val n = min(a.size, b.size)
        if (n == 0) return Double.MAX_VALUE
        var sum = 0.0
        for (i in 0 until n) {
            sum += hypot((a[i].x - b[i].x).toDouble(), (a[i].y - b[i].y).toDouble())
        }
        return sum / n
    }
}
