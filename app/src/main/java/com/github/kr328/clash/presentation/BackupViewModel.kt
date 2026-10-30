package com.github.kr328.clash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.backup.*
import com.github.kr328.clash.service.remote.IBackupManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BackupViewModel(private val saved:SavedStateHandle):ViewModel(){
    val config=MutableStateFlow<BackupConfig?>(null);val files=MutableStateFlow<List<BackupRemoteFile>>(emptyList());val job=MutableStateFlow<BackupJob?>(null);val error=MutableStateFlow<String?>(null);val busy=MutableStateFlow(false)
    private var poll:Job?=null
    init{action{config.value=client{getConfig()};saved.get<String>("backup.job")?.let{watch(it)}}}
    private suspend fun<T> client(block:suspend IBackupManager.()->T):T=withContext(Dispatchers.IO){Remote.service.remote.get().backup().block()}
    private fun action(block:suspend()->Unit)=viewModelScope.launch{busy.value=true;try{error.value=null;block()}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message?:"Backup operation failed"}finally{busy.value=false}}
    fun configure(value:BackupConfig,password:String?)=action{client{saveConfig(value,password)};config.value=client{getConfig()}}
    fun list()=action{files.value=client{listRemote()}}
    fun backup(passphrase:ByteArray)=action{try{watch(client{startBackup(passphrase)})}finally{passphrase.fill(0)}}
    fun prepare(name:String,passphrase:ByteArray)=action{try{watch(client{prepareRestore(name,passphrase)})}finally{passphrase.fill(0)}}
    fun commit()=action{job.value?.let{j->client{commitRestore(j.id)}}}
    fun cancel()=action{job.value?.let{j->client{cancel(j.id)}}}
    private fun watch(id:String){saved["backup.job"]=id;poll?.cancel();poll=viewModelScope.launch{try{while(isActive){val value=client{getJob(id)};job.value=value;if(value==null){error.value="Task is no longer available. Start again.";saved.remove<String>("backup.job");break};if(value.terminal){saved.remove<String>("backup.job");if(value.phase=="Completed")files.value=client{listRemote()};break};delay(500)}}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message}}}
}
