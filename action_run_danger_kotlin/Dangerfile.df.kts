@file:DependsOn("xyz.pavelkorolev.danger.detekt:plugin:1.2.0")

import systems.danger.kotlin.*
import systems.danger.kotlin.models.github.*
import xyz.pavelkorolev.danger.detekt.DetektPlugin
import java.io.File

register.plugin(DetektPlugin)

danger(args) {
    onGitHub {
        val file = File("build/reports/detekt/report.xml")
        if (!file.exists()) {
            warn("No detekt report found")
        } else {
            with(DetektPlugin) {
                val report = parse(file)
                val count = report.count
                if (count > 0) {
                    warn("Detekt violations found: **$count**.")
                    report(report)
                }
            }
        }
    }
}