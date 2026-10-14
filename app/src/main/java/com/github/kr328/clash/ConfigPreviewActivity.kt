package com.github.kr328.clash

import com.github.kr328.clash.common.constants.IntentName
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.ConfigPreviewDesign
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.util.withClash
import java.util.UUID
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class ConfigPreviewActivity : BaseActivity<ConfigPreviewDesign>() {
    override suspend fun main() {
        val uuid = intent.getStringExtra(IntentName.EXTRA_UUID)?.let { UUID.fromString(it) }

        val design = ConfigPreviewDesign(this)

        setContentDesign(design)

        val content = if (uuid != null) {
            val path = importedDir.resolve(uuid.toString()).absolutePath

            withClash {
                queryFinalConfig(path)
            }
        } else {
            null
        }

        design.update(content)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                }
            }
        }
    }
}
