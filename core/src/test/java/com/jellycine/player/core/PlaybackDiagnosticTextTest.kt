package com.jellycine.player.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PlaybackDiagnosticTextTest {
    @Test fun endpointRemovesCredentialsPathsAndTokens() {
        assertEquals("https://media.example:8096/….m3u8", PlaybackDiagnosticText.endpoint(
            "https://user:password@media.example:8096/Videos/private-title/master.m3u8?api_key=secret#token"))
        assertEquals("local_or_unknown", PlaybackDiagnosticText.endpoint("file:///private/movie.mp4"))
        assertEquals("unknown", PlaybackDiagnosticText.endpoint("invalid uri"))
    }

    @Test fun errorsKeepCauseTypesWithoutSensitiveMessages() {
        assertEquals("IOException -> IllegalStateException", PlaybackDiagnosticText.errorTypes(
            IOException("https://server/?token=secret", IllegalStateException("password"))))
    }
}
