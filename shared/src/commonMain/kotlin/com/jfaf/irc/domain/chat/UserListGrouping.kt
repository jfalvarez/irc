package com.jfaf.irc.domain.chat

/** Rango de un usuario en un canal, para agrupar la lista (UI-12). */
enum class UserRole { OPERATOR, VOICED, REGULAR }

data class UserGroup(val role: UserRole, val users: List<String>)

/** Reglas puras para agrupar la lista de usuarios de un canal por su modo (`@` operadores, `+` voz, el resto). */
object UserListGrouping {
    private const val OPERATOR_PREFIXES = "~&@%"
    private const val MODE_PREFIXES = "@+%&~"

    /** El modo más alto del usuario según sus prefijos (`@+nick` es operador); fundadores, admins y halfops cuentan como operadores. */
    fun roleOf(user: String): UserRole {
        val prefixes = user.takeWhile { it in MODE_PREFIXES }
        return when {
            prefixes.any { it in OPERATOR_PREFIXES } -> UserRole.OPERATOR
            '+' in prefixes -> UserRole.VOICED
            else -> UserRole.REGULAR
        }
    }

    /** Los grupos no vacíos, en orden operadores, voz y resto; dentro de cada uno, por nick sin distinguir mayúsculas. */
    fun group(users: List<String>): List<UserGroup> {
        val byRole = users.groupBy(::roleOf)
        return UserRole.entries.mapNotNull { role ->
            byRole[role]?.let { list ->
                UserGroup(role, list.sortedWith(compareBy({ NickColors.normalize(it).lowercase() }, { it })))
            }
        }
    }
}
