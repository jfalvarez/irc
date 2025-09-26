package com.jfaf.irc.ui.viewmodels.command

/**
 * Represents the outcome of processing a user command.
 */
sealed interface CommandResult {
    /** Indicates the command was fully handled by the processor, typically involving direct repository/state calls. */
    object Handled : CommandResult

    /** Instructs the ViewModel to display a system message to a specific target. */
    data class ShowSystemMessage(val target: String, val message: String, val isError: Boolean = false) : CommandResult

    /** Instructs the ViewModel to initiate joining a channel. */
    data class JoinChannel(val channel: String, val key: String?) : CommandResult

    /** Instructs the ViewModel to initiate parting a channel. */
    data class PartChannel(val channelToPart: String?, val partMessage: String?) : CommandResult

    /** Instructs the ViewModel to open a private message / query window. */
    data class OpenQuery(val nick: String, val initialMessage: String?) : CommandResult

    /** Instructs the ViewModel to disconnect from the server. */
    data class Quit(val quitMessage: String?) : CommandResult

    /** Instructs the ViewModel to clear messages for a specific target. */
    data class ClearWindow(val targetToClear: String) : CommandResult
    
    /** Fallback for an unknown command, instructing a generic error message. */
    data class UnknownCommand(val command: String) : CommandResult

    /** Fallback for invalid arguments for a known command, instructing a generic error message. */
    data class InvalidArguments(val usageHint: String) : CommandResult
}
