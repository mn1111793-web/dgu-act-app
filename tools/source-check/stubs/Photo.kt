package com.example.dguactapp
/** Only Android URI/UI parts are excluded; repository, codecs, models and checklist logic are real sources. */
data class ActPhoto(val id: String = java.util.UUID.randomUUID().toString(), val filePath: String)
