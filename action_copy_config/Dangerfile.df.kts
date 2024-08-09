@file:DependsOn("xyz.pavelkorolev.danger.detekt:plugin:1.2.0")

import systems.danger.kotlin.*
import systems.danger.kotlin.models.github.*
import xyz.pavelkorolev.danger.detekt.DetektPlugin
import java.io.File

register.plugin(DetektPlugin)

danger(args) {
    onGitHub {
        fun checkPRContents() {
            val insertions = pullRequest.additions ?: 0
            if (insertions > 750) {
                warn("Размер PR ($insertions строк) превышает рекомендуемый размер (750 строк)")
            }
            if (pullRequest.body?.isNullOrEmpty() == true) {
                warn("Отсутствует описание PR")
            }
            if (!pullRequest.title.contains("NO-ISSUE") && pullRequest.body?.contains("https://hq.tutu.ru/") == false) {
                warn("Отсутствует ссылка на задачу в Jira")
            }
        }

        fun String.isEnglishSentence(): Boolean {
            val words = split("\\s+".toRegex()).drop(1)
            var englishWordsCount = 0
            var allWordsCount = 0
            val englishWordsAllowedPercent = 0.7

            for (word in words) {
                allWordsCount++
                if (word.all { c -> c.isLetter() && (c in 'a'..'z' || c in 'A'..'Z') }) {
                    englishWordsCount++
                }
            }
            return (englishWordsCount.toDouble() / allWordsCount) > englishWordsAllowedPercent
        }

        fun checkBranch() {
            val releaseRegex = Regex("^release/([a-zA-Z]+_)?v[0-9.]+$")
            val releasePRRegex = Regex("[1-9]\\d*(\\.[1-9]\\d*)*")
            val testOrFeatureRegex = Regex("^(feature|tests)/([A-Z]+-[0-9]+|NO-ISSUE)_[a-zA-Z].+$")
            val testOrFeaturePRRegex = Regex("^([A-Z]+-[0-9]+|NO-ISSUE):")

            when {
                pullRequest.head.ref.matches(releaseRegex) -> {
                    if (!pullRequest.title.matches(releasePRRegex)) {
                        warn("Релиз должен иметь номер версии в формате **1.2.3** или **v.1.2.3**")
                    }
                }
                pullRequest.head.ref.matches(testOrFeatureRegex) -> {
                    if (!pullRequest.title.matches(testOrFeaturePRRegex)) {
                        warn("ПР должен начинаться с идентификатора задачи в Jira в формате:\n" +
                                "**JIRA-TICKET-ID: Наименование ПР** (например **USPACE-123: Фикс кнопки**)")
                    }
                    if (pullRequest.title.isEnglishSentence()) {
                        warn("ПР должен иметь заголовок на русском языке (можно использовать английские термины)")
                    }
                }

                else -> {
                    warn(
                        "1) Название ветки должно начинаться с **feature/**, **tests/** или **release/**\n" +
                                "2) Далее идет id задачи из jira в верхнем регистре\n" +
                                "3) Через нижнее подчеркивание идет краткое название задачи\n" +
                                "Например, **feature/MAPP-1111_do_something_good** или **feature/NO-ISSUE_do_something_good**\n" +
                                "Если ветка начинается на **release/**, то далее должен быть номер версии,\n" +
                                "к примеру **release/v1.5.0** или **release/avia_v1.5.0**"
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

        checkPRContents()
        checkBranch()
        runDetektCheck()
    }
}