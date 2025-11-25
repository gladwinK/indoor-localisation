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
import com.example.indoor_localisation_again.pdr.PdrEngine
import com.example.indoor_localisation_again.pdr.Point2D
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
import kotlinx.coroutines.withContext
import java.util.Locale
import android.media.MediaRecorder
import java.io.File
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.net.Uri
import com.example.indoor_localisation_again.BuildConfig
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers

class MainActivity : AppCompatActivity() {
    private lateinit var wifiScanner: WifiScanner
    private lateinit var repository: FingerprintRepository
    private lateinit var pdrEngine: PdrEngine
    private val adapter = FingerprintAdapter()

    private var currentReadings: List<AccessPointReading> = emptyList()
    private var pendingAction: (() -> Unit)? = null
    private var engine: LocalizationEngine? = null
    private var localizationService: LocalizationService? = null
    private var serviceBound = false
    private var continuousPredictionJob: Job? = null
    private var anchors: List<Point2D> = emptyList()
    private var pdrRunning = false
    private var currentPdrPosition: Point2D? = null
    private var currentGhostPath: List<Point2D> = emptyList()
    private var lastAnchor: Point2D? = null
    private var currentAnchor: Point2D? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private val httpClient = OkHttpClient()
    private val baseUrlForBrowser = "http://10.60.12.145:8081"
    private var isRecording = false

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

    private val floorplanPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { }

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
        pdrEngine = PdrEngine(this)
        pdrEngine.setListener { pos, ghost ->
            runOnUiThread {
                updatePdrUi(pos, ghost)
            }
        }

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
        // PDR controls temporarily disabled
        // findViewById<Button>(R.id.startPdrButton).setOnClickListener { togglePdr() }
        // findViewById<Button>(R.id.resetPdrButton).setOnClickListener { resetPdr() }
        findViewById<Button>(R.id.recordButton).setOnClickListener { toggleRecording() }
        findViewById<Button>(R.id.sendVoiceButton).setOnClickListener { sendVoiceToGemini() }

        val list = findViewById<RecyclerView>(R.id.fingerprintList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        // PDR map interactions temporarily disabled
        // findViewById<PdrMapView>(R.id.pdrMap).setOnMapTapListener { point ->
        //     findViewById<TextInputEditText>(R.id.xInput).setText(
        //         "%.2f".format(Locale.getDefault(), point.x)
        //     )
        //     findViewById<TextInputEditText>(R.id.yInput).setText(
        //         "%.2f".format(Locale.getDefault(), point.y)
        //     )
        //     Toast.makeText(this, "Coordinates filled from tap", Toast.LENGTH_SHORT).show()
        // }

        updateCurrentScanDetails(emptyList())
        updatePredictButtonState(false)
        // updatePdrUi(null, emptyList())
        loadSavedFloorplan()

        findViewById<Button>(R.id.toggleSettingsButton).setOnClickListener {
            val container = findViewById<View>(R.id.ipConfigContainer)
            val btn = findViewById<Button>(R.id.toggleSettingsButton)
            if (container.visibility == View.VISIBLE) {
                container.visibility = View.GONE
                btn.text = "Show Settings"
            } else {
                container.visibility = View.VISIBLE
                btn.text = "Hide Settings"
            }
        }
    }

