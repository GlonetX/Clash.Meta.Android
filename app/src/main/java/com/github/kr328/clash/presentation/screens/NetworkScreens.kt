package com.github.kr328.clash.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kr328.clash.core.model.*
import com.github.kr328.clash.presentation.FeatureViewModel
import com.github.kr328.clash.remote.Broadcasts
import com.github.kr328.clash.remote.Remote
import java.util.UUID
import com.github.kr328.clash.service.settings.UiPreferences
import com.github.kr328.clash.design.model.ProbeServices
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ProxyScreen(prefs:UiPreferences,onPrefs:((UiPreferences)->UiPreferences)->Unit,model:FeatureViewModel=viewModel()) {
    val groups by model.groups.collectAsStateWithLifecycle();val group by model.group.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle()
    var selected by rememberSaveable{mutableStateOf(prefs.proxyLastGroup)};var query by rememberSaveable{mutableStateOf("")};var menu by remember{mutableStateOf(false)}
    val sort=runCatching{ProxySort.valueOf(prefs.proxySort)}.getOrDefault(ProxySort.Default)
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val observer = object : Broadcasts.Observer {
            override fun onServiceRecreated() { revision++ }
            override fun onStarted() { revision++ }
            override fun onStopped(cause: String?) { revision++ }
            override fun onProfileChanged() { revision++ }
            override fun onProfileLoaded() { revision++ }
            override fun onProfileUpdateCompleted(uuid: UUID?) { revision++ }
            override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) = Unit
        }
        Remote.broadcasts.addObserver(observer)
        onDispose { Remote.broadcasts.removeObserver(observer) }
    }
    LaunchedEffect(prefs.proxyExcludeNotSelectable, revision) {
        model.group.value = null
        model.loadGroups(prefs.proxyExcludeNotSelectable).join()
        val next = selected.takeIf { it in model.groups.value } ?: model.groups.value.firstOrNull().orEmpty()
        selected = next
        if (next.isNotBlank()) model.loadGroup(next, sort)
    }
    LaunchedEffect(selected,sort){if(selected.isNotBlank()){model.loadGroup(selected,sort);onPrefs{it.copy(proxyLastGroup=selected)}} else model.group.value=null}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(query,{query=it},Modifier.weight(1f),label={Text(text("Find a node","搜索节点"))},singleLine=true);IconButton(onClick={menu=true}){Icon(Icons.Default.Tune,text("Display options","显示选项"))};DropdownMenu(menu,{menu=false}){ProxySort.values().forEach{s->DropdownMenuItem(text={Text(s.name)},onClick={onPrefs{it.copy(proxySort=s.name)};menu=false})};listOf("Tight", "Standard", "Loose").forEach { density -> DropdownMenuItem(text={Text(text("Card density: ","卡片密度：")+density)},onClick={onPrefs{it.copy(proxyDensity=density)};menu=false}) };DropdownMenuItem(text={Text(if(prefs.proxyIconStyle=="Flag")text("Hide flags","隐藏旗帜")else text("Show flags","显示旗帜"))},onClick={onPrefs{it.copy(proxyIconStyle=if(it.proxyIconStyle=="Flag")"None" else "Flag")};menu=false});DropdownMenuItem(text={Text(text("Toggle selectable groups only","切换仅显示可选择分组"))},onClick={onPrefs{it.copy(proxyExcludeNotSelectable=!it.proxyExcludeNotSelectable)};menu=false});DropdownMenuItem(text={Text(text("Columns: ","列数：")+prefs.proxyLine)},onClick={onPrefs{it.copy(proxyLine=it.proxyLine%4+1)};menu=false})}}
        if(groups.isNotEmpty()) ScrollableTabRow(groups.indexOf(selected).coerceAtLeast(0),edgePadding=16.dp){groups.forEach{g->Tab(selected==g,{selected=g},text={Text(g)})}}
        if(error!=null)ErrorState(error!!){model.loadGroups(prefs.proxyExcludeNotSelectable);if(selected.isNotBlank())model.loadGroup(selected,sort)}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text(group?.type.orEmpty(),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge);TextButton(enabled=selected.isNotBlank()&&!busy,onClick={model.action{model.repository.healthCheck(selected);model.group.value=model.repository.group(selected,sort)}}){Icon(Icons.Default.Speed,null);Text(text("Test latency","测速"))}}
        val nodes=group?.proxies.orEmpty().filter{it.name.contains(query,true)||it.title.contains(query,true)}
        if(nodes.isEmpty()&&!busy)EmptyState(text("No nodes. Select a subscription and connect.","没有节点，请选择订阅并连接。"))
        LazyVerticalGrid(columns=GridCells.Fixed(prefs.proxyLine.coerceIn(1,4)),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(nodes,key={it.name}){node-> val active=node.name==group?.now
                Surface(modifier=Modifier.fillMaxWidth().clickable{if(node.isGroup&&node.name in groups)selected=node.name else model.action{check(model.repository.select(selected,node.name)){"This group does not allow manual selection"};model.group.value=model.repository.group(selected,sort)}},shape=MaterialTheme.shapes.large,color=if(active)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,border=if(active)androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.primary)else null){Column(Modifier.padding(when(prefs.proxyDensity){"Tight", "Compact" -> 12.dp; "Loose" -> 24.dp; else -> 18.dp}),verticalArrangement=Arrangement.spacedBy(6.dp)){Row{if(prefs.proxyIconStyle=="Flag") { val flag=leadingNodeFlag(node.name); if(flag.isNotEmpty()) Text(flag,Modifier.padding(end=6.dp)) };Text(node.title.ifBlank{node.name},Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis);if(active)Icon(Icons.Default.Check,null,Modifier.size(16.dp))};if(prefs.proxyDensity !in listOf("Compact", "Tight"))Text(node.subtitle.ifBlank{node.type},style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis);Text(if(node.delay<=0)"—" else "${node.delay} ms",style=MaterialTheme.typography.labelLarge,color=if(node.delay in 1..300)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)}}
            }
        }
    }
}

