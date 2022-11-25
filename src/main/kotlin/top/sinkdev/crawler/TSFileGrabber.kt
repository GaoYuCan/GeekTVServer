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

    // Logger
    private val logger: Logger = LoggerFactory.getLogger(TSFileGrabber::class.java)

    private const val LEN_188 = 188
    private const val LEN_204 = 204

    private const val MAGIC = 0x47.toByte()

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
            if (allBytes[0] != MAGIC) {  // 是否是标准 TS
                var i = 0
                while (i < allBytes.size - LEN_188) {
                    if (isMPEG2_TS(allBytes, i)) {
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

    private fun isMPEG2_TS(bytes: ByteArray, offset: Int): Boolean {
        if (bytes[offset] != MAGIC) {
            return false
        }
        val dealt = bytes.size - offset
        var count = dealt / LEN_188
        var i = 0
        while (i < count) {
            if (bytes[offset + i++ * LEN_188] != MAGIC) {
                return false
            }
        }
        if (count >= 2) {
            return true
        }
        count = dealt / LEN_204
        i = 0
        while (i < count) {
            if (bytes[offset + i++ * LEN_204] != MAGIC) {
                return false
            }
        }
        return count > 0
    }
}