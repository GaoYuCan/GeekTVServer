package top.sinkdev.crawler

import ch.qos.logback.core.encoder.ByteArrayUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import top.sinkdev.globalHttpClient
import java.io.File
import java.security.MessageDigest

object TSFileGrabber {

    // Logger
    private val logger: Logger = LoggerFactory.getLogger(TSFileGrabber::class.java)

    private const val LEN_188 = 188
    private const val LEN_204 = 204

    private const val MAGIC = 0x47.toByte()

    fun downloadAndHandleTSFile(key: String, url: String) {
        try {
            val request = Request.Builder().url(url).get().build()
            val response = globalHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                logger.error("downloadAndHandleTSFile error: ${response.code}, url = ${url}")
                return
            }
            val urlHash = ByteArrayUtil.toHexString(MessageDigest.getInstance("MD5").digest(url.toByteArray()))
            // 创建父文件夹
            val parentFile = File("D:/video_cache/${key}")
            if (!parentFile.isDirectory) {
                parentFile.mkdirs()
            }
            val tsFileOutputStream = File(parentFile, "${urlHash}.ts").outputStream()
            // 开始校验文件头
            val allBytes: ByteArray = response.body!!.bytes()
            var offset = 0
            if (allBytes[0] != MAGIC) {  // 是否是标准 TS
                var i = 0
                while (i < allBytes.size - LEN_188) {
                    if (isMPEG2TS(allBytes, i, LEN_188) || isMPEG2TS(allBytes, i, LEN_204)) {
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

    private fun isMPEG2TS(bytes: ByteArray, offset: Int, len: Int): Boolean {
        if (bytes[offset] != MAGIC) {
            return false
        }
        val dealt = bytes.size - offset
        val count = dealt / len
        var i = 0
        while (i < count) {
            if (bytes[offset + len * (i++)] != MAGIC) {
                return false
            }
        }
        return count > 0
    }
}