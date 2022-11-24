package top.sinkdev.plugins

import ch.qos.logback.core.encoder.ByteArrayUtil
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import top.sinkdev.*
import top.sinkdev.crawler.SimpleCrawler
import top.sinkdev.crawler.TSFileGrabber
import top.sinkdev.model.createFailureResponse
import top.sinkdev.model.createSuccessResponse
import java.io.File
import java.security.MessageDigest


fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Geek TV Server!")
        }

        get("search") {
            val keyword = call.request.queryParameters["keyword"]!!
            // 搜索电影
            val movie = SimpleCrawler.searchMovies(keyword)
            call.respond(createSuccessResponse(movie))
        }

        get("/parse_series") {
            val key = call.request.queryParameters["key"]!!
            // 获取剧集列表
            val seriesList = SimpleCrawler.parseSeries(key)
            call.respond(createSuccessResponse(seriesList))
        }

        get("/parse_url") {
            val key = call.request.queryParameters["key"]!!
            // 是否存在文件
            val proxyM3U8 = File("D:/video_cache/${key}.m3u8")
            if (proxyM3U8.isFile) {
                call.respond(createSuccessResponse("http://localhost:8080/proxy_m3u8?key=${key}"))
                return@get
            }
            // 通过爬虫获取数据
            val parseURL = SimpleCrawler.parseM3U8URL(key)
            if (parseURL == null) {
                call.respond(createFailureResponse<String>("解析失败，请查看系统日志!"))
                return@get
            }
            // 异步生成代理 M3U8 文件
            generateProxyM3U8File(proxyM3U8, parseURL, key)
            call.respond(createSuccessResponse("http://localhost:8080/proxy_m3u8?key=${key}"))
        }

        get("/proxy_m3u8") {
            val key = call.request.queryParameters["key"]!!
            val proxyM3U8 = File("D:/video_cache/${key}.m3u8")
            if (!proxyM3U8.isFile) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondFile(proxyM3U8)
        }

        get("/proxy_ts") {
            try {
                val key = call.request.queryParameters["key"]!!
                val url = call.request.queryParameters["name"]!!.decodeURLBase64()
                // 获取文件名
                val urlHash = ByteArrayUtil.toHexString(MessageDigest.getInstance("MD5").digest(url.toByteArray()))
                val proxyTS = File("D:/video_cache/${key}/${urlHash}.ts")
                var i = 0
                while (!proxyTS.isFile && i++ < 3) {
                    // 缓存文件
                    TSFileGrabber.downloadAndHandleTSFile(key, url)
                }
                if (!proxyTS.isFile) {
                    globalLogger.error("proxyTS File, ${proxyTS.absolutePath} Not Found")
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respondFile(proxyTS)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun generateProxyM3U8File(proxyM3U8: File, parseURL: String, key: String) {
    GlobalScope.launch(Dispatchers.IO) {
        try {// 创建代理 M3U8 文件
            val m3u8FileOutputStream = proxyM3U8.outputStream().bufferedWriter()
            val readChannel = globalHttpClient.get(parseURL).bodyAsChannel()
            var line = readChannel.readUTF8Line()
            while (line != null) {
                if (line.startsWith("#")) {
                    // M3U8 控制信息不变
                    m3u8FileOutputStream.write(line)
                    m3u8FileOutputStream.newLine()
                } else if (line.isNotBlank()) {
                    // 修改链接信息
                    val newURL = "http://localhost:8080/proxy_ts?key=${key}&name=${line.encodeURLBase64()}"
                    m3u8FileOutputStream.write(newURL)
                    m3u8FileOutputStream.newLine()
                    // 判断是否为必须缓存信息
                    if (line.contains("ts.php")) {
                        // 缓存数据
                        val url = line
                        GlobalScope.launch { TSFileGrabber.downloadAndHandleTSFile(key, url) }
                    }
                }
                line = readChannel.readUTF8Line()
            }
            m3u8FileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
