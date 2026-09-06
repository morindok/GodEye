package app.godeye

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** App-private encrypted storage. Keys are never logged or included in backups. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("godeye_profiles", Context.MODE_PRIVATE)
    private val alias = "godeye_profiles_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): List<ModelProfile> {
        val encoded = prefs.getString("payload", null) ?: return emptyList()
        val parts = encoded.split(":")
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        val json = JSONArray(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        return (0 until json.length()).map {
            val obj = json.getJSONObject(it)
            ModelProfile(obj.getString("id"), obj.getString("name"), obj.getString("endpoint"), obj.getString("model"), obj.getString("apiKey"))
        }
    }
    fun save(profiles: List<ModelProfile>) {
        val json = JSONArray()
        profiles.forEach { p -> json.put(JSONObject().put("id", p.id).put("name", p.name)
            .put("endpoint", p.endpoint).put("model", p.model).put("apiKey", p.apiKey)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("payload", payload).commit()) { "ذخیره انجام نشد." }
    }
}
