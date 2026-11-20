package com.flashcards.sync

import org.json.JSONObject

/**
 * The tiny protocol the phone and the desktop app use to swap libraries over Wi-Fi.
 *
 * Discovery: the phone broadcasts [DISCOVER_REQUEST] on UDP [DISCOVERY_PORT]; every
 * desktop that is sharing answers with a JSON [HostInfo].
 *
 * Transfer: plain HTTP on the host's own port.
 *   GET  /library?code=NNNNNN  -> that device's library as backup JSON
 *   POST /library?code=NNNNNN  <- the merged library, which replaces what it had
 * The six-digit code is shown on the desktop and typed on the phone, so another
 * machine on the same network cannot read or overwrite a library by guessing.
 */
object SyncProtocol {
    const val DISCOVERY_PORT = 48723
    const val DEFAULT_HTTP_PORT = 48724
    const val DISCOVER_REQUEST = "FLASHCARDS_DISCOVER_V1"
    const val PATH = "/library"
    const val CODE_PARAM = "code"

    /** A desktop answering a discovery broadcast. */
    class HostInfo(val name: String, val address: String, val port: Int) {
        fun toJson(): String = JSONObject()
            .put("app", "flashcards")
            .put("name", name)
            .put("port", port)
            .toString()

        val label: String get() = "$name  ($address)"

        companion object {
            fun fromJson(json: String, address: String): HostInfo? = runCatching {
                val o = JSONObject(json)
                if (o.optString("app") != "flashcards") return null
                HostInfo(
                    name = o.optString("name").ifBlank { address },
                    address = address,
                    port = o.optInt("port", DEFAULT_HTTP_PORT),
                )
            }.getOrNull()
        }
    }

    fun url(address: String, port: Int, code: String): String =
        "http://$address:$port$PATH?$CODE_PARAM=$code"

    /** A fresh six-digit sharing code. */
    fun newCode(): String = (0 until 6).joinToString("") { ('0' + (0..9).random()).toString() }
}
