package com.github.kr328.clash.service.security

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class PublicProfileAddressTest {
    @Test fun deniesLocalAndReservedNetworksIncludingIpv6Ula() {
        listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254",
            "0.0.0.0", "100.64.0.1", "224.0.0.1", "255.255.255.255", "198.18.0.1",
            "::1", "::", "fd00::1", "fc00::1", "fe80::1", "ff02::1", "::ffff:127.0.0.1")
            .forEach { assertFalse(it, PublicProfileAddress.allowed(InetAddress.getByName(it))) }
    }
    @Test fun allowsPublicDestinations() {
        listOf("8.8.8.8", "1.1.1.1", "2606:4700:4700::1111")
            .forEach { assertTrue(it, PublicProfileAddress.allowed(InetAddress.getByName(it))) }
    }
    @Test fun rejectsLocalNamesCredentialsAndUnsupportedSchemes() {
        listOf("https://localhost/a", "http://router.local/a", "content://private/a", "file:///a", "https://user:password@example.com/a")
            .forEach { assertTrue(it, runCatching { PublicProfileAddress.parse(it) }.isFailure) }
    }
}
