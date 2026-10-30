package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.BackupViewModel
import com.github.kr328.clash.service.backup.*
import java.security.SecureRandom

private fun generateBackupPassword(): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val random = SecureRandom()
    return buildString(32) { repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) } }
}

@Composable fun BackupScreen(model:BackupViewModel=viewModel()){
    val config by model.config.collectAsStateWithLifecycle();val files by model.files.collectAsStateWithLifecycle();val job by model.job.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle()
    var configure by remember{mutableStateOf(false)};var operation by remember{mutableStateOf<String?>(null)};var password by remember{mutableStateOf("")};var repeat by remember{mutableStateOf("")};var confirm by remember{mutableStateOf(false)}
    val active=job?.let{!it.terminal}==true
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{Text(text("Encrypted backups","加密备份"),style=MaterialTheme.typography.headlineMedium);Text(text("Your subscriptions, local provider files and preferences are encrypted with your password. Keep the password safe; it cannot be recovered.","订阅、本地 Provider 文件和设置将使用密码加密。请妥善保管密码，遗失后无法恢复。"))}
        item{InfoCard("WebDAV",config?.let{it.url.ifBlank{text("Not configured","尚未配置")}}?:text("Loading…","加载中…"),onClick={configure=true})}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(enabled=!busy&&!active&&!config?.url.isNullOrBlank(),onClick={operation="backup";password="";repeat=""}){Text(text("Back up now","立即备份"))};OutlinedButton(enabled=!busy&&!active&&!config?.url.isNullOrBlank(),onClick={model.list()}){Text(text("Browse backups","浏览备份"))}}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())};if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
        job?.let{j->item{ElevatedCard{Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(j.phase,style=MaterialTheme.typography.titleMedium);Text(j.message);LinearProgressIndicator(progress={j.progress.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth());if(j.phase=="Ready"){Text(text("Restore preview: ","恢复预览：")+"${j.importedCount} "+text("subscriptions, ","个订阅，")+"${j.pendingCount} "+text("drafts","个草稿"));if(j.legacy)Text(text("Legacy backup: only the available settings and data can be imported. It is not a complete subscription backup.","旧格式备份：只能导入其中实际存在的设置和数据，不能恢复完整订阅。"),color=MaterialTheme.colorScheme.error);Button(onClick={confirm=true}){Text(text("Confirm restore","确认恢复"))}};if(!j.terminal)TextButton(onClick={model.cancel()}){Text(text("Cancel task","取消任务"))}}}}}
        if(files.isNotEmpty())item{SectionTitle(text("Remote backups","远程备份"))}
        items(files,key={it.name}){file->InfoCard(file.name,"${if(file.size>=0)bytes(file.size)else""} ${file.modified}",onClick=if(active||busy)null else {{operation=file.name;password="";repeat=""}})}
    }
    if(configure)config?.let{current->var url by remember{mutableStateOf(current.url)};var directory by remember{mutableStateOf(current.directory)};var username by remember{mutableStateOf(current.username)};var secret by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={secret="";configure=false},title={Text("WebDAV")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(url,{url=it},label={Text("HTTPS URL")});OutlinedTextField(directory,{directory=it},label={Text(text("Directory","目录"))});OutlinedTextField(username,{username=it},label={Text(text("Username","用户名"))});OutlinedTextField(secret,{secret=it},label={Text(if(current.hasPassword)text("Password (blank keeps saved)","密码（留空保留已保存值）")else text("Password","密码"))},visualTransformation=PasswordVisualTransformation())}},confirmButton={TextButton(onClick={model.configure(current.copy(url=url.trim(),directory=directory.trim(),username=username),secret.takeIf{it.isNotEmpty()});secret="";configure=false}){Text(text("Save","保存"))}},dismissButton={TextButton(onClick={secret="";configure=false}){Text(text("Cancel","取消"))}})
    }
    operation?.let { op ->
        val creating = op == "backup"
        val legacy = !creating && op.endsWith(".zip", true)
        var generated by remember(op) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { operation = null; password = ""; repeat = "" },
            title = { Text(if (creating) text("Encrypt backup", "加密备份") else text("Unlock backup", "解锁备份")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (legacy) text("This is an unencrypted legacy backup. It will be checked before anything is restored.", "这是未加密旧备份，将先检查内容，再确认恢复。") else text("Enter at least 16 characters. This password is not saved.", "请输入至少 16 个字符。此密码不会保存。"))
                    if (!legacy) {
                        OutlinedTextField(password, { password = it; generated = null }, label = { Text(text("Backup password", "备份密码")) }, visualTransformation = PasswordVisualTransformation())
                        if (creating) {
                            OutlinedTextField(repeat, { repeat = it }, label = { Text(text("Repeat password", "再次输入密码")) }, visualTransformation = PasswordVisualTransformation())
                            TextButton(onClick = { val value = generateBackupPassword(); password = value; repeat = value; generated = value }) { Text(text("Generate a random password", "生成随机密码")) }
                            generated?.let { value ->
                                Text(text("Save this password before continuing:", "继续前请保存此密码："))
                                SelectionContainer { Text(value) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = legacy || (password.length >= 16 && (!creating || password == repeat)), onClick = {
                    val bytes = password.toByteArray(Charsets.UTF_8)
                    password = ""; repeat = ""; operation = null
                    if (creating) model.backup(bytes) else model.prepare(op, bytes)
                }) { Text(if (creating) text("Create backup", "创建备份") else text("Check and preview", "检查并预览")) }
            },
            dismissButton = { TextButton(onClick = { operation = null; password = ""; repeat = "" }) { Text(text("Cancel", "取消")) } },
        )
    }
    if(confirm)ConfirmDialog(text("Restore this backup?","恢复此备份？"),text("The VPN will stop while your configuration is replaced. Review the counts above before continuing.","恢复替换配置期间 VPN 将停止。请核对上方预览数量后继续。"),{confirm=false}){confirm=false;model.commit()}
}
