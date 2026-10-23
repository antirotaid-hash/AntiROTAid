package com.mindlift.bridge

import org.json.JSONObject

/**
 * Kotlin mirror of the engine's JSON envelope contract
 * (see docs/architecture/bridge_contract.md):
 *
 *   { "ok": true,  "api_version": "1", "data": { ... } }
 *   { "ok": false, "api_version": "1", "error": { code, message, ... } }
 *
 * The bridge never throws across the JNI boundary; failures arrive as
 * [Envelope.Error] with an owner-safe, no-shame message.
 */
sealed interface Envelope {
    val apiVersion: String

    data class Success(override val apiVersion: String, val data: JSONObject) : Envelope
    data class Error(
        override val apiVersion: String,
        val code: String,
        val message: String,
        val recoverable: Boolean,
    ) : Envelope

    val isOk: Boolean get() = this is Success

    /** Data on success, or null on error — convenient for optional reads. */
    fun dataOrNull(): JSONObject? = (this as? Success)?.data

    /** Owner-safe message to show on error (already copy-guard clean). */
    fun messageOrNull(): String? = (this as? Error)?.message

    companion object {
        fun parse(raw: String): Envelope {
            val obj = JSONObject(raw)
            val version = obj.optString("api_version", "?")
            return if (obj.optBoolean("ok", false)) {
                Success(version, obj.optJSONObject("data") ?: JSONObject())
            } else {
                val err = obj.optJSONObject("error") ?: JSONObject()
                Error(
                    apiVersion = version,
                    code = err.optString("code", "engine_error"),
                    message = err.optString(
                        "message", "Something needs another try. Nothing was lost."
                    ),
                    recoverable = err.optBoolean("recoverable", true),
                )
            }
        }
    }
}
