package com.jellycine.player.video

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.media3.common.MimeTypes

/**
 * Manages HDR capability detection and format fallback logic
 * Provides intelligent fallback from Dolby Vision to HDR10 to SDR
 */
object HdrCapabilityManager {
    
    private const val TAG = "HdrCapabilityManager"
    
    /**
     * HDR format support levels in priority order
     */
    enum class HdrSupport(val displayName: String) {
        DOLBY_VISION("Dolby Vision"),
        HDR10_PLUS("HDR10+"),
        HDR10("HDR10"),
        HLG("HLG"),
        SDR("SDR")
    }
    
    /**
     * Video format information for fallback decisions
     */
    data class VideoFormatInfo(
        val mimeType: String,
        val isDolbyVision: Boolean = false,
        val isHdr10Plus: Boolean = false,
        val isHdr10: Boolean = false,
        val isHlg: Boolean = false,
        val hdrSupport: HdrSupport,
        val fallbackMimeType: String? = null
    )
    
    /**
     * Get the highest HDR format supported by the device display
     */
    fun getDeviceHdrSupport(context: Context): HdrSupport {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.d(TAG, "Device API level < 24, HDR not supported")
            return HdrSupport.SDR
        }
        
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            
            if (display == null || !display.isHdr) {
                Log.d(TAG, "Display doesn't support HDR")
                return HdrSupport.SDR
            }
            
            val hdrCapabilities = display.hdrCapabilities
            if (hdrCapabilities == null) {
                Log.d(TAG, "HDR capabilities not available")
                return HdrSupport.SDR
            }
            
            val supportedTypes = hdrCapabilities.supportedHdrTypes
            Log.d(TAG, "Supported HDR types: ${supportedTypes.contentToString()}")
            
