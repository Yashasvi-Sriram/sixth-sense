package sensing

import extensions.determinant
import extensions.dist
import extensions.inverse
import extensions.times
import org.ejml.data.FMatrix2
import org.ejml.data.FMatrix2x2
import processing.core.PApplet
import simulator.LaserSensor
import kotlin.math.*

class RANSACLeastSquares(private val applet: PApplet) : ObstacleLandmarkExtractionLogic {
    companion object {
        private const val RANSAC_ITER = 1000
        private const val RANSAC_THRESHOLD = 4f
        private const val RANSAC_MIN_INLIERS_FOR_LINE_SEGMENT = 15

        private const val DISCONTINUITY_THRESHOLD = 60.0
        private const val LOWER_LANDMARK_MARGIN = 1.0
        private const val INTERSECTION_MARGIN = 30.0
    }

    override fun getObservedObstaclesAndLandmarks(inputPoints: List<FMatrix2>, distances: List<Float>): Pair<List<Pair<FMatrix2, FMatrix2>>, List<FMatrix2>> {
        val observedLineSegmentObstacles = mutableListOf<Pair<FMatrix2, FMatrix2>>()
        val observedLandmarks = mutableListOf<FMatrix2>()

        val partitions = partitionBasedOnDiscontinuity(inputPoints, distances)
        for (partition in partitions) {
            val linesInPartition = fitLines(partition)
            observedLineSegmentObstacles.addAll(linesInPartition)
        }

        // Loose ends
        var j = 1
        var i = 1
        while (i < (distances.size) && j < inputPoints.size) {
            if (((distances[i] - distances[i - 1]) > DISCONTINUITY_THRESHOLD) || (distances[i] == LaserSensor.INVALID_DISTANCE && (distances[i] - distances[i - 1]) > LOWER_LANDMARK_MARGIN)) {
                observedLandmarks.add(inputPoints[j - 1])
            } else if (((distances[i - 1] - distances[i]) > DISCONTINUITY_THRESHOLD) || (distances[i - 1] == LaserSensor.INVALID_DISTANCE && (distances[i - 1] - distances[i]) > LOWER_LANDMARK_MARGIN)) {
                observedLandmarks.add(inputPoints[j])
            }
            if (distances[i] != LaserSensor.INVALID_DISTANCE) {
                j++
            }
            i++
        }

        // Intersection
        val intersectLandmarks = getLandmarksAtIntersection(observedLineSegmentObstacles, inputPoints)
        for (pt in intersectLandmarks) {
            observedLandmarks.add(pt)
        }

        return Pair(observedLineSegmentObstacles, observedLandmarks)
    }

    private fun partitionBasedOnDiscontinuity(points: List<FMatrix2>, distances: List<Float>): MutableList<MutableList<FMatrix2>> {
        val partitions = mutableListOf<MutableList<FMatrix2>>()
        if (points.isNotEmpty()) {
            // Add the first point as
            partitions.add(mutableListOf(points[0]))

            for (i in 1 until points.size) {
                // Add a new partition every time there is a significant change in distance
                if (abs(distances[i] - distances[i - 1]) > DISCONTINUITY_THRESHOLD) {
                    partitions.add(mutableListOf())
                }
                // Add the point to the latest segment
                partitions[partitions.lastIndex].add(points[i])
            }
        }
        return partitions
    }