@Composable fun ProvidersScreen(model:FeatureViewModel=viewModel()) {
    val providers by model.providers.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle()
    LaunchedEffect(Unit){model.loadProviders()}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{Button(enabled=!busy,onClick={model.action{providers.filter{it.vehicleType==Provider.VehicleType.HTTP}.forEach{model.repository.updateProvider(it)};model.providers.value=model.repository.providers()}}){Text(text("Update all","全部更新"))}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())};if(error!=null)item{ErrorState(error!!){model.loadProviders()}}
        if(providers.isEmpty()&&!busy)item{EmptyState(text("No providers in the active configuration","当前配置没有 Provider"))}
        items(providers,key={"${it.type}/${it.name}"}){p->InfoCard(p.name,"${p.type} · ${p.vehicleType}\n${DateFormat.getDateTimeInstance().format(Date(p.updatedAt))}",trailing={IconButton(enabled=!busy,onClick={model.action{model.repository.updateProvider(p);model.providers.value=model.repository.providers()}}){Icon(Icons.Default.Refresh,text("Update","更新"))}})}
    }
}

@Composable fun ConnectionsScreen(prefs:UiPreferences,onPrefs:((UiPreferences)->UiPreferences)->Unit,model:FeatureViewModel=viewModel()) {
    val list by model.connections.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();var query by rememberSaveable{mutableStateOf("")};var selected by remember{mutableStateOf<ConnectionItem?>(null)};var confirmAll by remember{mutableStateOf(false)};var paused by rememberSaveable{mutableStateOf(false)}
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(paused, prefs.lightweightMode, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (!paused) { model.loadConnections().join(); delay(if (prefs.lightweightMode) 5_000 else 1_500) }
        }
    }
    var rows=list.filter{query.isBlank()||listOf(it.host,it.destination,it.process.orEmpty(),it.rule.orEmpty(),it.chains.orEmpty()).any{s->s.contains(query,true)}}
    rows=when(prefs.connectionsSort){"Time"->rows.sortedByDescending{it.start};"Name"->rows.sortedBy{it.host};else->rows.sortedByDescending{it.upload+it.download}}
    if(prefs.connectionsSortReversed)rows=rows.reversed()
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text(text("Host, process, rule…","主机、进程、规则…"))},singleLine=true)}
        item{Row{FilterChip(paused,{paused=!paused},label={Text(text("Pause","暂停"))});Spacer(Modifier.width(8.dp));FilterChip(prefs.connectionsGroupByProcess,{onPrefs{it.copy(connectionsGroupByProcess=!it.connectionsGroupByProcess)}},label={Text(text("Group","分组"))});TextButton(onClick={onPrefs{it.copy(connectionsSort=when(it.connectionsSort){"Size"->"Time";"Time"->"Name";else->"Size"})}}){Text(prefs.connectionsSort)};IconButton(onClick={onPrefs{it.copy(connectionsSortReversed=!it.connectionsSortReversed)}}){Icon(Icons.Default.Sort,null)}}}
        item{TextButton(onClick={confirmAll=true},enabled=list.isNotEmpty()){Text(text("Close all connections","关闭所有连接"))}}
        if(error!=null)item{ErrorState(error!!){model.loadConnections()}}
        if(rows.isEmpty())item{EmptyState(text("No matching connections","没有匹配的连接"))}
        if(prefs.connectionsGroupByProcess)rows.groupBy{it.process?.ifBlank{null}?:"Unknown"}.forEach{(process,entries)->
            item(key="group/$process"){InfoCard(process,"${entries.size}",onClick={onPrefs{it.copy(connectionsCollapsedGroups=if(process in it.connectionsCollapsedGroups)it.connectionsCollapsedGroups-process else it.connectionsCollapsedGroups+process)}})}
            if(process !in prefs.connectionsCollapsedGroups)items(entries,key={it.id}){c->ConnectionRow(c){selected=c}}
        }else items(rows,key={it.id}){c->ConnectionRow(c){selected=c}}
    }
    selected?.let{c->AlertDialog(onDismissRequest={selected=null},title={Text(c.host.ifBlank{c.destination})},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){listOf("ID" to c.id,"Protocol" to "${c.network} / ${c.type}","Source" to c.source,"Destination" to c.destination,"Process" to c.process,"Rule" to c.rule,"Chain" to c.chains).forEach{(label,value)->Text("$label: ${value.orEmpty()}")}}},confirmButton={TextButton(onClick={selected=null;model.action{model.repository.closeConnection(c.id);model.connections.value=model.repository.connections()}}){Text(text("Close connection","关闭连接"))}},dismissButton={TextButton(onClick={selected=null}){Text(text("Back","返回"))}})}
    if(confirmAll)ConfirmDialog(text("Close all connections?","关闭所有连接？"),text("Applications may reconnect automatically.","应用可能会自动重新连接。"),{confirmAll=false}){confirmAll=false;model.action{list.forEach{model.repository.closeConnection(it.id)};model.connections.value=model.repository.connections()}}
}
@Composable private fun ConnectionRow(c:ConnectionItem,onClick:()->Unit){InfoCard(c.host.ifBlank{c.destination},"${c.network} · ${c.process.orEmpty()}\n↑ ${bytes(c.upload)}  ↓ ${bytes(c.download)}",onClick)}

