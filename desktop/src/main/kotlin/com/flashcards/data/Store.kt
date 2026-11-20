package com.flashcards.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Where the desktop app keeps its data: %APPDATA%\Flashcards on Windows (FLASHCARDS_DATA_DIR overrides). */
object DataDir {
    val root: File by lazy {
        System.getenv("FLASHCARDS_DATA_DIR")?.let { return@lazy File(it).apply { mkdirs() } }
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        File(base, "Flashcards").apply { mkdirs() }
    }
}

/**
 * All decks, cards and review logs, held in memory and written to one JSON file after
 * every change. The file uses the same format as the Android app's backups, so a phone
 * backup can be restored here and vice versa.
 */
class Store(private val file: File = File(DataDir.root, "flashcards.json")) {
    data class Data(
        val decks: List<Deck> = emptyList(),
        val cards: List<Card> = emptyList(),
        val logs: List<ReviewLog> = emptyList(),
    )

    private val _data = MutableStateFlow(load())
    val data: StateFlow<Data> = _data.asStateFlow()

    private fun load(): Data {
        if (!file.exists()) return Data()
        return runCatching {
            val b = CardFiles.fromJson(file.readText())
            Data(b.decks, b.cards, b.logs.mapIndexed { i, l -> l.copy(id = i + 1L) })
        }.getOrElse { e ->
            // Keep the unreadable file instead of overwriting it with an empty library.
            file.copyTo(File(file.parentFile, "flashcards-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
            System.err.println("Could not read ${file.path}: ${e.message}")
            Data()
        }
    }

    @Synchronized
    fun update(transform: (Data) -> Data) {
        val next = transform(_data.value)
        if (next == _data.value) return
        _data.value = next
        save(next)
    }

    private fun save(d: Data) {
        val json = CardFiles.toJson(CardFiles.Backup(d.decks, d.cards, d.logs))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun nextDeckId(d: Data) = (d.decks.maxOfOrNull { it.id } ?: 0) + 1
    fun nextCardId(d: Data) = (d.cards.maxOfOrNull { it.id } ?: 0) + 1
    fun nextLogId(d: Data) = (d.logs.maxOfOrNull { it.id } ?: 0) + 1
}

/** Small key/value settings file next to the data (the desktop stand-in for SharedPreferences). */
class Settings(private val file: File = File(DataDir.root, "settings.properties")) {
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    fun getString(key: String): String? = props.getProperty(key)
    fun getInt(key: String, default: Int) = props.getProperty(key)?.toIntOrNull() ?: default
    fun getBoolean(key: String, default: Boolean) = props.getProperty(key)?.toBooleanStrictOrNull() ?: default

    @Synchronized
    fun put(key: String, value: Any) {
        props.setProperty(key, value.toString())
        file.outputStream().use { props.store(it, "Flashcards settings") }
    }
}