    private fun observeDatabase() {
        lifecycleScope.launch {
            repository.fingerprints.collect { fingerprints ->
                adapter.submit(fingerprints)
                anchors = fingerprints.filter { it.xMeters != null && it.yMeters != null }
                    .map { Point2D(it.xMeters!!, it.yMeters!!) }
                updatePdrUi(currentPdrPosition, currentGhostPath)
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
        val xInput = findViewById<TextInputEditText>(R.id.xInput)
        val yInput = findViewById<TextInputEditText>(R.id.yInput)
        val label = locationInput.text?.toString().orEmpty()
        val x = xInput.text?.toString()?.toDoubleOrNull()
        val y = yInput.text?.toString()?.toDoubleOrNull()
        if (label.isBlank()) {
            Toast.makeText(this, "Location label required", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentReadings.isEmpty()) {
            Toast.makeText(this, "Run a scan first", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            engine?.saveFingerprint(label, currentReadings, x, y)
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
            val anchorFingerprint = repository.getFingerprint(prediction.fingerprintId)
            findViewById<TextView>(R.id.predictionLabel).text =
                "Prediction: ${prediction.locationName}"
            findViewById<TextView>(R.id.predictionDetails).text =
                "Score ${"%.1f".format(Locale.getDefault(), prediction.score)} with ${prediction.matchedCount} matching APs."
            localizationService?.updateStatus("At ${prediction.locationName}")
            val anchor = anchorFingerprint?.let {
                if (it.xMeters != null && it.yMeters != null) {
                    Point2D(it.xMeters, it.yMeters)
                } else null
            }
            anchor?.let {
                lastAnchor = currentAnchor
                currentAnchor = it
                pdrEngine.applyAnchor(it)
            }
            sendLiveLocation(prediction.locationName)
        }
        updateCurrentScanDetails(results)
    }

    private fun sendLiveLocation(locationName: String) {
        val ipInput = findViewById<TextInputEditText>(R.id.ipInput)
        val portInput = findViewById<TextInputEditText>(R.id.portInput)
        val ip = ipInput.text?.toString()?.trim() ?: ""
        val port = portInput.text?.toString()?.trim() ?: ""

        if (ip.isEmpty() || port.isEmpty()) {
            // Log that IP or port is empty
            println("sendLiveLocation: IP or port is empty, skipping request")
            return
        }

        val url = "http://$ip:$port/api/livelocation?currPos=$locationName"
        println("sendLiveLocation: Sending request to URL: $url")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    println("sendLiveLocation: Request successful, response code: ${response.code}")
                    response.close()
                } else {
                    println("sendLiveLocation: Request failed, response code: ${response.code}")
                    println("sendLiveLocation: Response message: ${response.message}")
                    response.close()
                }
            } catch (e: Exception) {
                println("sendLiveLocation: Error sending request - ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun clearDatabase() {
        lifecycleScope.launch {
            repository.clear()
            Toast.makeText(this@MainActivity, "Database cleared", Toast.LENGTH_SHORT).show()
            localizationService?.updateStatus("Database cleared")
        }
    }

    private fun togglePdr() {
        // PDR UI disabled
    }

    private fun resetPdr() {
        // PDR UI disabled
    }

    private fun updatePdrUi(position: Point2D?, path: List<Point2D>) {
        currentPdrPosition = position
        currentGhostPath = path
        // PDR map/status updates are currently disabled
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
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
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
        pdrEngine.stop()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
    }

    private fun pickFloorplan() {}

    private fun loadSavedFloorplan() {}

    private fun handleFloorplanUri(uri: android.net.Uri) {}

    private fun updatePredictButtonState(running: Boolean) {
        val button = findViewById<Button>(R.id.predictButton)
        button.text = if (running) {
            getString(R.string.button_stop_prediction)
        } else {
            getString(R.string.button_predict_location)
        }
    }

    private fun toggleRecording() {
        withPermissions {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        try {
            audioFile = File.createTempFile("voice_cmd", ".m4a", cacheDir)
            val recorder = MediaRecorder()
            mediaRecorder = recorder
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(audioFile?.absolutePath)
            recorder.prepare()
            recorder.start()
            isRecording = true
            findViewById<Button>(R.id.recordButton).text = "Stop recording"
            findViewById<TextView>(R.id.voiceStatus).text = "Recording..."
        } catch (e: Exception) {
            isRecording = false
            findViewById<TextView>(R.id.voiceStatus).text = "Record failed: ${e.message}"
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (_: Exception) {
        }
        mediaRecorder = null
        isRecording = false
        findViewById<Button>(R.id.recordButton).text = "Start recording"
        findViewById<TextView>(R.id.voiceStatus).text = "Recording stopped"
    }

    private fun sendVoiceToGemini() {
        val file = audioFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "No recording available", Toast.LENGTH_SHORT).show()
            return
        }
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Gemini API key missing", Toast.LENGTH_SHORT).show()
            return
        }

        val ipInput = findViewById<TextInputEditText>(R.id.ipInput)
        val portInput = findViewById<TextInputEditText>(R.id.portInput)
        val ip = ipInput.text?.toString()?.trim() ?: ""
        val port = portInput.text?.toString()?.trim() ?: ""

        if (ip.isEmpty() || port.isEmpty()) {
            Toast.makeText(this, "Please enter IP and Port", Toast.LENGTH_SHORT).show()
            return
        }
        val baseUrl = "http://$ip:$port"

        lifecycleScope.launch {
            findViewById<TextView>(R.id.voiceStatus).text = "Sending to Gemini..."
            val result = withContext(Dispatchers.IO) { callGemini(file, apiKey) }
            findViewById<TextView>(R.id.voiceStatus).text = result.status
            findViewById<TextView>(R.id.voiceResult).text = result.raw
            val startVal = result.start?.takeIf { it.isNotBlank() }
            val endVal = result.end?.takeIf { it.isNotBlank() }
            if (startVal != null && endVal != null) {
                val launched = openBrowser(baseUrl, startVal, endVal)
                if (!launched) {
                    Toast.makeText(this@MainActivity, "Could not open browser", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@MainActivity, "Missing start/end from Gemini", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class GeminiResult(
        val start: String?,
        val end: String?,
        val status: String,
        val raw: String
    )

    private fun callGemini(audio: File, apiKey: String): GeminiResult {
        return try {
            val bytes = audio.readBytes()
            val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val mimeType = "audio/m4a"
            val prompt =
                "Extract source and destination room numbers from this audio. Return ONLY JSON format: {\"start\": \"number\", \"end\": \"number\"}. If unclear, return null."
            val payload = """
                {
                  "contents": [{
                    "parts": [
                      { "text": ${JSONObject.quote(prompt)} },
                      { "inlineData": { "mimeType": "$mimeType", "data": "$base64Audio" } }
                    ]
                  }]
                }
            """.trimIndent()
            val mediaType = "application/json".toMediaType()
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(payload.toRequestBody(mediaType))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return GeminiResult(null, null, "Gemini error ${resp.code}", body)
                }
                val parsed = JSONObject(body)
                val text = parsed
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                val (start, end) = parseStartEnd(text.ifBlank { body })
                GeminiResult(start, end, "Gemini OK", text.ifBlank { body })
            }
        } catch (e: Exception) {
            GeminiResult(null, null, "Gemini call failed: ${e.message}", e.toString())
        }
    }

    private fun parseStartEnd(raw: String): Pair<String?, String?> {
        if (raw.isBlank()) return null to null
        val cleaned = raw
            .replace("```json", "")
            .replace("```", "")
            .trim()
        try {
            val obj = JSONObject(cleaned)
            val s = obj.optString("start", "").trim()
            val e = obj.optString("end", "").trim()
            if (s.isNotEmpty() && e.isNotEmpty()) return s to e
        } catch (_: Exception) {
        }
        // fallback regex
        val startRegex = "\"start\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val endRegex = "\"end\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val s = startRegex.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
        val e = endRegex.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
        return s to e
    }

    private fun openBrowser(baseUrl: String, start: String, end: String): Boolean {
        return try {
            val uri = Uri.parse("$baseUrl/navigate?source=$start&destination=$end")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val PREDICTION_INTERVAL_MS = 5_000L
        private const val PREFS = "pdr_prefs"
        private const val KEY_FLOORPLAN_URI = "floorplan_uri"
    }
}
