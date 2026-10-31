package com.jellycine.player.core

import android.content.Context
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.common.Format
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.jellycine.logging.LogLevel
import com.jellycine.logging.PlaybackLogStore
import com.jellycine.player.preferences.PlayerPreferences
import java.io.IOException

/** Collects evidence only; never changes tracks, buffering, retries or playback state. */
@UnstableApi
internal class ExoPlaybackDiagnostics(
    context: Context,
    private val player: ExoPlayer,
    private val sessionId: String,
    playbackContext: String
) : AnalyticsListener {
    private val appContext = context.applicationContext
    private val handler = Handler(player.applicationLooper)
    private val stats = PlaybackStatsListener(false, null)
    private val startedMs = SystemClock.elapsedRealtime()
    private var released = false
    private var bufferingStartedMs: Long? = null
    private var videoDecoder = "unknown"
    private var audioDecoder = "unknown"
    private var videoFormat = "unknown"
    private var audioFormat = "unknown"
    private var bandwidthBps: Long? = null
    private var bandwidthSampleMs: Long? = null
    private var bandwidthSampleBytes = 0L
    private var bandwidthLoadMs = 0
    private var loadedBytes = 0L
    private var loadErrors = 0
    private var lastLoad = "none"
    private val pendingLoads = mutableMapOf<Long, Long>()
    private var lastSampleMs: Long? = null
    private var lastPositionMs = 0L
    private var lastHadPlaybackIntent = false
    private var lastDroppedFrames = 0
    private var frameProcessingAvgUs: Long? = null

    private val sample = object : Runnable {
        override fun run() {
            if (released) return
            val now = SystemClock.elapsedRealtime()
            val previousMs = lastSampleMs
            val position = player.currentPosition
            val playbackIntent = player.playWhenReady &&
                player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
                player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING)
            val comparable = previousMs != null && lastHadPlaybackIntent && playbackIntent
            val stalled = comparable && position >= lastPositionMs && position - lastPositionMs < 250
            val counters = player.videoDecoderCounters
            counters?.ensureUpdated()
            val dropped = counters?.droppedBufferCount ?: 0
            val delta = (dropped - lastDroppedFrames).coerceAtLeast(0)
            if (player.playWhenReady || delta > 0) {
                snapshot(if (stalled) "progress_stall_suspected" else "sample",
                    "sampleWallMs=${previousMs?.let { now - it }} progressMs=${position - lastPositionMs} " +
                        "droppedSinceSample=$delta", if (stalled || delta > 0) LogLevel.WARN else LogLevel.INFO)
            }
            lastSampleMs = now
            lastPositionMs = position
            lastHadPlaybackIntent = playbackIntent
            lastDroppedFrames = dropped
            handler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    init {
        // Registered first so its counters are updated before our batched onEvents callback.
        player.addAnalyticsListener(stats)
        player.addAnalyticsListener(this)
        val preferences = PlayerPreferences(appContext)
        emit("session_start", "media3=${MediaLibraryInfo.VERSION} $playbackContext " +
            "hardwareRequested=${preferences.isHardwareAccelerationEnabled()} " +
            "decoderPriority=${preferences.getDecoderPriority()} asyncRequested=${preferences.isAsyncMediaCodecEnabled()} " +
            "batteryOptimization=${preferences.isBatteryOptimizationEnabled()} " +
            "cacheMb=${preferences.getPlayerCacheSizeMb()} cacheSeconds=${preferences.getPlayerCacheTimeSeconds()} " +
            "device=${Build.MANUFACTURER}/${Build.MODEL} api=${Build.VERSION.SDK_INT}")
        handler.postDelayed(sample, SAMPLE_INTERVAL_MS)
    }

    override fun onEvents(player: Player, events: AnalyticsListener.Events) {
        if (released) return
        if (events.contains(AnalyticsListener.EVENT_PLAYER_RELEASED)) {
            logSummary()
            released = true
            return
        }
        if (events.containsAny(AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED,
                AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED,
                AnalyticsListener.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                AnalyticsListener.EVENT_POSITION_DISCONTINUITY,
                AnalyticsListener.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
            if (events.containsAny(AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED,
                    AnalyticsListener.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                    AnalyticsListener.EVENT_POSITION_DISCONTINUITY,
                    AnalyticsListener.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                lastHadPlaybackIntent = false // Keep comparisons across READY/BUFFERING changes.
            }
            val now = SystemClock.elapsedRealtime()
            val buffering = player.playbackState == Player.STATE_BUFFERING
            if (buffering && bufferingStartedMs == null) {
                bufferingStartedMs = now
                snapshot("buffering_start", "")
            } else if (!buffering && bufferingStartedMs != null) {
                snapshot("buffering_end", "bufferingWallMs=${now - bufferingStartedMs!!}")
                bufferingStartedMs = null
            }
            snapshot("state", "events=${(0 until events.size()).joinToString(",") { events[it].toString() }}")
        }
    }

    override fun onPositionDiscontinuity(eventTime: EventTime, oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo, reason: Int) {
        emit("discontinuity", "reason=$reason fromMs=${oldPosition.positionMs} toMs=${newPosition.positionMs}")
    }

    override fun onPlayWhenReadyChanged(eventTime: EventTime, playWhenReady: Boolean, reason: Int) {
        emit("play_when_ready", "value=$playWhenReady reason=$reason")
    }

    override fun onVideoDecoderInitialized(eventTime: EventTime, decoderName: String,
        initializedTimestampMs: Long, initializationDurationMs: Long) {
        videoDecoder = decoderName
        emit("video_decoder", "name=$decoderName ${codecCapability(decoderName)} initMs=$initializationDurationMs")
    }

    override fun onAudioDecoderInitialized(eventTime: EventTime, decoderName: String,
        initializedTimestampMs: Long, initializationDurationMs: Long) {
        audioDecoder = decoderName
        emit("audio_decoder", "name=$decoderName ${codecCapability(decoderName)} initMs=$initializationDurationMs")
    }

    override fun onVideoInputFormatChanged(eventTime: EventTime, format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?) {
        videoFormat = formatText(format)
        emit("video_format", "$videoFormat reuse=${decoderReuseEvaluation?.result} discardReasons=${decoderReuseEvaluation?.discardReasons}")
    }

    override fun onAudioInputFormatChanged(eventTime: EventTime, format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?) {
        audioFormat = formatText(format)
        emit("audio_format", audioFormat)
    }

    override fun onRenderedFirstFrame(eventTime: EventTime, output: Any, renderTimeMs: Long) {
        snapshot("first_frame", "")
    }

    override fun onDroppedVideoFrames(eventTime: EventTime, droppedFrames: Int, elapsedMs: Long) {
        snapshot("dropped_frames", "${eventStamp(eventTime)} count=$droppedFrames windowMs=$elapsedMs", LogLevel.WARN)
    }

    override fun onVideoFrameProcessingOffset(eventTime: EventTime, totalProcessingOffsetUs: Long, frameCount: Int) {
        frameProcessingAvgUs = if (frameCount > 0) totalProcessingOffsetUs / frameCount else null
    }

    override fun onSurfaceSizeChanged(eventTime: EventTime, width: Int, height: Int) {
        emit("surface_size", "${eventStamp(eventTime)} size=${width}x$height")
    }

    override fun onAudioUnderrun(eventTime: EventTime, bufferSize: Int, bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long) {
        snapshot("audio_underrun", "${eventStamp(eventTime)} outputBufferBytes=$bufferSize outputBufferMs=$bufferSizeMs " +
            "sinceLastFeedMs=$elapsedSinceLastFeedMs", LogLevel.WARN)
    }

    override fun onAudioSinkError(eventTime: EventTime, audioSinkError: Exception) {
        snapshot("audio_sink_error", PlaybackDiagnosticText.errorTypes(audioSinkError), LogLevel.ERROR)
    }

    override fun onVideoCodecError(eventTime: EventTime, videoCodecError: Exception) {
        snapshot("video_codec_error", PlaybackDiagnosticText.errorTypes(videoCodecError), LogLevel.ERROR)
    }

    override fun onAudioCodecError(eventTime: EventTime, audioCodecError: Exception) {
        snapshot("audio_codec_error", PlaybackDiagnosticText.errorTypes(audioCodecError), LogLevel.ERROR)
    }

    override fun onPlayerError(eventTime: EventTime, error: PlaybackException) {
        snapshot("player_error", "code=${error.errorCodeName} causes=${PlaybackDiagnosticText.errorTypes(error)}", LogLevel.ERROR)
    }

    override fun onBandwidthEstimate(eventTime: EventTime, totalLoadTimeMs: Int,
        totalBytesLoaded: Long, bitrateEstimate: Long) {
        bandwidthBps = bitrateEstimate
        bandwidthSampleMs = SystemClock.elapsedRealtime()
        bandwidthSampleBytes = totalBytesLoaded
        bandwidthLoadMs = totalLoadTimeMs
    }

    override fun onLoadStarted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, retryCount: Int) {
        pendingLoads[loadEventInfo.loadTaskId] = loadEventInfo.elapsedRealtimeMs
        if (retryCount > 0) emit("load_retry", "${eventStamp(eventTime)} loadId=${loadEventInfo.loadTaskId} retryCount=$retryCount")
    }

    override fun onLoadCompleted(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        pendingLoads.remove(loadEventInfo.loadTaskId)
        loadedBytes += loadEventInfo.bytesLoaded
        lastLoad = "id=${loadEventInfo.loadTaskId},bytes=${loadEventInfo.bytesLoaded},ms=${loadEventInfo.loadDurationMs},type=${mediaLoadData.dataType}"
    }

    override fun onLoadCanceled(eventTime: EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        pendingLoads.remove(loadEventInfo.loadTaskId)
    }

    override fun onLoadError(eventTime: EventTime, loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        if (wasCanceled) pendingLoads.remove(loadEventInfo.loadTaskId)
        loadErrors++
        val status = (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        snapshot("load_error", "${eventStamp(eventTime)} loadId=${loadEventInfo.loadTaskId} type=${mediaLoadData.dataType} " +
            "trackType=${mediaLoadData.trackType} httpStatus=$status canceled=$wasCanceled " +
            "bytes=${loadEventInfo.bytesLoaded} loadMs=${loadEventInfo.loadDurationMs} " +
            "rangeStart=${loadEventInfo.dataSpec.position} rangeLength=${loadEventInfo.dataSpec.length} " +
            "endpoint=${PlaybackDiagnosticText.endpoint(loadEventInfo.uri.toString())} " +
            "causes=${PlaybackDiagnosticText.errorTypes(error)}", LogLevel.WARN)
    }

    override fun onPlayerReleased(eventTime: EventTime) {
        handler.removeCallbacks(sample)
        // The summary is emitted from onEvents, after PlaybackStatsListener processes release.
    }

    private fun logSummary() {
        val summary = stats.combinedPlaybackStats
        emit("session_end", "rebufferCount=${summary.totalRebufferCount} rebufferMs=${summary.totalRebufferTimeMs} " +
            "seekCount=${summary.totalSeekCount} seekMs=${summary.totalSeekTimeMs} " +
            "droppedFrames=${summary.totalDroppedFrames} audioUnderruns=${summary.totalAudioUnderruns} " +
            "loadErrors=$loadErrors loadedBytes=$loadedBytes " +
            "openBufferingWallMs=${bufferingStartedMs?.let { SystemClock.elapsedRealtime() - it }}")
    }

    private fun snapshot(event: String, detail: String, level: LogLevel = LogLevel.INFO) {
        val now = SystemClock.elapsedRealtime()
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val audioCounters = player.audioDecoderCounters
        audioCounters?.ensureUpdated()
        val summary = stats.combinedPlaybackStats
        val runtime = Runtime.getRuntime()
        emit(event, "$detail posMs=${player.currentPosition} bufferedMs=${player.totalBufferedDuration} " +
            "bufferedPosMs=${player.bufferedPosition} state=${stateName(player.playbackState)} " +
            "playWhenReady=${player.playWhenReady} playing=${player.isPlaying} " +
            "suppression=${player.playbackSuppressionReason} loading=${player.isLoading} speed=${player.playbackParameters.speed} " +
            "videoDecoder=$videoDecoder audioDecoder=$audioDecoder video=[$videoFormat] audio=[$audioFormat] " +
            "rendered=${counters?.renderedOutputBufferCount} dropped=${counters?.droppedBufferCount} " +
            "maxConsecutiveDrops=${counters?.maxConsecutiveDroppedBufferCount} " +
            "audioInput=${audioCounters?.queuedInputBufferCount} audioRendered=${audioCounters?.renderedOutputBufferCount} " +
            "audioSkipped=${audioCounters?.skippedOutputBufferCount} " +
            "frameProcessingAvgUs=$frameProcessingAvgUs " +
            "skipped=${counters?.skippedOutputBufferCount} dropToKeyframe=${counters?.droppedToKeyframeCount} " +
            "rebufferCount=${summary.totalRebufferCount} rebufferMs=${summary.totalRebufferTimeMs} " +
            "seekCount=${summary.totalSeekCount} audioUnderruns=${summary.totalAudioUnderruns} " +
            "bandwidthEstimateBps=$bandwidthBps bandwidthAgeMs=${bandwidthSampleMs?.let { now - it }} " +
            "bandwidthSampleBytes=$bandwidthSampleBytes bandwidthLoadMs=$bandwidthLoadMs " +
            "activeLoads=${pendingLoads.size} oldestLoadMs=${pendingLoads.values.minOrNull()?.let { now - it }} " +
            "lastLoad=[$lastLoad] loadedBytes=$loadedBytes loadErrors=$loadErrors " +
            "heapUsedMb=${(runtime.totalMemory() - runtime.freeMemory()) / 1048576} " +
            "heapMaxMb=${runtime.maxMemory() / 1048576} ${environment()}", level)
    }

    private fun emit(event: String, message: String, level: LogLevel = LogLevel.INFO) {
        PlaybackLogStore.log(appContext, "ExoPlayback", "session=$sessionId tMs=${SystemClock.elapsedRealtime() - startedMs} event=$event $message", level)
    }

    private fun eventStamp(eventTime: EventTime) =
        "eventTMs=${eventTime.realtimeMs - startedMs} eventPosMs=${eventTime.eventPlaybackPositionMs}"

    private fun environment(): String = runCatching {
        val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        val transport = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        "network=$transport validated=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)} " +
            "metered=${connectivity?.isActiveNetworkMetered} powerSave=${power?.isPowerSaveMode} " +
            "thermalStatus=${if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus else null}"
    }.getOrDefault("environment=unavailable")

    private fun codecCapability(name: String): String = runCatching {
        if (Build.VERSION.SDK_INT < 29) return "hardware=unknown software=unknown"
        val codec = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name == name }
        "hardware=${codec?.isHardwareAccelerated} software=${codec?.isSoftwareOnly} vendor=${codec?.isVendor}"
    }.getOrDefault("hardware=unknown software=unknown")

    private fun formatText(format: Format) = "mime=${format.sampleMimeType} codecs=${format.codecs} " +
        "size=${format.width}x${format.height} fps=${format.frameRate} bitrateBps=${format.bitrate} " +
        "channels=${format.channelCount} sampleRate=${format.sampleRate} pcm=${format.pcmEncoding} " +
        "colorSpace=${format.colorInfo?.colorSpace} colorTransfer=${format.colorInfo?.colorTransfer}"

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> state.toString()
    }

    companion object {
        private const val SAMPLE_INTERVAL_MS = 5_000L
    }
}