@Composable fun QualityScreen(model:FeatureViewModel=viewModel()) {
    val quality by model.quality.collectAsStateWithLifecycle();val exit by model.ip.collectAsStateWithLifecycle();val direct by model.directIp.collectAsStateWithLifecycle();val busy by model.busy.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();var address by rememberSaveable{mutableStateOf("")}
    LaunchedEffect(Unit){model.loadQuality()}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{OutlinedTextField(address,{address=it},Modifier.fillMaxWidth(),label={Text(text("IP address (blank uses exit IP)","IP 地址（留空使用出口 IP）"))})};item{Button(enabled=!busy,onClick={model.loadQuality(address)}){Text(text("Check quality","检测质量"))}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())};if(error!=null)item{ErrorState(error!!){model.loadQuality(address)}}
        item{InfoCard(text("Proxy exit","代理出口"),exit?.let{listOfNotNull(it.ip,it.country,it.city,it.isp,it.asn).joinToString("\n")}?:"—")};item{InfoCard(text("Direct network","直连网络"),direct?.let{listOfNotNull(it.ip,it.country,it.city,it.isp).joinToString("\n")}?:"—")}
        quality?.let{q->item{InfoCard("Scamalytics","${q.scamalyticsScore?:"—"} · ${q.scamalyticsRisk?:"—"}")};item{InfoCard("ProxyCheck","Risk: ${q.proxyCheckRisk?:"—"}\nProxy: ${q.proxyCheckProxy?:"—"}\nType: ${q.proxyCheckType?:"—"}")};item{InfoCard("IP-API","ISP: ${q.ipApiISP?:"—"}\nProxy: ${q.ipApiProxy?:"—"}\nHosting: ${q.ipApiHosting?:"—"}\nMobile: ${q.ipApiMobile?:"—"}")}}
    }
}

