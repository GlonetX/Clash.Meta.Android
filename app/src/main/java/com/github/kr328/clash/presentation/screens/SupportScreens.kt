package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

@Composable
fun HelpScreen(onOpenUrl: (String) -> Unit) {
    val links = listOf(
        R.string.clash_wiki to R.string.clash_wiki_url,
        R.string.clash_meta_wiki to R.string.clash_meta_wiki_url,
        R.string.clash_meta_core to R.string.clash_meta_core_url,
        R.string.clash_meta_for_android to R.string.meta_github_url,
        R.string.reforged_by_glonetx to R.string.glonetx_url,
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.tips_help), style = MaterialTheme.typography.bodyLarge) }
        links.forEach { (title, url) ->
            item(key = title) {
                val target = stringResource(url)
                Card(onClick = { onOpenUrl(target) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(headlineContent = { Text(stringResource(title)) }, supportingContent = { Text(target) })
                }
            }
        }
    }
}

@Composable
fun AboutScreen(version: String, onOpenUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("GlonetX", style = MaterialTheme.typography.headlineLarge)
            Text(version, style = MaterialTheme.typography.labelLarge)
        }
        HelpScreen(onOpenUrl)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.github.kr328.clash.R.string.support_back))
            }
        })
    }) { insets -> Box(Modifier.fillMaxSize().padding(insets)) { content() } }
}

@Composable
fun CrashScreen(logs: String?, onShare: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(com.github.kr328.clash.R.string.support_crash_description))
        Button(onClick = onShare, enabled = !logs.isNullOrEmpty()) {
            Text(stringResource(com.github.kr328.clash.R.string.support_share_logs))
        }
        if (logs == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LazyColumn(Modifier.weight(1f)) {
            item { SelectionContainer { Text(logs, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

@Composable
fun BrokenInstallationScreen(onOpenUrl: (String) -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.application_broken_tips), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = { onOpenUrl("https://github.com/GlonetX/glonetx-android/releases") }) {
            Text(stringResource(R.string.github_releases))
        }
    }
}
