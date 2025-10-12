package com.example.qr.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Retrofit 클라이언트 싱글톤
 * 자체 서명 SSL 인증서를 허용하는 설정 포함
 */
object RetrofitClient {

    private const val DEFAULT_TIMEOUT = 5000L  // 5초

    /**
     * 자체 서명 인증서를 허용하는 TrustManager
     */
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    /**
     * SSL을 무시하는 OkHttpClient 생성
     */
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            // SSL Context 생성
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            // Logging interceptor
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }  // 모든 호스트명 허용
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)
                .addInterceptor(loggingInterceptor)
                .build()
        } catch (e: Exception) {
            throw RuntimeException("Failed to create OkHttpClient", e)
        }
    }

    /**
     * Retrofit 인스턴스 생성
     *
     * @param baseUrl 마스터 서버 URL (예: https://192.168.0.10:8443)
     */
    fun createApi(baseUrl: String): MasterServerApi {
        // baseUrl이 /로 끝나지 않으면 추가
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(MasterServerApi::class.java)
    }
}
