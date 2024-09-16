@file:DependsOn("xyz.pavelkorolev.danger.detekt:plugin:1.2.0")

import systems.danger.kotlin.*
import systems.danger.kotlin.models.github.*
import xyz.pavelkorolev.danger.detekt.DetektPlugin
import systems.danger.kotlin.sdk.DangerContext
import xyz.pavelkorolev.danger.detekt.DetektViolationReporter
import xyz.pavelkorolev.danger.detekt.model.DetektViolation
import xyz.pavelkorolev.danger.detekt.model.DetektViolationSeverity
import java.io.File

register.plugin(DetektPlugin)

danger(args) {

    class FailReporter(private val context: DangerContext, private val diffFiles: List<String>) : DetektViolationReporter {

        private val pathPrefix = File("").absolutePath

        override fun report(violation: DetektViolation) {
            if (violation.filePath in diffFiles) {
                val message = createMessage(violation)
                val file = violation.filePath?.let(::File)
                val filePath = file?.let(::createFilePath)
                val line = violation.location?.startLine
                if (filePath != null && line != null) {
                    context.warn(message, filePath, line)
                }
            }
        }

        private fun createFilePath(file: File): String? {
            if (file.absolutePath == pathPrefix) return null
            return file.absolutePath.removePrefix(pathPrefix + File.separator)
        }

        private fun createMessage(violation: DetektViolation): String {
            val message = violation.message?.let { "**Detekt**: $it" }
            val rule = violation.ruleId?.let { "**Rule**: $it" }
            return listOfNotNull(
                "",
                message,
                rule,
            ).joinToString(separator = "\n")
        }
    }

    onGitHub {
        fun checkPRContents() {
            val taskRegex = Regex("([A-Z].*?)\\d+")
            val insertions = pullRequest.additions ?: 0
            if (insertions > 750) {
                warn("Размер PR ($insertions строк) превышает рекомендуемый размер (750 строк)")
            }
            if (pullRequest.body?.isNullOrEmpty() == true) {
                warn("Отсутствует описание PR")
            }
            if (!pullRequest.title.contains("NO-ISSUE") && pullRequest.body?.contains("https://hq.tutu.ru/") == false) {
                val title = taskRegex.find(pullRequest.title)?.value
                warn(title?.let { "Задача в Jira – [https://hq.tutu.ru/browse/$title](https://hq.tutu.ru/browse/$title)" } ?: "Отсутствует ссылка на задачу в Jira")
            }
        }

        fun checkBranch() {
            val releaseRegex = Regex("^release/([a-zA-Z]+_)?v[0-9.]+$")
            val releasePRRegex = Regex("\\d\\.\\d\\.\\d")
            val testOrFeatureRegex = Regex("^(feature|tests|fix)/([A-Z]+-[0-9]+|NO-ISSUE)_[a-zA-Z_]+\$")
            val testOrFeaturePRRegex = Regex("^([A-Z]+-[0-9]+|NO-ISSUE):")
            val russianLettersRegex = Regex("[а-яА-ЯёЁ]+")

            when {
                pullRequest.head.ref.matches(releaseRegex) ->
                    if (releasePRRegex !in pullRequest.title) {
                        warn("Релиз должен иметь номер версии в формате **1.2.3** или **v.1.2.3**")
                    }
                pullRequest.head.ref.matches(testOrFeatureRegex) -> {
                    if (testOrFeaturePRRegex !in pullRequest.title) {
                        warn(
                            "ПР должен начинаться с идентификатора задачи в Jira в формате:\n" +
                                    "**JIRA-TICKET-ID: Наименование ПР** (например **USPACE-123: Фикс кнопки**)"
                        )
                    }
                    if (russianLettersRegex !in pullRequest.title) {
                        warn("ПР должен иметь заголовок на русском языке (можно использовать английские термины)")
                    }
                }
                else ->
                    warn(
                        "Название ветки не соответствует паттерну!\n" +
                                "1) Название ветки должно начинаться с **feature/**, **tests/**, **fix/** или **release/**\n" +
                                "2) Далее идет id задачи из jira в верхнем регистре\n" +
                                "3) Через нижнее подчеркивание идет краткое название задачи\n" +
                                "Например, **feature/MAPP-1111_do_something_good** или **feature/NO-ISSUE_do_something_good**\n" +
                                "Если ветка начинается на **release/**, то далее должен быть номер версии,\n" +
                                "к примеру **release/v1.5.0** или **release/avia_v1.5.0**"
                    )
            }
        }

        fun runDetektCheck() {
            val file = File("build/reports/detekt/merge.xml")
            if (!file.exists()) {
                warn("Не найден отчет detekt")
            } else {
                with(DetektPlugin) {
                    val diffFilesList = git.modifiedFiles + git.createdFiles
                    report(parse(file), reporter = FailReporter(context, diffFilesList))
                }
            }
        }

        checkPRContents()
        checkBranch()
        runDetektCheck()
    }
}