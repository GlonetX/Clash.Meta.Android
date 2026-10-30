package com.github.kr328.clash.data

import android.content.*
import android.net.Uri
import android.os.IBinder
import com.github.kr328.clash.LogcatService
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.log.LogcatFilter
import com.github.kr328.clash.log.LogcatReader
import com.github.kr328.clash.util.logsDir
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import java.io.OutputStreamWriter
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LogRepository(private val context:Context){
    suspend fun files()=withContext(Dispatchers.IO){context.logsDir.listFiles().orEmpty().mapNotNull{LogFile.parseFromFileName(it.name)}.sortedByDescending{it.date}}
    suspend fun read(name:String)=withContext(Dispatchers.IO){val file=requireNotNull(LogFile.parseFromFileName(name));LogcatReader(context,file).use{it.readAll()}}
    suspend fun delete(name:String)=withContext(Dispatchers.IO){val file=requireNotNull(LogFile.parseFromFileName(name));context.logsDir.resolve(file.fileName).delete()}
    suspend fun deleteAll()=withContext(Dispatchers.IO){context.logsDir.listFiles().orEmpty().filter{LogFile.parseFromFileName(it.name)!=null}.forEach{it.delete()}}
    fun stop(){context.stopService(Intent(context,LogcatService::class.java))}
    suspend fun level(level:LogMessage.Level)=withClash{val o=queryOverride(Clash.OverrideSlot.Session);o.logLevel=level;patchOverride(Clash.OverrideSlot.Session,o)}
    suspend fun export(uri:Uri,messages:List<LogMessage>)=withContext(Dispatchers.IO){
        val stream=requireNotNull(context.contentResolver.openOutputStream(uri));LogcatFilter(OutputStreamWriter(stream),context).use{out->out.writeHeader(messages.firstOrNull()?.time?:Date());messages.forEach(out::writeMessage)}
    }
    fun stream()=flow {
        var connection:ServiceConnection?=null
        try{
            val service=withContext(Dispatchers.Main){
                context.startForegroundServiceCompat(Intent(context,LogcatService::class.java))
                suspendCancellableCoroutine<LogcatService>{continuation->
                    val conn=object:ServiceConnection{
                        override fun onServiceConnected(name:ComponentName?,binder:IBinder?){val service=binder?.queryLocalInterface("") as? LogcatService;if(continuation.isActive){if(service!=null)continuation.resume(service)else continuation.resumeWithException(IllegalStateException("Log service unavailable"))}}
                        override fun onServiceDisconnected(name:ComponentName?){}
                    }
                    connection=conn
                    if(!context.bindService(Intent(context,LogcatService::class.java),conn,Context.BIND_AUTO_CREATE))continuation.resumeWithException(IllegalStateException("Unable to bind log service"))
                }
            }
            while(currentCoroutineContext().isActive){service.snapshot(true)?.let{emit(it.messages)};delay(500)}
        }finally{connection?.let{withContext(NonCancellable+Dispatchers.Main){runCatching{context.unbindService(it)}}}}
    }
}
