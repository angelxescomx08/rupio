package com.rupio.app

import com.rupio.app.domain.ReorderOps
import org.junit.Assert.*
import org.junit.Test

/** v1.9: reglas de reordenar (las mismas para arrastrar, Subir/Bajar, TalkBack y la base). */
class ReorderOpsTest {
    /** Elemento de prueba: llave y superserie. */
    private data class E(val k: String, val g: String? = null)
    private val key: (E) -> String = { it.k }
    private val grp: (E) -> String? = { it.g }
    private fun keys(l: List<E>?) = l?.joinToString("") { it.k }

    // a, [b c] = superserie S, d, [e f] = superserie T
    private val list = listOf(E("a"), E("b", "S"), E("c", "S"), E("d"), E("e", "T"), E("f", "T"))

    @Test fun bloques() {
        assertEquals(listOf("a", "bc", "d", "ef"), ReorderOps.blocks(list, grp).map { b -> b.joinToString("") { it.k } })
        // Dos superseries distintas pegadas siguen siendo dos bloques.
        assertEquals(2, ReorderOps.blocks(listOf(E("x", "S"), E("y", "T")), grp).size)
    }

    @Test fun valido_solo_si_cada_superserie_esta_junta() {
        assertTrue(ReorderOps.isValid(list, grp))
        assertFalse(ReorderOps.isValid(listOf(E("b", "S"), E("a"), E("c", "S")), grp))
        assertTrue(ReorderOps.isValid(emptyList(), grp))
    }

    @Test fun subir_y_bajar_sueltos_saltan_la_superserie_completa() {
        // d sube: brinca el bloque [b c] completo (no se mete en medio).
        assertEquals("adbcef", keys(ReorderOps.moveStep(list, "d", -1, key, grp)))
        // a baja: queda después del bloque [b c].
        assertEquals("bcadef", keys(ReorderOps.moveStep(list, "a", 1, key, grp)))
    }

    @Test fun dentro_de_la_superserie_cambian_entre_ellos_y_en_la_orilla_se_mueve_el_bloque() {
        assertEquals("acbdef", keys(ReorderOps.moveStep(list, "b", 1, key, grp)))   // b ↔ c
        assertEquals("abcdfe", keys(ReorderOps.moveStep(list, "f", -1, key, grp)))  // f ↔ e (dentro de T)
        assertEquals("bcadef", keys(ReorderOps.moveStep(list, "b", -1, key, grp)))  // b en la orilla: sube [b c]
        assertEquals("adbcef", keys(ReorderOps.moveStep(list, "c", 1, key, grp)))   // c en la orilla: baja [b c]
    }

    @Test fun en_las_orillas_no_se_puede() {
        assertNull(ReorderOps.moveStep(list, "a", -1, key, grp))
        assertNull(ReorderOps.moveStep(list, "f", 1, key, grp))
        assertNull(ReorderOps.moveStep(list, "zzz", 1, key, grp))
        assertFalse(ReorderOps.canMove(list, "a", -1, key, grp))
        assertTrue(ReorderOps.canMove(list, "a", 1, key, grp))
        assertNull(ReorderOps.moveStep(listOf(E("solo")), "solo", 1, key, grp))
        assertNull("saltos raros no hacen nada", ReorderOps.moveStep(list, "d", -100, key, grp))
        assertNull(ReorderOps.moveStep(list, "d", 0, key, grp))
    }

    @Test fun nunca_se_parte_una_superserie_con_ningun_movimiento() {
        // Todas las combinaciones de subir/bajar/inicio/final en listas con superseries: siempre válidas y mismos elementos.
        val lists = listOf(list, listOf(E("x", "S"), E("y", "S"), E("z", "S")), listOf(E("p", "S"), E("q", "S"), E("r", "T"), E("s", "T"), E("t")))
        for (l in lists) for (e in l) {
            val results = listOf(
                ReorderOps.moveStep(l, e.k, -1, key, grp), ReorderOps.moveStep(l, e.k, 1, key, grp),
                ReorderOps.moveToEdge(l, e.k, true, key, grp), ReorderOps.moveToEdge(l, e.k, false, key, grp),
            )
            results.filterNotNull().forEach { r ->
                assertTrue("válida: ${keys(r)}", ReorderOps.isValid(r, grp))
                assertEquals("mismos elementos", l.toSet(), r.toSet())
                assertEquals(l.size, r.size)
            }
        }
    }

    @Test fun al_inicio_y_al_final() {
        assertEquals("dabcef", keys(ReorderOps.moveToEdge(list, "d", true, key, grp)))
        assertEquals("bcdefa", keys(ReorderOps.moveToEdge(list, "a", false, key, grp)))
        // Dentro de una superserie primero va a la orilla del grupo…
        assertEquals("acbdef", keys(ReorderOps.moveToEdge(list, "c", true, key, grp)))
        // …y si ya está en la orilla del grupo, se va el bloque completo.
        assertEquals("bcadef", keys(ReorderOps.moveToEdge(list, "b", true, key, grp)))
        assertNull(ReorderOps.moveToEdge(list, "a", true, key, grp))
        assertNull(ReorderOps.moveToEdge(list, "f", false, key, grp))
    }

    @Test fun mover_bloques_y_miembros() {
        val bs = ReorderOps.blocks(list, grp)
        assertEquals("dabcef", ReorderOps.moveBlock(bs, 2, 0).flatten().joinToString("") { it.k })
        assertEquals("adefbc", ReorderOps.moveBlock(bs, 1, 3).flatten().joinToString("") { it.k })
        assertSame(bs, ReorderOps.moveBlock(bs, 1, 1)); assertSame(bs, ReorderOps.moveBlock(bs, -1, 2)); assertSame(bs, ReorderOps.moveBlock(bs, 0, 9))
        assertEquals("cb", ReorderOps.moveWithinBlock(bs[1], 1, 0).joinToString("") { it.k })
    }

    @Test fun aplicar_orden_rechaza_listas_que_no_coinciden_o_parten_superseries() {
        assertEquals("dabcef", keys(ReorderOps.applyOrder(list, "dabcef".map { "$it" }, key, grp)))
        assertNull("falta uno", ReorderOps.applyOrder(list, "dabce".map { "$it" }, key, grp))
        assertNull("sobra uno", ReorderOps.applyOrder(list, "dabcefg".map { "$it" }, key, grp))
        assertNull("repetido", ReorderOps.applyOrder(list, "dabcee".map { "$it" }, key, grp))
        assertNull("otro elemento", ReorderOps.applyOrder(list, "dabcex".map { "$it" }, key, grp))
        assertNull("parte la superserie S", ReorderOps.applyOrder(list, "badcef".map { "$it" }, key, grp))
        assertEquals("mismo orden", "abcdef", keys(ReorderOps.applyOrder(list, "abcdef".map { "$it" }, key, grp)))
    }
}
