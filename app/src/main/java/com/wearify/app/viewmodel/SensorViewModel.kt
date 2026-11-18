package com.wearify.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearify.app.ble.BleRepository
import com.wearify.app.ble.PolarH10Repository
import com.wearify.app.data.publicDeviceName
import com.wearify.app.data.BleConnectionState
import com.wearify.app.data.AvatarAppearance
import com.wearify.app.data.AvatarGenderPresentation
import com.wearify.app.data.AvatarHairColor
import com.wearify.app.data.CONTACT_MIC_SAMPLE_RATE_HZ
import com.wearify.app.data.ContactMicAnalysis
import com.wearify.app.data.ContactMicAnalyzer
import com.wearify.app.data.ContactMicFrame
import com.wearify.app.data.ContactMicPoint
import com.wearify.app.data.DataCollectionActivity
import com.wearify.app.data.DEFAULT_DEVICE_NAME
import com.wearify.app.data.DEFAULT_LIVE_DISPLAY_METRICS
import com.wearify.app.data.LiveDisplayMetric
import com.wearify.app.data.Modality
import com.wearify.app.data.PairingMode
import com.wearify.app.data.PpgAnalysis
import com.wearify.app.data.PpgAnalyzer
import com.wearify.app.data.PolarAccSample
import com.wearify.app.data.PolarCaptureEvent
import com.wearify.app.data.PolarEcgSample
import com.wearify.app.data.PolarH10ConnectionState
import com.wearify.app.data.PolarH10CsvLogger
import com.wearify.app.data.PolarLogStatus
import com.wearify.app.data.RollingContactMicBuffer
import com.wearify.app.data.RollingPolarAccBuffer
import com.wearify.app.data.RollingPolarEcgBuffer
import com.wearify.app.data.RollingSampleBuffer
import com.wearify.app.data.ScannedBleDevice
import com.wearify.app.data.SensorCsvLogger
import com.wearify.app.data.SensorSample
import com.wearify.app.data.WearifyUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

private const val CONTACT_MIC_ANALYSIS_INTERVAL_MS = 200L
private const val PPG_ANALYSIS_INTERVAL_MS = 200L
private const val LIVE_DATA_STALE_MS = 2_500L
private const val LIVE_DATA_STALE_CHECK_MS = 1_000L
private const val AUTO_RECONNECT_DELAY_MS = 1_000L
private const val DEMO_FRAME_PERIOD_MS = 100L
private const val DEMO_SENSOR_SAMPLE_RATE_HZ = 50
private const val DEMO_SENSOR_SAMPLES_PER_FRAME =
    DEMO_SENSOR_SAMPLE_RATE_HZ * DEMO_FRAME_PERIOD_MS.toInt() / 1_000
private const val SETTINGS_PREFS = "wearify_settings"
private const val KEY_TARGET_DEVICE_NAME = "target_device_name"
private const val KEY_ENABLED_LIVE_METRICS = "enabled_live_metrics"
private const val KEY_DATA_COLLECTION_ACTIVITY = "data_collection_activity"
private const val KEY_AVATAR_GENDER = "avatar_gender"
private const val KEY_AVATAR_SKIN_TONE = "avatar_skin_tone"
private const val KEY_AVATAR_HAIR_COLOR = "avatar_hair_color"

class SensorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BleRepository(application)
    private val polarRepository = PolarH10Repository(application)
    private val sensorCsvLogger = SensorCsvLogger(application)
    private val polarCsvLogger = PolarH10CsvLogger(application)
    private val settingsPrefs = application.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val rollingBuffer = RollingSampleBuffer()
    private val contactMicBuffer = RollingContactMicBuffer()
    private val polarEcgBuffer = RollingPolarEcgBuffer()
    private val polarAccBuffer = RollingPolarAccBuffer()
    private val samples = MutableStateFlow<List<SensorSample>>(emptyList())
    private val contactMicPoints = MutableStateFlow<List<ContactMicPoint>>(emptyList())
    private val contactMicAnalysis = MutableStateFlow(ContactMicAnalysis())
    private val ppgAnalysis = MutableStateFlow(PpgAnalysis())
    private val polarEcgSamples = MutableStateFlow<List<PolarEcgSample>>(emptyList())
    private val polarAccSamples = MutableStateFlow<List<PolarAccSample>>(emptyList())
    private val selectedModality = MutableStateFlow(Modality.Accelerometer)
    private val pairingMode = MutableStateFlow(PairingMode.Target)
    private val targetDeviceName = MutableStateFlow(loadTargetDeviceName())
    private val dataCollectionActivity = MutableStateFlow(loadDataCollectionActivity())
    private val avatarAppearance = MutableStateFlow(loadAvatarAppearance())
    private val enabledLiveMetrics = MutableStateFlow(loadEnabledLiveMetrics())
    private val demoConnectionState = MutableStateFlow<BleConnectionState?>(null)
    private var demoJob: Job? = null
    private var autoConnectJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectEnabled = false
    private var explicitDisconnect = false
    private var lastLiveFrameReceivedAtMs: Long? = null
    private var lastContactMicAnalysisMs = 0L
    private var lastPpgAnalysisMs = 0L

    private val effectiveConnectionState = combine(
        repository.connectionState,
        demoConnectionState,
    ) { repositoryState, demoState ->
        demoState ?: repositoryState
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BleConnectionState.Idle,
    )

    private val modalityAndPairingMode = combine(
        selectedModality,
        pairingMode,
        targetDeviceName,
        enabledLiveMetrics,
        combine(
            dataCollectionActivity,
            avatarAppearance,
        ) { activity, appearance ->
            DataCollectionSelections(activity, appearance)
        },
    ) { modality, mode, targetName, liveMetrics, collectionLabels ->
        UiSelections(
            modality = modality,
            pairingMode = mode,
            targetDeviceName = targetName,
            dataCollectionActivity = collectionLabels.activity,
            avatarAppearance = collectionLabels.avatarAppearance,
            enabledLiveMetrics = liveMetrics,
        )
    }

    private val sensorUiState: StateFlow<WearifyUiState> = combine(
        effectiveConnectionState,
        repository.devices,
        samples,
        modalityAndPairingMode,
        repository.malformedLineCount,
    ) { connection, devices, sampleWindow, selections, malformed ->
        WearifyUiState(
            connectionState = connection,
            devices = devices,
            samples = sampleWindow,
            selectedModality = selections.modality,
            pairingMode = selections.pairingMode,
            targetDeviceName = selections.targetDeviceName,
            dataCollectionActivity = selections.dataCollectionActivity,
            avatarAppearance = selections.avatarAppearance,
            enabledLiveMetrics = selections.enabledLiveMetrics,
            malformedLineCount = malformed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WearifyUiState(),
    )

    private val baseUiState: StateFlow<WearifyUiState> = combine(
        sensorUiState,
        contactMicPoints,
        contactMicAnalysis,
        ppgAnalysis,
    ) { state, micPoints, micAnalysis, ppg ->
        state.copy(
            contactMicPoints = micPoints,
            contactMicAnalysis = micAnalysis,
            ppgAnalysis = ppg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WearifyUiState(),
    )

    private val polarUiState = combine(
        polarRepository.state,
        polarEcgSamples,
        polarAccSamples,
        polarCsvLogger.status,
    ) { connection, ecg, acc, logStatus ->
        PolarUiState(connection, ecg, acc, logStatus)
    }

    val uiState: StateFlow<WearifyUiState> = combine(
        baseUiState,
        repository.scanDebugStatus,
        sensorCsvLogger.status,
        polarUiState,
    ) { state, scanDebug, logStatus, polar ->
        state.copy(
            scanDebugStatus = scanDebug,
            dataLogStatus = logStatus,
            polarConnectionState = polar.connectionState,
            polarEcgSamples = polar.ecgSamples,
            polarAccSamples = polar.accSamples,
            polarLogStatus = polar.logStatus,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WearifyUiState(),
    )


    init {
        viewModelScope.launch {
            repository.samples.collect { sample ->
                markLiveFrameReceived(sample.receivedAtMs)
                val updatedSamples = rollingBuffer.append(sample)
                samples.value = updatedSamples
                updatePpgState(updatedSamples, sample.timestampMs)
            }
        }
        viewModelScope.launch {
            repository.contactMicFrames.collect { frame ->
                markLiveFrameReceived(frame.receivedAtMs)
                updateContactMicState(frame)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.contactMicFrames.collect { frame ->
                sensorCsvLogger.appendContactMicFrame(frame)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.samples.collect { sample ->
                sensorCsvLogger.appendSample(sample)
            }
        }
        viewModelScope.launch {
            polarRepository.ecgFrames.collect { frame ->
                polarEcgSamples.value = polarEcgBuffer.append(frame)
            }
        }
        viewModelScope.launch {
            polarRepository.accFrames.collect { frame ->
                polarAccSamples.value = polarAccBuffer.append(frame)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            polarRepository.ecgFrames.collect(polarCsvLogger::appendEcgFrame)
        }
        viewModelScope.launch(Dispatchers.IO) {
            polarRepository.accFrames.collect(polarCsvLogger::appendAccFrame)
        }
        viewModelScope.launch(Dispatchers.IO) {
            polarRepository.heartRate.collect(polarCsvLogger::appendHeartRate)
        }
        viewModelScope.launch(Dispatchers.IO) {
            polarRepository.packets.collect(polarCsvLogger::appendPacket)
        }
        viewModelScope.launch(Dispatchers.IO) {
            polarRepository.events.collect(polarCsvLogger::appendEvent)
        }
        viewModelScope.launch(Dispatchers.IO) {
            polarRepository.state.collect { state ->
                when (state) {
                    is PolarH10ConnectionState.Error -> {
                        polarCsvLogger.appendEvent(
                            PolarCaptureEvent(System.currentTimeMillis(), "capture_error", state.message),
                        )
                        polarCsvLogger.stopSession()
                    }
                    is PolarH10ConnectionState.Disconnected -> {
                        polarCsvLogger.appendEvent(
                            PolarCaptureEvent(
                                System.currentTimeMillis(),
                                "capture_closed",
                                state.reason.orEmpty(),
                            ),
                        )
                        polarCsvLogger.stopSession()
                    }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            effectiveConnectionState.collect { state ->
                if (state is BleConnectionState.Disconnected || state is BleConnectionState.Error) {
                    sensorCsvLogger.stopSession()
                    scheduleAutoReconnect()
                } else if (state is BleConnectionState.Connected) {
                    explicitDisconnect = false
                    reconnectJob?.cancel()
                    reconnectJob = null
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(LIVE_DATA_STALE_CHECK_MS)
                clearStaleLiveDataIfNeeded()
            }
        }
    }

    fun startScan() {
        stopAutoConnect()
        stopAutoReconnect()
        stopDemo(clearState = true)
        reconnectEnabled = false
        explicitDisconnect = false
        if (effectiveConnectionState.value is BleConnectionState.Connected) {
            pairingMode.value = PairingMode.Target
            return
        }
        pairingMode.value = PairingMode.Manual
        repository.startScan(targetDeviceName = targetDeviceName.value.trim().ifBlank { DEFAULT_DEVICE_NAME })
    }

    fun stopScan() {
        stopAutoConnect()
        stopAutoReconnect()
        reconnectEnabled = false
        pairingMode.value = PairingMode.Target
        repository.stopScan()
    }

    fun startAutoConnect() {
        reconnectEnabled = true
        explicitDisconnect = false
        beginTargetAutoConnect(clearDemo = true)
    }

    private fun beginTargetAutoConnect(clearDemo: Boolean) {
        stopAutoConnect()
        if (clearDemo) {
            stopDemo(clearState = true)
        }
        if (effectiveConnectionState.value is BleConnectionState.Connected) {
            pairingMode.value = PairingMode.Target
            return
        }
        pairingMode.value = PairingMode.Target
        val targetName = targetDeviceName.value.trim().ifBlank { DEFAULT_DEVICE_NAME }
        repository.startScan(targetOnly = true, targetDeviceName = targetName)
        autoConnectJob = viewModelScope.launch {
            repository.devices.collect { devices ->
                val target = devices.firstOrNull { device ->
                    device.matchesTargetName(targetName)
                }
                if (target != null) {
                    connect(target)
                }
            }
        }
    }

    fun connect(device: ScannedBleDevice) {
        stopAutoConnect()
        stopAutoReconnect()
        stopDemo(clearState = true)
        reconnectEnabled = true
        explicitDisconnect = false
        pairingMode.value = PairingMode.Target
        clearLiveDataState()
        repository.clearMalformedCount()
        viewModelScope.launch {
            sensorCsvLogger.startSession(
                activity = dataCollectionActivity.value,
            )
        }
        repository.connect(device)
    }

    fun startDemo() {
        stopAutoConnect()
        stopAutoReconnect()
        reconnectEnabled = false
        explicitDisconnect = true
        pairingMode.value = PairingMode.Target
        repository.stopScan()
        repository.disconnect()
        repository.clearMalformedCount()
        stopDemo(clearState = false)
        clearLiveDataState()
        selectedModality.value = Modality.Accelerometer
        demoConnectionState.value = BleConnectionState.Connected("Demo Sensor")

        demoJob = viewModelScope.launch {
            sensorCsvLogger.stopSession()
            sensorCsvLogger.startSession(
                activity = dataCollectionActivity.value,
            )
            val startedAtMs = System.currentTimeMillis()
            var frameIndex = 0L
            while (isActive) {
                delay(DEMO_FRAME_PERIOD_MS)
                frameIndex += 1
                val receivedAtMs = System.currentTimeMillis()
                val frameTimestampMs = startedAtMs + frameIndex * DEMO_FRAME_PERIOD_MS
                val frameElapsedSec = (frameTimestampMs - startedAtMs) / 1000.0
                val contactMicFrame =
                    demoContactMicFrame(
                        timestampMs = frameTimestampMs,
                        receivedAtMs = receivedAtMs,
                        startedAtMs = startedAtMs,
                        elapsedSec = frameElapsedSec,
                        sequence = frameIndex,
                    )
                var updatedSamples = samples.value
                repeat(DEMO_SENSOR_SAMPLES_PER_FRAME) { sampleIndex ->
                    val offsetMs =
                        (sampleIndex + 1) * 1_000L / DEMO_SENSOR_SAMPLE_RATE_HZ
                    val sampleTimestampMs =
                        frameTimestampMs - DEMO_FRAME_PERIOD_MS + offsetMs
                    val sample = demoSample(
                        timestampMs = sampleTimestampMs,
                        receivedAtMs = receivedAtMs,
                        startedAtMs = startedAtMs,
                        elapsedSec = (sampleTimestampMs - startedAtMs) / 1000.0,
                    )
                    updatedSamples = rollingBuffer.append(sample)
                    sensorCsvLogger.appendSample(sample)
                }
                samples.value = updatedSamples
                updatePpgState(updatedSamples, frameTimestampMs)
                updateContactMicState(contactMicFrame)
                sensorCsvLogger.appendContactMicFrame(contactMicFrame)
            }
        }
    }

    fun disconnect() {
        explicitDisconnect = true
        reconnectEnabled = false
        stopAutoConnect()
        stopAutoReconnect()
        pairingMode.value = PairingMode.Target
        viewModelScope.launch {
            sensorCsvLogger.stopSession()
        }
        if (demoJob != null) {
            stopDemo(clearState = false)
            demoConnectionState.value = BleConnectionState.Disconnected("Demo session ended")
        } else {
            repository.disconnect()
        }
        clearLiveDataState()
    }

    fun togglePolarCapture() {
        when (polarRepository.state.value) {
            PolarH10ConnectionState.Scanning,
            is PolarH10ConnectionState.Connecting,
            is PolarH10ConnectionState.StartingStreams,
            is PolarH10ConnectionState.Recording
            -> polarRepository.disconnect()

            else -> viewModelScope.launch {
                clearPolarLiveData()
                polarCsvLogger.startSession(
                    activity = dataCollectionActivity.value,
                )
                polarCsvLogger.appendEvent(
                    PolarCaptureEvent(System.currentTimeMillis(), "session_created"),
                )
                polarRepository.startCapture()
            }
        }
    }

    fun selectModality(modality: Modality) {
        selectedModality.value = modality
    }

    fun updateTargetDeviceName(name: String) {
        val sanitized = publicDeviceName(name).orEmpty()
            .replace("\n", "")
            .replace("\r", "")
            .take(48)
        targetDeviceName.value = sanitized
        settingsPrefs.edit()
            .putString(KEY_TARGET_DEVICE_NAME, sanitized)
            .apply()
    }

    fun selectDataCollectionActivity(activity: DataCollectionActivity) {
        dataCollectionActivity.value = activity
        settingsPrefs.edit()
            .putString(KEY_DATA_COLLECTION_ACTIVITY, activity.name)
            .apply()
    }

    fun updateAvatarAppearance(appearance: AvatarAppearance) {
        val sanitized = appearance.copy(skinToneIndex = appearance.skinToneIndex.coerceIn(0, 4))
        avatarAppearance.value = sanitized
        settingsPrefs.edit()
            .putString(KEY_AVATAR_GENDER, sanitized.genderPresentation.name)
            .putInt(KEY_AVATAR_SKIN_TONE, sanitized.skinToneIndex)
            .putString(KEY_AVATAR_HAIR_COLOR, sanitized.hairColor.name)
            .apply()
    }

    fun setLiveDisplayMetricEnabled(
        metric: LiveDisplayMetric,
        enabled: Boolean,
    ) {
        val current = enabledLiveMetrics.value
        val updated = if (enabled) {
            current + metric
        } else {
            current - metric
        }
        enabledLiveMetrics.value = updated
        persistEnabledLiveMetrics(updated)
    }

    fun resetSettings() {
        targetDeviceName.value = DEFAULT_DEVICE_NAME
        dataCollectionActivity.value = DataCollectionActivity.Default
        avatarAppearance.value = AvatarAppearance()
        enabledLiveMetrics.value = DEFAULT_LIVE_DISPLAY_METRICS
        settingsPrefs.edit()
            .putString(KEY_TARGET_DEVICE_NAME, DEFAULT_DEVICE_NAME)
            .putString(KEY_DATA_COLLECTION_ACTIVITY, DataCollectionActivity.Default.name)
            .putString(KEY_AVATAR_GENDER, AvatarGenderPresentation.Man.name)
            .putInt(KEY_AVATAR_SKIN_TONE, AvatarAppearance().skinToneIndex)
            .putString(KEY_AVATAR_HAIR_COLOR, AvatarHairColor.Black.name)
            .putStringSet(
                KEY_ENABLED_LIVE_METRICS,
                DEFAULT_LIVE_DISPLAY_METRICS.map { it.name }.toSet(),
            )
            .apply()
    }

    override fun onCleared() {
        stopAutoConnect()
        stopAutoReconnect()
        stopDemo(clearState = true)
        viewModelScope.launch {
            sensorCsvLogger.stopSession()
            polarCsvLogger.stopSession()
        }
        repository.disconnect(closeOnly = true)
        polarRepository.close()
        super.onCleared()
    }

    private fun stopDemo(clearState: Boolean) {
        demoJob?.cancel()
        demoJob = null
        if (clearState) {
            demoConnectionState.value = null
        }
    }

    private fun stopAutoConnect() {
        autoConnectJob?.cancel()
        autoConnectJob = null
    }

    private fun stopAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun scheduleAutoReconnect() {
        if (!reconnectEnabled || explicitDisconnect || reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            delay(AUTO_RECONNECT_DELAY_MS)
            if (
                reconnectEnabled &&
                !explicitDisconnect &&
                effectiveConnectionState.value !is BleConnectionState.Connected
            ) {
                beginTargetAutoConnect(clearDemo = false)
            }
        }
    }

    private fun markLiveFrameReceived(receivedAtMs: Long) {
        val previous = lastLiveFrameReceivedAtMs
        lastLiveFrameReceivedAtMs = if (previous == null) {
            receivedAtMs
        } else {
            maxOf(previous, receivedAtMs)
        }
    }

    private fun clearStaleLiveDataIfNeeded() {
        val state = effectiveConnectionState.value
        if (state is BleConnectionState.Connected) return

        val lastReceivedAt = lastLiveFrameReceivedAtMs ?: return
        if (System.currentTimeMillis() - lastReceivedAt >= LIVE_DATA_STALE_MS) {
            clearLiveDataState()
        }
    }

    private fun clearLiveDataState() {
        rollingBuffer.clear()
        clearContactMicState()
        clearPpgState()
        samples.value = emptyList()
        lastLiveFrameReceivedAtMs = null
    }

    private fun clearPolarLiveData() {
        polarEcgBuffer.clear()
        polarAccBuffer.clear()
        polarEcgSamples.value = emptyList()
        polarAccSamples.value = emptyList()
    }

    private suspend fun updateContactMicState(frame: ContactMicFrame) {
        val updatedPoints = contactMicBuffer.append(frame)
        contactMicPoints.value = updatedPoints

        val shouldAnalyze = frame.timestampMs - lastContactMicAnalysisMs >= CONTACT_MIC_ANALYSIS_INTERVAL_MS
        if (shouldAnalyze) {
            val analysis = withContext(Dispatchers.Default) {
                ContactMicAnalyzer.analyze(
                    points = updatedPoints,
                    sampleRateHz = frame.sampleRateHz,
                )
            }
            contactMicAnalysis.value = analysis
            lastContactMicAnalysisMs = frame.timestampMs
        }
    }

    private suspend fun updatePpgState(updatedSamples: List<SensorSample>, timestampMs: Long) {
        val shouldAnalyze = timestampMs - lastPpgAnalysisMs >= PPG_ANALYSIS_INTERVAL_MS
        if (shouldAnalyze) {
            val analysis = withContext(Dispatchers.Default) {
                PpgAnalyzer.analyze(updatedSamples)
            }
            ppgAnalysis.value = analysis
            lastPpgAnalysisMs = timestampMs
        }
    }

    private fun clearContactMicState() {
        contactMicBuffer.clear()
        contactMicPoints.value = emptyList()
        contactMicAnalysis.value = ContactMicAnalysis()
        lastContactMicAnalysisMs = 0L
    }

    private fun clearPpgState() {
        ppgAnalysis.value = PpgAnalysis()
        lastPpgAnalysisMs = 0L
    }

    private fun loadTargetDeviceName(): String =
        publicDeviceName(settingsPrefs.getString(KEY_TARGET_DEVICE_NAME, DEFAULT_DEVICE_NAME)) ?: DEFAULT_DEVICE_NAME

    private fun loadDataCollectionActivity(): DataCollectionActivity =
        DataCollectionActivity.fromStoredValue(
            settingsPrefs.getString(KEY_DATA_COLLECTION_ACTIVITY, null),
        )

    private fun loadAvatarAppearance(): AvatarAppearance = AvatarAppearance(
        genderPresentation = settingsPrefs.getString(KEY_AVATAR_GENDER, null)
            ?.let { stored -> runCatching { AvatarGenderPresentation.valueOf(stored) }.getOrNull() }
            ?: AvatarGenderPresentation.Man,
        skinToneIndex = settingsPrefs.getInt(
            KEY_AVATAR_SKIN_TONE,
            AvatarAppearance().skinToneIndex,
        ).coerceIn(0, 4),
        hairColor = settingsPrefs.getString(KEY_AVATAR_HAIR_COLOR, null)
            ?.let { stored ->
                if (stored == "Gray") {
                    AvatarHairColor.Blonde
                } else {
                    runCatching { AvatarHairColor.valueOf(stored) }.getOrNull()
                }
            }
            ?: AvatarHairColor.Black,
    )

    private fun loadEnabledLiveMetrics(): Set<LiveDisplayMetric> {
        val saved = settingsPrefs.getStringSet(KEY_ENABLED_LIVE_METRICS, null)
            ?: return DEFAULT_LIVE_DISPLAY_METRICS
        return saved
            .mapNotNull { name ->
                runCatching { LiveDisplayMetric.valueOf(name) }.getOrNull()
            }
            .toSet()
    }

    private fun persistEnabledLiveMetrics(metrics: Set<LiveDisplayMetric>) {
        settingsPrefs.edit()
            .putStringSet(KEY_ENABLED_LIVE_METRICS, metrics.map { it.name }.toSet())
            .apply()
    }

    private fun demoSample(
        timestampMs: Long,
        receivedAtMs: Long,
        startedAtMs: Long,
        elapsedSec: Double,
    ): SensorSample {
        val walk = sin(2 * PI * 1.2 * elapsedSec)
        val pulse = (sin(2 * PI * 1.15 * elapsedSec).coerceAtLeast(0.0))
        val ambient = 430.0 + 90.0 * sin(2 * PI * 0.04 * elapsedSec)

        return SensorSample(
            timestampMs = timestampMs,
            receivedAtMs = receivedAtMs,
            deviceUptimeMs = timestampMs - startedAtMs,
            als = ambient,
            uv = 18.0 + 4.0 * sin(2 * PI * 0.03 * elapsedSec + 0.6),
            tempC = 24.2 + 0.35 * sin(2 * PI * 0.02 * elapsedSec),
            ax = 0.18 * walk,
            ay = 0.12 * sin(2 * PI * 1.2 * elapsedSec + 1.3),
            az = 0.98 + 0.04 * walk,
            gx = 8.0 * sin(2 * PI * 0.9 * elapsedSec),
            gy = 5.0 * sin(2 * PI * 0.7 * elapsedSec + 0.8),
            gz = 3.0 * sin(2 * PI * 0.5 * elapsedSec + 1.8),
            ir = 84_000.0 + 2_500.0 * pulse,
        )
    }

    private fun demoContactMicFrame(
        timestampMs: Long,
        receivedAtMs: Long,
        startedAtMs: Long,
        elapsedSec: Double,
        sequence: Long,
    ): ContactMicFrame {
        val frameSamples = CONTACT_MIC_SAMPLE_RATE_HZ / 10
        val frameStartSec = elapsedSec - frameSamples.toDouble() / CONTACT_MIC_SAMPLE_RATE_HZ
        val heartPeriodSec = 60.0 / 72.0
        val samples = List(frameSamples) { index ->
            val t = frameStartSec + index.toDouble() / CONTACT_MIC_SAMPLE_RATE_HZ
            val phase = ((t % heartPeriodSec) + heartPeriodSec) % heartPeriodSec
            fun thump(center: Double, width: Double, gain: Double): Double {
                val x = (phase - center) / width
                return gain * (1.0 - x * x) * exp(-0.5 * x * x)
            }
            val s1 = thump(center = 0.08, width = 0.028, gain = 1.0)
            val s2 = thump(center = 0.32, width = 0.024, gain = 0.72)
            val lowMotion = 0.06 * sin(2 * PI * 8.0 * t)
            ((s1 + s2 + lowMotion) * 14_000).toInt()
        }

        return ContactMicFrame(
            timestampMs = timestampMs,
            receivedAtMs = receivedAtMs,
            deviceUptimeMs = timestampMs - startedAtMs,
            deviceUptimeUs = (timestampMs - startedAtMs) * 1_000L,
            sequence = sequence,
            sampleRateHz = CONTACT_MIC_SAMPLE_RATE_HZ,
            samples = samples,
        )
    }
}

private data class UiSelections(
    val modality: Modality,
    val pairingMode: PairingMode,
    val targetDeviceName: String,
    val dataCollectionActivity: DataCollectionActivity,
    val avatarAppearance: AvatarAppearance,
    val enabledLiveMetrics: Set<LiveDisplayMetric>,
)

private data class DataCollectionSelections(
    val activity: DataCollectionActivity,
    val avatarAppearance: AvatarAppearance,
)

private data class PolarUiState(
    val connectionState: PolarH10ConnectionState,
    val ecgSamples: List<PolarEcgSample>,
    val accSamples: List<PolarAccSample>,
    val logStatus: PolarLogStatus,
)
