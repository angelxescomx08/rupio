package com.rupio.app.domain

/**
 * v1.9 · Reordenar listas (ejercicios de una rutina o del entreno, y las rutinas).
 *
 * Regla de oro: **una superserie nunca se parte**. Los miembros de una superserie son consecutivos y se mueven juntos;
 * dentro de la superserie se pueden reacomodar entre ellos. Todas las funciones son puras (no tocan la base) para que
 * la pantalla de "Ordenar", los botones "Subir/Bajar", TalkBack y la base usen exactamente las mismas reglas.
 *
 * Un "bloque" es un ejercicio suelto o una superserie completa.
 */
object ReorderOps {

    /** Agrupa en bloques: elementos consecutivos con el mismo grupo (null = suelto, cada uno es su propio bloque). */
    fun <T> blocks(list: List<T>, group: (T) -> String?): List<List<T>> {
        val out = mutableListOf<MutableList<T>>()
        for (it in list) {
            val g = group(it)
            val last = out.lastOrNull()
            if (g != null && last != null && group(last.first()) == g) last += it else out += mutableListOf(it)
        }
        return out
    }

    /** ¿Cada superserie está junta (sus miembros consecutivos)? Si no, el orden no se acepta. */
    fun <T> isValid(list: List<T>, group: (T) -> String?): Boolean {
        val seen = HashSet<String>()
        var prev: String? = null
        for (it in list) {
            val g = group(it)
            if (g != null && g != prev && !seen.add(g)) return false
            prev = g
        }
        return true
    }

    /**
     * Subir (-1) o bajar (+1) un paso. Dentro de una superserie, el ejercicio cambia de lugar con su vecino del grupo;
     * en la orilla del grupo (o si va suelto) se mueve el bloque completo. Devuelve null si no se puede mover.
     */
    fun <T> moveStep(list: List<T>, key: String, delta: Int, keyOf: (T) -> String, group: (T) -> String?): List<T>? {
        if (delta != -1 && delta != 1) return null        // un salto raro (p. ej. ±100) no hace nada ni truena
        val bs = blocks(list, group).map { it.toMutableList() }.toMutableList()
        val bi = bs.indexOfFirst { b -> b.any { keyOf(it) == key } }
        if (bi < 0) return null
        val b = bs[bi]
        val inner = b.indexOfFirst { keyOf(it) == key }
        when {
            b.size > 1 && inner + delta in b.indices -> java.util.Collections.swap(b, inner, inner + delta)
            bi + delta in bs.indices -> java.util.Collections.swap(bs, bi, bi + delta)
            else -> return null
        }
        return bs.flatten()
    }

    /** ¿Se puede subir/bajar? (para apagar el botón en la orilla). */
    fun <T> canMove(list: List<T>, key: String, delta: Int, keyOf: (T) -> String, group: (T) -> String?): Boolean =
        moveStep(list, key, delta, keyOf, group) != null

    /**
     * Al inicio o al final: el bloque del ejercicio va a la orilla de la lista; si el ejercicio está en una superserie y
     * no es ya la orilla de su grupo, se mueve a la orilla DENTRO del grupo (primero o último de la superserie).
     */
    fun <T> moveToEdge(list: List<T>, key: String, toStart: Boolean, keyOf: (T) -> String, group: (T) -> String?): List<T>? {
        val bs = blocks(list, group).map { it.toMutableList() }.toMutableList()
        val bi = bs.indexOfFirst { b -> b.any { keyOf(it) == key } }
        if (bi < 0) return null
        val b = bs[bi]
        val inner = b.indexOfFirst { keyOf(it) == key }
        val innerEdge = if (toStart) 0 else b.lastIndex
        if (b.size > 1 && inner != innerEdge) {
            b.add(innerEdge, b.removeAt(inner))
            return bs.flatten()
        }
        val target = if (toStart) 0 else bs.lastIndex
        if (bi == target) return null
        bs.add(target, bs.removeAt(bi))
        return bs.flatten()
    }

    /** Mueve el bloque [from] a la posición de bloque [to] (lo que hace arrastrar en la pantalla de "Ordenar"). */
    fun <T> moveBlock(blocks: List<List<T>>, from: Int, to: Int): List<List<T>> {
        if (from !in blocks.indices || to !in blocks.indices || from == to) return blocks
        return blocks.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Mueve un miembro dentro de su bloque. */
    fun <T> moveWithinBlock(block: List<T>, from: Int, to: Int): List<T> {
        if (from !in block.indices || to !in block.indices || from == to) return block
        return block.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Aplica un orden por llaves a la lista real. Devuelve null si las llaves no son exactamente las mismas (un elemento
     * de más, de menos o repetido) o si el orden partiría una superserie: así nunca se guarda un orden a medias.
     */
    fun <T> applyOrder(list: List<T>, orderedKeys: List<String>, keyOf: (T) -> String, group: (T) -> String?): List<T>? {
        if (orderedKeys.size != list.size || orderedKeys.toSet().size != orderedKeys.size) return null
        val byKey = list.associateBy(keyOf)
        if (byKey.keys != orderedKeys.toSet()) return null
        val out = orderedKeys.map { byKey.getValue(it) }
        return if (isValid(out, group)) out else null
    }
}
