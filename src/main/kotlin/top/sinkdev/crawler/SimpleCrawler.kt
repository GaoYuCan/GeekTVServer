package top.sinkdev.crawler

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.http.*
import io.ktor.util.*
import org.jsoup.Jsoup
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import top.sinkdev.encodeURLBase64
import top.sinkdev.globalGson
import top.sinkdev.globalHttpClient
import top.sinkdev.model.Movie
import top.sinkdev.model.Series
import java.util.Properties


object SimpleCrawler {
    private val domainURL: String
    private val searchURL: String
    private val getKeyURL: String
    private val getM3U8URL: String

    // 正则表达式
    private val regex_1 = "var player_aaaa=(\\{.+})".toRegex()
    private val regex_2 = "var config = (\\{.+?})".toRegex()

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
        val response = globalHttpClient.get(searchURL.format(keyword))
        if (response.status != HttpStatusCode.OK) {
            logger.error("searchMovies error: statusCode = ${response.status}")
            return emptyList()
        }
        val htmlStr = response.body<String>()
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

    suspend fun parseSeries(key: String): List<Series> {
        val response = globalHttpClient.get(domainURL + key.decodeBase64String())
        if (response.status != HttpStatusCode.OK) {
            logger.error("parseSeries error: statusCode = ${response.status}")
            return emptyList()
        }
        val htmlStr = response.body<String>()
        val document = Jsoup.parse(htmlStr)
        // select 选集列表
        val seriesItems = document.select("ul.hl-plays-list>li.hl-col-xs-4")
        val seriesList = mutableListOf<Series>()
        // 遍历选集列表获取选集名，key
        for (seriesItem in seriesItems) {
            val tag = seriesItem.selectFirst("a")!!
            val title = tag.textNodes().fold("") { p, textNode -> p + textNode.text().trim() }
            seriesList += Series(title, tag.attr("href").encodeURLBase64())
        }
        return seriesList
    }

    suspend fun parseM3U8URL(key: String): String? {
        // 请求视频播放页以获得 playerAAA
        var response = globalHttpClient.get(domainURL + key.decodeBase64String())
        if (response.status != HttpStatusCode.OK) {
            logger.error("parseM3U8URL error: statusCode = ${response.status}")
            return null
        }
        var responseText = response.body<String>()
        // 查找 playerAAA
        var matchResult = regex_1.find(responseText)
        if (matchResult == null) {
            logger.error("parseM3U8URL error: regex_1 匹配失败")
            return null
        }
        // 解析 JSON
        val playerAAA = JsonParser.parseString(matchResult.groupValues[1]) as JsonObject
        // 根据 JSON 发起 GET
        response = globalHttpClient.get(getKeyURL.format(playerAAA["url"].asString,
                playerAAA["tm"].asInt.toString(), playerAAA["key"].asString, playerAAA["link_next"].asString, ""))
        if (response.status != HttpStatusCode.OK) {
            logger.error("parseM3U8URL getKeyURL error: statusCode = ${response.status}")
            return null
        }
        // 全变到一行去，为正则匹配作准备
        responseText = response.body<String>().replace("\r\n", "").replace("\n", "")
        // 查找 config
        matchResult = regex_2.find(responseText)
        if (matchResult == null) {
            logger.error("parseM3U8URL error: regex_2 匹配失败")
            return null
        }

        val config =  JsonParser.parseString(matchResult.groupValues[1]) as JsonObject
        // 获取 M3U8 URL
        response = globalHttpClient.post(getM3U8URL) {
            formData {
                parameter("url", config["url"].asString)
                parameter("time", config["time"].asInt)
                parameter("key", config["key"].asString)
            }
        }
        if (response.status != HttpStatusCode.OK) {
            logger.error("parseM3U8URL getM3U8URL error: statusCode = ${response.status}")
            return null
        }
        responseText = response.body<String>()
        // 解析 JSON 得到M3U8 URL
        val respJSON =  JsonParser.parseString(responseText) as JsonObject
        if (respJSON["success"].asInt != 1) {
            logger.error("parseM3U8URL error: ${respJSON}")
            return null
        }
        return respJSON["url"].asString
    }
}