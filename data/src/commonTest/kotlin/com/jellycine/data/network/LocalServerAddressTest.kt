package com.jellycine.data.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalServerAddressTest {
    @Test fun embyLanUrlAllowsWhitespacePortAndBasePath() {
        assertTrue(isLocalServerAddress("\u00a0http://192.168.5.7:8096/\u00a0"))
        assertTrue(isLocalServerAddress("https://192.168.5.7:8920/emby/"))
    }

    @Test fun privateAndLinkLocalIpv4AddressesAreDirect() {
        listOf("10.1.2.3", "172.16.0.1", "172.31.255.254", "192.168.5.7", "169.254.2.3")
            .forEach { assertTrue(isLocalServerAddress("http://$it:8096"), it) }
    }

    @Test fun publicAddressesOutsidePrivateRangesKeepNormalRouting() {
        listOf("172.15.0.1", "172.32.0.1", "192.167.5.7", "192.169.5.7", "8.8.8.8")
            .forEach { assertFalse(isLocalServerAddress("https://$it"), it) }
    }

    @Test fun publicHostnamesCannotPretendToBeLocalAddresses() {
        assertFalse(isLocalServerAddress("https://192.168.5.7.example.org/"))
        assertFalse(isLocalServerAddress("https://example.org/192.168.5.7"))
        assertFalse(isLocalServerAddress("https://example.local.example.org/"))
    }

    @Test fun localIpv6AndMdnsNamesAreDirect() {
        listOf("http://[fd12::1]:8096/", "http://[fc00::1234]/", "http://[fe80::1]/", "http://emby.local/")
            .forEach { assertTrue(isLocalServerAddress(it), it) }
        assertFalse(isLocalServerAddress("https://[2001:4860:4860::8888]/"))
    }

    @Test fun loopbackAddressesAreDirect() {
        listOf("http://localhost:8096/", "http://127.0.0.1:8096/", "http://[::1]:8096/")
            .forEach { assertTrue(isLocalServerAddress(it), it) }
    }
}
