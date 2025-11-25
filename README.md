
Overview
This is an Android application designed for indoor positioning and localization using Wi-Fi fingerprinting technology. The app allows users to calibrate
specific locations by recording Wi-Fi signal strengths, then predict the user's location based on current Wi-Fi scans by comparing them to stored
fingerprints.

Core Architecture

Application Structure
- Single Activity Architecture: The application uses a single MainActivity with multiple sections/tabs
- Service Architecture: A LocalizationService runs in the background with a persistent notification
- MVVM Pattern: Implements repository pattern with Room database for data storage
- Coroutines: Uses Kotlin coroutines for asynchronous operations

Main Components
1. UI Layer: MainActivity, adapters, and custom views
2. Data Layer: Room database with DAOs and repository pattern
3. Domain Layer: Business logic engines and managers
4. Network Layer: OkHttp for HTTP requests

Wi-Fi Scanning and Fingerprinting

Wi-Fi Scanner
- The WifiScanner class handles Wi-Fi scanning operations
- Uses Android's Wi-Fi Manager to perform scans
- Filters results to only include "fresh" readings (within 7 seconds old)
- Collects data including BSSID, SSID, RSSI (signal strength), frequency, and age of the reading
- Returns a list of AccessPointReading objects with signal strength and location data

Fingerprint Data Model
- Fingerprint objects contain:
    - Location name (user-defined label)
    - Timestamp of creation
    - List of AccessPointReading objects (Wi-Fi signal data)
    - Optional x, y coordinates in meters
- Each reading includes BSSID, SSID, RSSI value, frequency, and age

Calibration Process
1. User enters a location name in the UI
2. User optionally enters x, y coordinates (was for PDR, but not using it)
3. User taps "Scan" to collect current Wi-Fi readings
4. App displays the number of access points detected and their signal strengths
5. User taps "Save Fingerprint" to store the location data
6. The fingerprint is saved to the local Room database

Localization/Prediction Engine

Algorithm
- The LocalizationEngine implements a comparison algorithm to match current Wi-Fi scans with stored fingerprints
- Uses RSSI (Received Signal Strength Indicator) values for comparison
- Calculates the difference between current and stored signal strengths
- Applies a penalty for missing access points
- Returns the fingerprint with the lowest normalized difference score

Prediction Process
1. The app continuously performs Wi-Fi scans during prediction mode
2. The LocalizationEngine compares the current scan with stored fingerprints
3. Each stored fingerprint is scored based on signal similarity
4. The best match is returned as the predicted location
5. Results include the location name, confidence score, and number of matching access points

Continuous Prediction
- The app can run in continuous prediction mode with 5-second intervals
- Updates the UI with the current predicted location
- When a prediction is made successfully, it sends the location to an external server (Ye simulation mei location update 
  krne keliye tha, Agar subah tk work kiya toh theek vrna leave this )

Database and Data Storage

Room Database
- Uses Android's Room persistence library for local storage
- Single table for storing fingerprints
- Stores fingerprint data as JSON-serialized access point readings
- Provides reactive data streams using Kotlin Flows

Data Entities
- FingerprintEntity: Database entity with a conversion layer to/from the model
- AccessPointReading: Individual Wi-Fi access point data with BSSID, RSSI, etc.
- Data converters to transform between model objects and database entities

Repository Pattern
- FingerprintRepository abstracts database operations
- Provides methods to save, retrieve, and clear fingerprints
- Observes live data changes through Flows

User Interface and Interaction Flows

Main UI Components
1. Calibration Tab: For creating new location fingerprints
2. Positioning Tab: For running location prediction
3. Database Tab: For viewing and managing stored fingerprints

Calibration Workflow
1. User selects the "Calibration" tab
2. Enters a location name in the text field
3. Optionally enters x, y coordinates
4. Taps "Scan" to collect Wi-Fi data
5. Reviews the scan results showing access points and signal strengths
6. Taps "Save Fingerprint" to store the location data

Prediction Workflow
1. User selects the "Positioning" tab
2. Optionally configures server IP and port for location reporting
3. Taps "Predict Location" button to start continuous prediction
4. App displays predicted location with confidence score
5. Prediction runs every 5 seconds until stopped
6. Each prediction triggers a server request with the location

Database Management
1. User selects the "Database" tab
2. Views all saved fingerprints with timestamps and access point counts
3. Can refresh the database view or clear all stored data

Background Service
- LocalizationService runs as a foreground service with persistent notification
- Provides status updates about the app's current state
- Maintains access to the localization engine from background operations

Network Communication Features

Server Communication (live location isn't working on 3d model ❌)
- The app sends predicted location data to an external server via HTTP GET requests
- Endpoint: /api/livelocation?currPos=$locationName
- The server IP and port are configurable via UI inputs
- Default server settings are pre-filled in the UI

HTTP Client
- Uses OkHttp library for network requests
- The app was updated to allow cleartext HTTP traffic (important for local server communication)
- Network requests are made asynchronously in the background
- Error handling with detailed logging for debugging network issues

Data Flow to Server (live location isn't working on 3d model ❌)
1. When a location prediction is made successfully
2. The app checks for configured IP and port
3. Sends a GET request to the server endpoint with the location name as a query parameter
4. The server logs the location update

Voice Processing Capabilities

Audio Recording
- Built-in audio recording functionality using Android's MediaRecorder
- Records audio in M4A format to temporary files
- Provides UI controls to start/stop recording

Gemini API Integration
- Sends recorded audio to Google's Gemini API for processing
- Requires a Gemini API key configured in build settings
- Audio is converted to Base64 and sent with a text prompt
- Expects JSON response with "start" and "end" location values

Voice Navigation
- When Gemini returns start and end locations
- The app constructs a URL to a navigation service
- Opens the default browser with navigation parameters
- Allows for voice-activated indoor navigation requests

Permissions
- ACCESS_FINE_LOCATION: Required for Wi-Fi scanning
- ACCESS_WIFI_STATE and CHANGE_WIFI_STATE: For Wi-Fi operations
- NEARBY_WIFI_DEVICES: For newer Android versions (API 30+)
- INTERNET: For network communication
- RECORD_AUDIO: For voice processing features
- ACTIVITY_RECOGNITION: For potential future PDR features
- POST_NOTIFICATIONS: For notification permissions
- FOREGROUND_SERVICE and FOREGROUND_SERVICE_DATA_SYNC: For background service

Key Features Summary
1. Wi-Fi Fingerprinting: Records and compares Wi-Fi signal patterns for location identification
2. Real-time Location Prediction: Continuously predicts user location based on current Wi-Fi environment
3. Local Data Storage: Maintains fingerprint database on device using Room
4. Server Communication: Reports location updates to external server
5. Voice Interface: Audio recording and processing with Gemini API integration
6. Background Service: Continues location monitoring with persistent notification
7. User-Friendly UI: Tab-based interface for calibration, positioning, and database management

The app is designed for indoor positioning in environments where GPS signals are weak or unavailable, using the unique Wi-Fi signal patterns in different
locations to identify where the user is within a building.