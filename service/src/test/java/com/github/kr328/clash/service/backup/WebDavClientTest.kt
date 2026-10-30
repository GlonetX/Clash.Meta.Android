package com.github.kr328.clash.service.backup

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WebDavClientTest {
    @Test fun rejectsUnsafeWebDavConfiguration() {
        listOf("http://example.com", "https://u:p@example.com", "https://example.com/?q=x", "https://example.com/#fragment")
            .forEach { assertTrue(it, runCatching { WebDavClient.validate(BackupConfig(url = it)) }.isFailure) }
        listOf("../private", "foo/../private", "foo\\bar").forEach {
            assertTrue(it, runCatching { WebDavClient.validate(BackupConfig(url = "https://example.com", directory = it)) }.isFailure)
        }
    }

    @Test fun uploadsAndDownloadsOverTlsWithoutFollowingRedirects() = runBlocking {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val http = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .followRedirects(false).followSslRedirects(false).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val dav = WebDavClient(BackupConfig(server.url("/dav/").toString(), "backups", "user"), "secret", http)
            val source = File.createTempFile("backup-test", ".age")
            val target = File.createTempFile("backup-test", ".download")
            try {
                source.writeText("encrypted bytes")
                server.enqueue(MockResponse().setResponseCode(207))
                server.enqueue(MockResponse().setResponseCode(201))
                dav.upload("one.gxbackup.age", source)
                val propfind = server.takeRequest()
                assertEquals("PROPFIND", propfind.method)
                assertEquals("/dav/backups/", propfind.path)
                assertTrue(propfind.getHeader("Authorization")!!.startsWith("Basic "))
                val put = server.takeRequest()
                assertEquals("PUT", put.method)
                assertEquals("encrypted bytes", put.body.readUtf8())
                server.enqueue(MockResponse().setBody("downloaded"))
                dav.download("one.gxbackup.age", target)
                assertEquals("downloaded", target.readText())
                server.takeRequest()
                server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://example.com/"))
                assertTrue(runCatching { dav.download("one.gxbackup.age", target) }.isFailure)
                assertEquals(4, server.requestCount)
            } finally { source.delete(); target.delete() }
        }
    }
}
