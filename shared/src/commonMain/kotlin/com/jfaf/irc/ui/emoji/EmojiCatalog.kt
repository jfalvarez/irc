package com.jfaf.irc.ui.emoji

import com.jfaf.irc.resources.Res
import com.jfaf.irc.resources.emoji_category_activities
import com.jfaf.irc.resources.emoji_category_animals
import com.jfaf.irc.resources.emoji_category_faces
import com.jfaf.irc.resources.emoji_category_food
import com.jfaf.irc.resources.emoji_category_gestures
import com.jfaf.irc.resources.emoji_category_symbols
import com.jfaf.irc.resources.emoji_category_travel
import org.jetbrains.compose.resources.StringResource

/** Una pestaña del selector: el emoticono que la representa, su nombre y sus emoticonos. */
class EmojiCategory(val icon: String, val name: StringResource, val emojis: List<String>)

/**
 * Los emoticonos del selector. Sólo de hasta Emoji 12 (2019) para que se vean en Windows 10 y 11 y en los Linux y
 * macOS actuales, sin variantes de tono ni secuencias unidas. Los que necesitan el selector de variante
 * (`\uFE0F`, para que se pinten como emoticono y no como símbolo) llevan el escape explícito.
 */
object EmojiCatalog {

    private fun emojis(spaceSeparated: String): List<String> = spaceSeparated.split(' ').filter { it.isNotEmpty() }

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory(
            "😀", Res.string.emoji_category_faces,
            emojis(
                "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 " +
                    "😏 😒 😞 😔 😟 😕 🙁 ☹\uFE0F 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 " +
                    "🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 " +
                    "😈 👿 👹 👺 🤡 💩 👻 💀 ☠\uFE0F 👽 👾 🤖 🎃 😺 😸 😹 😻 😼 😽 🙀 😿 😾"
            )
        ),
        EmojiCategory(
            "👍", Res.string.emoji_category_gestures,
            emojis(
                "👍 👎 👌 ✌\uFE0F 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝\uFE0F ✋ 🤚 🖐\uFE0F 🖖 👋 🤏 💪 🙏 🤝 👏 🙌 " +
                    "👐 🤲 ✍\uFE0F 💅 🤳 👀 🧠 👂 👃 👄 👅 🦷 🙋 🙆 🙅 🤦 🤷 💁 🙇 🚶 🏃 💃 🕺 👶 👦 👧 👨 👩 👴 👵"
            )
        ),
        EmojiCategory(
            "❤\uFE0F", Res.string.emoji_category_symbols,
            emojis(
                "❤\uFE0F 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣\uFE0F 💕 💞 💓 💗 💖 💘 💝 💟 ✨ ⭐ 🌟 💫 🔥 💥 💯 ✅ ❌ " +
                    "❓ ❗ ⚡ 💢 💤 💬 💭 ➕ ➖ ⚠\uFE0F 🔔 🔕 ♻\uFE0F ✔\uFE0F ➡\uFE0F ⬅\uFE0F " +
                    "⬆\uFE0F ⬇\uFE0F 🔝 🆗 🆒 🆕 🆓"
            )
        ),
        EmojiCategory(
            "🐶", Res.string.emoji_category_animals,
            emojis(
                "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 " +
                    "🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐘 🦏 🦒 🐫 🐑 🐐 🐓 🦃 🕊\uFE0F " +
                    "🌵 🌲 🌳 🌴 🍀 🌸 🌹 🌻 🌼 🍁 🍄"
            )
        ),
        EmojiCategory(
            "🍕", Res.string.emoji_category_food,
            emojis(
                "🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🍆 🥕 🌽 🌶\uFE0F 🥔 🍞 🥐 🧀 🥚 🍳 🥓 " +
                    "🍔 🍟 🍕 🌭 🌮 🌯 🍝 🍜 🍣 🍤 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🍺 🍻 🥂 🍷 🍸 🍹 🥃 🥤"
            )
        ),
        EmojiCategory(
            "⚽", Res.string.emoji_category_activities,
            emojis(
                "⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🏓 🏆 🥇 🥈 🥉 🎮 🎲 🎯 🎵 🎶 🎤 🎧 🎸 🎹 🎬 📷 💻 📱 ☎\uFE0F 💡 🔑 🔒 🎁 🎉 " +
                    "🎊 🎈 📚 ✏\uFE0F 📌 📎 🔧 🔨 💰 💳 📺 📻 🔋 🔍"
            )
        ),
        EmojiCategory(
            "🚗", Res.string.emoji_category_travel,
            emojis(
                "🚗 🚕 🚌 🚑 🚒 🚓 🚲 ✈\uFE0F 🚀 ⛵ 🚢 🚂 🏠 🏢 🏥 🏫 ⛪ 🌍 🌎 🌏 🌙 ☀\uFE0F ⛅ ☁\uFE0F " +
                    "🌧\uFE0F ⛈\uFE0F ❄\uFE0F 🌈 💧 🌊"
            )
        )
    )
}
