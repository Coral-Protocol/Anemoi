package org.coralprotocol.coralserver

fun <K, V> List<Pair<K, V>>.toMapOnDuplicate(onDuplicates: (duplicates: List<K>) -> Unit): Map<K, V> {
    val groups: Map<K, List<Pair<K, V>>> = groupBy { it.first }
    val duplicates = groups.filter { it.value.size > 1 }.map { it.key }
    if (duplicates.isNotEmpty()) {
        onDuplicates(duplicates)
    }
    return groups.mapValues { it.value.first().second }
}