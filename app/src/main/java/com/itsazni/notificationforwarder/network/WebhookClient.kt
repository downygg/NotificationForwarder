package com.itsazni.notificationforwarder.network

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.itsazni.notificationforwarder.data.QueueItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class SendResult(val success:Boolean, val isPermanentFailure:Boolean, val message:String, val httpStatus:Int?=null)
class WebhookClient {
    private val gson=Gson()
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).writeTimeout(20,TimeUnit.SECONDS).addInterceptor(HttpLoggingInterceptor().apply{level=HttpLoggingInterceptor.Level.BASIC}).build()
    fun send(url:String,method:String,headers:Map<String,String>,queryParams:Map<String,String>,payloadTemplate:String,item:QueueItem,deviceId:String):SendResult=try{
        val vars=mapOf("deviceId" to deviceId,"packageName" to escapeJson(item.packageName),"appName" to escapeJson(item.appName),"title" to escapeJson(item.title),"text" to escapeJson(item.text),"postedAt" to item.postedAt.toString(),"notificationKey" to escapeJson(item.notificationKey))
        val finalUrl=buildUrl(url,queryParams)
        val bodyJson=if(payloadTemplate.isBlank()) gson.toJson(mapOf("deviceId" to deviceId,"packageName" to item.packageName,"appName" to item.appName,"title" to item.title,"text" to item.text,"postedAt" to item.postedAt,"notificationKey" to item.notificationKey)) else renderTemplate(payloadTemplate,vars)
        val builder=Request.Builder().url(finalUrl)
        if(method.equals("GET",true)) builder.get() else builder.method(method.uppercase(),bodyJson.toRequestBody((headers["Content-Type"]?:"application/json").toMediaType()))
        headers.forEach{(k,v)->builder.addHeader(k,v)}
        client.newCall(builder.build()).execute().use{r->if(r.isSuccessful) SendResult(true,false,"OK",r.code) else SendResult(false,r.code in 400..499&&r.code!=429,"HTTP ${r.code}",r.code)}
    }catch(e:Exception){SendResult(false,false,e.message?:"network error")}
    private fun buildUrl(base:String,params:Map<String,String>):String{if(params.isEmpty())return base;val u=base.toHttpUrlOrNull()?:return base;return u.newBuilder().apply{params.forEach{(k,v)->addQueryParameter(k,v)}}.build().toString()}
    private fun renderTemplate(template:String,vars:Map<String,String>):String{var r=template;vars.forEach{(k,v)->r=r.replace("{$k}",v)};JsonParser.parseString(r);return r}
    private fun escapeJson(t:String)=t.replace("\\","\\\\").replace("\"","\\\"").replace("\b","\\b").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")
}
