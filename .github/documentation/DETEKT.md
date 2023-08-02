# Добавление в проект Detekt

[Пример настройки detekt в проекте UserSpace](https://github.com/tutu-ru-mobile/android-user-space/blob/master/build.gradle.kts)

[Файл правил в проекте UserSpace](https://github.com/tutu-ru-mobile/android-user-space/blob/master/config/detekt/detekt.yml)

[Dangerfile в проекте UserSpace](https://github.com/tutu-ru-mobile/android-user-space/blob/master/Dangerfile)

[Gemfile в проекте UserSpace](https://github.com/tutu-ru-mobile/android-user-space/blob/master/Gemfile)

[Gemfile.lock в проекте UserSpace](https://github.com/tutu-ru-mobile/android-user-space/blob/master/Gemfile.lock)

## Подключение

Для подключения необходимо добавить плагин. После добавления в GlobalVersions версию можно будет взять там.

```

plugins {
    id("io.gitlab.arturbosch.detekt") version("1.23.0")
}

subprojects {
    plugins.apply("io.gitlab.arturbosch.detekt")

    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.0")
    }
}

```

## Настройка

Необходимо скопировать [файл правил](https://github.com/tutu-ru-mobile/android-user-space/blob/master/config/detekt/detekt.yml) из проекта UserSpace.
Там уже произведена настройка согласно правилам [кодстайла Туту](https://dom.tutu.ru/display/MOBILEDEV/Code+style+standard).

После необходимо подключить и настроить detekt во все модули где необходимо производить проверку.
При этом необходимо учесть, что для работы Danger плагина detekt необходимо указывать единый файл с отчётами по ошибкам.
Для этого нужно мёрджить все файлы отчётов из модулей в единый файл.

```

subprojects {

    val configFile = files("$rootDir/config/detekt/detekt.yml")
    val mergedReportFile = file("${rootProject.buildDir}/reports/detekt/report.xml")

    /**
     * Location of single module report inside its build directory
     * Must be named detekt.xml
     * Workaround for https://github.com/detekt/detekt/issues/4192#issuecomment-946325201
     */
    val outputReportFile = file("$buildDir/reports/detekt/detekt.xml")

    detekt {
        parallel = true
        ignoreFailures = true
        config.setFrom(configFile)
    }

    val reportMerge: TaskProvider<ReportMergeTask> =
        rootProject.registerMaybe("reportMerge") {
            description = "Runs merge of all detekt reports into single one"
            output.set(mergedReportFile)
        }

    tasks.withType<Detekt>().configureEach {
        reports {
            xml.required.set(true)
            xml.outputLocation.set(outputReportFile)
        }
    }

    /**
     * Finalizes every detekt task with ReportMergeTask
     */
    plugins.withType<DetektPlugin> {
        tasks.withType<Detekt> {
            finalizedBy(reportMerge)
            reportMerge.configure {
                input.from(xmlReportFile)
            }
        }
    }
}

inline fun <reified T : Task> Project.registerMaybe(
    taskName: String,
    configuration: Action<in T>,
): TaskProvider<T> {
    if (taskName in tasks.names) {
        return tasks.named(taskName, T::class, configuration)
    }
    return tasks.register(taskName, T::class, configuration)
}

```

Если требуется изолировать текущее состояние проекта от проверок и в дальнейшем проверять только новый код
то нужно выполнить команду `./gradlew detektBaseline`, которая сгенерирует файл со списком всех текущих ошибок.

## Настройка Danger

В Dangerfile надо добавить проверку отчёта и отображение ошибок в ПРе в виде инлайн комментариев

```

# Process Detekt results
detektFile = String.new("build/reports/detekt/report.xml")
if File.file?(detektFile)
    kotlin_detekt.report_file = detektFile
    kotlin_detekt.skip_gradle_task = true
    kotlin_detekt.severity = "warning"
    kotlin_detekt.filtering = true
    kotlin_detekt.detekt(inline_mode: true)
else
    warn("Отсутствует файл с описанием ошибок - #{detektFile}")
end

```

Дальше нужно заменить (если используются стандартные) или дополнить файлы [Gemfile](https://github.com/tutu-ru-mobile/android-user-space/blob/master/Gemfile) и [Gemfile.lock](https://github.com/tutu-ru-mobile/android-user-space/blob/master/Gemfile.lock)

## Настройка workflow

В workflow проверки ПРа (обычно это `run_check_pr`) заменить `tutu-ru-mobile/github-workflows/.github/workflows/check_pull_request.yml@main`
на `tutu-ru-mobile/github-workflows/.github/workflows/check_android_pr_with_detekt.yml@main`