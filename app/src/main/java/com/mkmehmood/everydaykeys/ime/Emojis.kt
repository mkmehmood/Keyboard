package com.mkmehmood.everydaykeys.ime

object Emojis {
    private const val RAW = "😀 smile happy grin|😃 smile happy|😄 laugh happy|😁 grin|😆 laugh|😅 sweat laugh|😂 joy laugh tears|🤣 rofl laugh|🙂 smile|😉 wink|😊 blush smile|😍 love heart eyes|🥰 love|😘 kiss|😋 yum tasty|😎 cool sunglasses|🤔 think|😐 neutral|😴 sleep|😭 cry sad|😢 sad tear|😡 angry|🥳 party|😱 shock scream|🙄 eyeroll|😇 angel|🤗 hug|🙏 pray thanks please|👍 thumbs up yes ok|👎 thumbs down no|👏 clap|🙌 celebrate hands|💪 strong muscle|👋 wave hello|🤝 handshake deal|✌️ peace|👌 ok|❤️ heart love|💔 broken heart|🔥 fire hot|⭐ star|✨ sparkle|🎉 party celebrate|🎁 gift|🎂 cake birthday|☕ coffee|🍕 pizza|🍔 burger|🍎 apple|🍌 banana|🍉 watermelon|🌹 rose flower|🌞 sun|🌙 moon|⚡ lightning|☔ rain|❄️ snow|🌈 rainbow|🐶 dog|🐱 cat|🐼 panda|🦁 lion|🐎 horse|🚗 car|✈️ plane flight|🚆 train|🏠 home house|📱 phone|💻 laptop computer|📷 camera|🎵 music|⚽ football soccer|🏏 cricket|🏆 trophy|💡 idea bulb|📌 pin|📎 clip|✅ check done|❌ cross no|⚠️ warning|💯 hundred|🎯 target|💰 money|📅 calendar|⏰ alarm clock|🔒 lock|🔑 key"

    /** emoji to search keywords */
    val all: List<Pair<String, String>> = RAW.split("|").map { val i = it.indexOf(' '); it.substring(0, i) to it.substring(i + 1) }
}