            // Check in priority order
            when {
                supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) -> {
                    Log.d(TAG, "Device supports Dolby Vision")
                    HdrSupport.DOLBY_VISION
                }
                supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS) -> {
                    Log.d(TAG, "Device supports HDR10+")
                    HdrSupport.HDR10_PLUS
                }
                supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) -> {
                    Log.d(TAG, "Device supports HDR10")
                    HdrSupport.HDR10
                }
                supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_HLG) -> {
                    Log.d(TAG, "Device supports HLG")
                    HdrSupport.HLG
                }
                else -> {
                    Log.d(TAG, "No known HDR formats supported")
                    HdrSupport.SDR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking HDR support", e)
            HdrSupport.SDR
        }
    }
    
    /**
     * Analyze video format and determine HDR characteristics
     */
    fun analyzeVideoFormat(mimeType: String?, codec: String?, colorInfo: String? = null): VideoFormatInfo {
        val mime = mimeType ?: MimeTypes.VIDEO_H264
        
        // Analyze Dolby Vision
        val isDolbyVision = mime.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true) ||
                           codec?.contains("dvhe", ignoreCase = true) == true ||
                           codec?.contains("dvh1", ignoreCase = true) == true ||
                           codec?.contains("dolby", ignoreCase = true) == true
        
        // Analyze HDR10+
        val isHdr10Plus = codec?.contains("hev1", ignoreCase = true) == true ||
                         codec?.contains("hvc1", ignoreCase = true) == true ||
                         colorInfo?.contains("smpte2084", ignoreCase = true) == true ||
                         colorInfo?.contains("bt2020", ignoreCase = true) == true
        
        // Analyze HDR10
        val isHdr10 = mime.equals(MimeTypes.VIDEO_H265, ignoreCase = true) ||
                     codec?.contains("hev", ignoreCase = true) == true ||
                     codec?.contains("h265", ignoreCase = true) == true
        
        // Analyze HLG
        val isHlg = colorInfo?.contains("arib-std-b67", ignoreCase = true) == true ||
                   colorInfo?.contains("hlg", ignoreCase = true) == true
        
        // Determine highest HDR support level
        val hdrSupport = when {
            isDolbyVision -> HdrSupport.DOLBY_VISION
            isHdr10Plus -> HdrSupport.HDR10_PLUS
            isHdr10 -> HdrSupport.HDR10
            isHlg -> HdrSupport.HLG
            else -> HdrSupport.SDR
        }
        
        // Determine fallback MIME type
        val fallbackMimeType = when {
            isDolbyVision -> MimeTypes.VIDEO_H265
            isHdr10Plus -> MimeTypes.VIDEO_H265
            isHdr10 -> MimeTypes.VIDEO_H264
            else -> null
        }
        
        Log.d(TAG, "Video format analysis - MIME: $mime, HDR: $hdrSupport, Fallback: $fallbackMimeType")
        
        return VideoFormatInfo(
            mimeType = mime,
            isDolbyVision = isDolbyVision,
            isHdr10Plus = isHdr10Plus,
            isHdr10 = isHdr10,
            isHlg = isHlg,
            hdrSupport = hdrSupport,
            fallbackMimeType = fallbackMimeType
        )
    }
    
    /**
     * Get the best playable format for the device
     * Returns the original format if supported, otherwise returns fallback
     */
    fun getBestPlayableFormat(
        context: Context,
        videoFormat: VideoFormatInfo
    ): VideoFormatInfo {
        val deviceSupport = getDeviceHdrSupport(context)
        
        Log.d(TAG, "Device supports: $deviceSupport, Content requires: ${videoFormat.hdrSupport}")
        
        // If device supports the content format, use it as-is
        if (deviceSupport.ordinal <= videoFormat.hdrSupport.ordinal) {
            Log.d(TAG, "Device supports content format, using original")
            return videoFormat
        }
        
        // Device doesn't support the content format, need fallback
        Log.d(TAG, "Device doesn't support content format, using fallback")
        
        return when (deviceSupport) {
            HdrSupport.HDR10_PLUS, HdrSupport.HDR10 -> {
                if (videoFormat.isDolbyVision) {
                    videoFormat.copy(
                        mimeType = videoFormat.fallbackMimeType ?: MimeTypes.VIDEO_H265,
                        isDolbyVision = false,
                        isHdr10 = true,
                        hdrSupport = HdrSupport.HDR10
                    )
                } else {
                    videoFormat
                }
            }
            HdrSupport.HLG -> {
                // Device only supports HLG, fallback accordingly
                videoFormat.copy(
                    mimeType = videoFormat.fallbackMimeType ?: MimeTypes.VIDEO_H265,
                    isDolbyVision = false,
                    isHdr10Plus = false,
                    isHdr10 = false,
                    isHlg = true,
                    hdrSupport = HdrSupport.HLG
                )
            }
            HdrSupport.SDR -> {
                videoFormat.copy(
                    mimeType = MimeTypes.VIDEO_H264,
                    isDolbyVision = false,
                    isHdr10Plus = false,
                    isHdr10 = false,
                    isHlg = false,
                    hdrSupport = HdrSupport.SDR,
                    fallbackMimeType = null
                )
            }
            else -> videoFormat
        }
    }
    
    /**
     * Get user-friendly description of current playback format
     */
    fun getPlaybackFormatDescription(
        context: Context,
        deviceSupport: HdrSupport,
        contentFormat: VideoFormatInfo,
        actualFormat: VideoFormatInfo
    ): String {
        return if (contentFormat.hdrSupport == actualFormat.hdrSupport) {
            // No fallback needed
            context.getString(com.jellycine.core.R.string.core_playing_format, actualFormat.hdrSupport.displayName)
        } else {
            // Fallback occurred
            context.getString(com.jellycine.core.R.string.core_hdr_fallback, contentFormat.hdrSupport.displayName, actualFormat.hdrSupport.displayName, deviceSupport.displayName)
        }
    }
    
    /**
     * Check if specific HDR type is supported by the device
     */
    fun isHdrTypeSupported(context: Context, hdrType: HdrSupport): Boolean {
        val deviceSupport = getDeviceHdrSupport(context)
        return deviceSupport.ordinal <= hdrType.ordinal
    }
    
    /**
     * Get detailed HDR capability information for debugging
     */
    fun getDetailedHdrInfo(context: Context): String {
        return buildString {
            appendLine(context.getString(com.jellycine.core.R.string.core_hdr_capability_report))
            appendLine(context.getString(com.jellycine.core.R.string.core_api_level, Build.VERSION.SDK_INT))
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                    val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                    
                    appendLine(context.getString(com.jellycine.core.R.string.core_display_hdr, context.getString(if (display?.isHdr == true) com.jellycine.core.R.string.core_yes else com.jellycine.core.R.string.core_no)))
                    
                    display?.hdrCapabilities?.let { capabilities ->
                        appendLine(context.getString(com.jellycine.core.R.string.core_hdr_types, capabilities.supportedHdrTypes.contentToString()))
                        appendLine(context.getString(com.jellycine.core.R.string.core_max_luminance, capabilities.desiredMaxLuminance.toString()))
                        appendLine(context.getString(com.jellycine.core.R.string.core_max_average, capabilities.desiredMaxAverageLuminance.toString()))
                        appendLine(context.getString(com.jellycine.core.R.string.core_min_luminance, capabilities.desiredMinLuminance.toString()))
                    }
                    
                    val deviceSupport = getDeviceHdrSupport(context)
                    appendLine(context.getString(com.jellycine.core.R.string.core_best_format, deviceSupport.displayName))
                    
                } catch (e: Exception) {
                    appendLine(context.getString(com.jellycine.core.R.string.core_hdr_read_error))
                }
            } else {
                appendLine(context.getString(com.jellycine.core.R.string.core_hdr_unsupported))
            }
        }
    }
}
