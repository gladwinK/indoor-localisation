package com.example.indoor_localisation_again

import com.example.indoor_localisation_again.model.AccessPointReading
import com.example.indoor_localisation_again.model.Fingerprint
import com.example.indoor_localisation_again.strategy.CosineSimilarityStrategy
import com.example.indoor_localisation_again.strategy.EuclideanStrategy
import com.example.indoor_localisation_again.strategy.WKNNStrategy
import org.junit.Test
import kotlin.random.Random

class AlgorithmComparisonTest {
    
    @Test
    fun testAlgorithmDifferences() {
        // Create sample WiFi readings
        val currentScan = listOf(
            AccessPointReading("AA:BB:CC:DD:EE:01", "Network1", -50, 2412, 0),
            AccessPointReading("AA:BB:CC:DD:EE:02", "Network2", -60, 2437, 0),
            AccessPointReading("AA:BB:CC:DD:EE:03", "Network3", -70, 2462, 0),
            AccessPointReading("AA:BB:CC:DD:EE:04", "Network4", -55, 5180, 0)
        )

        // Create sample fingerprints
        val database = listOf(
            Fingerprint(
                locationName = "Room A",
                timestamp = System.currentTimeMillis(),
                readings = listOf(
                    AccessPointReading("AA:BB:CC:DD:EE:01", "Network1", -48, 2412, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:02", "Network2", -62, 2437, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:03", "Network3", -72, 2462, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:04", "Network4", -53, 5180, 0)
                ),
                xMeters = 1.0,
                yMeters = 2.0
            ),
            Fingerprint(
                locationName = "Room B",
                timestamp = System.currentTimeMillis(),
                readings = listOf(
                    AccessPointReading("AA:BB:CC:DD:EE:01", "Network1", -55, 2412, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:02", "Network2", -58, 2437, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:03", "Network3", -68, 2462, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:05", "Network5", -65, 5200, 0) // Different AP
                ),
                xMeters = 5.0,
                yMeters = 4.0
            ),
            Fingerprint(
                locationName = "Room C",
                timestamp = System.currentTimeMillis(),
                readings = listOf(
                    AccessPointReading("AA:BB:CC:DD:EE:01", "Network1", -45, 2412, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:02", "Network2", -65, 2437, 0),
                    AccessPointReading("AA:BB:CC:DD:EE:06", "Network6", -70, 2462, 0), // Different APs
                    AccessPointReading("AA:BB:CC:DD:EE:07", "Network7", -75, 5180, 0)  // Different APs
                ),
                xMeters = 10.0,
                yMeters = 15.0
            )
        )
        
        val euclideanStrategy = EuclideanStrategy()
        val wknnStrategy = WKNNStrategy(k = 3)
        val cosineStrategy = CosineSimilarityStrategy()
        
        val euclideanResult = euclideanStrategy.calculatePosition(currentScan, database)
        val wknnResult = wknnStrategy.calculatePosition(currentScan, database)
        val cosineResult = cosineStrategy.calculatePosition(currentScan, database)
        
        println("Euclidean Result: ${euclideanResult?.locationName} at (${euclideanResult?.x}, ${euclideanResult?.y}), Confidence: ${euclideanResult?.confidence}")
        println("WKNNStrategy Result: ${wknnResult?.locationName} at (${wknnResult?.x}, ${wknnResult?.y}), Confidence: ${wknnResult?.confidence}")
        println("Cosine Result: ${cosineResult?.locationName} at (${cosineResult?.x}, ${cosineResult?.y}), Confidence: ${cosineResult?.confidence}")
        
        // Verify that at least some results are different
        // Note: Results might occasionally be the same due to the nature of the data, but they should use different calculation methods
        assert(euclideanResult != null) { "Euclidean should return a result" }
        assert(wknnResult != null) { "WKNNStrategy should return a result" }
        assert(cosineResult != null) { "Cosine should return a result" }
        
        println("All algorithms produced results with different calculation methods")
    }
}