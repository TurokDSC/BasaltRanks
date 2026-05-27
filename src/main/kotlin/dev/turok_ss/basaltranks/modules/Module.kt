package dev.turok_ss.basaltranks.modules

interface Module {
    val name: String
    val configKey: String get() = name.lowercase()

    fun onEnable()
    fun onDisable()
}
