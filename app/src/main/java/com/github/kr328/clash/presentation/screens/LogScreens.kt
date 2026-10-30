package com.github.kr328.clash.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.LogsViewModel
import com.github.kr328.clash.core.model.LogMessage
import java.text.DateFormat

@Composable fun LogsScreen(onOpen:(String)->Unit,model:LogsViewModel=viewModel()){
    val files by model.files.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();var confirm by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){model.refresh()}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row{Button(onClick={onOpen("live")}){Text(text("Live logs","实时日志"))};Spacer(Modifier.width(12.dp));TextButton(enabled=files.isNotEmpty(),onClick={confirm=true}){Text(text("Delete all","全部删除"))}}}
        if(error!=null)item{ErrorState(error!!){model.refresh()}}
        items(files,key={it.fileName}){file->InfoCard(DateFormat.getDateTimeInstance().format(file.date),file.fileName,onClick={onOpen(file.fileName)},trailing={Icon(Icons.Default.Description,null)})}
        if(files.isEmpty())item{EmptyState(text("No saved logs","没有保存的日志"))}
    }
    if(confirm)ConfirmDialog(text("Delete saved logs?","删除已保存日志？"),text("This cannot be undone.","此操作无法撤销。"),{confirm=false}){confirm=false;model.action{model.repository.deleteAll();model.files.value=model.repository.files()}}
}

@Composable fun LogViewerScreen(name:String,onBack:()->Unit,model:LogsViewModel=viewModel()){
    val messages by model.messages.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();var query by rememberSaveable{mutableStateOf("")};var paused by rememberSaveable{mutableStateOf(false)};var level by rememberSaveable{mutableStateOf("Debug")};var confirm by remember{mutableStateOf(false)}
    val list=rememberLazyListState();var follow by rememberSaveable{mutableStateOf(true)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")){uri->if(uri!=null)model.export(uri)}
    LaunchedEffect(name,paused){if(name=="live"){if(!paused)model.stream()else model.pause()}else model.read(name)}
    DisposableEffect(Unit){onDispose{model.pause()}}
    val filtered=messages.filter{it.message.contains(query,true)&&(level=="Debug"||it.level.ordinal>=(runCatching{LogMessage.Level.valueOf(level)}.getOrDefault(LogMessage.Level.Debug)).ordinal)}
    LaunchedEffect(filtered.size,follow){if(follow&&filtered.isNotEmpty())list.animateScrollToItem(filtered.lastIndex+3)}
    LazyColumn(state=list,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(text("Search messages","搜索日志"))},singleLine=true)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){if(name=="live")FilterChip(paused,{paused=!paused},label={Text(text("Pause","暂停"))});FilterChip(follow,{follow=!follow},label={Text(text("Follow","跟随"))});TextButton(onClick={export.launch(if(name=="live")"glonetx-live.log"else name)}){Text(text("Export","导出"))};TextButton(onClick={confirm=true}){Text(if(name=="live")text("Stop","停止")else text("Delete","删除"))}}}
        item{Row{listOf(LogMessage.Level.Debug,LogMessage.Level.Info,LogMessage.Level.Warning,LogMessage.Level.Error).forEach{l->FilterChip(level==l.name,{level=l.name;if(name=="live")model.action{model.repository.level(l)}},label={Text(l.name)})}}}
        if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
        itemsIndexed(filtered,key={index,m->"${m.time.time}/$index"}){_,m->Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(12.dp)){Text("${DateFormat.getTimeInstance().format(m.time)} · ${m.level}",style=MaterialTheme.typography.labelSmall,color=if(m.level==LogMessage.Level.Error)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary);androidx.compose.foundation.text.selection.SelectionContainer{Text(m.message,style=MaterialTheme.typography.bodySmall)}}}}
    }
    if(confirm)ConfirmDialog(if(name=="live")text("Stop recording?","停止记录？")else text("Delete log?","删除日志？"),name,{confirm=false}){confirm=false;if(name=="live"){model.stop();onBack()}else model.action{model.repository.delete(name);onBack()}}
}
