package com.wearify.app.data

import android.bluetooth.BluetoothDevice
import kotlin.math.abs
import kotlin.math.roundToInt

const val DEFAULT_DEVICE_NAME = "XIAO-Sensor"
const val SENSOR_WINDOW_MS = 60_000L
const val CONTACT_MIC_SAMPLE_RATE_HZ = 4_000
const val CONTACT_MIC_WINDOW_MS = 2_000L

private val hardwareAddressInName = Regex("(?i)(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}")

internal fun publicDeviceName(name: String?): String? =
    name?.takeUnless { hardwareAddressInName.containsMatchIn(it) }

data class ScannedBleDevice(
    val name: String?,
    val rssi: Int,
    val bluetoothDevice: BluetoothDevice,
) {
    val displayName: String = name?.takeIf { it.isNotBlank() } ?: "Unnamed device"
    val isWearifyCandidate: Boolean = name.equals(DEFAULT_DEVICE_NAME, ignoreCase = true)
    val hasXiaoName: Boolean = name?.trim()?.startsWith("xiao", ignoreCase = true) == true
    val hasDisplayName: Boolean = name?.isNotBlank() == true

    fun matchesTargetName(targetName: String): Boolean =
        name.equals(targetName.trim(), ignoreCase = true)
}

data class SensorSample(
    val timestampMs: Long,
    val receivedAtMs: Long = timestampMs,
    val deviceUptimeMs: Long?,
    val als: Double?,
    val uv: Double?,
    val tempC: Double?,
    val ax: Double?,
    val ay: Double?,
    val az: Double?,
    val gx: Double?,
    val gy: Double?,
    val gz: Double?,
    val ir: Double?,
    val ppgChannel: String? = null,
    val ppgSequence: Long? = null,
    val ppgLedPower: Int? = null,
    val ppgQualityPct: Int? = null,
    val ppgFlags: Int? = null,
    val ppgAcPerMille: Int? = null,
    val ppgErrorCode: Int? = null,
    val ppgRecoveryAttemptCount: Long? = null,
    val ppgBrownoutCount: Long? = null,
    val ppgFirmwareResetCount: Long? = null,
    val resetReason: Long? = null,
    val i2cQuarantineMask: Int? = null,
    val i2cBusFaultCount: Long? = null,
    val i2cBusRecoveryCount: Long? = null,
    val watchdogSensorMask: Int? = null,
)

data class ContactMicFrame(
    val timestampMs: Long,
    val receivedAtMs: Long = timestampMs,
    val deviceUptimeMs: Long?,
    val deviceUptimeUs: Long? = null,
    val sequence: Long?,
    val sampleRateHz: Int,
    val samples: List<Int>,
)

data class ContactMicPoint(
    val timestampMs: Long,
    val amplitude: Int,
)

data class HeartSoundPair(
    val s1TimestampMs: Long,
    val s2TimestampMs: Long,
    val gapMs: Long,
)

enum class ContactMicQualityVerdict(val label: String) {
    Waiting("Waiting"),
    GoodContact("Good contact"),
    MarginalContact("Marginal"),
    LowConfidence("Low confidence"),
}

data class ContactMicAnalysis(
    val sampleRateHz: Int = CONTACT_MIC_SAMPLE_RATE_HZ,
    val filteredPoints: List<ContactMicPoint> = emptyList(),
    val envelopePoints: List<ContactMicPoint> = emptyList(),
    val s1s2Pairs: List<HeartSoundPair> = emptyList(),
    val bpm: Double? = null,
    val regularityCvPct: Double? = null,
    val qualityScore: Double = 0.0,
    val verdict: ContactMicQualityVerdict = ContactMicQualityVerdict.Waiting,
    val reasons: List<String> = emptyList(),
)

data class PpgPoint(
    val timestampMs: Long,
    val rawIr: Double,
    val detrended: Double,
    val smoothed: Double,
    val normalized: Double,
)

data class PpgPeak(
    val timestampMs: Long,
    val normalized: Double,
)

enum class PpgQualityVerdict(val label: String) {
    Waiting("Waiting"),
    GoodContact("Good contact"),
    MarginalContact("Marginal"),
    LowSignal("Low signal"),
    MotionArtifact("Motion"),
    Saturated("Saturated"),
}

