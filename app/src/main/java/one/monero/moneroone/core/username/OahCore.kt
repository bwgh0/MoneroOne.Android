package one.monero.moneroone.core.username

import org.json.JSONObject

/** The same Rust rules and verifier used by the registry and iOS. */
object OahCore {
    init { System.loadLibrary("oah_jni") }
    @JvmStatic private external fun callNative(input: ByteArray): String?
    fun call(request: JSONObject): JSONObject {
        val raw = callNative(request.toString().toByteArray(Charsets.UTF_8)) ?: error("mobile.internal")
        val response = JSONObject(raw)
        if (response.has("error")) throw OahException(response.getString("error"))
        return response.getJSONObject("ok")
    }
    fun normalize(input: String): String = call(JSONObject().put("op","validate").put("handle",input)).getString("handle")
}
class OahException(val code: String): Exception(code)
