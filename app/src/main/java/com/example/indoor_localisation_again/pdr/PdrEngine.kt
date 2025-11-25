package com.example.indoor_localisation_again.pdr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.cos
import kotlin.math.sin

class PdrEngine(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var stepLengthMeters = DEFAULT_STEP_LENGTH
    private var headingRad = 0.0
    private var position = Point2D(0.0, 0.0)
    private val ghost: MutableList<Point2D> = mutableListOf()
    private var listener: ((Point2D, List<Point2D>) -> Unit)? = null
    private var running = false

    private var correctionPerStep = Point2D(0.0, 0.0)
    private var correctionStepsRemaining = 0

    fun start() {
        if (running) return
        running = true
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
            SensorManager.SENSOR_DELAY_GAME
        )
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR),
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
    }

    fun reset() {
        position = Point2D(0.0, 0.0)
        ghost.clear()
        correctionPerStep = Point2D(0.0, 0.0)
        correctionStepsRemaining = 0
        notifyListener()
    }

    fun setListener(block: ((Point2D, List<Point2D>) -> Unit)?) {
        listener = block
    }

    fun setStepLength(lengthMeters: Double) {
        if (lengthMeters > 0) {
            stepLengthMeters = lengthMeters
        }
    }

    fun applyAnchor(anchor: Point2D, smoothingSteps: Int = CORRECTION_STEPS) {
        if (smoothingSteps <= 0) return
        val delta = anchor - position
        correctionPerStep = Point2D(
            delta.x / smoothingSteps,
            delta.y / smoothingSteps
        )
        correctionStepsRemaining = smoothingSteps
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> updateHeading(event.values)
            Sensor.TYPE_STEP_DETECTOR -> handleStep()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleStep() {
        val dx = stepLengthMeters * cos(headingRad)
        val dy = stepLengthMeters * sin(headingRad)
        var correction = Point2D(0.0, 0.0)
        if (correctionStepsRemaining > 0) {
            correction = correctionPerStep
            correctionStepsRemaining--
        }
        position = position + Point2D(dx, dy) + correction
        ghost.add(position)
        if (ghost.size > MAX_GHOST_POINTS) {
            ghost.removeAt(0)
        }
        notifyListener()
    }

    private fun updateHeading(values: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        // orientation[0] is azimuth in radians
        headingRad = orientation[0].toDouble()
    }

    private fun notifyListener() {
        listener?.invoke(position, ghost.toList())
    }

    companion object {
        private const val MAX_GHOST_POINTS = 20
        private const val CORRECTION_STEPS = 6
        private const val DEFAULT_STEP_LENGTH = 0.7
    }
}
