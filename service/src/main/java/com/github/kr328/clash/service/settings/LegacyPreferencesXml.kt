package com.github.kr328.clash.service.settings

import android.util.Xml
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser

/** Legacy imports extract a typed allowlist; XML files are never restored into live preferences. */
internal object LegacyPreferencesXml {
    fun toPortable(files: Map<String, String>): String {
        val values = files.mapKeys { it.key.substringAfterLast('/').removeSuffix(".xml") }.mapValues { parse(it.value) }
        val legacy = LegacySettingsMigration.readMaps(values["settings"].orEmpty(), values["ui"].orEmpty(), values["service"].orEmpty())
        val runtime = legacy.desired.copy(enableVpn = true, accessControlMode = "AcceptAll", accessControlPackages = emptySet(),
            wifiNetworkAction = WifiNetworkAction.Disabled, wifiSsidList = emptySet(), stopOnNetworkLostBypassInterfaces = emptySet(),
            externalControlEnabled = false)
        val ui = legacy.ui.copy(hideAppIcon = false, hideFromRecents = false, connectionsCollapsedGroups = emptySet(), proxyLastGroup = "")
        SettingsManager.validate(runtime, ui)
        return Json.encodeToString(PortableSettings(runtime = runtime, ui = ui))
    }
    private fun parse(xml: String): Map<String, Any> {
        require(xml.length <= 2 * 1024 * 1024 && !xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "Invalid preferences XML" }
        val parser = Xml.newPullParser().apply { setInput(xml.reader()) }
        val values = mutableMapOf<String, Any>()
        var setName: String? = null
        var set = mutableSetOf<String>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                val name = parser.getAttributeValue(null, "name")
                when (parser.name) {
                    "set" -> { setName = name; set = mutableSetOf() }
                    "string" -> {
                        val value = parser.nextText()
                        if (setName != null) set.add(value) else if (name != null) values[name] = value
                    }
                    "boolean" -> if (name != null) values[name] = parser.getAttributeValue(null, "value").toBooleanStrict()
                    "int", "long" -> if (name != null) values[name] = parser.getAttributeValue(null, "value").toLong()
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "set") {
                setName?.let { values[it] = set.toSet() }; setName = null
            }
            parser.next()
        }
        return values
    }
}
