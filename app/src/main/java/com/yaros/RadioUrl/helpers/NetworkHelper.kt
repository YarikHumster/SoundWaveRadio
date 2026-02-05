package com.yaros.RadioUrl.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.yaros.RadioUrl.Keys
import okhttp3.*
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkHelper {

    private val TAG: String = NetworkHelper::class.java.simpleName
    private lateinit var appContext: Context
    private var networkQualityCallback: ((NetworkQuality) -> Unit)? = null

    enum class NetworkQuality { POOR, MODERATE, GOOD, UNKNOWN }

    val client: OkHttpClient by lazy {
        createSecureOkHttpClient()
    }

    /**
     * Creates OkHttpClient that accepts all SSL certificates
     * This is necessary for radio streams that may not have valid SSL certificates
     */
    fun createSecureOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        return try {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor { chain ->
                    try {
                        val request = chain.request().newBuilder()
                            .addHeader("Connection", "keep-alive")
                            .addHeader("User-Agent", "SoundWaveRadio/1.0")
                            .addHeader("Accept", "*/*")
                            .build()
                        chain.proceed(request)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e("Interceptor error: ${e.message}")
                        throw e
                    }
                }
                .build()
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error creating OkHttpClient: ${e.message}")
            // Fallback to basic client
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    fun isConnectedToNetwork(): Boolean {
        return try {
            val connMgr = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkCapabilities = connMgr.getNetworkCapabilities(connMgr.activeNetwork)
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error checking network connection: ${e.message}")
            false
        }
    }

    data class ContentType(var type: String = "", var charset: String = "")

    fun initialize(context: Context) {
        appContext = context.applicationContext
        setupNetworkMonitoring()
    }

    suspend fun detectContentType(urlString: String): ContentType {
        if (!isConnectedToNetwork()) {
            throw IOException("No internet connection")
        }

        return suspendCoroutine { cont ->
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .head() // Use HEAD request for efficiency
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Timber.tag(TAG).e("detectContentType failed for $urlString: ${e.message}")
                        when (e) {
                            is SocketTimeoutException -> {
                                Timber.tag(TAG).e("Socket timeout occurred: ${e.message}")
                                if (e.message?.contains("SSL handshake timed out") == true) {
                                    Timber.tag(TAG).e("SSL handshake timed out. Possible network or server issue.")
                                } else if (e.message?.contains("Read timed out") == true) {
                                    Timber.tag(TAG).e("Read timed out. The server might be slow or unresponsive.")
                                }
                                cont.resumeWithException(IOException("Request timed out. Please try again later.", e))
                            }
                            is ConnectException -> {
                                Timber.tag(TAG).e("Connection error: ${e.message}")
                                cont.resumeWithException(IOException("Unable to connect to the server.", e))
                            }
                            is UnknownHostException -> {
                                Timber.tag(TAG).e("Unknown host: ${e.message}")
                                cont.resumeWithException(IOException("Unable to resolve the server address.", e))
                            }
                            else -> {
                                Timber.tag(TAG).e("General IO error: ${e.message}")
                                cont.resumeWithException(e)
                            }
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
                                val contentTypeHeader: String = response.header("Content-Type") ?: ""
                                Timber.tag(TAG).v("Raw content type header: $contentTypeHeader for URL: $urlString")

                                val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
                                contentTypeHeaderParts.forEachIndexed { index, part ->
                                    if (index == 0 && part.isNotEmpty()) {
                                        contentType.type = part.trim()
                                    } else if (part.contains("charset=")) {
                                        contentType.charset = part.substringAfter("charset=").trim()
                                    }
                                }

                                if (contentType.type.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
                                    Timber.tag(TAG).w("Special case \"application/octet-stream\"")
                                    val headerFieldContentDisposition: String? = response.header("Content-Disposition")
                                    if (headerFieldContentDisposition != null && headerFieldContentDisposition.contains("=")) {
                                        val fileName: String = headerFieldContentDisposition.split("=")[1].replace("\"", "")
                                        Timber.tag(TAG).i("File name from Content-Disposition: $fileName")
                                    } else {
                                        Timber.tag(TAG).i("Unable to get file name from \"Content-Disposition\" header field.")
                                    }
                                }

                                Timber.tag(TAG).i("Content type: ${contentType.type} | Character set: ${contentType.charset}")
                                cont.resume(contentType)
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).e("Error processing response: ${e.message}")
                            cont.resumeWithException(IOException("Error processing server response", e))
                        }
                    }
                })
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error creating request: ${e.message}")
                cont.resumeWithException(IOException("Error creating request", e))
            }
        }
    }

    suspend fun downloadPlaylist(playlistUrlString: String): List<String> {
        if (!isConnectedToNetwork()) {
            throw IOException("No internet connection")
        }
        return suspendCoroutine { cont ->
            try {
                val request = Request.Builder()
                    .url(playlistUrlString)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Timber.tag(TAG).e("downloadPlaylist failed for $playlistUrlString: ${e.message}")
                        when (e) {
                            is SocketTimeoutException -> {
                                Timber.tag(TAG).e("Socket timeout occurred: ${e.message}")
                                if (e.message?.contains("SSL handshake timed out") == true) {
                                    Timber.tag(TAG).e("SSL handshake timed out. Possible network or server issue.")
                                } else if (e.message?.contains("Read timed out") == true) {
                                    Timber.tag(TAG).e("Read timed out. The server might be slow or unresponsive.")
                                }
                                cont.resumeWithException(IOException("Request timed out. Please try again later.", e))
                            }
                            is ConnectException -> {
                                Timber.tag(TAG).e("Connection error: ${e.message}")
                                cont.resumeWithException(IOException("Unable to connect to the server.", e))
                            }
                            is UnknownHostException -> {
                                Timber.tag(TAG).e("Unknown host: ${e.message}")
                                cont.resumeWithException(IOException("Unable to resolve the server address.", e))
                            }
                            else -> {
                                Timber.tag(TAG).e("General IO error: ${e.message}")
                                cont.resumeWithException(e)
                            }
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                if (!response.isSuccessful) {
                                    Timber.tag(TAG).e("Unsuccessful response: ${response.code}")
                                    cont.resumeWithException(IOException("Server returned error: ${response.code}"))
                                    return
                                }

                                val lines = mutableListOf<String>()
                                response.body?.byteStream()?.bufferedReader()?.useLines { sequence ->
                                    sequence.take(100).forEach { line ->
                                        val trimmedLine = line.take(2000)
                                        lines.add(trimmedLine)
                                    }
                                }
                                Timber.tag(TAG).i("Downloaded playlist with ${lines.size} lines")
                                cont.resume(lines)
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).e("Error processing playlist response: ${e.message}")
                            cont.resumeWithException(IOException("Error processing playlist", e))
                        }
                    }
                })
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error creating playlist request: ${e.message}")
                cont.resumeWithException(IOException("Error creating request", e))
            }
        }
    }

    suspend fun detectContentTypeSuspended(urlString: String): ContentType {
        if (!isConnectedToNetwork()) {
            throw IOException("No internet connection")
        }
        return suspendCoroutine { cont ->
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .head() // Use HEAD request for efficiency
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Timber.tag(TAG).e("detectContentTypeSuspended failed for $urlString: ${e.message}")
                        when (e) {
                            is SocketTimeoutException -> Timber.tag(TAG).e("Socket timeout: ${e.message}")
                            is ConnectException -> Timber.tag(TAG).e("Connection error: ${e.message}")
                            is UnknownHostException -> Timber.tag(TAG).e("Unknown host: ${e.message}")
                            else -> Timber.tag(TAG).e("General IO error: ${e.message}")
                        }
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use {
                                val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
                                val contentTypeHeader: String = response.header("Content-Type") ?: ""
                                Timber.tag(TAG).v("Raw content type header: $contentTypeHeader for URL: $urlString")

                                val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
                                contentTypeHeaderParts.forEachIndexed { index, part ->
                                    if (index == 0 && part.isNotEmpty()) {
                                        contentType.type = part.trim()
                                    } else if (part.contains("charset=")) {
                                        contentType.charset = part.substringAfter("charset=").trim()
                                    }
                                }

                                if (contentType.type.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
                                    Timber.tag(TAG).w("Special case \"application/octet-stream\"")
                                    val headerFieldContentDisposition: String? = response.header("Content-Disposition")
                                    if (headerFieldContentDisposition != null && headerFieldContentDisposition.contains("=")) {
                                        val fileName: String = headerFieldContentDisposition.split("=")[1].replace("\"", "")
                                        Timber.tag(TAG).i("File name from Content-Disposition: $fileName")
                                    } else {
                                        Timber.tag(TAG).i("Unable to get file name from \"Content-Disposition\" header field.")
                                    }
                                }

                                Timber.tag(TAG).i("Content type: ${contentType.type} | Character set: ${contentType.charset}")
                                cont.resume(contentType)
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).e("Error processing response: ${e.message}")
                            cont.resumeWithException(IOException("Error processing server response", e))
                        }
                    }
                })
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error creating request: ${e.message}")
                cont.resumeWithException(IOException("Error creating request", e))
            }
        }
    }

    private fun setupNetworkMonitoring() {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                determineNetworkQuality(capabilities)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        }
    }

    private fun determineNetworkQuality(capabilities: NetworkCapabilities) {
        val downstreamSpeedKbps = capabilities.linkDownstreamBandwidthKbps
        val upstreamSpeedKbps = capabilities.linkUpstreamBandwidthKbps

        val quality = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                when {
                    downstreamSpeedKbps > 50000 -> NetworkQuality.GOOD
                    downstreamSpeedKbps > 10000 -> NetworkQuality.MODERATE
                    else -> NetworkQuality.POOR
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                when {
                    downstreamSpeedKbps > 5000 -> NetworkQuality.GOOD
                    downstreamSpeedKbps > 1000 -> NetworkQuality.MODERATE
                    else -> NetworkQuality.POOR
                }
            }
            else -> NetworkQuality.UNKNOWN
        }

        networkQualityCallback?.invoke(quality)
    }

    fun setNetworkQualityListener(callback: (NetworkQuality) -> Unit) {
        networkQualityCallback = callback
    }

    suspend fun getRadioBrowserServerSuspended(): String {
        if (!isConnectedToNetwork()) {
            throw IOException("No internet connection")
        }
        return suspendCoroutine { cont ->
            try {
                val serverAddressList: Array<InetAddress> = InetAddress.getAllByName(Keys.RADIO_BROWSER_API_BASE)
                val serverAddress = serverAddressList[Random().nextInt(serverAddressList.size)].canonicalHostName
                PreferencesHelper.saveRadioBrowserApiAddress(serverAddress)
                cont.resume(serverAddress)
            } catch (e: UnknownHostException) {
                Timber.tag(TAG).e("Error resolving server address: ${e.message}")
                cont.resumeWithException(IOException("Unable to resolve the server address.", e))
            } catch (e: SecurityException) {
                Timber.tag(TAG).e("Security exception: ${e.message}")
                cont.resumeWithException(IOException("Security error while resolving the server address.", e))
            } catch (e: SocketTimeoutException) {
                Timber.tag(TAG).e("Socket timeout occurred: ${e.message}")
                cont.resumeWithException(IOException("Request timed out while resolving the server address.", e))
            } catch (e: Exception) {
                Timber.tag(TAG).e("General exception: ${e.message}")
                cont.resumeWithException(IOException("An unexpected error occurred.", e))
            }
        }
    }
}
