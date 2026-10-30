package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.presentation.*
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.service.settings.*
import com.github.kr328.clash.design.model.ProbeServices
import java.util.Locale

private val widgets = listOf("outbound_mode", "traffic_chart", "traffic_total", "memory", "connections", "uptime", "services", "providers", "ip_info", "profile")
@Composable private fun widgetTitle(key:String):String = when(key){"outbound_mode"->text("Routing mode","路由模式");"traffic_chart"->text("Network activity","网络活动");"traffic_total"->text("Transferred","累计流量");"memory"->text("Memory","内存");"connections"->text("Connections","连接");"uptime"->text("Connected for","运行时长");"services"->text("Service availability","服务可用性");"providers"->text("Providers","Providers");"ip_info"->text("Exit address","出口地址");else->text("Subscription","订阅")}

@Composable fun HomeScreen(state: HomeState, preferences: UiPreferences, settings: SettingsSnapshot?, onToggle:()->Unit, onMode:(TunnelState.Mode)->Unit, onNavigate:(String)->Unit, onRefreshIp:()->Unit, onPreferences:((UiPreferences)->UiPreferences)->Unit, probeModel:FeatureViewModel=viewModel(key="dashboard.probes")) {
    var edit by rememberSaveable{mutableStateOf(false)};var window by rememberSaveable{mutableIntStateOf(60)}
    val probes by probeModel.probes.collectAsStateWithLifecycle();val probing by probeModel.probeRunning.collectAsStateWithLifecycle()
    val order=(preferences.dashboardWidgetOrder+widgets).distinct().filter{it in widgets&&it !in preferences.dashboardCardHidden}
    LazyVerticalGrid(columns=GridCells.Adaptive(170.dp),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) { Surface(color=if(state.connected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,shape=RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(if(state.connected)text("Connected","已连接")else text("Ready when you are","随时准备连接"),style=MaterialTheme.typography.headlineLarge);Text(state.active?.name?:text("Choose a subscription to begin","选择订阅以开始连接"),color=MaterialTheme.colorScheme.onSurfaceVariant) };FilledIconButton(onClick=onToggle,enabled=!state.loading,modifier=Modifier.size(64.dp)){Icon(Icons.Default.PowerSettingsNew,if(state.connected)text("Disconnect","断开")else text("Connect","连接"),Modifier.size(32.dp))} }
                if(settings?.trafficPolicy==TrafficPolicy.Paused) Text(text("Traffic paused by your network policy","网络策略已暂停流量"),color=MaterialTheme.colorScheme.error)
                if(settings?.trafficPolicy==TrafficPolicy.Direct) Text(text("Direct connection policy is active","直连策略生效中"),color=MaterialTheme.colorScheme.error)
                if(settings?.pendingRestartFields?.isNotEmpty()==true) Text(text("Settings saved. Reconnect to apply.","设置已保存，重新连接后生效。"))
                Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){Text("↓ ${bytes(state.download)}/s",style=MaterialTheme.typography.titleMedium);Text("↑ ${bytes(state.upload)}/s",style=MaterialTheme.typography.titleMedium)}
            }
        } }
        if(state.error!=null)item(span={GridItemSpan(maxLineSpan)}){Text(state.error,color=MaterialTheme.colorScheme.error)}
        item(span={GridItemSpan(maxLineSpan)}) { Row(verticalAlignment=Alignment.CenterVertically){Text(text("Overview","概览"),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={edit=true}){Icon(Icons.Default.Tune,null);Spacer(Modifier.width(4.dp));Text(text("Customize","自定义"))}} }
        items(order,key={it},span={key->GridItemSpan(if(preferences.dashboardWidgetSizes[key]=="Small"||key in listOf("memory","connections","uptime","traffic_total"))1 else maxLineSpan)}) { key ->
            Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(widgetTitle(key),style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                when(key) {
                    "outbound_mode" -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(TunnelState.Mode.Rule,TunnelState.Mode.Global,TunnelState.Mode.Direct).forEach{mode->FilterChip(state.mode==mode,{onMode(mode)},label={Text(when(mode){TunnelState.Mode.Rule->text("Rule","规则");TunnelState.Mode.Global->text("Global","全局");else->text("Direct","直连")})})}}
                    "traffic_chart" -> {val interval=if(preferences.lightweightMode)5 else 1;val effectiveWindow=if(preferences.lightweightMode)window.coerceAtMost(300)else window;TrafficGraph(state.history.takeLast(effectiveWindow/interval));Row{(if(preferences.lightweightMode)listOf(60,300)else listOf(60,300,900)).forEach{n->TextButton(onClick={window=n}){Text("${n/60}m")}}}}
                    "traffic_total" -> {Text(bytes(state.totalUpload+state.totalDownload),style=MaterialTheme.typography.headlineMedium);Text("↑ ${bytes(state.totalUpload)}   ↓ ${bytes(state.totalDownload)}",style=MaterialTheme.typography.bodySmall)}
                    "memory" -> Text(bytes(state.memory),style=MaterialTheme.typography.headlineMedium)
                    "connections" -> {Text(state.connections.toString(),style=MaterialTheme.typography.headlineMedium);TextButton(onClick={onNavigate("connections")}){Text(text("Inspect","查看"))}}
                    "uptime" -> {val secs=if(state.startedAt>0)(android.os.SystemClock.elapsedRealtime()-state.startedAt)/1000 else 0;Text(String.format(Locale.ROOT,"%02d:%02d:%02d",secs/3600,secs/60%60,secs%60),style=MaterialTheme.typography.headlineMedium)}
                    "providers" -> {Text(state.providerCount.toString(),style=MaterialTheme.typography.headlineMedium);TextButton(onClick={onNavigate("providers")}){Text(text("Manage","管理"))}}
                    "profile" -> {Text(state.active?.name?:text("No subscription selected","尚未选择订阅"),style=MaterialTheme.typography.titleLarge);Button(onClick={onNavigate("profiles")}){Text(text("Subscriptions","订阅管理"))}}
                    "ip_info" -> {Text(state.ip?.ip?:"—",style=MaterialTheme.typography.titleLarge);state.ip?.let{Text(listOfNotNull(it.country,it.city,it.isp).joinToString(" · "))};Row{TextButton(onClick=onRefreshIp){Text(text("Refresh","刷新"))};TextButton(onClick={onNavigate("quality")}){Text(text("IP quality","IP 质量"))}}}
                    "services" -> {probes.forEach{(name,result)->Row(Modifier.fillMaxWidth()){Text(name,Modifier.weight(1f));Text(if(result==null)text("Unavailable","不可用")else "${result.code} · ${result.latency} ms")}};Row{TextButton(enabled=!probing,onClick={val selected=(preferences.probeServiceOrder+ProbeServices.names).distinct().filter{preferences.probeServiceOrder.isEmpty()||it in preferences.probeServiceEnabled};probeModel.probe(ProbeServices.ALL.filter{it.name in selected}.map{it.name to it.url})}){Text(if(probing)text("Checking…","检测中…")else text("Check now","立即检测"))};TextButton(onClick={onNavigate("latency")}){Text(text("Details","详情"))}}}
                }
            } }
        }
        item(span={GridItemSpan(maxLineSpan)}){Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={onNavigate("logs")}){Text(text("Logs","日志"))};TextButton(onClick={onNavigate("help")}){Text(text("Help","帮助"))};TextButton(onClick={onNavigate("about")}){Text(text("About","关于"))}}}
    }
    if(edit) DashboardEditor(preferences,{edit=false},onPreferences)
}

