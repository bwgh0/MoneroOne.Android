package one.monero.moneroone.data.util

import one.monero.moneroone.data.model.PriceDataPoint
import kotlin.math.abs

/**
 * Binary search to find the nearest PriceDataPoint by timestamp.
 * Returns null if the list is empty.
 */
fun List<PriceDataPoint>.nearestByTimestamp(target: Long): PriceDataPoint? {
    if (isEmpty()) return null
    if (size == 1) return first()

    var low = 0
    var high = lastIndex

    while (low < high) {
        val mid = (low + high) / 2
        when {
            this[mid].timestamp < target -> low = mid + 1
            this[mid].timestamp > target -> high = mid
            else -> return this[mid]
        }
    }

    // At this point, low == high
    // Check neighbors to find the closest
    return when {
        low == 0 -> first()
        low == lastIndex -> last()
        else -> {
            val prev = this[low - 1]
            val curr = this[low]
            if (abs(prev.timestamp - target) < abs(curr.timestamp - target)) prev else curr
        }
    }
}

/**
 * Find nearest point by index (for x-position based lookup)
 */
fun List<PriceDataPoint>.nearestByIndex(normalizedX: Float): PriceDataPoint? {
    if (isEmpty()) return null
    val index = (normalizedX * lastIndex).toInt().coerceIn(0, lastIndex)
    return getOrNull(index)
}

/**
 * Calculate the index for a given x position in a chart
 */
fun calculateChartIndex(x: Float, width: Float, dataSize: Int): Int {
    if (dataSize <= 1) return 0
    val normalizedX = (x / width).coerceIn(0f, 1f)
    return (normalizedX * (dataSize - 1)).toInt().coerceIn(0, dataSize - 1)
}
