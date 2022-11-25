package top.sinkdev

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import top.sinkdev.plugins.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Logger
val globalLogger: Logger = LoggerFactory.getLogger(Application::class.java)

// 全局的 U-A
private val globalUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/107.0.0.0 Safari/537.36"

// 抓取线程池
val globalExecutors = Executors.newFixedThreadPool(3)


// OkHttp 全局 Client
val globalHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .protocols(arrayListOf(Protocol.HTTP_1_1))
    .addInterceptor {
        val newRequest = it.request().newBuilder()
            .addHeader("user-agent", globalUserAgent)
            .build()
        return@addInterceptor it.proceed(newRequest)
    }
    .addInterceptor(HttpLoggingInterceptor { message -> globalLogger.info(message) }
        .apply { level = HttpLoggingInterceptor.Level.HEADERS })
    .build()

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureRouting()
}
