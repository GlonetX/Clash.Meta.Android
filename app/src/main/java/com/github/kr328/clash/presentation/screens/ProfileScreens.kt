package com.github.kr328.clash.presentation.screens

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.FeatureViewModel
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.settings.UiPreferences
import com.github.kr328.clash.service.settings.SubscriptionAnnotation
import java.text.DateFormat
import java.util.Date

@Composable fun ProfilesScreen(onEdit: (String)->Unit, onCreate: ()->Unit, preferences: UiPreferences, onPreferences: ((UiPreferences)->UiPreferences)->Unit, model: FeatureViewModel = viewModel()) {
    val profiles by model.profiles.collectAsStateWithLifecycle(); val busy by model.busy.collectAsStateWithLifecycle(); val error by model.error.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }; var deleting by remember { mutableStateOf<Profile?>(null) }
    var sharing by remember { mutableStateOf<Profile?>(null) }
    var annotating by remember { mutableStateOf<Profile?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) { model.loadProfiles() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically) { OutlinedTextField(query,{query=it},Modifier.weight(1f),label={Text(text("Search subscriptions","搜索订阅"))},singleLine=true); IconButton(onClick=onCreate){Icon(Icons.Default.Add,text("Add","添加"))} } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = { model.action {
                profiles.filter { it.imported && it.type != Profile.Type.File }.forEach { model.repository.update(it) }
                model.loadProfiles()
            } }) { Text(text("Update all", "全部更新")) }
        } }
        if(error!=null) item { ErrorState(error!!){model.loadProfiles()} }
        if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(profiles.isEmpty()&&!busy) item { EmptyState(text("Add a subscription to get started","添加订阅后开始连接")){Button(onClick=onCreate){Text(text("Add subscription","添加订阅"))}} }
        items(profiles.filter { p -> val annotation=preferences.subscriptionMetadata[p.uuid.toString()]; p.name.contains(query,true) || annotation?.tag?.contains(query,true)==true || annotation?.note?.contains(query,true)==true },key={it.uuid.toString()}) { p ->
            ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(p.name,style=MaterialTheme.typography.titleLarge); Text(p.type.name,style=MaterialTheme.typography.labelMedium) }; RadioButton(p.active,onClick={model.action{model.repository.activate(p);model.loadProfiles()}}) }
                preferences.subscriptionMetadata[p.uuid.toString()]?.let { annotation ->
                    if (annotation.tag.isNotBlank()) Text(annotation.tag, style=MaterialTheme.typography.labelMedium)
                    if (annotation.note.isNotBlank()) Text(annotation.note, style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick={annotating=p}) { Text(text("Tag and note", "标签和备注")) }
                if(p.total>0) { Text("${bytes(p.upload+p.download)} / ${bytes(p.total)}"); LinearProgressIndicator(progress={((p.upload+p.download).toFloat()/p.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth()) }
                if(p.expire>0) Text(text("Expires: ","到期：")+DateFormat.getDateInstance().format(Date(p.expire)))
                if(p.updatedAt>0) Text(text("Updated: ","更新：")+DateFormat.getDateTimeInstance().format(Date(p.updatedAt)),style=MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick={onEdit(p.uuid.toString())}){Text(text("Edit","编辑"))}; TextButton(enabled=!busy,onClick={model.action{model.repository.update(p);model.loadProfiles()}}){Text(text("Update","更新"))}; IconButton(onClick={model.action{onEdit(model.repository.clone(p))}}){Icon(Icons.Default.ContentCopy,text("Duplicate","复制"))}; IconButton(onClick={sharing=p},enabled=p.type==Profile.Type.Url && p.source.isNotBlank()){Icon(Icons.Default.QrCode,text("Share QR code","分享二维码"))}; IconButton(onClick={deleting=p}){Icon(Icons.Default.Delete,text("Delete","删除"))} }
            } }
        }
    }
    annotating?.let { p ->
        val original = preferences.subscriptionMetadata[p.uuid.toString()] ?: SubscriptionAnnotation()
        var tag by rememberSaveable(p.uuid.toString()) { mutableStateOf(original.tag) }
        var note by rememberSaveable(p.uuid.toString()) { mutableStateOf(original.note) }
        AlertDialog(onDismissRequest={annotating=null}, title={Text(text("Tag and note", "标签和备注"))}, text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tag,{if(it.length<=256)tag=it},label={Text(text("Tag", "标签"))},singleLine=true)
                OutlinedTextField(note,{if(it.length<=4096)note=it},label={Text(text("Note", "备注"))})
            }
        }, confirmButton={TextButton(onClick={val id=p.uuid.toString();val value=SubscriptionAnnotation(tag,note);val remove=tag.isBlank()&&note.isBlank();onPreferences { ui -> ui.copy(subscriptionMetadata=if(remove) ui.subscriptionMetadata-id else ui.subscriptionMetadata+(id to value)) };annotating=null}){Text(text("Save", "保存"))}}, dismissButton={TextButton(onClick={annotating=null}){Text(text("Cancel", "取消"))}})
    }
    sharing?.let { p ->
        val bitmap = remember(p.source) { runCatching {
            val matrix = QRCodeWriter().encode(p.source, BarcodeFormat.QR_CODE, 512, 512)
            val pixels = IntArray(512 * 512) { i -> if (matrix[i % 512, i / 512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            Bitmap.createBitmap(pixels, 512, 512, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull() }
        AlertDialog(onDismissRequest = { sharing = null }, title = { Text(p.name) }, text = {
            if (bitmap != null) Image(bitmap, contentDescription = text("Subscription QR code", "订阅二维码"), modifier = Modifier.fillMaxWidth())
            else Text(text("Unable to generate QR code", "无法生成二维码"))
        }, confirmButton = { TextButton(onClick = {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, p.name).putExtra(Intent.EXTRA_TEXT, p.source), p.name))
        }) { Text(text("Share", "分享")) } }, dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(p.source)); sharing = null }) { Text(text("Copy link", "复制链接")) } })
    }
    deleting?.let{ p -> ConfirmDialog(text("Delete subscription?","删除订阅？"),p.name,{deleting=null}){deleting=null;model.action{model.repository.delete(p);model.loadProfiles()}} }
}