data class PpgAnalysis(
    val sampleRateHz: Double? = null,
    val processedPoints: List<PpgPoint> = emptyList(),
    val peaks: List<PpgPeak> = emptyList(),
    val bpm: Double? = null,
    val halfRateCandidateBpm: Double? = null,
    val qualityScore: Double = 0.0,
    val verdict: PpgQualityVerdict = PpgQualityVerdict.Waiting,
    val reasons: List<String> = emptyList(),
    val rawIr: Double? = null,
    val acDcRatioPct: Double? = null,
    val motionScore: Double = 0.0,
)

enum class LiveHeartRateFusionState {
    WaitingForBoth,
    LowQuality,
    Disagreement,
    Fused,
}

data class LiveHeartRateFusion(
    val bpm: Double? = null,
    val state: LiveHeartRateFusionState = LiveHeartRateFusionState.WaitingForBoth,
    val differenceBpm: Double? = null,
)

fun fuseLiveHeartRate(
    ppg: PpgAnalysis,
    pcg: ContactMicAnalysis,
): LiveHeartRateFusion {
    val pcgBpm = pcg.bpm?.takeIf { it.isFinite() && it in 40.0..200.0 }
    val ppgCandidates = listOfNotNull(
        ppg.bpm?.takeIf { it.isFinite() && it in 40.0..200.0 },
        ppg.halfRateCandidateBpm?.takeIf { it.isFinite() && it in 40.0..200.0 },
    ).distinct()
    if (ppgCandidates.isEmpty() || pcgBpm == null) {
        return LiveHeartRateFusion()
    }
    // A perfectly repeated PPG secondary lobe can be indistinguishable from a
    // weak alternating pulse using optical morphology alone. Treat a detected
    // 2x rate as a candidate and let the independent PCG rate decide which
    // optical interpretation is safe to display. Ties retain the base rate.
    val ppgBpm = ppgCandidates.minBy { abs(it - pcgBpm) }
    val difference = abs(ppgBpm - pcgBpm)
    val ppgQualityUsable = ppg.verdict in setOf(
        PpgQualityVerdict.GoodContact,
        PpgQualityVerdict.MarginalContact,
    )
    val pcgQualityUsable = pcg.verdict in setOf(
        ContactMicQualityVerdict.GoodContact,
        ContactMicQualityVerdict.MarginalContact,
    )
    if (
        ppg.qualityScore < 62.0 ||
        pcg.qualityScore < 62.0 ||
        !ppgQualityUsable ||
        !pcgQualityUsable
    ) {
        return LiveHeartRateFusion(
            state = LiveHeartRateFusionState.LowQuality,
            differenceBpm = difference,
        )
    }
    if (difference > 8.0) {
        return LiveHeartRateFusion(
            state = LiveHeartRateFusionState.Disagreement,
            differenceBpm = difference,
        )
    }
    val ppgWeight = ppg.qualityScore.coerceAtLeast(1.0)
    val pcgWeight = pcg.qualityScore.coerceAtLeast(1.0)
    val fusedBpm = (
        (ppgBpm * ppgWeight + pcgBpm * pcgWeight) /
            (ppgWeight + pcgWeight)
        )
    return LiveHeartRateFusion(
        bpm = (fusedBpm * 10.0).roundToInt() / 10.0,
        state = LiveHeartRateFusionState.Fused,
        differenceBpm = difference,
    )
}

data class BleScanDebugStatus(
    val scanStarts: Int = 0,
    val rawCallbacks: Int = 0,
    val batchCallbacks: Int = 0,
    val lastName: String? = null,
    val lastRssi: Int? = null,
    val hasScanPermission: Boolean = false,
    val hasConnectPermission: Boolean = false,
    val hasFineLocationPermission: Boolean = false,
    val hasCoarseLocationPermission: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val isLocationEnabled: Boolean = false,
    val lastError: String? = null,
) {
    val callbackTotal: Int = rawCallbacks + batchCallbacks
}

