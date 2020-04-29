package simulator

import extensions.circleXZ
import extensions.plus
import extensions.timesAssign
import org.ejml.data.DMatrix2
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
        const val MIN_THETA = -Math.PI / 2
        const val MAX_THETA = Math.PI / 2
        const val ANGULAR_RESOLUTION = (MAX_THETA - MIN_THETA) / COUNT
        const val MAX_DISTANCE = 500.0
        const val DISTANCE_ERROR_LIMIT = 5.0
        const val ANGLE_ERROR_LIMIT = 0.05
        const val INVALID_MEASUREMENT = MAX_DISTANCE + 1
    }

    // Multi-thread access
    private val measurements: MutableList<Double> = ArrayList(COUNT)

    init {
        for (i in 0 until COUNT) {
            measurements.add(INVALID_MEASUREMENT)
        }
    }

    fun updateLaserScan(position: DMatrix2, orientation: Double, obstacles: List<Obstacle>) {
        val newMeasurements: MutableList<Double> = ArrayList(COUNT)
        for (i in 0 until COUNT) {
            newMeasurements.add(INVALID_MEASUREMENT)
        }

        // For each laser beam
        for (i in 0 until COUNT) {
            val percentage = i / (COUNT - 1.0)
            val laserThErr = ThreadLocalRandom.current().nextDouble(-ANGLE_ERROR_LIMIT * ANGULAR_RESOLUTION, ANGLE_ERROR_LIMIT * ANGULAR_RESOLUTION)
            val theta = MIN_THETA + (MAX_THETA - MIN_THETA) * percentage + orientation + laserThErr
            val v = DMatrix2(cos(theta), sin(theta))

            // Check intersection for each line feature
            for (landmark in obstacles) {
                val rayDistance = landmark.shortestRayDistanceFrom(position, v)
                if (rayDistance >= 0 && rayDistance < MAX_DISTANCE) {
                    newMeasurements[i] = min(rayDistance, newMeasurements[i])
                }
            }

            // Add some noise to new measurements
            if (newMeasurements[i] < INVALID_MEASUREMENT) {
                val laserMeasurementError = ThreadLocalRandom.current().nextDouble(-DISTANCE_ERROR_LIMIT, DISTANCE_ERROR_LIMIT)
                newMeasurements[i] += laserMeasurementError
            }
        }

        // Update measurements
        synchronized(measurements) {
            for (i in newMeasurements.indices) {
                measurements[i] = newMeasurements[i]
            }
        }
    }

    /* User callable */
    fun getMeasurements(): List<Double> {
        var currentMeasurements: List<Double>
        synchronized(measurements) { currentMeasurements = ArrayList(measurements) }
        return currentMeasurements
    }

    fun draw(position: DMatrix2, orientation: Double) {
        if (!DRAW_LASERS) {
            return
        }
        val distances = getMeasurements()
        val lasersEnds: MutableList<DMatrix2> = ArrayList(COUNT)
        for (i in distances.indices) {
            if (distances[i] == INVALID_MEASUREMENT) {
                continue
            }
            val percentage = i / (COUNT - 1.0)
            // FIXME : Save and use noisy angles here, as everything else drawn is the noisy version or the one that happened really
            val theta = MIN_THETA + (MAX_THETA - MIN_THETA) * percentage
            val laserBeam = DMatrix2(cos(orientation + theta),
                    sin(orientation + theta))
            laserBeam *= distances[i]
            val laserEnd = position + laserBeam
            lasersEnds.add(laserEnd)
        }
        applet.noFill()
        for (laserEnd in lasersEnds) {
            applet.stroke(1f, 0f, 0f)
            applet.line(position.a1.toFloat(), 0f, position.a2.toFloat(), laserEnd.a1.toFloat(), 0f, laserEnd.a2.toFloat())
            applet.stroke(1f, 1f, 0f)
            applet.circleXZ(laserEnd.a1, laserEnd.a2, 1.0)
        }
    }

}