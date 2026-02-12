package one.monero.moneroone.data.util

import one.monero.moneroone.data.model.PriceDataPoint
import kotlin.math.abs

/**
 * Largest Triangle Three Buckets (LTTB) downsampling algorithm.
 * Preserves visual shape of the data while reducing point count.
 */
fun lttbDownsample(data: List<PriceDataPoint>, targetPoints: Int): List<PriceDataPoint> {
    if (data.size <= targetPoints) return data
    if (targetPoints < 3) return data

    val result = mutableListOf<PriceDataPoint>()

    // Always keep the first point
    result.add(data.first())

    val bucketSize = (data.size - 2).toDouble() / (targetPoints - 2)

    var prevSelectedIndex = 0

    for (i in 1 until targetPoints - 1) {
        // Calculate bucket boundaries
        val bucketStart = ((i - 1) * bucketSize + 1).toInt()
        val bucketEnd = (i * bucketSize + 1).toInt().coerceAtMost(data.size - 1)

        // Calculate the average point of the next bucket (for area computation)
        val nextBucketStart = (i * bucketSize + 1).toInt()
        val nextBucketEnd = ((i + 1) * bucketSize + 1).toInt().coerceAtMost(data.size - 1)

        var avgTimestamp = 0.0
        var avgPrice = 0.0
        val nextBucketCount = nextBucketEnd - nextBucketStart + 1

        for (j in nextBucketStart..nextBucketEnd.coerceAtMost(data.lastIndex)) {
            avgTimestamp += data[j].timestamp
            avgPrice += data[j].price
        }
        avgTimestamp /= nextBucketCount
        avgPrice /= nextBucketCount

        // Find the point in the current bucket that forms the largest triangle
        val prevPoint = data[prevSelectedIndex]
        var maxArea = -1.0
        var maxAreaIndex = bucketStart

        for (j in bucketStart..bucketEnd.coerceAtMost(data.lastIndex)) {
            val area = abs(
                (prevPoint.timestamp.toDouble() - avgTimestamp) * (data[j].price - prevPoint.price) -
                    (prevPoint.timestamp.toDouble() - data[j].timestamp.toDouble()) * (avgPrice - prevPoint.price)
            ) * 0.5

            if (area > maxArea) {
                maxArea = area
                maxAreaIndex = j
            }
        }

        result.add(data[maxAreaIndex])
        prevSelectedIndex = maxAreaIndex
    }

    // Always keep the last point
    result.add(data.last())

    return result
}

/**
 * Exponential Moving Average smoothing.
 * @param span The number of periods for the EMA (higher = smoother)
 */
fun emaSmooth(data: List<PriceDataPoint>, span: Int = 10): List<PriceDataPoint> {
    if (data.size <= 1) return data

    val alpha = 2.0 / (span + 1)
    val result = mutableListOf<PriceDataPoint>()

    var ema = data.first().price
    result.add(data.first())

    for (i in 1 until data.size) {
        ema = alpha * data[i].price + (1 - alpha) * ema
        result.add(PriceDataPoint(data[i].timestamp, ema))
    }

    return result
}
