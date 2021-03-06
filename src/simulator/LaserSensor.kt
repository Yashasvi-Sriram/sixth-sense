package simulator

import extensions.circleXZ
import extensions.plus
import extensions.timesAssign
import org.ejml.data.FMatrix2
import processing.core.PApplet
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class LaserSensor internal constructor(private val applet: PApplet) {

    companion object {
        var DRAW_LASERS = true

        const val COUNT = 181
        const val MIN_THETA = -Math.PI.toFloat() / 2f
        const val MAX_THETA = Math.PI.toFloat() / 2f
        const val ANGULAR_RESOLUTION = (MAX_THETA - MIN_THETA) / COUNT

        const val DISTANCE_ERROR_LIMIT = 1f
        const val ANGLE_ERROR_LIMIT = 0.05f

        const val MAX_DISTANCE = 500f
        val INVALID_DISTANCE = Float.POSITIVE_INFINITY
    }

    // Multi-thread access
    private val distances: MutableList<Float> = ArrayList(COUNT)
    private val angles: MutableList<Float> = ArrayList(COUNT)
    private var timestamp = 0L

    init {
        for (i in 0 until COUNT) {
            distances.add(INVALID_DISTANCE)
            angles.add(i.toFloat())
        }
    }

    fun updateLaserScan(position: FMatrix2, orientation: Float, obstacles: List<Obstacle>) {
        val newMeasurements: MutableList<Float> = ArrayList(COUNT)
        for (i in 0 until COUNT) {
            newMeasurements.add(INVALID_DISTANCE)
        }

        // For each laser beam
        for (i in 0 until COUNT) {
            val percentage = i / (COUNT - 1f)
            val laserThErr = ThreadLocalRandom.current().nextDouble((-ANGLE_ERROR_LIMIT * ANGULAR_RESOLUTION).toDouble(), (ANGLE_ERROR_LIMIT * ANGULAR_RESOLUTION).toDouble()).toFloat()
            val theta = MIN_THETA + (MAX_THETA - MIN_THETA) * percentage + orientation + laserThErr
            angles[i] = theta
            val v = FMatrix2(cos(theta), sin(theta))

            // Check intersection for each line feature
            for (landmark in obstacles) {
                val rayDistance = landmark.shortestRayDistanceFrom(position, v)
                if (rayDistance >= 0 && rayDistance < MAX_DISTANCE) {
                    newMeasurements[i] = min(rayDistance.toDouble(), newMeasurements[i].toDouble()).toFloat()
                }
            }

            // Add some noise to new measurements
            if (newMeasurements[i] < INVALID_DISTANCE) {
                val laserMeasurementError = ThreadLocalRandom.current().nextDouble(-DISTANCE_ERROR_LIMIT.toDouble(), DISTANCE_ERROR_LIMIT.toDouble()).toFloat()
                newMeasurements[i] += laserMeasurementError
            }
        }

        // Update measurements
        synchronized(distances) {
            synchronized(timestamp) {
                for (i in newMeasurements.indices) {
                    distances[i] = newMeasurements[i]
                }
                timestamp = System.currentTimeMillis()
            }
        }
    }

    private fun getAngles(): List<Float> {
        var currentAngles: List<Float>
        synchronized(angles) { currentAngles = ArrayList(angles) }
        return currentAngles
    }

    /* User callable */
    fun getDistances(): Pair<List<Float>, Long> {
        var currentDistances: List<Float>
        var currentTimestamp: Long
        synchronized(distances) {
            synchronized(timestamp) {
                currentDistances = ArrayList(distances)
                currentTimestamp = timestamp
            }
        }
        return Pair(currentDistances, currentTimestamp)
    }

    fun draw(source: FMatrix2) {
        if (!DRAW_LASERS) {
            return
        }
        val (distances, _) = getDistances()
        val angles = getAngles()
        val laserEnds: MutableList<FMatrix2> = ArrayList(COUNT)
        for (i in 0 until COUNT) {
            if (distances[i] == INVALID_DISTANCE) {
                continue
            }
            val theta = angles[i]
            val laserBeam = FMatrix2(cos(theta), sin(theta))
            laserBeam *= distances[i]
            val laserEnd = source + laserBeam
            laserEnds.add(laserEnd)
        }
        applet.noFill()
        for (laserEnd in laserEnds) {
            applet.stroke(1f, 0f, 0f)
            applet.line(source.a1, 0f, source.a2, laserEnd.a1, 0f, laserEnd.a2)
            applet.stroke(1f, 1f, 0f)
            applet.circleXZ(laserEnd.a1, laserEnd.a2, 1f)
        }
    }

}