package top.sinkdev.crawler

import ch.qos.logback.core.encoder.ByteArrayUtil
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import top.sinkdev.globalHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.Arrays

object TSFileGrabber {
    // ts 文件头
    private val TS_HEADER = ByteArrayUtil.hexStringToByteArray("474011100042f025")
    // Logger
    private val logger: Logger = LoggerFactory.getLogger(TSFileGrabber::class.java)


    suspend fun downloadAndHandleTSFile(key: String, url: String) = withContext(Dispatchers.IO) {
        try {
            val response = globalHttpClient.get(url)
            if (response.status != HttpStatusCode.OK) {
                logger.error("downloadAndHandleTSFile error: ${response.status}, url = ${url}")
                return@withContext
            }
            val urlHash = ByteArrayUtil.toHexString(MessageDigest.getInstance("MD5").digest(url.toByteArray()))
            // 创建父文件夹
            val parentFile = File("D:/video_cache/${key}")
            if (!parentFile.isDirectory) {
                parentFile.mkdirs()
            }
            val tsFileOutputStream = File(parentFile, "${urlHash}.ts").outputStream()
            // 开始校验文件头
            val allBytes: ByteArray = response.body()

            var offset = 0
            if (!Arrays.equals(allBytes, 0, TS_HEADER.size, TS_HEADER, 0, TS_HEADER.size)) {  // 是否是标准 TS
                var i = 0
                while (i < allBytes.size - TS_HEADER.size) {
                    if (Arrays.equals(allBytes, i, i + TS_HEADER.size, TS_HEADER, 0, TS_HEADER.size)) {
                        offset = i
                        break
                    }
                    i++
                }

            }
            // 写出文件
            allBytes.inputStream(offset, allBytes.size - offset).copyTo(tsFileOutputStream)
            tsFileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}