package com.example.puretube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloaderImpl : Downloader() {
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = request.httpMethod()
        
        request.headers().forEach { (key, list) ->
            for (value in list) {
                connection.addRequestProperty(key, value)
            }
        }
        
        if (request.dataToSend() != null) {
            connection.doOutput = true
            connection.outputStream.write(request.dataToSend())
        }
        
        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage
        
        val headers = mutableMapOf<String, List<String>>()
        connection.headerFields.forEach { (key, value) ->
            if (key != null) {
                headers[key] = value
            }
        }
        
        val bodyStr = try {
            if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
        } catch (e: Exception) {
            ""
        }
        
        val latestUrl = connection.url.toString()
        
        return Response(responseCode, responseMessage, headers, bodyStr, latestUrl)
    }
}