@Composable fun LatencyScreen(prefs:UiPreferences,onPrefs:((UiPreferences)->UiPreferences)->Unit,model:FeatureViewModel=viewModel()){
    val probes by model.probes.collectAsStateWithLifecycle();val busy by model.probeRunning.collectAsStateWithLifecycle();val error by model.error.collectAsStateWithLifecycle();var url by rememberSaveable{mutableStateOf("https://www.gstatic.com/generate_204")};var result by rememberSaveable{mutableStateOf<String?>(null)}
    val order=(prefs.probeServiceOrder+ProbeServices.names).distinct();val enabled=if(prefs.probeServiceOrder.isEmpty())order.toSet()else prefs.probeServiceEnabled
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text(text("Test URL","测试地址"))})};item{Row(verticalAlignment=Alignment.CenterVertically){Button(enabled=!busy,onClick={model.action{result="${model.repository.latency(url)} ms"}}){Text(text("Test","测试"))};Spacer(Modifier.width(16.dp));Text(result.orEmpty())}}
        item{SectionTitle(text("Service probes","服务检测"))};item{Button(enabled=!busy,onClick={model.probe(ProbeServices.ALL.filter{it.name in enabled}.map{it.name to it.url})}){Text(if(busy)text("Checking…","检测中…")else text("Check selected services","检测已选服务"))}}
        if(error!=null)item{ErrorState(error!!){model.error.value=null}}
        itemsIndexed(order,key={_,s->s}){index,name->val r=probes[name];InfoCard(name,if(name !in probes)"—"else if(r==null)text("Unavailable","不可用")else "HTTP ${r.code} · ${r.latency} ms",trailing={Row(verticalAlignment=Alignment.CenterVertically){if(index>0)IconButton(onClick={val next=order.toMutableList().apply{add(index-1,removeAt(index))};onPrefs{it.copy(probeServiceOrder=next,probeServiceEnabled=enabled)}}){Icon(Icons.Default.ArrowUpward,text("Move up","上移"))};Checkbox(name in enabled,{checked->onPrefs{it.copy(probeServiceOrder=order,probeServiceEnabled=if(checked)enabled+name else enabled-name)}})}})}
    }
}

private fun leadingNodeFlag(name: String): String {
    val value = name.trimStart()
    if (value.isEmpty()) return ""
    val first = value.codePointAt(0)
    if (first in 0x1F1E6..0x1F1FF) {
        val next = Character.charCount(first)
        if (next < value.length && value.codePointAt(next) in 0x1F1E6..0x1F1FF)
            return value.substring(0, next + Character.charCount(value.codePointAt(next)))
        return ""
    }
    val code = value.take(2)
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return ""
    return buildString { code.forEach { append(Character.toChars(0x1F1E6 + it.code - 'A'.code)) } }
}
