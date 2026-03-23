package com.example.dguactapp

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.listSaver

enum class DiagnosisType(val storageValue: String, val title: String) {
    Primary(storageValue = "primary", title = "Первичная диагностика"),
    Advanced(storageValue = "advanced", title = "Углублённая диагностика");

    companion object {
        fun fromStorageValue(value: String): DiagnosisType =
            values().firstOrNull { it.storageValue == value } ?: Primary
    }
}

data class ChecklistItemState(
    val key: String,
    val title: String,
    val checked: Boolean = false,
    val faulty: Boolean = false,
    val comment: String = ""
)

data class ChecklistSectionDefinition(
    val title: String,
    val items: List<ChecklistItemDefinition>
)

data class ChecklistItemDefinition(
    val key: String,
    val title: String
)

object DiagnosticChecklistCatalog {
    private val primarySections = listOf(
        ChecklistSectionDefinition(
            title = "Внешний осмотр",
            items = listOf(
                ChecklistItemDefinition("visual_external_inspection", "Внешний осмотр"),
                ChecklistItemDefinition("visual_completeness", "Комплектность"),
                ChecklistItemDefinition("visual_casing_condition", "Состояние корпуса"),
                ChecklistItemDefinition("visual_damage_traces", "Следы повреждений")
            )
        ),
        ChecklistSectionDefinition(
            title = "Проверка запуска",
            items = listOf(
                ChecklistItemDefinition("startup_launch", "Запуск"),
                ChecklistItemDefinition("startup_noise", "Шум"),
                ChecklistItemDefinition("startup_vibration", "Вибрация"),
                ChecklistItemDefinition("startup_smoke", "Дым"),
                ChecklistItemDefinition("startup_error_indication", "Индикация ошибок")
            )
        )
    )

    private val advancedSections = listOf(
        ChecklistSectionDefinition(
            title = "Углублённая проверка",
            items = listOf(
                ChecklistItemDefinition("advanced_measurements", "Замеры"),
                ChecklistItemDefinition("advanced_disassembly", "Вскрытие"),
                ChecklistItemDefinition("advanced_internal_nodes", "Состояние внутренних узлов"),
                ChecklistItemDefinition("advanced_electrical_measurements", "Электрические измерения"),
                ChecklistItemDefinition("advanced_mechanical_wear", "Механические зазоры / износ")
            )
        )
    )

    fun sectionsFor(type: DiagnosisType): List<ChecklistSectionDefinition> = when (type) {
        DiagnosisType.Primary -> primarySections
        DiagnosisType.Advanced -> primarySections + advancedSections
    }

    fun stateFor(
        type: DiagnosisType,
        savedItems: List<ChecklistItemState> = emptyList(),
        legacyAct: ActRecord? = null
    ): List<ChecklistItemState> {
        val savedByKey = savedItems.associateBy { it.key }
        val legacyByKey = buildLegacySeed(legacyAct)

        return sectionsFor(type)
            .flatMap { it.items }
            .map { definition ->
                savedByKey[definition.key]
                    ?: legacyByKey[definition.key]
                    ?: ChecklistItemState(key = definition.key, title = definition.title)
            }
    }

    fun buildLegacySeed(legacyAct: ActRecord?): Map<String, ChecklistItemState> {
        if (legacyAct == null) return emptyMap()
        return listOfNotNull(
            legacyAct.completeness.takeIf { it.isNotBlank() }?.let {
                ChecklistItemState(
                    key = "visual_completeness",
                    title = "Комплектность",
                    comment = it
                )
            },
            legacyAct.externalCondition.takeIf { it.isNotBlank() }?.let {
                ChecklistItemState(
                    key = "visual_casing_condition",
                    title = "Состояние корпуса",
                    comment = it
                )
            }
        ).associateBy { it.key }
    }

    fun primaryConclusionFromLegacy(legacyAct: ActRecord?): String =
        legacyAct?.malfunctionDescription.orEmpty()
}

val ChecklistStateSaver = listSaver<SnapshotStateList<ChecklistItemState>, String>(
    save = { list ->
        list.flatMap { item ->
            listOf(
                item.key,
                item.title,
                item.checked.toString(),
                item.faulty.toString(),
                item.comment
            )
        }
    },
    restore = { restored ->
        restored.chunked(5)
            .mapNotNull { chunk ->
                if (chunk.size < 5) {
                    null
                } else {
                    ChecklistItemState(
                        key = chunk[0],
                        title = chunk[1],
                        checked = chunk[2].toBoolean(),
                        faulty = chunk[3].toBoolean(),
                        comment = chunk[4]
                    )
                }
            }
            .toMutableStateList()
    }
)

fun SnapshotStateList<ChecklistItemState>.replaceWith(newItems: List<ChecklistItemState>) {
    clear()
    addAll(newItems)
}
