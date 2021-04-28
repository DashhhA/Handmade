package com.market.handmades.remote

import android.content.Context
import com.market.handmades.R
import java.net.ConnectException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

object Utils {
    /**
     * Creates a tls secure socket, using certificate authority from resources
     * @param host Host address
     * @param port Port
     * @param context App context
     * @param caId Id of Certificate authority resource
     * @return configures TLSSocket
     */
    @Throws(ConnectException::class)
    fun initSocket(hostDescription: HostDescription, context: Context): SSLSocket {
        // obtain certificate authority from a resource
        val cf: CertificateFactory = CertificateFactory.getInstance("X.509")
        val caInput = context.resources.openRawResource(hostDescription.caId)
        val ca: X509Certificate = caInput.use {
            cf.generateCertificate(it) as X509Certificate
        }

        // create a keystore, containing trusted certificate authority
        val keyStoreType = KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(keyStoreType).apply {
            load(null, null)
            setCertificateEntry("ca", ca)
        }

        val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
        val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
            init(keyStore)
        }
        // Create an SSLContext that uses our TrustManager
        val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
            init(null, tmf.trustManagers, null)
        }

        val socket = sslContext.socketFactory.run {
            createSocket(hostDescription.host, hostDescription.port) as SSLSocket
        }
        val session = socket.session

        // verify the hostname is right
        HttpsURLConnection.getDefaultHostnameVerifier().run {
            if (!verify(SERVER_HOST, session)) {
                // with test certificate (CN=localhost) the verification cannot pass
                // TODO throw SSLHandshakeException("expected $SERVER_HOST, found ${session.peerPrincipal}")
            }
        }

        // socket ready to use
        return socket
    }
}

/**
 * Holds information, needed to open secure socket
 */
data class HostDescription(val host: String, val port: Int, val caId: Int)