package com.example.indoor_localisation_again.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.indoor_localisation_again.R
import com.example.indoor_localisation_again.data.FingerprintRepository
import com.example.indoor_localisation_again.engine.LocalizationEngine
import com.example.indoor_localisation_again.model.AccessPointReading
import com.example.indoor_localisation_again.service.LocalizationService
import com.example.indoor_localisation_again.wifi.WifiScanner
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var wifiScanner: WifiScanner
    private lateinit var repository: FingerprintRepository
    private val adapter = FingerprintAdapter()

    private var currentReadings: List<AccessPointReading> = emptyList()
    private var pendingAction: (() -> Unit)? = null
    private var engine: LocalizationEngine? = null
    private var localizationService: LocalizationService? = null
    private var serviceBound = false
    private var continuousPredictionJob: Job? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            val allGranted = requiredPermissions().all { perm ->
                grantResults[perm] == true || hasPermission(perm)
            }
            if (allGranted) {
                pendingAction?.invoke()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_rationale),
                    Toast.LENGTH_SHORT
                ).show()
            }
            pendingAction = null
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? LocalizationService.LocalizationBinder ?: return
            localizationService = localBinder.service()
            engine = localizationService?.getEngine()
            localizationService?.updateStatus(getString(R.string.notification_running))
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            localizationService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = FingerprintRepository.create(this)
        wifiScanner = WifiScanner(this)
        engine = LocalizationEngine(repository)

        startAndBindService()
        requestNotificationPermissionIfNeeded()
        bindUi()
        observeDatabase()
    }

    private fun bindUi() {
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.modeToggleGroup)
        val calibrationTab = findViewById<MaterialButton>(R.id.calibrationTab)
        val positioningTab = findViewById<MaterialButton>(R.id.positioningTab)
        val databaseTab = findViewById<MaterialButton>(R.id.databaseTab)
        calibrationTab.isChecked = true

        val calibrationSection = findViewById<View>(R.id.calibrationSection)
        val positioningSection = findViewById<View>(R.id.positioningSection)
        val databaseSection = findViewById<View>(R.id.databaseSection)

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            calibrationSection.visibility = if (checkedId == R.id.calibrationTab) View.VISIBLE else View.GONE
            positioningSection.visibility = if (checkedId == R.id.positioningTab) View.VISIBLE else View.GONE
            databaseSection.visibility = if (checkedId == R.id.databaseTab) View.VISIBLE else View.GONE
        }

        findViewById<Button>(R.id.scanButton).setOnClickListener { collectScan() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveFingerprint() }
        findViewById<Button>(R.id.predictButton).setOnClickListener { toggleContinuousPrediction() }
        findViewById<Button>(R.id.refreshDbButton).setOnClickListener { refreshDatabaseList() }
        findViewById<Button>(R.id.clearDbButton).setOnClickListener { clearDatabase() }

        val list = findViewById<RecyclerView>(R.id.fingerprintList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        updateCurrentScanDetails(emptyList())
        updatePredictButtonState(false)
    }

    private fun observeDatabase() {
        lifecycleScope.launch {
            repository.fingerprints.collect { fingerprints ->
                adapter.submit(fingerprints)
                if (fingerprints.isEmpty()) {
                    findViewById<TextView>(R.id.predictionDetails).text =
                        getString(R.string.label_no_fingerprints)
                }
            }
        }
    }

    private fun refreshDatabaseList() {
        lifecycleScope.launch {
            val snapshot = repository.fingerprints.first()
            adapter.submit(snapshot)
            Toast.makeText(
                this@MainActivity,
                "Database refreshed (${snapshot.size} rows)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun collectScan() {
        withPermissions {
            lifecycleScope.launch {
                setFreshnessText("Scanning Wi-Fi...")
                localizationService?.updateStatus("Scanning Wi-Fi...")
                val results = wifiScanner.scanFreshReadings()
                currentReadings = results
                if (results.isEmpty()) {
                    setFreshnessText("No fresh scan available. Try again in a few seconds.")
                    updateCurrentScanDetails(emptyList())
                } else {
                    val newest = results.minOf { it.ageMs }
                    setFreshnessText("Fresh scan: ${results.size} APs (newest ${newest}ms old)")
                    updateCurrentScanDetails(results)
                    localizationService?.updateStatus("Last scan: ${results.size} APs")
                }
            }
        }
    }

    private fun saveFingerprint() {
        val locationInput = findViewById<TextInputEditText>(R.id.locationInput)
        val label = locationInput.text?.toString().orEmpty()
        if (label.isBlank()) {
            Toast.makeText(this, "Location label required", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentReadings.isEmpty()) {
            Toast.makeText(this, "Run a scan first", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            engine?.saveFingerprint(label, currentReadings)
            Toast.makeText(this@MainActivity, "Fingerprint saved", Toast.LENGTH_SHORT).show()
            localizationService?.updateStatus("Saved fingerprint for $label")
        }
    }

    private fun toggleContinuousPrediction() {
        if (continuousPredictionJob != null) {
            stopContinuousPrediction()
            return
        }
        withPermissions {
            startContinuousPrediction()
        }
    }

    private fun startContinuousPrediction() {
        stopContinuousPrediction()
        updatePredictButtonState(true)
        continuousPredictionJob = lifecycleScope.launch {
            while (isActive) {
                runPredictionOnce()
                delay(PREDICTION_INTERVAL_MS)
            }
        }
    }

    private fun stopContinuousPrediction() {
        continuousPredictionJob?.cancel()
        continuousPredictionJob = null
        updatePredictButtonState(false)
        localizationService?.updateStatus("Continuous prediction stopped")
    }

    private suspend fun runPredictionOnce() {
        findViewById<TextView>(R.id.predictionLabel).text = "Predicting..."
        localizationService?.updateStatus("Predicting location...")
        val results = wifiScanner.scanFreshReadings()
        currentReadings = results
        if (results.isEmpty()) {
            findViewById<TextView>(R.id.predictionLabel).text = getString(R.string.label_prediction_unknown)
            findViewById<TextView>(R.id.predictionDetails).text = "No fresh Wi-Fi scan available."
            return
        }

        val prediction = engine?.predict(results)
        if (prediction == null) {
            findViewById<TextView>(R.id.predictionLabel).text = getString(R.string.label_prediction_unknown)
            findViewById<TextView>(R.id.predictionDetails).text =
                "No matching fingerprints. Add calibration data first."
            localizationService?.updateStatus("No matching fingerprints yet")
        } else {
            findViewById<TextView>(R.id.predictionLabel).text =
                "Prediction: ${prediction.locationName}"
            findViewById<TextView>(R.id.predictionDetails).text =
                "Score ${"%.1f".format(Locale.getDefault(), prediction.score)} with ${prediction.matchedCount} matching APs."
            localizationService?.updateStatus("At ${prediction.locationName}")
        }
        updateCurrentScanDetails(results)
    }

    private fun clearDatabase() {
        lifecycleScope.launch {
            repository.clear()
            Toast.makeText(this@MainActivity, "Database cleared", Toast.LENGTH_SHORT).show()
            localizationService?.updateStatus("Database cleared")
        }
    }

    private fun updateCurrentScanDetails(results: List<AccessPointReading>) {
        val detailView = findViewById<TextView>(R.id.currentScanDetails)
        if (results.isEmpty()) {
            detailView.text = "No scan yet."
            return
        }
        val top = results
            .sortedBy { it.ageMs }
            .take(6)
            .joinToString(separator = "\n") {
                "${it.bssid} | ${it.rssi} dBm | ${it.ageMs}ms old"
            }
        detailView.text = top
    }

    private fun setFreshnessText(text: String) {
        findViewById<TextView>(R.id.freshnessLabel).text = text
    }

    private fun withPermissions(onGranted: () -> Unit) {
        val missing = requiredPermissions().filterNot { hasPermission(it) }
        if (missing.isEmpty()) {
            onGranted()
        } else {
            pendingAction = onGranted
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun startAndBindService() {
        val intent = Intent(this, LocalizationService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopContinuousPrediction()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
    }

    private fun updatePredictButtonState(running: Boolean) {
        val button = findViewById<Button>(R.id.predictButton)
        button.text = if (running) {
            getString(R.string.button_stop_prediction)
        } else {
            getString(R.string.button_predict_location)
        }
    }

    companion object {
        private const val PREDICTION_INTERVAL_MS = 5_000L
    }
}
