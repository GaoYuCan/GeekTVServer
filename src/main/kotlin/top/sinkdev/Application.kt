package top.sinkdev

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import top.sinkdev.plugins.*
import java.util.concurrent.Executors

// 全局网络请求客户端
val globalHttpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        connectTimeoutMillis = 10000
        requestTimeoutMillis = 10000
        socketTimeoutMillis = 10000
    }
}

// Gson
val globalGson = Gson()

// Logger
val globalLogger = LoggerFactory.getLogger(Application::class.java)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureRouting()
}
