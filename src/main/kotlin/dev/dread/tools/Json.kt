package dev.dread.tools

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * @project kweb3-contract-gen
 * @author andy@hlwgroup.dev - 8/24/2023 4:48 PM
 */
internal object Json {
    val mapper = jacksonObjectMapper().apply {
        setDefaultLeniency(true)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        registerModule(
            KotlinModule.Builder()
                .configure(KotlinFeature.NullIsSameAsDefault, enabled = true)
                .build()
        )
    }
}