package dev.dread.tools

import com.fasterxml.jackson.annotation.JsonProperty
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import dev.klepto.kweb3.Web3Response
import dev.klepto.kweb3.abi.type.*
import dev.klepto.kweb3.contract.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

/**
 * @project contract-gen
 * @author andy@hlwgroup.dev - 8/24/2023 4:02 PM
 */
class ContractABI(
    @JsonProperty("_format") val format: String,
    val contractName: String,
    val sourceName: String,
    val abi: Array<AbiDefinition>
) {

    private val generatedTypes = generateTypeSpecs()

    fun toKtSource(packageName: String, className: String, destPath: Path) {
        val contractClassName = ClassName(packageName, className)
        val eventTypes = generateEventTypes()
        val contractFile = FileSpec.builder(contractClassName)
            .addType(
                TypeSpec.interfaceBuilder(contractClassName)
                    .apply { generatedTypes.values.forEach(::addType) }
                    .apply { eventTypes.forEach(::addType) }
                    .addKdoc("""
                        Auto generated code from kweb3-contract-gen (https://github.com/ReverendDread/kweb3-contract-gen)
                        Please do not modify.
                    """.trimIndent())
                    .addSuperinterface(Contract::class)
                    .addFunctions(functions.map { it.generateFunctionSpec() })
                    .build()
            )
            .addAnnotation(
                AnnotationSpec.builder(ClassName("", "Suppress"))
                    .addMember("%S,".repeat(3), "RedundantVisibilityModifier", "UNCHECKED_CAST", "UNUSED")
                    .build()
            )
            .build()
        println(contractFile.toString())
        contractFile.writeTo(destPath)
    }

    private fun AbiDefinition.generateFunctionSpec(): FunSpec {
        return FunSpec.builder(name!!).apply {

            // adds annotation for view or transaction
            addAnnotation(
                AnnotationSpec.builder(
                    getAnnotationForStateMutability(stateMutability!!)
                ).build()
            )
            // interface functions are abstract always
            addModifiers(KModifier.ABSTRACT)

            // add parameters
            val parameters = inputs?.mapIndexed { i, solType ->
                val type = solType.getKtType()
                val annotation = generateTypeAnnotation(type, solType.valueSize, false)
                ParameterSpec.builder(solType.getParameterName(i), type).apply {
                    annotation?.let { addAnnotation(it) }
                }.build()
            }
            parameters?.let { addParameters(it) }

            // use direct struct return type
            if (isStruct()) {
                val struct = outputs!!.first()
                val topLevelType = struct.getStructName()!!
                val returnTypeName = ClassName("", topLevelType)

                // we have to add annotation to decode struct
                addAnnotation(AnnotationSpec.builder(Type::class)
                    .addMember(if (struct.isArray()) "Array<%T>::class" else "%T::class", Struct::class).build())

                if (struct.isArray())
                    returns(Array::class.asClassName().parameterizedBy(returnTypeName))
                else
                    returns(returnTypeName)
            } else if (isTuple()) {
                // if we just return a single struct
                val tupleName = getTupleName()
                returns(ClassName("", tupleName))
            } else if (isPrimitive()) {
                // use primitive return type
                val solType = outputs!!.first()
                val returnType = solType.getKtType()

                // we have to add annotation to decode struct
                addAnnotation(AnnotationSpec.builder(Type::class)
                    .addMember(if (solType.isArray()) "Array<%T>::class" else "%T::class", returnType).build())

                if (solType.isArray())
                    returns(Array::class.asClassName().parameterizedBy(returnType.asTypeName()))
                else
                    returns(returnType)
            } else {
                // default to use Web3Response
                returns(Web3Response::class)
            }
        }.build()
    }

    private fun getAnnotationForStateMutability(stateMutability: StateMutability): KClass<out Annotation> {
        return when(stateMutability) {
            StateMutability.pure, StateMutability.view, StateMutability.nonpayable -> View::class
            StateMutability.payable -> Transaction::class
        }
    }

    private fun generateTypeAnnotation(type: KClass<*>, valueSize: Int, isField: Boolean): AnnotationSpec? {
        // skip annotations for types that are not ABI values
        if (!type.isSubclassOf(AbiValue::class)) {
            return null
        }

        // skip annotations for default decoded type
        if (type == Address::class
            || (type == Uint::class && valueSize == 256)
            || (type == dev.klepto.kweb3.abi.type.Int::class && valueSize == 256)
            || (type == Bytes::class && valueSize == 32)
        ) {
            return null
        }

        return AnnotationSpec.builder(Type::class).apply {
            // add field annotation if needed, this is used for tuple fields
            if (isField)
                useSiteTarget(AnnotationSpec.UseSiteTarget.FIELD)
            // don't add valueSize if it's not needed
            if (valueSize == -1)
                addMember("%T::class", type)
            // wrap value size in an array if it's an array type
            else if (type == Array::class)
                addMember("Array<%T>::class, arraySize = %L", type, valueSize)
            else
                addMember("%T::class, valueSize = %L", type, valueSize)
        }.build()
    }

    private fun generateEventTypes(): List<TypeSpec> {
        return events.filter { !it.anonymous!! }.map { it.generateEventType() }
    }

    private fun AbiDefinition.generateEventType(): TypeSpec {
        val className = name!! + "Event"
        val properties = mutableListOf<PropertySpec>()
        return TypeSpec.classBuilder(className)
            .addAnnotation(AnnotationSpec.builder(Event::class).addMember("\"$name\"").build())
            .primaryConstructor(FunSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder("emitter", Address::class)
                    .addModifiers()
                    .addAnnotation(AnnotationSpec.builder(Event.Address::class).build()).build())
                .apply {
                    inputs?.forEachIndexed { i, arg ->
                        val ktType = arg.getKtType()
                        addParameter(ParameterSpec.builder(arg.getParameterName(i), ktType).build())
                        properties.add(PropertySpec.builder(arg.getParameterName(i), ktType).apply {
                            val valueSize = arg.valueSize
                            val annotation = generateTypeAnnotation(ktType, valueSize, true)
                            if (arg.indexed == true)
                                addAnnotation(AnnotationSpec.builder(Event.Indexed::class).build())
                            annotation?.let { addAnnotation(it) }
                            initializer(arg.getParameterName(i))
                        }.build())
                    }
                }
                .build())
            .addProperty(PropertySpec.builder("emitter", Address::class)
                .initializer("emitter")
                .build())
            .addProperties(properties)
            .addModifiers(KModifier.DATA)
            .build()
    }

    private fun generateTupleClass(name: String, components: List<AbiDefinition.NamedType>): TypeSpec {
        return TypeSpec.classBuilder(name).apply {
            var constructorIndex = 0
            var propertyIndex = 0
            primaryConstructor(
                FunSpec.constructorBuilder().apply {
                    components.forEachIndexed { i, namedType ->
                        val solType = namedType.getKtType()
                        addParameter(namedType.getParameterName(i), solType)
                    }
                }.build()
            ).build()

            components.forEachIndexed { i, namedType ->
                val solType = namedType.getKtType()
                addProperty(PropertySpec.builder(namedType.getParameterName(i), solType).apply {
                    generateTypeAnnotation(solType, namedType.valueSize, true)?.let { addAnnotation(it) }
                }.initializer(namedType.getParameterName(i)).build())
            }
        }
        .addModifiers(KModifier.DATA)
        .build()
    }

    private fun generateTypeSpecs(): Map<String, TypeSpec> {
        val types = mutableMapOf<String, List<AbiDefinition.NamedType>>()
        abi.forEach { def ->
            if (def.inputs != null) {
                types.putAll(def.inputs.mapNameToComponents())
            }
            if (def.outputs != null) {
                val createWrapperTuple = def.outputs.size > 1
                if (createWrapperTuple) {
                    val wrapperTupleName = (def.name!!.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } + "Tuple").replace(".", "").replace("[]", "").replace(" ", "")
                    types[wrapperTupleName] = def.outputs
                }

                types.putAll(def.outputs.mapNameToComponents())
            }
        }
        return types.generateTypeSpecs()
    }

    private fun Map<String, List<AbiDefinition.NamedType>>.generateTypeSpecs(): Map<String, TypeSpec> {
        return buildMap {
            this@generateTypeSpecs.forEach { (name, components) ->
                put(name, generateTupleClass(name, components))
            }
        }
    }

    private fun List<AbiDefinition.NamedType>.mapNameToComponents(): Map<String, List<AbiDefinition.NamedType>> {
        return filter {
            it.getStructName() != null
        }.associate {
            val structName = it.getStructName()!!
            val components = it.components!!
            structName to components
        }
    }

    val functions = abi.filter { it.type == FUNCTION_TYPE }
    val events = abi.filter { it.type == EVENT_TYPE }
    val constructors = abi.filter { it.type == CONSTRUCTOR_TYPE }
    val fallbacks = abi.filter { it.type == FALLBACK_TYPE }
    val receives = abi.filter { it.type == RECEIVE_TYPE }

    companion object {

        const val FUNCTION_TYPE = "function"
        const val EVENT_TYPE = "event"
        const val CONSTRUCTOR_TYPE = "constructor"
        const val FALLBACK_TYPE = "fallback"
        const val RECEIVE_TYPE = "receive"

        fun from(path: String): ContractABI? {
            try {
                val stream = Files.readAllBytes(Path.of(path))
                return Json.mapper.readValue(stream.inputStream(), ContractABI::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

    }

    override fun toString(): String {
        return "ContractABI(format='$format', contractName='$contractName', sourceName='$sourceName', abi=${abi.contentToString()})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContractABI) return false

        if (format != other.format) return false
        if (contractName != other.contractName) return false
        if (sourceName != other.sourceName) return false
        if (!abi.contentEquals(other.abi)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + contractName.hashCode()
        result = 31 * result + sourceName.hashCode()
        result = 31 * result + abi.contentHashCode()
        return result
    }

}