package com.mkmehmood.everydaykeys.ime

object EmojiData {
    private const val RAW = """😀 grin smile happy
😃 smile happy joy
😄 laugh happy
😁 beam grin
😆 laughing squint
😅 sweat relief
😂 joy tears laugh lol
🤣 rofl rolling laugh
🙂 slight smile
😉 wink
😊 blush smile happy
😇 angel halo innocent
🥰 love hearts adore
😍 heart eyes love
😘 kiss blow
😋 yum tasty
😎 cool sunglasses
🤩 star struck wow
🤔 thinking hmm
😐 neutral
😑 expressionless
🙄 eye roll
😏 smirk
😴 sleep tired zzz
😪 sleepy
😷 mask sick
🤒 fever sick
🤕 hurt injured
🤢 nauseated sick
🥵 hot
🥶 cold freezing
😵 dizzy
🤯 mind blown
🥳 party celebrate
😭 cry sob sad
😢 sad tear
😞 disappointed
😔 pensive sad
😟 worried
😡 angry mad
😠 angry
🤬 swearing angry
😱 scream shocked fear
😨 fearful
😰 anxious sweat
😳 flushed embarrassed
🥺 pleading puppy eyes
😬 grimace awkward
🤗 hug hugging
🤫 shush quiet secret
🤭 giggle oops
🙃 upside down
😜 wink tongue silly
🤪 zany crazy
👍 thumbs up yes good like
👎 thumbs down no bad dislike
👌 ok perfect
✌ victory peace
🤞 fingers crossed luck
🤝 handshake deal agree
👏 clap applause bravo
🙌 raised hands celebrate
🙏 pray thanks please
💪 strong muscle flex
👋 wave hello bye
🤙 call me
👉 point right
👈 point left
👆 point up
👇 point down
✋ stop hand
👀 eyes look
🧠 brain smart
❤ red heart love
🧡 orange heart
💛 yellow heart
💚 green heart
💙 blue heart
💜 purple heart
🖤 black heart
💔 broken heart
💕 two hearts love
💯 hundred perfect
🔥 fire hot lit
✨ sparkles shine
⭐ star
🌟 glowing star
💥 boom collision
💫 dizzy star
🎉 party popper celebrate
🎊 confetti
🎂 birthday cake
🎁 gift present
🎈 balloon
🏆 trophy win
⚽ football soccer
🏀 basketball
🏏 cricket
🎮 game controller
🎵 music note
🎧 headphones
📱 phone mobile
💻 laptop computer
⌨ keyboard
📷 camera photo
📞 telephone call
📧 email mail
📩 envelope message
📎 paperclip attach
📌 pin
📍 location pin
📅 calendar date
⏰ alarm clock
⌛ hourglass time
🔒 lock secure
🔑 key
💡 idea bulb
🔋 battery
💰 money bag
💵 dollar cash
💳 credit card
🛒 cart shopping
📦 package box
✈ airplane flight travel
🚗 car drive
🚌 bus
🚆 train
🏠 house home
🏨 hotel
🌍 earth world
🌙 moon night
☀ sun sunny
⛅ cloudy
🌧 rain
❄ snow cold
🌈 rainbow
🌹 rose flower
🌸 blossom flower
🌳 tree
🐶 dog puppy
🐱 cat kitten
🐭 mouse
🐰 rabbit bunny
🦁 lion
🐯 tiger
🐻 bear
🐼 panda
🐵 monkey
🐔 chicken
🐦 bird
🐢 turtle
🐟 fish
🍎 apple fruit
🍌 banana
🍇 grapes
🍉 watermelon
🍕 pizza
🍔 burger
🍟 fries
🌮 taco
🍜 noodles ramen
🍚 rice
🍰 cake dessert
🍫 chocolate
☕ coffee tea hot drink
🍵 tea
🥤 drink cup
🍺 beer
✅ check done yes
❌ cross no wrong
⚠ warning caution
❓ question
❗ exclamation
➕ plus
➖ minus
➡ arrow right
⬅ arrow left
⬆ arrow up
⬇ arrow down"""

    val entries: List<Pair<String, String>> = RAW.lines().filter { it.isNotBlank() }.map {
        val i = it.indexOf(' ')
        it.substring(0, i) to it.substring(i + 1)
    }

    fun search(q: String): List<String> {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return entries.map { it.first }
        return entries.filter { it.second.contains(t) }.map { it.first }
    }
}
