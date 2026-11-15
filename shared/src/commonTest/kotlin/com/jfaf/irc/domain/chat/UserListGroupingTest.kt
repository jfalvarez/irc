package com.jfaf.irc.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class UserListGroupingTest {
    @Test
    fun rangoPorPrefijo() {
        assertEquals(UserRole.OPERATOR, UserListGrouping.roleOf("@ana"))
        assertEquals(UserRole.OPERATOR, UserListGrouping.roleOf("~fundador"))
        assertEquals(UserRole.OPERATOR, UserListGrouping.roleOf("&admin"))
        assertEquals(UserRole.OPERATOR, UserListGrouping.roleOf("%halfop"))
        assertEquals(UserRole.VOICED, UserListGrouping.roleOf("+voz"))
        assertEquals(UserRole.REGULAR, UserListGrouping.roleOf("kiko"))
    }

    @Test
    fun conVariosPrefijosGanaElMasAlto() {
        assertEquals(UserRole.OPERATOR, UserListGrouping.roleOf("+@ana"))
        assertEquals(UserRole.OPERATOR, UserListGrouping.roleOf("@+ana"))
    }

    @Test
    fun unSimboloQueNoEsPrefijoNoCuenta() {
        assertEquals(UserRole.REGULAR, UserListGrouping.roleOf("[ana]"))
        assertEquals(UserRole.REGULAR, UserListGrouping.roleOf("a@b"))
    }

    @Test
    fun agrupaEnOrdenYOrdenaCadaGrupo() {
        val groups = UserListGrouping.group(listOf("zeta", "+voz2", "@Beto", "ana", "@alba", "+Voz1", "Carlos"))
        assertEquals(listOf(UserRole.OPERATOR, UserRole.VOICED, UserRole.REGULAR), groups.map { it.role })
        assertEquals(listOf("@alba", "@Beto"), groups[0].users)
        assertEquals(listOf("+Voz1", "+voz2"), groups[1].users)
        assertEquals(listOf("ana", "Carlos", "zeta"), groups[2].users)
    }

    @Test
    fun noDevuelveGruposVacios() {
        val groups = UserListGrouping.group(listOf("ana", "kiko"))
        assertEquals(listOf(UserRole.REGULAR), groups.map { it.role })
        assertEquals(emptyList(), UserListGrouping.group(emptyList()))
    }

    @Test
    fun ordenaIgnorandoElPrefijoDentroDelGrupo() {
        val groups = UserListGrouping.group(listOf("@zeta", "~ana", "&mario"))
        assertEquals(listOf("~ana", "&mario", "@zeta"), groups.single().users)
    }

    @Test
    fun visibleQuitaALosIgnoradosSinDistinguirMayusculasNiPrefijo() {
        val users = listOf("@Ana", "+kiko", "Luis", "eva")
        assertEquals(listOf("@Ana", "Luis"), UserListGrouping.visible(users, setOf("KIKO", "EVA")))
        assertEquals(users, UserListGrouping.visible(users, emptySet()))
        assertEquals(emptyList(), UserListGrouping.visible(emptyList(), setOf("ana")))
    }
}
