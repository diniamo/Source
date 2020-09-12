package net.sourcebot.module.starboard

import net.sourcebot.api.module.SourceModule
import net.sourcebot.module.starboard.misc.StarboardDataManager
import net.sourcebot.module.starboard.misc.StarboardListener

class Starboard : SourceModule() {
    override fun onEnable() {
        val dataManager = StarboardDataManager(source.guildConfigurationManager)
        StarboardListener(
            source.jdaEventSystem,
            source.mongodb,
            dataManager
        ).listen(this)
    }
}