@Composable fun NewProfileScreen(onCreated:(String)->Unit,onScan:()->Unit,onImportFile:()->Unit,onExternal:()->Unit,model:FeatureViewModel=viewModel()) {
    var name by rememberSaveable{mutableStateOf("")};var url by rememberSaveable{mutableStateOf("")}
    val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle()
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item{Text(text("Your connection, your rules","由你掌控连接规则"),style=MaterialTheme.typography.headlineMedium)}
        item{OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text(text("Name","名称"))},singleLine=true)}
        item{OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text(text("Subscription URL","订阅链接"))},singleLine=true)}
        item{Button(enabled=!busy&&url.isNotBlank(),onClick={model.action{onCreated(model.repository.create(Profile.Type.Url,name.ifBlank{"Subscription"},url.trim()))}},modifier=Modifier.fillMaxWidth()){Text(text("Continue","继续"))}}
        if(error!=null)item{ErrorState(error!!){model.error.value=null}}
        item{SectionTitle(text("Other sources","其他来源"))}
        item{InfoCard(text("Scan QR code","扫描二维码"),onClick=onScan,trailing={Icon(Icons.Default.QrCodeScanner,null)})}
        item{InfoCard(text("Import a configuration file","导入配置文件"),onClick=onImportFile,trailing={Icon(Icons.Default.FolderOpen,null)})}
        item{InfoCard(text("Create local configuration","新建本地配置"),onClick={model.action{onCreated(model.repository.create(Profile.Type.File,name.ifBlank{"Local configuration"}))}})}
        item{InfoCard(text("External profile providers","外部配置提供程序"),onClick=onExternal)}
    }
}

@Composable fun ProfileEditorScreen(id:String,onBack:()->Unit,onFiles:(String)->Unit,model:FeatureViewModel=viewModel()) {
    val profile by model.profile.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();val done by model.committed.collectAsStateWithLifecycle();val progress by model.progress.collectAsStateWithLifecycle()
    var confirmExit by remember{mutableStateOf(false)};var showSecret by remember{mutableStateOf(false)}
    var filesVisited by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(id){model.loadProfile(id)};LaunchedEffect(done){if(done)onBack()}
    BackHandler { if(filesVisited || profile!=model.loadedProfile)confirmExit=true else model.discardProfile(onBack) }
    val p=profile
    if(p==null){if(error!=null)ErrorState(error!!){model.loadProfile(id)}else BusyState();return}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item{OutlinedTextField(p.name,{s->model.editProfile{it.copy(name=s)}},Modifier.fillMaxWidth(),label={Text(text("Name","名称"))},enabled=!busy,singleLine=true)}
        if(p.type!=Profile.Type.File)item{OutlinedTextField(p.source,{s->model.editProfile{it.copy(source=s)}},Modifier.fillMaxWidth(),label={Text(text("Source URL","订阅地址"))},enabled=!busy)}
        item{var interval by rememberSaveable(p.uuid.toString()){mutableStateOf((p.interval/60000).toString())};OutlinedTextField(interval,{s->interval=s;s.toLongOrNull()?.takeIf{it>=0&&it<=525600}?.let{n->model.editProfile{it.copy(interval=n*60000)}}},Modifier.fillMaxWidth(),label={Text(text("Update interval (minutes, 0 disables)","更新间隔（分钟，0 为关闭）"))},singleLine=true,enabled=!busy)}
        item{OutlinedTextField(p.ageSecretKey.orEmpty(),{s->model.editProfile{it.copy(ageSecretKey=s.ifBlank{null})}},Modifier.fillMaxWidth(),label={Text(text("age decryption key","age 解密密钥"))},visualTransformation=if(showSecret)androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={showSecret=!showSecret}){Icon(Icons.Default.Visibility,null)}},enabled=!busy)}
        item{InfoCard(text("Configuration and provider files","配置和 Provider 文件"),onClick={filesVisited=true;onFiles(id)},trailing={Icon(Icons.Default.FolderOpen,null)})}
        if(error!=null)item{ErrorState(error!!){model.error.value=null}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth());progress?.let{Text(it,style=MaterialTheme.typography.bodySmall)}}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){OutlinedButton(onClick={confirmExit=true},enabled=!busy,modifier=Modifier.weight(1f)){Text(text("Discard","放弃"))};Button(onClick={model.saveProfile()},enabled=!busy&&p.name.isNotBlank(),modifier=Modifier.weight(1f)){Text(text("Save and validate","保存并验证"))}}}
    }
    if(confirmExit)ConfirmDialog(text("Discard changes?","放弃修改？"),text("Your saved configuration will not be changed.","已保存的配置不会改变。"),{confirmExit=false}){confirmExit=false;model.discardProfile(onBack)}
}
