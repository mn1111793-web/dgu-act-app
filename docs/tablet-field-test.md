# Изолированная тестовая сборка для Android 13

Разрешённый локальный тестовый этап 24.10.2026, без изменения рабочего приложения. База исходников e0851170e89ed73e6b17237f51e8fcc542f49eca. Старый android-build-plan.md — исторический план до разрешения сборки; новый этап использует реальный планшет только для РУЧНОЙ установки владельцем, без эмулятора/ADB/автоустановки.

Dedicated build type `fieldTest` наследует `debug`, имеет `applicationIdSuffix = ".test"`, `debuggable=true`, отдельную signingConfig и task `:app:assembleFieldTest`. Итоговый package ровно `com.example.dguactapp.test`, label «ДГУ Ямал — ТЕСТ», жёлтый vector icon и постоянная экранная плашка. Production defaultConfig/applicationId, release/debug signing и backup-настройки не менялись. В main manifest добавлена только явная optional camera feature для исправления lint; установленное рабочее приложение не менялось. Namespace и FQCN MainActivity остаются com.example.dguactapp.

AGP 8.6.1, Gradle 8.7, JDK17, SDK Platform35/BuildTools34.0.0, Kotlin1.9.24/Compose compiler1.5.14. Wrapper4files включены в Git, официальный SHA distribution в properties, официальный SHA wrapper JAR проверен перед запуском. Инструменты/SDK/Gradle cache только в workspace/.local-tools, никакого system install. Подготовка зависит от штатных лицензий SDK. Точная среда и результаты — в передаваемом build-report/metadata/logs, не в исходниках с ключами.

Для разработчика в изолированной среде нужны JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME и приватные DGU_TEST_KEYSTORE, DGU_TEST_STORE_PASSWORD, DGU_TEST_KEY_PASSWORD. Ключ RSA отдельный тестовый, alias dgu-field-test, файл и конфигурация 600 в приватном workspace вне Git. Значения секретов не коммитить/не передавать в чат. При отсутствии ключа сборка не должна заменять его production key. Зависимости фиксированных версий, без новых сервисов/API.

```text
./gradlew --no-daemon :app:assembleFieldTest :app:lintFieldTest :app:testFieldTestUnitTest --stacktrace --console=plain
python3 tools/source-check/run.py --all
python3 tools/source-check/check_static.py
```

`testFieldTestUnitTest` может оказаться NO-SOURCE: это не PASS Android-тестов; meaningful regression остаются отдельными source host regressions (точный результат в отчёте). Не выполнять assembleRelease/bundle/install/connected-tests. build scans не публиковать. Gradle/Kotlin работают только на host; Android приложение само не запускается.

Изоляция: test data только Context.filesDir/applicationInfo.dataDir, legacy preferences также только test context. Нет обращения к рабочему com.example.dguactapp dataDir/authorities, sharedUserId или общему каталогу. FileProvider `${applicationId}.fileprovider`, roots только private act_photos/act_pdfs. PDF имена TEST_ + actId/revision/UUID, сохранение только через системный CreateDocument; share PDF не export базы. Никакого переноса/извлечения working data. На первой установке test package ожидается пустым, готовых записей в APK нет.

Test manifest удаляет INTERNET/network state/broad storage/media permissions; CAMERA остаётся для явного снимка, system photo picker без широкого доступа. Backup allowBackup=false/fullBackupContent=false и dataExtractionRules исключают cloud/device-transfer domains. Это декларативные ограничения, не универсальная гарантия от vendor transfer. Release/production backup policy не меняется.

Перед передачей обязательны фактические apksigner/aapt2 проверки package/label/launcher/provider/permissions/path XML/backup rules и содержимого APK. Проверять production package отдельно по уже переданному установленному APK A без доступа к планшету. Другой package даёт штатное основание установки рядом; выполнение на конкретной Ulefone/камера/SAF/PDF/печать не доказаны host build.

Ручное ОБНОВЛЕНИЕ уже установленного тестового приложения владельцем: открыть только новый dgu-yamal-test-draft-fix.apk → имя «ДГУ Ямал — ТЕСТ», package com.example.dguactapp.test, та же подпись c34efca5… и versionCode3 → «Обновить» допустимо ТОЛЬКО тестовому приложению → сохранить прежние synthetic записи, проверить черновики. Рабочее com.example.dguactapp не обновлять. Не удалять/не очищать; не ожидать пустой список при update. Старое имя, подпись/совместимость не совпали, запрос удаления/очистки или блокировка Play Protect — отменить, защиту не отключать. Полная инструкция manual-test-update.md в новом output.

Manual NOT RUN: один синтетический заказчик, две единицы/две заявки, каждый из трёх актов, по3фото камера+галерея, draft/Назад/reopen, подтверждённая serial correction той же единицы, PDF длинный serial/всефото/A4 preview. Повторный ремонт одной equipmentId через many requests всё ещё ограничен моделью, не скрывать. Роли, новые БД, дизайн, Google/ИНН/backend не реализованы.

Официальные источники: [AGP8.6](https://developer.android.com/build/releases/agp-8-6-0-release-notes), [Wrapper/checksums](https://docs.gradle.org/current/userguide/gradle_wrapper.html), [build variants](https://developer.android.com/build/build-variants), [manifest placeholders](https://developer.android.com/build/manage-manifests), [ограничения backup](https://developer.android.com/identity/data/autobackup). Доступность runtime и безопасности обновления рабочего APK не выводить из одного имени/версии.

Первый полный lint выявил6NewApi(File.toPath/Path.startsWith приmin24) и1camera feature error. Исправлены реальной canonical/segment-aware проверкой через java.io.File и явной optional camera feature, без baseline/подавления. Новая host-регрессия проверяет sibling-prefix, ../ и symlink escape. Test build исключает ненужные debug-only preview/testing tooling dependencies; production debug deps сохранены.

Текущий bugfix от9764bac: [аудит](draft-fix-audit.md), [UI-план](tablet-ui-plan.md), [статические эскизы](tablet-ui-mockups.html). Новая модель DTOv2, прежние source/API/production версии сохранены; только fieldTest code3 и -draftfix1.
