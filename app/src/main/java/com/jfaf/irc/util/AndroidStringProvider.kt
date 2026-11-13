package com.jfaf.irc.util

import android.content.Context
import com.jfaf.irc.R
import com.jfaf.irc.domain.text.Msg
import com.jfaf.irc.domain.text.StringProvider

/** Implementación Android de [StringProvider] sobre `strings.xml` (capa específica de plataforma). */
class AndroidStringProvider(
    private val context: Context
) : StringProvider {

    override fun get(msg: Msg, vararg args: Any): String = context.getString(resId(msg), *args)

    private fun resId(msg: Msg): Int = when (msg) {
        Msg.CMD_INVALID -> R.string.msg_cmd_invalid
        Msg.CMD_ME_USAGE -> R.string.msg_cmd_me_usage
        Msg.CMD_ME_WRONG_CONTEXT -> R.string.msg_cmd_me_wrong_context
        Msg.CMD_NICK_USAGE -> R.string.msg_cmd_nick_usage
        Msg.CMD_NICK_TRYING -> R.string.msg_cmd_nick_trying
        Msg.CMD_JOIN_USAGE -> R.string.msg_cmd_join_usage
        Msg.CMD_AWAY_OFF -> R.string.msg_cmd_away_off
        Msg.CMD_AWAY_ON -> R.string.msg_cmd_away_on
        Msg.CMD_MSG_USAGE -> R.string.msg_cmd_msg_usage
        Msg.CMD_MSG_SELF -> R.string.msg_cmd_msg_self
        Msg.CMD_MSG_SERVER_TAB -> R.string.msg_cmd_msg_server_tab
        Msg.CMD_MSG_SENT -> R.string.msg_cmd_msg_sent
        Msg.CMD_QUERY_USAGE -> R.string.msg_cmd_query_usage
        Msg.CMD_QUERY_SELF -> R.string.msg_cmd_query_self
        Msg.CMD_QUERY_CHANNEL -> R.string.msg_cmd_query_channel
        Msg.CMD_TOPIC_USAGE -> R.string.msg_cmd_topic_usage
        Msg.CMD_TOPIC_NO_CHANNEL -> R.string.msg_cmd_topic_no_channel
        Msg.CMD_TOPIC_TRYING -> R.string.msg_cmd_topic_trying
        Msg.CMD_TOPIC_REQUESTING -> R.string.msg_cmd_topic_requesting
        Msg.CMD_CLEAR_NO_WINDOW -> R.string.msg_cmd_clear_no_window
        Msg.CMD_HELP -> R.string.msg_cmd_help
        Msg.NICKSERV_IDENTIFYING -> R.string.msg_nickserv_identifying
        Msg.CLOSE_SERVER_TAB_FORBIDDEN -> R.string.msg_close_server_tab_forbidden
        Msg.CONNECTING_TO -> R.string.msg_connecting_to
        Msg.DISCONNECTING_REQUESTED -> R.string.msg_disconnecting_requested
        Msg.IGNORE_REQUIRES_LOGIN -> R.string.msg_ignore_requires_login
        Msg.USER_IGNORED_SYSTEM -> R.string.msg_user_ignored_system
        Msg.JOIN_INVALID_CHANNEL -> R.string.msg_join_invalid_channel
        Msg.JOIN_REQUESTING -> R.string.msg_join_requesting
        Msg.JOIN_NOT_CONNECTED -> R.string.msg_join_not_connected
        Msg.PM_INVALID_NICK -> R.string.msg_pm_invalid_nick
        Msg.PM_STARTED -> R.string.msg_pm_started
        Msg.PM_NOT_CONNECTED -> R.string.msg_pm_not_connected
        Msg.PART_INVALID -> R.string.msg_part_invalid
        Msg.WHOIS_NOT_CONNECTED -> R.string.msg_whois_not_connected
        Msg.WHOIS_REQUESTING -> R.string.msg_whois_requesting
        Msg.SEND_NOT_CONNECTED -> R.string.msg_send_not_connected
        Msg.SEND_WINDOW_CLEARED -> R.string.msg_send_window_cleared
        Msg.SEND_UNKNOWN_COMMAND -> R.string.msg_send_unknown_command
        Msg.SEND_TO_SERVER_TAB_FORBIDDEN -> R.string.msg_send_to_server_tab_forbidden
        Msg.SEND_NO_ACTIVE_TARGET -> R.string.msg_send_no_active_target
        Msg.AUTO_REPLY_IGNORED -> R.string.msg_auto_reply_ignored
        Msg.LINK_RECONNECTED -> R.string.msg_link_reconnected
        Msg.LINK_CONNECTED -> R.string.msg_link_connected
        Msg.LINK_LOST_RECONNECTING -> R.string.msg_link_lost_reconnecting
        Msg.LINK_RECONNECT_FAILED -> R.string.msg_link_reconnect_failed
        Msg.LINK_DISCONNECTED -> R.string.msg_link_disconnected
        Msg.SNACK_FRIEND_ONLINE -> R.string.msg_snack_friend_online
        Msg.SNACK_DISCONNECTED -> R.string.msg_snack_disconnected
        Msg.SNACK_IGNORED_ADDED -> R.string.msg_snack_ignored_added
        Msg.SNACK_WHOIS_REQUESTED -> R.string.msg_snack_whois_requested
        Msg.SNACK_WHOIS_NOT_CONNECTED -> R.string.msg_snack_whois_not_connected
        Msg.EVT_JOIN -> R.string.msg_evt_join
        Msg.EVT_PART -> R.string.msg_evt_part
        Msg.EVT_QUIT -> R.string.msg_evt_quit
        Msg.EVT_SOMEONE -> R.string.msg_evt_someone
        Msg.EVT_SOMEONE_LC -> R.string.msg_evt_someone_lc
        Msg.EVT_NICK_CHANGE -> R.string.msg_evt_nick_change
        Msg.EVT_MODE -> R.string.msg_evt_mode
        Msg.EVT_KICK -> R.string.msg_evt_kick
        Msg.ERR_WHOIS_UNKNOWN_USER -> R.string.msg_err_whois_unknown_user
        Msg.ERR_WHOIS_NOT_FOUND -> R.string.msg_err_whois_not_found
        Msg.ERR_WHOIS_PREFIX -> R.string.msg_err_whois_prefix
        Msg.ERR_NUMERIC -> R.string.msg_err_numeric
        Msg.ERR_NUMERIC_PREFIX -> R.string.msg_err_numeric_prefix
    }
}
