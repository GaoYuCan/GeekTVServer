package top.sinkdev.crawler

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.http.*
import io.ktor.server.http.*
import io.ktor.util.*
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import top.sinkdev.encodeURLBase64
import top.sinkdev.globalHttpClient
import top.sinkdev.model.Movie
import top.sinkdev.model.Series
import top.sinkdev.model.Sources
import java.util.Properties


object SimpleCrawler {
    private val domainURL: String
    private val searchURL: String
    private val getKeyURL: String
    private val getM3U8URL: String

    // 正则表达式
    private val regex_1 = "var player_aaaa=(\\{.+})".toRegex()
    private val regex_2 = "var config = (\\{.+?), +}".toRegex()

    // Logger
    private val logger: Logger = LoggerFactory.getLogger(SimpleCrawler::class.java)

    init {
        // 读取配置文件获取网站信息
        val properties = Properties()
        properties.load(SimpleCrawler::class.java.classLoader.getResourceAsStream("crawler.properties"))
        domainURL = properties.getProperty("domain")
        searchURL = properties.getProperty("searchURL")
        getKeyURL = properties.getProperty("getKeyURL")
        getM3U8URL = properties.getProperty("getM3U8URL")
    }


    suspend fun searchMovies(keyword: String): List<Movie> {
        val request = Request.Builder()
            .url(searchURL.format(keyword))
            .get()
            .build()
        val response = globalHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            logger.error("searchMovies error: statusCode = ${response.code}")
            return emptyList()
        }
        val htmlStr = response.body!!.string()
        val document = Jsoup.parse(htmlStr)
        // select 搜索返回列表
        val items = document.select("div.hl-item-div")
        val moviesList = mutableListOf<Movie>()
        // 遍历搜索返回列表，提取必要信息
        for (item in items) {
            val imageLabel = item.select("div.hl-item-pic>a.hl-item-thumb")
            val coverURL = imageLabel.attr("data-original")
            val title = imageLabel.attr("title")
            val key = imageLabel.attr("href").encodeURLBase64()
            val detail = item.select("div.hl-item-content>p.hl-item-sub")
            val category = detail[0].textNodes().fold("") { p, textNode -> p + textNode.text().trim() }
            val actor = detail[1].textNodes().fold("") { p, textNode -> p + textNode.text().trim() }
            val intro = detail[2].textNodes().fold("") { p, textNode -> p + textNode.text().trim() }
            moviesList += Movie(title, coverURL, key, category, actor, intro)
        }
        return moviesList
    }

    suspend fun parseSeries(key: String): List<Sources> {
        val request = Request.Builder()
            .url(domainURL + key.decodeBase64String())
            .get()
            .build()
        val response = globalHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            logger.error("parseSeries error: statusCode = ${response.code}")
            return emptyList()
        }
        val htmlStr = response.body!!.string()
        val document = Jsoup.parse(htmlStr)
        // select 源数量
        val sourceNames = document.select("div.hl-plays-from>a.hl-tabs-btn")
        val sourcePlayLists = document.select("div.hl-play-source>div.hl-tabs-box")
        val sourcesList = mutableListOf<Sources>()
        var i = 0
        while (i < Math.min(sourceNames.size, sourcePlayLists.size)) {
            val seriesItems = sourcePlayLists[i].select("ul.hl-plays-list>li.hl-col-xs-4")
            // select 选集列表
            val playList = mutableListOf<Series>()
            // 遍历选集列表获取选集名，key
            for (seriesItem in seriesItems) {
                val tag = seriesItem.selectFirst("a")!!
                val title = tag.textNodes().fold("") { p, textNode -> p + textNode.text().trim() }
                playList += Series(title, tag.attr("href").encodeURLBase64())
            }
            sourcesList += Sources(sourceNames[i].attr("alt"), playList)
            i++
        }

        return sourcesList
    }

    suspend fun parseM3U8URL(key: String): String? {
        var request = Request.Builder()
            .url(domainURL + key.decodeBase64String())
            .get()
            .build()
        // 请求视频播放页以获得 playerAAA
        var response = globalHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            logger.error("parseM3U8URL error: statusCode = ${response.code}")
            return null
        }
        var responseText = response.body!!.string()
        // 查找 playerAAA
        var matchResult = regex_1.find(responseText)
        if (matchResult == null) {
            logger.error("parseM3U8URL error: regex_1 匹配失败")
            return null
        }
        // 解析 JSON
        val playerAAA = JsonParser.parseString(matchResult.groupValues[1]) as JsonObject
        // 根据 JSON 发起 GET
        request = Request.Builder()
            .url(getKeyURL.format(playerAAA["url"].asString, playerAAA["tm"].asInt.toString(),
                    playerAAA["key"].asString, playerAAA["link_next"].asString, ""))
            .get()
            .build()
        response = globalHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            logger.error("parseM3U8URL getKeyURL error: statusCode = ${response.code}")
            return null
        }
        // 全变到一行去，为正则匹配作准备
        responseText = response.body!!.string().replace("\r\n", "").replace("\n", "")
        logger.info(responseText)
        // 查找 config
        matchResult = regex_2.find(responseText)
        if (matchResult == null) {
            logger.error("parseM3U8URL error: regex_2 匹配失败")
            return null
        }

        val config = JsonParser.parseString(matchResult.groupValues[1] + "}") as JsonObject
        // 获取 m3u8 URL
        val formBody = FormBody.Builder()
            .add("url", config["url"].asString)
            .add("time", config["time"].asString)
            .add("key", config["key"].asString)
            .build()
        request = Request.Builder()
            .url(getM3U8URL)
            .post(formBody)
            .build()
        response = globalHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            logger.error("parseM3U8URL getM3U8URL error: statusCode = ${response.code}")
            return null
        }
        responseText = response.body!!.string()
        // 解析 JSON 得到M3U8 URL
        val respJSON = JsonParser.parseString(responseText) as JsonObject
        if (respJSON["success"].asInt != 1) {
            logger.error("parseM3U8URL error: ${respJSON}")
            return null
        }
        return respJSON["url"].asString
    }
}