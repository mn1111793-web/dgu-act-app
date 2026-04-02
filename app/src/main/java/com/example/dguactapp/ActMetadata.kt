package com.example.dguactapp

enum class DocumentType(val storageValue: String, val title: String) {
    DiagnosticAct(storageValue = "diagnostic_act", title = "Акт диагностики"),
    TransferAcceptanceAct(storageValue = "transfer_acceptance_act", title = "Акт приёма-передачи"),
    AcceptanceAct(storageValue = "acceptance_act", title = "Акт сдачи-приёма из ремонта");

    companion object {
        fun fromStorageValue(value: String): DocumentType =
            values().firstOrNull { it.storageValue == value } ?: DiagnosticAct
    }
}

enum class ActStatus(val storageValue: String, val title: String) {
    Draft(storageValue = "draft", title = "Черновик"),
    Saved(storageValue = "saved", title = "Сохранён"),
    Signed(storageValue = "signed", title = "Подписан"),
    Approved(storageValue = "approved", title = "Утверждён");

    companion object {
        fun fromStorageValue(value: String): ActStatus =
            values().firstOrNull { it.storageValue == value } ?: Draft
    }
}
