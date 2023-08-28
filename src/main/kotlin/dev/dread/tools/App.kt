package dev.dread.tools

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import kotlin.io.path.Path

/**
 * @project kweb3-contract-gen
 * @author andy@hlwgroup.dev - 8/24/2023 4:53 PM
 */
fun main(args: Array<String>) {
    val parser = ArgParser("contract-gen")
    val abiDir by parser.option(ArgType.String, "abi", shortName = "a").required()
    val outputDir by parser.option(ArgType.String, "out", shortName = "o").required()
    val className by parser.option(ArgType.String, "className", shortName = "c").required()
    val packageName by parser.option(ArgType.String, "packageName", shortName = "p").default("")
    parser.parse(args)

    val abi = ContractABI.from(abiDir) ?: throw Exception("Failed to parse ABI")
    abi.toKtSource(packageName, className, Path(outputDir))
}