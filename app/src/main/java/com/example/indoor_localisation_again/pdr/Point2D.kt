package com.example.indoor_localisation_again.pdr

data class Point2D(
    val x: Double,
    val y: Double
) {
    operator fun plus(other: Point2D) = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D) = Point2D(x - other.x, y - other.y)
    operator fun times(scalar: Double) = Point2D(x * scalar, y * scalar)
}
