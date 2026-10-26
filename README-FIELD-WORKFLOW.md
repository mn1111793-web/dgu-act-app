# Isolated field workflow — TEST only

The `fieldTest` variant opens a nine-step Compose workflow in the existing application. The production application ID and entry point remain unchanged. Update only `com.example.dguactapp.test` using the existing test signing certificate; do not uninstall or clear its data.

The workflow uses a strictly decoded business DTO in `filesDir/field_workflow_v1.json`. Writes stage and sync bytes, preserve the previous complete version, and rename within the same directory. Unknown schemas/fields block writes; stale record revisions are rejected. This additive sidecar does not change the existing snapshot schema. Before continuing legacy data, the app retains a private byte backup of the old snapshot. Previous acts/PDFs remain available. Legacy drafts retain original JSON, photos, pending camera context and signature strokes without assuming new signing roles or consent.

One request supplies shared customer/equipment fields to intake, diagnostics and return. Intake hours are labelled separately from later measured hours. Saved PDFs have unique immutable paths and business snapshots. A price entry is distinct from customer consent. Quote revisions reset approval; partial approval stores exact item IDs. Physical receipt is distinct from quality acceptance. Document statuses remain draft/saved/signed/approved, with one optional process state.

Photos reuse the existing private `PhotoStorage`/FileProvider and scoped picker. Pending camera and gallery category/stage are durable. No network or broad-storage permission is added. Offline voice, server authentication, synchronization and a fourth act type are not implemented.

Build with the existing SDK/JDK/cache and original test signing environment variables:

```
./gradlew --offline --no-daemon :app:assembleFieldTest :app:lintFieldTest :app:testFieldTestUnitTest
```

`tools/source-check/FieldWorkflowRegression.kt` is a standalone JVM regression entry point compiling the actual `FieldWorkflow`, `FieldContent` and `EnterpriseCardParser` sources against `org.json`. It tests consent, quote versions/positions, conditional PDF content, shared DTO restoration, immutable snapshots, stale revisions, interrupted writes, strict recovery and structured imports using synthetic temporary files. It is distinct from the Gradle unit task when that task has no test sources.

Build/static/host checks do not establish actual Android UI, touch, lifecycle, camera/picker, PDF rendering or printer behavior. Those require owner acceptance on the existing isolated test app. No device installation or emulator execution is part of this change. Legal wording follows the approved direction of the document mockups and is not represented as a final legal opinion.
