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

    private const val RESAMPLE_COUNT = 20
    private const val DTW_BAND = 4
    private val rankMapCache = java.util.concurrent.ConcurrentHashMap<String, Map<String, Int>>()

    private val ACCENT_FALLBACKS: Array<Pair<Char, Char>> = arrayOf(
        'ä' to 'a', 'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ą' to 'a',
        'ö' to 'o', 'ó' to 'o', 'ò' to 'o', 'ô' to 'o', 'õ' to 'o',
        'ü' to 'u', 'ú' to 'u', 'ù' to 'u', 'û' to 'u',
        'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e', 'ę' to 'e',
        'í' to 'i', 'ì' to 'i', 'î' to 'i', 'ï' to 'i', 'ı' to 'i',
        'ß' to 's', 'ś' to 's', 'ş' to 's', 'š' to 's',
        'ç' to 'c', 'ć' to 'c', 'ñ' to 'n', 'ń' to 'n', 'ğ' to 'g', 'ł' to 'l', 'ż' to 'z', 'ź' to 'z'
    )

    private fun rankMapFor(locale: String, primaryVocab: List<String>): Map<String, Int> =
        rankMapCache.getOrPut(locale) {
            val map = HashMap<String, Int>(primaryVocab.size * 2)
            primaryVocab.forEachIndexed { idx, w -> map.putIfAbsent(w.lowercase(), idx) }
            map
        }

    private fun candidateRank(candidates: List<Pair<Char, Float>>, target: Char): Int {
        for (i in candidates.indices) {
            if (candidates[i].first == target) return i
        }
        return -1
    }

    fun decodeSwipe(
        points: List<SwipePoint>,
        keyCenters: List<KeyCenter>,
        locale: String,
        previousWord: String? = null,
        dictionaryStore: AdaptiveDictionaryStore? = null,
        secondaryLocales: List<String> = emptyList(),
        atSentenceStart: Boolean = false,
        offlinePack: LoadedDictionaryPack? = null,
        previousTwoWord: String? = null,
        appPackage: String? = null
    ): List<String> {
        if (points.size < 2 || keyCenters.isEmpty()) return emptyList()

        val startPt = points.first()
        val endPt = points.last()
        val startCandidates = nearestKeys(startPt, keyCenters, count = 3)
        val endCandidates = nearestKeys(endPt, keyCenters, count = 3)
        if (startCandidates.isEmpty() || endCandidates.isEmpty()) return emptyList()

        val centerByChar = buildEffectiveKeyMap(keyCenters)
        val resampled = resamplePath(points, targetCount = RESAMPLE_COUNT)
        val pathLength = computePathLength(resampled).coerceAtLeast(1f)

        val avgKeyGap = estimateKeyWidth(keyCenters).coerceAtLeast(40f)
        val primaryVocab = LanguageVocabularies.vocabularyFor(locale)
        val primaryRankMap = rankMapFor(locale, primaryVocab)

        val pool = linkedSetOf<String>()
        dictionaryStore?.allKnownVocabularyWords()?.let { pool.addAll(it) }
        pool.addAll(primaryVocab)
        offlinePack?.topSwipePool?.let { pool.addAll(it) }
        secondaryLocales.forEach { sec ->
            pool.addAll(LanguageVocabularies.vocabularyFor(sec))
        }

        val contextNext = HashSet<String>(16)
        if (!previousWord.isNullOrBlank()) {
            dictionaryStore?.predictNextWords(
                previousWord = previousWord,
                limit = 10,
                previousTwoWord = previousTwoWord,
                appPackage = appPackage
            )?.forEach { contextNext += it.lowercase() }
            LanguageVocabularies.commonNextWords(locale, previousWord, previousTwoWord.orEmpty())
                .forEach { contextNext += it.lowercase() }
        }

        val scored = mutableListOf<Pair<String, Double>>()
        for (word in pool) {
            val clean = word.trim()
            if (clean.length < 2) continue
            val lower = clean.lowercase()

            // Fast O(1) boundary filter before scanning every character of the candidate word
            val firstChar = baseSwipeChar(lower[0], centerByChar)
            val startRank = candidateRank(startCandidates, firstChar)
            if (startRank == -1) continue

            val lastChar = baseSwipeChar(lower[lower.length - 1], centerByChar)
            val endRank = candidateRank(endCandidates, lastChar)
            if (endRank == -1) continue

            var allOnBoard = true
            for (ci in 1 until lower.length - 1) {
                if (!centerByChar.containsKey(lower[ci])) {
                    allOnBoard = false
                    break
                }
            }
            if (!allOnBoard || !centerByChar.containsKey(lower[0]) || !centerByChar.containsKey(lower[lower.length - 1])) {
                continue
            }

            val idealPoints = buildIdealWordPath(lower, centerByChar, resampled.size) ?: continue
            val dtwDist = bandedDtwDistance(resampled, idealPoints) / avgKeyGap
            if (dtwDist > 1.75) continue

            val idealLen = computePathLength(idealPoints)
            val lenRatioPenalty = kotlin.math.abs(pathLength - idealLen) / maxOf(pathLength, idealLen, 1f)

            val startEndPenalty = startCandidates[startRank].second / avgKeyGap +
                endCandidates[endRank].second / avgKeyGap

            val personalScore = (dictionaryStore?.wordScore(clean) ?: 0).coerceAtMost(200)
            val contextBonus = if (lower in contextNext) 0.48 else 0.0
            val personalBonus = (personalScore / 200.0) * 0.35
            val freqBonus = frequencyPriorBonus(lower, primaryRankMap, offlinePack)

            val totalCost = dtwDist * 1.0 + startEndPenalty * 0.55 + lenRatioPenalty * 0.30 -
                contextBonus - personalBonus - freqBonus
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

    private fun frequencyPriorBonus(
        lower: String,
        primaryRankMap: Map<String, Int>,
        offlinePack: LoadedDictionaryPack?
    ): Double {
        val primaryRank = primaryRankMap[lower]
        if (primaryRank != null) {
            return (0.22 * (1.0 - (primaryRank.toDouble() / (primaryRankMap.size + 1)))).coerceIn(0.04, 0.22)
        }
        val freq = offlinePack?.wordFreq?.get(lower) ?: return 0.0
        return (kotlin.math.ln1p(freq.toDouble()) / 20.0).coerceIn(0.0, 0.14)
    }

    private fun buildEffectiveKeyMap(centers: List<KeyCenter>): Map<Char, KeyCenter> {
        val map = HashMap<Char, KeyCenter>(centers.size + 16)
        for (kc in centers) {
            map[kc.char.lowercaseChar()] = kc
        }
        // Fallback coordinates for umlauts/accents when the keyboard layout doesn't have dedicated keys
        for ((accented, base) in ACCENT_FALLBACKS) {
            if (!map.containsKey(accented)) {
                map[base]?.let { map[accented] = it }
            }
        }
        return map
    }

    private fun baseSwipeChar(ch: Char, centerByChar: Map<Char, KeyCenter>): Char {
        return centerByChar[ch]?.char?.lowercaseChar() ?: ch
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
        val raw = ArrayList<SwipePoint>(word.length)
        var prevChar: Char? = null
        for (ch in word) {
            // Collapse consecutive duplicate letters (e.g. "ll" in "hallo") to mirror natural swipe paths
            if (ch == prevChar) continue
            prevChar = ch
            val kc = centers[ch] ?: return null
            raw += SwipePoint(kc.x, kc.y)
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

    /**
     * Fast Sakoe-Chiba banded Dynamic Time Warping distance normalized by path length.
     * Handles uneven finger speeds and slight pauses mid-swipe far better than 1:1 point indexing.
     */
    internal fun bandedDtwDistance(a: List<SwipePoint>, b: List<SwipePoint>, band: Int = DTW_BAND): Double {
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0) return Double.MAX_VALUE
        val inf = 1e12
        var prev = DoubleArray(m + 1) { inf }
        var curr = DoubleArray(m + 1) { inf }
        prev[0] = 0.0
        for (i in 1..n) {
            curr.fill(inf)
            val jStart = maxOf(1, i - band)
            val jEnd = minOf(m, i + band)
            val ax = a[i - 1].x
            val ay = a[i - 1].y
            for (j in jStart..jEnd) {
                val dx = (ax - b[j - 1].x).toDouble()
                val dy = (ay - b[j - 1].y).toDouble()
                val cost = hypot(dx, dy)
                val bestPrev = minOf(prev[j], curr[j - 1], prev[j - 1])
                curr[j] = cost + bestPrev
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[m] / maxOf(n, m)
    }
}
