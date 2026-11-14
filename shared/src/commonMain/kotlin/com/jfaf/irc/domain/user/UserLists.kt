package com.jfaf.irc.domain.user

/** Las dos listas del usuario. Los nicks van siempre en minúsculas. */
enum class UserList { IGNORED, FRIENDS }

data class UserLists(
    val ignored: Set<String> = emptySet(),
    val friends: Set<String> = emptySet()
) {
    fun isEmpty(): Boolean = ignored.isEmpty() && friends.isEmpty()

    operator fun plus(other: UserLists) = UserLists(ignored + other.ignored, friends + other.friends)
    operator fun minus(other: UserLists) = UserLists(ignored - other.ignored, friends - other.friends)
    infix fun intersect(other: UserLists) = UserLists(ignored intersect other.ignored, friends intersect other.friends)

    fun with(list: UserList, nick: String): UserLists = this + of(list, nick)
    fun without(list: UserList, nick: String): UserLists = this - of(list, nick)

    companion object {
        /** Una lista con un solo nick (en minúsculas). */
        fun of(list: UserList, nick: String): UserLists {
            val value = setOf(nick.lowercase())
            return when (list) {
                UserList.IGNORED -> UserLists(ignored = value)
                UserList.FRIENDS -> UserLists(friends = value)
            }
        }
    }
}

/**
 * Lo que se guarda en el dispositivo: las listas y lo necesario para sincronizarlas con una cuenta.
 * Sin cuenta nunca hay pendientes: al iniciar sesión por primera vez se sube todo lo local.
 */
data class LocalListsState(
    val lists: UserLists = UserLists(),
    /** Cuenta con la que se sincronizaron por última vez; null si nunca. */
    val ownerUid: String? = null,
    /** Cambios hechos sin sesión, por subir a [ownerUid] cuando vuelva a iniciarla. */
    val pendingAdd: UserLists = UserLists(),
    val pendingRemove: UserLists = UserLists()
) {
    /** Añade [nick]. Con [offline] (sin sesión) y una cuenta asociada, lo apunta como pendiente. */
    fun add(list: UserList, nick: String, offline: Boolean): LocalListsState {
        val track = offline && ownerUid != null
        return copy(
            lists = lists.with(list, nick),
            pendingAdd = if (track) pendingAdd.with(list, nick) else pendingAdd,
            pendingRemove = if (track) pendingRemove.without(list, nick) else pendingRemove
        )
    }

    /** Quita [nick]. Con [offline] (sin sesión) y una cuenta asociada, lo apunta como pendiente. */
    fun remove(list: UserList, nick: String, offline: Boolean): LocalListsState {
        val track = offline && ownerUid != null
        return copy(
            lists = lists.without(list, nick),
            pendingAdd = if (track) pendingAdd.without(list, nick) else pendingAdd,
            pendingRemove = if (track) pendingRemove.with(list, nick) else pendingRemove
        )
    }
}

/** Resultado de juntar lo local con la cuenta [LocalListsState.ownerUid] al iniciar sesión. */
data class Reconciliation(
    val state: LocalListsState,
    /** Lo que hay que añadir en la cuenta. */
    val toAdd: UserLists,
    /** Lo que hay que quitar de la cuenta. */
    val toRemove: UserLists
)

/**
 * Qué hacer con lo local al recibir por primera vez en una sesión las listas [remote] de la cuenta [uid]:
 * - nunca sincronizado: se juntan las dos (y se sube lo que sólo estaba en local);
 * - la misma cuenta de antes: se suben los cambios hechos sin sesión;
 * - otra cuenta: manda la cuenta y se descartan los pendientes (eran de la anterior).
 */
fun reconcile(local: LocalListsState, uid: String, remote: UserLists): Reconciliation =
    when (local.ownerUid) {
        null -> Reconciliation(
            state = LocalListsState(remote + local.lists, uid),
            toAdd = local.lists - remote,
            toRemove = UserLists()
        )
        uid -> Reconciliation(
            state = LocalListsState(remote + local.pendingAdd - local.pendingRemove, uid),
            toAdd = local.pendingAdd - remote,
            toRemove = local.pendingRemove intersect remote
        )
        else -> Reconciliation(LocalListsState(remote, uid), UserLists(), UserLists())
    }
