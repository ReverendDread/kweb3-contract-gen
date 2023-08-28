package dev.dread.tools

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import dev.klepto.kweb3.abi.type.Address
import dev.klepto.kweb3.abi.type.Bytes
import dev.klepto.kweb3.abi.type.Tuple
import dev.klepto.kweb3.abi.type.Uint
import java.lang.IllegalStateException
import java.util.*
import kotlin.reflect.KClass

/**
 * @project kweb3-contract-gen
 * @author andy@hlwgroup.dev - 8/24/2023 6:22 PM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class AbiDefinition(
    val type: String,
    val name: String? = null,
    val inputs: List<NamedType>? = null,
    val outputs: List<NamedType>? = null,
    val stateMutability: StateMutability? = null,
    val anonymous: Boolean? = true
) {

    data class NamedType(
        val type: String,
        val name: String? = null,
        val components: List<NamedType>? = null,
        val internalType: String? = null,
        val indexed: Boolean? = null,
    ) {

        fun getKtType(): KClass<*> {
            return when {
                isArray() -> {
                    val withoutArray = type.replace("[]", "")
                    copy(type = withoutArray, internalType = withoutArray).getKtType()
                }
                type.contains("uint") -> Uint::class
                type.contains("int") -> dev.klepto.kweb3.abi.type.Int::class
                type.contains("bytes") -> Bytes::class
                type.contains("string") -> String::class
                type.contains("bool") -> Boolean::class
                type.contains("address") -> Address::class
                type == "tuple" -> Tuple::class
                else -> throw IllegalArgumentException("Invalid type: $type")
            }
        }

        val valueSize: Int = when {
            type.contains("uint") -> {
                val size = type.replace("uint", "").replace("[]", "").toInt()
                if (size % 8 != 0) {
                    throw IllegalArgumentException("Invalid uint size: $size")
                }
                size
            }
            type.contains("int") -> {
                val size = type.replace("int", "").replace("[]", "").toInt()
                if (size % 8 != 0) {
                    throw IllegalArgumentException("Invalid int size: $size")
                }
                size
            }
            type.contains("bytes") -> {
                if (type == "bytes")
                    32
                else {
                    val size = type.replace("bytes", "").replace("[]", "").toInt()
                    if (size !in 1..32) {
                        throw IllegalArgumentException("Invalid bytes size: $size")
                    }
                    size
                }
            }
            else -> -1
        }

        fun isArray() = type.contains("[]") || internalType?.contains("[]") ?: false

        fun isTuple() = type.contains("tuple")

        fun getStructName(): String? {
            return if (isTuple()) {
                if (name.isNullOrBlank())
                    internalType!!.substringAfter("struct ").replace(".", "").replace("[]", "")
                else
                    name
            } else
                null
        }

    }

    fun getTupleName(): String {
        if (!isTuple())
            throw IllegalStateException("")
        return name!!.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + "Tuple"
    }

    fun isTuple(): Boolean {
        return outputs != null && outputs.size > 1
    }

    fun isStruct(): Boolean {
        return outputs != null && outputs.size == 1 && outputs.first().isTuple()
    }

    fun isPrimitive(): Boolean {
        return outputs != null && outputs.size == 1 && !outputs.first().isTuple()
    }

    override fun toString(): String {
        return "AbiDefinition(type='$type', name=$name, inputs=$inputs, outputs=$outputs, stateMutability=$stateMutability)"
    }

}