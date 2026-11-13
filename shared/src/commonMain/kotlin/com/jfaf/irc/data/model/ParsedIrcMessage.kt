package com.jfaf.irc.data.model

/**
 * Represents a parsed IRC message.
 * See RFC 2812 for message format details (Section 2.3.1).
 * <message>  ::= [':' <prefix> <SPACE> ] <command> <params> <crlf>
 * <prefix>   ::= <servername> | <nick> [ '!' <user> ] [ '@' <host> ]
 * <command>  ::= <letter> { <letter> } | <number> <number> <number>
 * <params>   ::= <SPACE> [ ':' <trailing> | <middle> <params> ]
 */
data class ParsedIrcMessage(
    val rawLine: String,
    val prefix: String? = null, // servername or nick!user@host
    val command: String,          // e.g., PRIVMSG, NOTICE, JOIN, 001, 433
    val params: List<String>,     // Parameters before the trailing part
    val trailing: String? = null, // The part after the colon, can contain spaces
) {
    // Utility to get sender's nickname from prefix if available
    val senderNickname: String? by lazy {
        if (prefix != null && prefix.contains("!")) {
            prefix.substringBefore("!")
        } else if (prefix != null && !prefix.contains(".")) {
            // Could be a nickname without user/host (e.g., in some server notices)
            prefix
        } else null
    }
}