data class DataLogStatus(
    val isRecording: Boolean = false,
    val displayName: String? = null,
    val relativePath: String? = null,
    val micRelativePath: String? = null,
    val samplesWritten: Long = 0,
    val micSamplesWritten: Long = 0,
    val lastError: String? = null,
)

enum class Modality(val title: String) {
    Accelerometer("Accelerometer"),
    Gyroscope("Gyroscope"),
    Ppg("PPG"),
    ContactMic("Contact Mic"),
    Environment("Environment"),
}

enum class LiveDisplayMetric(val title: String, val detail: String) {
    HeartRate("Heart Rate", "Stable optical + heart-sound fusion"),
    ContactMic("Heart Sound", "Envelope and S1/S2 timing"),
    Temperature("Near-body Temp", "Garment-adjacent temperature"),
    Motion("Movement", "Smoothed 5-second category"),
    Uv("UV Exposure", "Low, moderate, or high"),
    AmbientLight("Light", "Dim, moderate, or bright"),
}

val DEFAULT_LIVE_DISPLAY_METRICS: Set<LiveDisplayMetric> = setOf(
    LiveDisplayMetric.HeartRate,
    LiveDisplayMetric.ContactMic,
    LiveDisplayMetric.Temperature,
    LiveDisplayMetric.Motion,
    LiveDisplayMetric.Uv,
    LiveDisplayMetric.AmbientLight,
)

enum class PairingMode {
    Target,
    Manual,
}

sealed interface BleConnectionState {
    data object Idle : BleConnectionState
    data object Scanning : BleConnectionState
    data class Connecting(val deviceName: String) : BleConnectionState
    data class Connected(val deviceName: String) : BleConnectionState
    data class Disconnected(val reason: String? = null) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

enum class AvatarGenderPresentation(val label: String) {
    Man("Man"),
    Woman("Woman"),
    NonBinary("Non-binary"),
}

enum class AvatarHairColor(val label: String, val argb: Long) {
    Black("Black", 0xFF211D22),
    Brown("Brown", 0xFF5A3828),
    Auburn("Auburn", 0xFF80402E),
    Blonde("Blonde", 0xFFC99B55),
}

data class AvatarAppearance(
    val genderPresentation: AvatarGenderPresentation = AvatarGenderPresentation.Man,
    val skinToneIndex: Int = 1,
    val hairColor: AvatarHairColor = AvatarHairColor.Black,
)

data class WearifyUiState(
    val connectionState: BleConnectionState = BleConnectionState.Idle,
    val devices: List<ScannedBleDevice> = emptyList(),
    val samples: List<SensorSample> = emptyList(),
    val contactMicPoints: List<ContactMicPoint> = emptyList(),
    val contactMicAnalysis: ContactMicAnalysis = ContactMicAnalysis(),
    val ppgAnalysis: PpgAnalysis = PpgAnalysis(),
    val selectedModality: Modality = Modality.Accelerometer,
    val pairingMode: PairingMode = PairingMode.Target,
    val targetDeviceName: String = DEFAULT_DEVICE_NAME,
    val dataCollectionActivity: DataCollectionActivity = DataCollectionActivity.Default,
    val avatarAppearance: AvatarAppearance = AvatarAppearance(),
    val enabledLiveMetrics: Set<LiveDisplayMetric> = DEFAULT_LIVE_DISPLAY_METRICS,
    val malformedLineCount: Int = 0,
    val scanDebugStatus: BleScanDebugStatus = BleScanDebugStatus(),
    val dataLogStatus: DataLogStatus = DataLogStatus(),
    val polarConnectionState: PolarH10ConnectionState = PolarH10ConnectionState.Idle,
    val polarEcgSamples: List<PolarEcgSample> = emptyList(),
    val polarAccSamples: List<PolarAccSample> = emptyList(),
    val polarLogStatus: PolarLogStatus = PolarLogStatus(),
) {
    val hasLiveSession: Boolean = samples.isNotEmpty() ||
        connectionState is BleConnectionState.Connected ||
        polarConnectionState is PolarH10ConnectionState.Recording
    val lastSample: SensorSample? = samples.lastOrNull()
    val resolvedTargetDeviceName: String = targetDeviceName.trim().ifBlank { DEFAULT_DEVICE_NAME }
}
