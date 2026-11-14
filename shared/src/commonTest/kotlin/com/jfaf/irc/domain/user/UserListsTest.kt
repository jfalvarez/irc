package com.jfaf.irc.domain.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserListsTest {

    private val empty = UserLists()

    @Test
    fun nicksEnMinusculas() {
        assertEquals(UserLists(friends = setOf("ana")), empty.with(UserList.FRIENDS, "AnA"))
        assertEquals(empty, UserLists(ignored = setOf("pepe")).without(UserList.IGNORED, "PEPE"))
    }

    @Test
    fun sinCuentaAsociada_noApuntaPendientes() {
        val state = LocalListsState().add(UserList.FRIENDS, "ana", offline = true)
        assertEquals(setOf("ana"), state.lists.friends)
        assertTrue(state.pendingAdd.isEmpty())
    }

    @Test
    fun conCuentaYSinSesion_apuntaPendientesYQuitarAnulaAnadir() {
        val base = LocalListsState(ownerUid = "u1")
        val added = base.add(UserList.IGNORED, "pepe", offline = true)
        assertEquals(UserLists(ignored = setOf("pepe")), added.pendingAdd)

        val removed = added.remove(UserList.IGNORED, "pepe", offline = true)
        assertTrue(removed.pendingAdd.isEmpty())
        assertEquals(UserLists(ignored = setOf("pepe")), removed.pendingRemove)
    }

    @Test
    fun conSesion_noApuntaPendientes() {
        val state = LocalListsState(ownerUid = "u1").add(UserList.FRIENDS, "ana", offline = false)
        assertTrue(state.pendingAdd.isEmpty())
    }

    @Test
    fun nuncaSincronizado_juntaYSubeLoQueSoloEstabaEnLocal() {
        val local = LocalListsState(UserLists(ignored = setOf("pepe"), friends = setOf("ana")))
        val remote = UserLists(friends = setOf("ana", "luis"))
        val result = reconcile(local, "u1", remote)
        assertEquals(LocalListsState(UserLists(setOf("pepe"), setOf("ana", "luis")), "u1"), result.state)
        assertEquals(UserLists(ignored = setOf("pepe")), result.toAdd)
        assertTrue(result.toRemove.isEmpty())
    }

    @Test
    fun mismaCuenta_subeLosPendientesYMandaLaCuenta() {
        val local = LocalListsState(
            lists = UserLists(ignored = setOf("viejo", "nuevo")),
            ownerUid = "u1",
            pendingAdd = UserLists(ignored = setOf("nuevo")),
            pendingRemove = UserLists(friends = setOf("ana", "fantasma"))
        )
        // En la cuenta, otro dispositivo quitó "viejo" y añadió "luis"
        val remote = UserLists(friends = setOf("ana", "luis"))
        val result = reconcile(local, "u1", remote)
        assertEquals(LocalListsState(UserLists(setOf("nuevo"), setOf("luis")), "u1"), result.state)
        assertEquals(UserLists(ignored = setOf("nuevo")), result.toAdd)
        assertEquals(UserLists(friends = setOf("ana")), result.toRemove)
    }

    @Test
    fun otraCuenta_mandaLaCuentaYSeDescartanLosPendientes() {
        val local = LocalListsState(
            lists = UserLists(ignored = setOf("pepe")),
            ownerUid = "u1",
            pendingAdd = UserLists(ignored = setOf("pepe"))
        )
        val remote = UserLists(friends = setOf("luis"))
        val result = reconcile(local, "u2", remote)
        assertEquals(LocalListsState(remote, "u2"), result.state)
        assertTrue(result.toAdd.isEmpty())
        assertTrue(result.toRemove.isEmpty())
    }
}
