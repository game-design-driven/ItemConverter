package settingdust.item_converter.networking

internal const val BULK_COUNT = -1

internal fun resolveBulkInputCountForSingleOutputStack(
    availableInput: Int,
    outputPerInput: Int,
    outputMaxStackSize: Int
): Int {
    if (availableInput <= 0) return 0

    val safeOutputPerInput = outputPerInput.coerceAtLeast(1)
    val maxOutput = outputMaxStackSize.coerceAtLeast(1)
    val requiredInput = maxOutput / safeOutputPerInput
    if (requiredInput <= 0) return 0

    return minOf(availableInput, requiredInput)
}

internal fun resolveBulkInputCountForSingleOutputStack(
    availableInput: Long,
    outputPerInput: Int,
    outputMaxStackSize: Int
): Long {
    if (availableInput <= 0L) return 0L

    val safeOutputPerInput = outputPerInput.coerceAtLeast(1)
    val maxOutput = outputMaxStackSize.coerceAtLeast(1)
    val requiredInput = maxOutput / safeOutputPerInput
    if (requiredInput <= 0) return 0L

    return minOf(availableInput, requiredInput.toLong())
}
