@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package com.github.kr328.clash.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.OverrideViewModel
import com.github.kr328.clash.core.model.ConfigurationOverride
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*

@Composable fun OverrideScreen(meta:Boolean,onBack:()->Unit,model:OverrideViewModel=viewModel()){
    val draft by model.draft.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();val saved by model.saved.collectAsStateWithLifecycle()
    var discard by remember{mutableStateOf(false)};var advanced by remember{mutableStateOf(false)};var raw by remember{mutableStateOf("")};var rawError by remember{mutableStateOf<String?>(null)}
    BackHandler{if(model.dirty)discard=true else onBack()};LaunchedEffect(saved){if(saved)onBack()}
    val value=draft
    if(value==null){if(error!=null)ErrorState(error!!){model.load()}else BusyState();return}
    val descriptor=ConfigurationOverride.serializer().descriptor
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text(text("Inherit leaves the subscription value unchanged. Changes apply when the configuration reloads.","继承表示沿用订阅中的值。修改将在配置重新加载时生效。"))}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(enabled=!busy,onClick={model.save()}){Text(text("Save","保存"))};OutlinedButton(onClick={raw=model.codec.encodeToString(JsonObject.serializer(),value);advanced=true}){Text(text("JSON editor","JSON 编辑器"))}}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())};if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
        for(i in 0 until descriptor.elementsCount){val key=descriptor.getElementName(i);val isMeta=key in setOf("sniffer","geox-url","geodata-mode","tcp-concurrent","unified-delay","find-process-mode");if(!meta||isMeta)item(key=key){ConfigurationField(listOf(key),descriptor.getElementDescriptor(i),value[key]?:JsonNull,model::set)}}
    }
    if(discard)ConfirmDialog(text("Discard changes?","放弃修改？"),text("The saved overrides will stay unchanged.","已保存的覆盖配置不会改变。"),{discard=false}){discard=false;onBack()}
    if(advanced)AlertDialog(onDismissRequest={advanced=false},title={Text(text("Configuration overrides","配置覆盖"))},text={Column{OutlinedTextField(raw,{raw=it},Modifier.fillMaxWidth().heightIn(min=160.dp,max=420.dp),isError=rawError!=null);rawError?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={try{val parsed=model.codec.decodeFromString(ConfigurationOverride.serializer(),raw);model.draft.value=model.codec.encodeToJsonElement(ConfigurationOverride.serializer(),parsed).jsonObject;advanced=false;rawError=null}catch(e:Exception){rawError=e.message}}){Text(text("Apply to draft","应用到草稿"))}},dismissButton={TextButton(onClick={advanced=false}){Text(text("Cancel","取消"))}})
}

@Composable private fun ConfigurationField(path:List<String>,descriptor:SerialDescriptor,value:JsonElement,onChange:(List<String>,JsonElement)->Unit){
    val label=path.last();var expanded by rememberSaveable(path.joinToString(".")){mutableStateOf(false)};var menu by remember{mutableStateOf(false)};var edit by remember{mutableStateOf(false)};var input by remember{mutableStateOf("")};var error by remember{mutableStateOf<String?>(null)}
    val kind=descriptor.kind
    if(kind==StructureKind.CLASS||kind==StructureKind.OBJECT){
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.padding(16.dp)){TextButton(onClick={expanded=!expanded}){Text("${if(expanded)"▾"else"▸"} $label",style=MaterialTheme.typography.titleMedium)};if(expanded)for(i in 0 until descriptor.elementsCount){val key=descriptor.getElementName(i);ConfigurationField(path+key,descriptor.getElementDescriptor(i),(value as? JsonObject)?.get(key)?:JsonNull,onChange)}}};return
    }
    val options:List<Pair<String,JsonElement>>?=when(kind){PrimitiveKind.BOOLEAN->listOf(text("Inherit","继承") to JsonNull,text("Enabled","启用") to JsonPrimitive(true),text("Disabled","关闭") to JsonPrimitive(false));SerialKind.ENUM->listOf(text("Inherit","继承") to JsonNull)+(0 until descriptor.elementsCount).map{descriptor.getElementName(it).let{s->s to JsonPrimitive(s)}};else->null}
    val isSecret=label in setOf("secret","authentication")
    val summary=if(value==JsonNull)text("Inherit","继承")else if(isSecret)"••••••••"else(value as? JsonPrimitive)?.content?:value.toString()
    Box{InfoCard(label,summary,onClick={if(options!=null)menu=true else{input=if(value==JsonNull)""else if(kind==PrimitiveKind.STRING)value.jsonPrimitive.content else value.toString();error=null;edit=true}})
        if(options!=null)DropdownMenu(menu,{menu=false}){options.forEach{(title,option)->DropdownMenuItem(text={Text(title)},onClick={onChange(path,option);menu=false})}}
    }
    if(edit)AlertDialog(onDismissRequest={edit=false},title={Text(label)},text={Column{Text(if(kind==StructureKind.LIST||kind==StructureKind.MAP)text("Enter JSON; leave empty to inherit.","输入 JSON，留空表示继承。")else text("Leave empty to inherit.","留空表示继承。"));OutlinedTextField(input,{input=it},Modifier.fillMaxWidth().heightIn(max=300.dp),isError=error!=null,visualTransformation=if(isSecret)androidx.compose.ui.text.input.PasswordVisualTransformation()else androidx.compose.ui.text.input.VisualTransformation.None);error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(onClick={try{val parsed=if(input.isEmpty())JsonNull else if(kind==PrimitiveKind.STRING)JsonPrimitive(input)else Json.parseToJsonElement(input);if(kind==StructureKind.LIST&&parsed!=JsonNull)require(parsed is JsonArray){"Expected a JSON array"};if(kind==StructureKind.MAP&&parsed!=JsonNull)require(parsed is JsonObject){"Expected a JSON object"};if(kind in setOf(PrimitiveKind.INT,PrimitiveKind.LONG,PrimitiveKind.FLOAT,PrimitiveKind.DOUBLE)&&parsed!=JsonNull)require((parsed as? JsonPrimitive)?.doubleOrNull!=null){"Expected a number"};onChange(path,parsed);edit=false}catch(e:Exception){error=e.message}}){Text(text("Apply","应用"))}},dismissButton={TextButton(onClick={edit=false}){Text(text("Cancel","取消"))}})
}
