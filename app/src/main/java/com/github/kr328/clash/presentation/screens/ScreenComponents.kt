package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.util.Locale

@Composable fun text(english: String, chinese: String): String = if(ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language == "zh") chinese else english
@Composable fun SectionTitle(title: String, modifier: Modifier = Modifier) { Text(title, modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
@Composable fun InfoCard(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().then(if(onClick != null) Modifier.clickable(onClick=onClick) else Modifier)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { Text(title, style=MaterialTheme.typography.titleMedium); if(!subtitle.isNullOrBlank()) Text(subtitle, style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant, modifier=Modifier.padding(top=4.dp)) }
            trailing?.invoke()
        }
    }
}
@Composable fun BusyState() { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment=Alignment.Center) { CircularProgressIndicator() } }
@Composable fun EmptyState(message: String, action: (@Composable () -> Unit)? = null) { Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(16.dp)) { Text(message, style=MaterialTheme.typography.bodyLarge); action?.invoke() } }
@Composable fun ErrorState(message: String, retry: () -> Unit) { InfoCard(text("Unable to load", "加载失败"), message, trailing = { TextButton(onClick=retry) { Text(text("Retry", "重试")) } }) }
@Composable fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss, title={Text(title)}, text={Text(message)}, dismissButton={TextButton(onClick=onDismiss){Text(text("Cancel","取消"))}}, confirmButton={TextButton(onClick=onConfirm){Text(text("Confirm","确认"))}})
}
fun bytes(value: Long): String { val n=value.toDouble(); return when { n>=1L.shl(30) -> String.format(Locale.ROOT,"%.2f GiB",n/(1L.shl(30))); n>=1L.shl(20) -> String.format(Locale.ROOT,"%.1f MiB",n/(1L.shl(20))); n>=1024 -> String.format(Locale.ROOT,"%.1f KiB",n/1024); else -> "$value B" } }
