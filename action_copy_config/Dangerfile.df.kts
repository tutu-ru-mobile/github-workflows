@file:DependsOn("xyz.pavelkorolev.danger.detekt:plugin:1.2.0")

import systems.danger.kotlin.*
import systems.danger.kotlin.models.github.*
import xyz.pavelkorolev.danger.detekt.DetektPlugin
import java.io.File

register.plugin(DetektPlugin)

danger(args) {
    onGitHub {
        checkPRContents()
        checkBranch()
        runDetektCheck()

        fun checkPRContents() {
            val insertions = pullRequest.additions ?: 0
            val body = pullRequest?.body
            if (insertions > 750) {
                warn("Размер PR ($insertions строк) превышает рекомендуемый размер (750 строк)")
            }
            if (body == null) {
                warn("Отсутствует описание PR")

                if (!pullRequest.title.contains("NO-ISSUE") && !body.contains("https://hq.tutu.ru/")) {
                    warn("Отсутствует ссылка на задачу в Jira")
                }
            }
        }

        fun checkBranch() {
            val releaseRegex = Regex("^release/([a-zA-Z]+_)?v[0-9.]+$")
            val releasePRRegex = Regex("[1-9]\\d*(\\.[1-9]\\d*)*")
            val testOrFeatureRegex = "^(feature|tests)/([A-Z]+-[0-9]+|NO-ISSUE)-[a-zA-Z].+$"
            val testOrFeaturePRRegex = "^([A-Z]+-[0-9]+|NO-ISSUE):.+$"

            when {
                pullRequest.head.label.mathches(releaseRegex) -> {
                    if (!pullRequest.title.mathches(releasePRRegex)) {
                        warn("Релиз должен иметь номер версии")
                    }
                }
                pullRequest.head.label.matches(testOrFeatureRegex) -> {
                    if (!pullRequest.title.mathches(testOrFeaturePRRegex)) {
                        warn("ПР должен начинаться с идентификатора задачи в Jira в формате '" +
                                "JIRA-TICKET-ID: Наименование ПР' (например: 'USPACE-123: Фикс кнопки'). " +
                                "Подробности в документации: https://dom.tutu.ru/display/MOBILEDEV/Pull+request+standard")
                    }
                }
                else -> {
                    warn("Название ветки должно начинаться с feature/, tests/ или release/.\n" +
                            "Если ветка начинается на feature/ или test/, то далее должно быть MAPP-{номер задачи} " +
                            "или NO-ISSUE или USPACE-{номер задачи} или TUTUID-{номер задачи},\n" +
                            "если ветка начинается на release/, то далее должен быть номер версии,\n" +
                            "к примеру release/v1.5.0 или release/avia_v1.5.0"
                    )
                }
            }
        }

        fun runDetektCheck() {
            val file = File("build/reports/detekt/merge.xml")
            if (!file.exists()) {
                warn("Не найден отчет detekt")
            } else {
                with(DetektPlugin) {
                    val report = parse(file)
                    val count = report.count
                    if (count > 0) {
                        warn("Количество ошибок detekt: **$count**.")
                        report(report)
                    }
                }
            }
        }
    }
}