    private fun fitLines(points: List<FMatrix2>): List<Pair<FMatrix2, FMatrix2>> {
        val lines = mutableListOf<Pair<FMatrix2, FMatrix2>>()
        // Make a copy of original points
        var remainingPoints = points.toMutableList()
        while (true) {
            // Find the biggest line
            var bestInliers = mutableListOf<FMatrix2>()
            var bestRemainingPoints = mutableListOf<FMatrix2>()

            for (i in 0 until RANSAC_ITER) {
                val remainingPointsCopy = remainingPoints.toMutableList()
                val definingPoints = mutableListOf(remainingPointsCopy.random(), remainingPointsCopy.random())
                // Get inlier indices
                val inlierIndices = mutableListOf<Int>()
                for ((k, possibleInlier) in remainingPointsCopy.withIndex()) {
                    val dist = getPerpendicularDistance(definingPoints[0], definingPoints[1], possibleInlier)
                    if (dist < RANSAC_THRESHOLD) {
                        inlierIndices.add(k)
                    }
                }
                // Collect the inliers
                val inliers = mutableListOf<FMatrix2>()
                for (index in inlierIndices.reversed()) {
                    inliers.add(remainingPointsCopy.removeAt(index))
                }
                // Update the best fits
                if (inlierIndices.size > bestInliers.size) {
                    bestInliers = inliers
                    bestRemainingPoints = remainingPointsCopy
                }
            }
            // If number of inliers is big enough consider it as a line
            val prevLineCount = lines.size
            if (bestInliers.size > RANSAC_MIN_INLIERS_FOR_LINE_SEGMENT) {
                lines.add(Pair(bestInliers.first(), bestInliers.last()))
                remainingPoints = bestRemainingPoints
            }
            // Exit if no new lines are found or we've run out of points
            if ((lines.size == prevLineCount) or (remainingPoints.size < RANSAC_MIN_INLIERS_FOR_LINE_SEGMENT + 2)) {
                break
            }
        }

        return lines
    }

    private fun getPerpendicularDistance(P1: FMatrix2, P2: FMatrix2, P0: FMatrix2): Float {
        val num = abs((P2.a2 - P1.a2) * P0.a1 - (P2.a1 - P1.a1) * P0.a2 + P2.a1 * P1.a2 - P2.a2 * P1.a1)
        val den = sqrt((P2.a2 - P1.a2).toDouble().pow(2.0) + (P2.a1 - P1.a1).toDouble().pow(2.0))
        return (num / den).toFloat()
    }

    private fun getLandmarksAtIntersection(lines: List<Pair<FMatrix2, FMatrix2>>, points: List<FMatrix2>): MutableList<FMatrix2> {
        val observedLandmarks = mutableListOf<FMatrix2>()
        for (i in 0 until lines.size - 1) {
            for (j in i + 1 until lines.size) {
                val L1 = lines[i]
                val L2 = lines[j]

                // FIXME : what happens when den ~ 0?
                val m1 = (L1.second.a2 - L1.first.a2) / (L1.second.a1 - L1.first.a1)
                val b1 = -m1 * L1.first.a1 + L1.first.a2

                val m2 = (L2.second.a2 - L2.first.a2) / (L2.second.a1 - L2.first.a1)
                val b2 = -m2 * L2.first.a1 + L2.first.a2

                val A = FMatrix2x2(-m1, 1f, -m2, 1f)
                val b = FMatrix2(b1, b2)
                if (abs(A.determinant()) < 1e-6) {
                    continue
                }
                val intersect = A.inverse() * b
                var minDist = Float.MAX_VALUE
                var minIdx = 0
                for (p in 0 until (points.size)) {
                    if (intersect.dist(points[p]) < minDist) {
                        minIdx = p
                        minDist = intersect.dist(points[p])
                    }
                }
                if (minDist < INTERSECTION_MARGIN && checkLimit(points[minIdx], L1) && checkLimit(points[minIdx], L2)) {
                    observedLandmarks.add(points[minIdx])
                }
            }
        }
        return observedLandmarks
    }

    private fun checkLimit(pt: FMatrix2, L: Pair<FMatrix2, FMatrix2>): Boolean {
        val x1 = L.first.a1
        val y1 = L.first.a2
        val x2 = L.second.a1
        val y2 = L.second.a2
        if (pt.a1 > (min(x1, x2) - INTERSECTION_MARGIN)
                && pt.a1 < (max(x1, x2) + INTERSECTION_MARGIN)
                && pt.a2 > (min(y1, y2) - INTERSECTION_MARGIN)
                && pt.a2 < (max(y1, y2) + INTERSECTION_MARGIN)) {
            return true
        }
        return false
    }
}