@Composable fun TrafficGraph(points:List<TrafficPoint>) {
    val down=MaterialTheme.colorScheme.primary;val up=MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val maximum=points.maxOfOrNull{maxOf(it.upload,it.download)}?.coerceAtLeast(1)?:1
        fun line(selector:(TrafficPoint)->Long,color:androidx.compose.ui.graphics.Color) { val path=Path();points.forEachIndexed{i,p->val x=i.toFloat()/(points.size-1).coerceAtLeast(1)*size.width;val y=size.height-selector(p).toFloat()/maximum*size.height;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,color,style=Stroke(2.dp.toPx())) }
        line({it.download},down);line({it.upload},up)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DashboardEditor(prefs:UiPreferences,onDismiss:()->Unit,onSave:((UiPreferences)->UiPreferences)->Unit) {
    var order by remember{mutableStateOf((prefs.dashboardWidgetOrder+widgets).distinct().filter{it in widgets})};var hidden by remember{mutableStateOf(prefs.dashboardCardHidden)};var sizes by remember{mutableStateOf(prefs.dashboardWidgetSizes)}
    val step=with(LocalDensity.current){72.dp.toPx()}
    ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.padding(horizontal=20.dp)){Text(text("Customize overview","自定义概览"),style=MaterialTheme.typography.headlineSmall);Text(text("Hold the handle to reorder cards","长按拖动手柄调整顺序"));LazyColumn(Modifier.weight(1f,false).heightIn(max=480.dp)){itemsIndexed(order,key={_,k->k}){index,key->Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically){var travel by remember{mutableFloatStateOf(0f)};Icon(Icons.Default.DragHandle,text("Reorder","调整顺序"),Modifier.padding(12.dp).pointerInput(order){detectDragGesturesAfterLongPress(onDragStart={travel=0f},onDragEnd={travel=0f},onDragCancel={travel=0f}){change,amount->change.consume();travel+=amount.y;if(kotlin.math.abs(travel)>step){val current=order.indexOf(key);val next=(current+if(travel>0)1 else -1).coerceIn(0,order.lastIndex);if(current!=next)order=order.toMutableList().apply{add(next,removeAt(current))};travel=0f}}});Text(widgetTitle(key),Modifier.weight(1f));TextButton(onClick={sizes=sizes+(key to if(sizes[key]=="Small")"Large" else "Small")}){Text(if(sizes[key]=="Small")text("Small","半宽")else text("Wide","全宽"))};Checkbox(key !in hidden,{checked->hidden=if(checked)hidden-key else hidden+key})}}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={order=widgets;hidden=emptySet();sizes=emptyMap()}){Text(text("Reset","重置"))};Button(onClick={val o=order;val h=hidden;val s=sizes;onSave{it.copy(dashboardWidgetOrder=o,dashboardCardHidden=h,dashboardWidgetSizes=s)};onDismiss()}){Text(text("Save","保存"))}};Spacer(Modifier.height(24.dp))}}
}
