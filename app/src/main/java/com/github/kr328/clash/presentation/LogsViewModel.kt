package com.github.kr328.clash.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.data.LogRepository
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.model.LogFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class LogsViewModel(app:Application):AndroidViewModel(app){
    val repository=LogRepository(app);val files=MutableStateFlow<List<LogFile>>(emptyList());val messages=MutableStateFlow<List<LogMessage>>(emptyList());val error=MutableStateFlow<String?>(null);val loading=MutableStateFlow(false)
    private var streaming:Job?=null
    fun action(block:suspend()->Unit)=viewModelScope.launch{loading.value=true;try{block();error.value=null}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message?:"Log operation failed"}finally{loading.value=false}}
    fun refresh()=action{files.value=repository.files()}
    fun read(name:String)=action{messages.value=repository.read(name)}
    fun stream(){streaming?.cancel();streaming=viewModelScope.launch{try{repository.stream().collect{messages.value=it}}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message}}}
    fun pause(){streaming?.cancel();streaming=null}
    fun stop(){pause();repository.stop()}
    fun export(uri:Uri)=action{repository.export(uri,messages.value)}
}
