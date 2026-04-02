package com.example.dguactapp

data class EquipmentType(
    val code: String,
    val name: String,
    val brands: List<EquipmentBrand> = emptyList()
) {
    val displayName: String = "$code — $name"
}

data class EquipmentBrand(
    val name: String,
    val models: List<String> = emptyList()
)

object EquipmentCatalog {
    const val OTHER_OPTION = "Другое"

    val equipmentTypes: List<EquipmentType> = listOf(
        EquipmentType(
            code = "DGU",
            name = "дизельный генератор",
            brands = listOf(
                EquipmentBrand("FG Wilson", listOf("P110-3", "P165-5")),
                EquipmentBrand("Cummins", listOf("C110 D5", "C220 D5")),
                EquipmentBrand("Teksan", listOf("TJ88PE5A", "TJ275PE5C"))
            )
        ),
        EquipmentType(
            code = "WGU",
            name = "сварочный генератор",
            brands = listOf(
                EquipmentBrand("Denyo", listOf("DLW-400ESW", "GAW-190ES2")),
                EquipmentBrand("Mosa", listOf("TS 200 BS/CF", "Magic Weld 200"))
            )
        ),
        EquipmentType(
            code = "BGU",
            name = "бензиновый генератор",
            brands = listOf(
                EquipmentBrand("Huter", listOf("DY6500L", "DY8000LX")),
                EquipmentBrand("Fubag", listOf("BS 6600", "BS 8500 XD ES"))
            )
        ),
        EquipmentType(
            code = "CMP",
            name = "компрессор",
            brands = listOf(
                EquipmentBrand("Atlas Copco", listOf("XAS 88", "XATS 138")),
                EquipmentBrand("Chicago Pneumatic", listOf("CPS 5.0", "CPS 185"))
            )
        ),
        EquipmentType(
            code = "RLM",
            name = "вальцовочный станок",
            brands = listOf(
                EquipmentBrand("Metal Master", listOf("MSR 1215", "MSR 2020")),
                EquipmentBrand("Stalex", listOf("W01-0.8x1300", "ESR-1300x1.5"))
            )
        ),
        EquipmentType(
            code = "LAT",
            name = "токарный станок",
            brands = listOf(
                EquipmentBrand("JET", listOf("BD-8A", "GH-1640ZX")),
                EquipmentBrand("Proma", listOf("SPB-550", "SPA-500P"))
            )
        ),
        EquipmentType(code = "RQU", name = "прочее оборудование"),
        EquipmentType(
            code = "INV",
            name = "инверторный генератор",
            brands = listOf(
                EquipmentBrand("Honda", listOf("EU22i", "EU32i")),
                EquipmentBrand("Hyundai", listOf("HHY 3050Si", "HHY 7050Si"))
            )
        ),
        EquipmentType(
            code = "ATS",
            name = "АВР / шкаф автоматического ввода резерва",
            brands = listOf(
                EquipmentBrand("TCC", listOf("ATS 63A", "ATS 160A")),
                EquipmentBrand("TSS", listOf("ATS 63A", "ATS 160A")),
                EquipmentBrand("EKF", listOf("AVR-63", "AVR-100"))
            )
        ),
        EquipmentType(
            code = "PMP",
            name = "насос / насосная установка",
            brands = listOf(
                EquipmentBrand("Grundfos", listOf("JP 4-47", "CM 3-5")),
                EquipmentBrand("Wilo", listOf("WJ 202", "MHI 405"))
            )
        ),
        EquipmentType(
            code = "HTR",
            name = "тепловая пушка / нагреватель",
            brands = listOf(
                EquipmentBrand("Master", listOf("B 70", "BV 110")),
                EquipmentBrand("Ballu", listOf("BHP-P2-5", "BHDN-20"))
            )
        ),
        EquipmentType(
            code = "WEL",
            name = "сварочный аппарат",
            brands = listOf(
                EquipmentBrand("Сварог", listOf("REAL ARC 200", "MIG 250Y")),
                EquipmentBrand("Ресанта", listOf("САИ-190", "САИПА-220"))
            )
        ),
        EquipmentType(
            code = "LGT",
            name = "осветительная мачта",
            brands = listOf(
                EquipmentBrand("Atlas Copco", listOf("HiLight V4", "HiLight B5+")),
                EquipmentBrand("Pramac", listOf("LSW-8", "GLT4-M"))
            )
        ),
        EquipmentType(
            code = "PLT",
            name = "виброплита",
            brands = listOf(
                EquipmentBrand("Wacker Neuson", listOf("VP1550", "DPU 4545")),
                EquipmentBrand("Masalta", listOf("MS60-4", "MS125-4"))
            )
        ),
        EquipmentType(
            code = "CUT",
            name = "резчик / станок для резки",
            brands = listOf(
                EquipmentBrand("Husqvarna", listOf("K 770", "FS 400 LV")),
                EquipmentBrand("Cedima", listOf("CF-13.3", "CTS-57G"))
            )
        ),
        EquipmentType(
            code = "DRL",
            name = "дрель / бурильная установка",
            brands = listOf(
                EquipmentBrand("Hilti", listOf("DD 150-U", "DD 250-CA")),
                EquipmentBrand("Cardi", listOf("DP 2200 MA-16", "T6-375-EL"))
            )
        ),
        EquipmentType(
            code = "MIX",
            name = "смеситель / бетономешалка",
            brands = listOf(
                EquipmentBrand("Zitrek", listOf("B-1510", "B-1808")),
                EquipmentBrand("Вихрь", listOf("БМ-130", "БМ-160"))
            )
        )
    )

    fun findEquipment(code: String): EquipmentType? =
        equipmentTypes.firstOrNull { it.code == code.uppercase() }

    fun brandOptions(equipmentCode: String): List<String> {
        val names = findEquipment(equipmentCode)
            ?.brands
            ?.map { it.name }
            .orEmpty()
        return (names + OTHER_OPTION).distinct()
    }

    fun modelOptions(equipmentCode: String, brandName: String): List<String> {
        val equipment = findEquipment(equipmentCode) ?: return listOf(OTHER_OPTION)
        val models = equipment.brands.firstOrNull { it.name == brandName }?.models.orEmpty()
        return (models + OTHER_OPTION).distinct()
    }
}
