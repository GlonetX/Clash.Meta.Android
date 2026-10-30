package com.github.kr328.clash.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.data.ClientRepository
import com.github.kr328.clash.core.model.ConfigurationOverride
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class OverrideViewModel(app:Application):AndroidViewModel(app){
    private val repository=ClientRepository(app)
    val codec=Json{encodeDefaults=true;explicitNulls=true;prettyPrint=true}
    val draft=MutableStateFlow<JsonObject?>(null);val busy=MutableStateFlow(false);val error=MutableStateFlow<String?>(null);val saved=MutableStateFlow(false)
    private var original:JsonObject?=null
    val dirty get()=draft.value!=original
    init{load()}
    fun load()=viewModelScope.launch{busy.value=true;try{val value=codec.encodeToJsonElement(ConfigurationOverride.serializer(),repository.override()).jsonObject;draft.value=value;original=value;error.value=null}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message}finally{busy.value=false}}
    fun set(path:List<String>,value:JsonElement){
        fun replace(node:JsonObject,remaining:List<String>):JsonObject{val key=remaining.first();return JsonObject(node.toMutableMap().apply{put(key,if(remaining.size==1)value else replace((node[key] as? JsonObject)?:JsonObject(emptyMap()),remaining.drop(1)))})}
        draft.value=draft.value?.let{replace(it,path)}
    }
    fun parse(value:String){draft.value=codec.parseToJsonElement(value).jsonObject}
    fun save()=viewModelScope.launch{busy.value=true;try{val value=requireNotNull(draft.value);val model=codec.decodeFromJsonElement(ConfigurationOverride.serializer(),value);repository.saveOverride(model);original=value;saved.value=true;error.value=null}catch(e:CancellationException){throw e}catch(e:Exception){error.value=e.message}finally{busy.value=false}}
}
