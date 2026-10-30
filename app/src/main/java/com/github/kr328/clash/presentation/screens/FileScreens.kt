package com.github.kr328.clash.presentation.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.FeatureViewModel
import com.github.kr328.clash.design.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun FilesScreen(id:String,onBack:()->Unit,model:FeatureViewModel=viewModel()) {
    val context=LocalContext.current
    var parent by rememberSaveable(id){mutableStateOf(id)};var editing by remember{mutableStateOf<File?>(null)};var deleting by remember{mutableStateOf<File?>(null)};var rename by rememberSaveable{mutableStateOf("")};var transfer by rememberSaveable{mutableStateOf<String?>(null)}
    val files by model.files.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->val target=transfer;if(uri!=null&&target!=null)model.action{model.repository.exportFile(target,uri)};transfer=null}
    val replace=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->val target=transfer;if(uri!=null&&target!=null)model.action{model.repository.replaceFile(target,uri);model.files.value=model.repository.files(parent)};transfer=null}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.action{val name=withContext(Dispatchers.IO){context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else null}}?:"provider.yaml";require('/' !in name&&'\\' !in name&&name!="..") {"Invalid file name"};model.repository.importFile(parent,uri,name);model.files.value=model.repository.files(parent)}}
    LaunchedEffect(parent){model.loadFiles(parent)}
    BackHandler{if(parent!=id)parent=parent.substringBeforeLast('/',id)else onBack()}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Row(verticalAlignment=Alignment.CenterVertically){if(parent!=id)IconButton(onClick={parent=parent.substringBeforeLast('/',id)}){Icon(Icons.Default.ArrowUpward,text("Parent directory","上级目录"))};Text(parent.substringAfter(id).ifBlank{"/"},Modifier.weight(1f));if(parent.contains("/providers"))IconButton(onClick={import.launch(arrayOf("*/*"))}){Icon(Icons.Default.UploadFile,text("Import","导入"))};IconButton(onClick={model.loadFiles(parent)}){Icon(Icons.Default.Refresh,text("Refresh","刷新"))}}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())};if(error!=null)item{ErrorState(error!!){model.loadFiles(parent)}}
        if(files.isEmpty()&&!busy)item{EmptyState(text("This folder is empty","文件夹为空"))}
        items(files,key={it.id}){f->Column{InfoCard(f.name,if(f.isDirectory)text("Folder","文件夹")else bytes(f.size),onClick={if(f.isDirectory)parent=if(f.id.startsWith(id))f.id else "$parent/${f.id}" else {val documentId=if(f.id.startsWith(id))f.id else "$parent/${f.id}";val uri=model.repository.fileUri(documentId);runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_EDIT).setDataAndType(uri,"text/plain").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),f.name))}.onFailure{model.error.value=it.message}}},trailing={Icon(if(f.isDirectory)Icons.Default.Folder else Icons.Default.Description,null)})
            if(!f.isDirectory)Row{TextButton(onClick={transfer=if(f.id.startsWith(id))f.id else "$parent/${f.id}";export.launch(f.name)}){Text(text("Export","导出"))};TextButton(onClick={transfer=if(f.id.startsWith(id))f.id else "$parent/${f.id}";replace.launch(arrayOf("*/*"))}){Text(text("Replace","替换"))};if(parent.contains("/providers")){TextButton(onClick={editing=f;rename=f.name}){Text(text("Rename","重命名"))};TextButton(onClick={deleting=f}){Text(text("Delete","删除"))}}}
        }}
    }
    editing?.let{f->AlertDialog(onDismissRequest={editing=null},title={Text(text("Rename file","重命名文件"))},text={OutlinedTextField(rename,{rename=it},singleLine=true)},confirmButton={TextButton(onClick={editing=null;model.action{require(rename.isNotBlank()&&'/' !in rename&&'\\' !in rename&&rename!=".."){"Invalid file name"};model.repository.renameFile(if(f.id.startsWith(id))f.id else "$parent/${f.id}",rename);model.files.value=model.repository.files(parent)}}){Text(text("Save","保存"))}},dismissButton={TextButton(onClick={editing=null}){Text(text("Cancel","取消"))}})}
    deleting?.let{f->ConfirmDialog(text("Delete file?","删除文件？"),f.name,{deleting=null}){deleting=null;model.action{model.repository.deleteFile(if(f.id.startsWith(id))f.id else "$parent/${f.id}");model.files.value=model.repository.files(parent)}}}
}
