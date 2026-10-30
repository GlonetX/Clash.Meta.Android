package com.github.kr328.clash.presentation

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InstalledApp(val packageName:String,val label:String,val system:Boolean,val installed:Long,val updated:Long)
class AccessControlViewModel(app:Application):AndroidViewModel(app){
    val apps=MutableStateFlow<List<InstalledApp>>(emptyList());val error=MutableStateFlow<String?>(null)
    init{viewModelScope.launch{try{apps.value=withContext(Dispatchers.IO){val pm=app.packageManager;pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).filter{it.packageName!=app.packageName&&(it.requestedPermissions?.contains(android.Manifest.permission.INTERNET)==true||(it.applicationInfo?.uid?:10000)<10000)}.mapNotNull{p->p.applicationInfo?.let{InstalledApp(p.packageName,it.loadLabel(pm).toString(),it.flags and ApplicationInfo.FLAG_SYSTEM!=0,p.firstInstallTime,p.lastUpdateTime)}}}}catch(e:Exception){error.value=e.message}}}
}
