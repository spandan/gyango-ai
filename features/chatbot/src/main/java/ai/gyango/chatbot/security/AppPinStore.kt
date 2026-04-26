package ai.gyango.chatbot.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.gyango.core.InferenceSettings
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores a device-PIN hash and recovery hash (profile name + birth month/year) in encrypted prefs.
 * Never stores the raw PIN or recovery answers in plain text.
 */
class AppPinStore(context: Context) {

    private val appCtx = context.applicationContext
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appCtx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appCtx,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasPin(): Boolean =
        prefs.getString(KEY_PIN_HASH, null)?.isNotBlank() == true &&
            prefs.getString(KEY_PIN_SALT, null)?.isNotBlank() == true

    fun savePin(pin: String, profile: InferenceSettings): Boolean {
        val normalized = normalizePin(pin) ?: return false
        val recovery = recoveryPayload(profile)
        val pinSalt = randomSaltB64()
        val recoverySalt = randomSaltB64()
        val pinHash = sha256B64(pinSalt, normalized)
        val recoveryHash = sha256B64(recoverySalt, recovery)
        prefs.edit()
            .putString(KEY_PIN_SALT, pinSalt)
            .putString(KEY_PIN_HASH, pinHash)
            .putString(KEY_RECOVERY_SALT, recoverySalt)
            .putString(KEY_RECOVERY_HASH, recoveryHash)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        if (!hasPin()) return false
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val normalized = normalizePin(pin) ?: return false
        return constantTimeEquals(expected, sha256B64(salt, normalized))
    }

    /**
     * Replaces the stored PIN with [newPin] after [currentPin] verifies. Recovery material is unchanged.
     */
    fun changePin(currentPin: String, newPin: String): Boolean {
        if (!verifyPin(currentPin)) return false
        val normalized = normalizePin(newPin) ?: return false
        val pinSalt = randomSaltB64()
        val pinHash = sha256B64(pinSalt, normalized)
        prefs.edit()
            .putString(KEY_PIN_SALT, pinSalt)
            .putString(KEY_PIN_HASH, pinHash)
            .apply()
        return true
    }

    /**
     * Returns true if [profileName], [birthMonth], and [birthYear] match the recovery material saved
     * with the PIN (does not change the PIN).
     */
    fun verifyRecoveryIdentity(profileName: String, birthMonth: Int?, birthYear: Int?): Boolean {
        if (!hasPin()) return false
        val recoverySalt = prefs.getString(KEY_RECOVERY_SALT, null) ?: return false
        val expectedRecovery = prefs.getString(KEY_RECOVERY_HASH, null) ?: return false
        val candidate = recoveryPayloadFromInputs(profileName, birthMonth, birthYear)
        return constantTimeEquals(expectedRecovery, sha256B64(recoverySalt, candidate))
    }

    /**
     * When recovery matches stored profile answers, replaces the PIN with [newPin].
     */
    fun verifyRecoveryAndSetNewPin(
        profileName: String,
        birthMonth: Int?,
        birthYear: Int?,
        newPin: String,
    ): Boolean {
        if (!hasPin()) return false
        if (!verifyRecoveryIdentity(profileName, birthMonth, birthYear)) {
            return false
        }
        val normalized = normalizePin(newPin) ?: return false
        val pinSalt = randomSaltB64()
        val pinHash = sha256B64(pinSalt, normalized)
        prefs.edit()
            .putString(KEY_PIN_SALT, pinSalt)
            .putString(KEY_PIN_HASH, pinHash)
            .apply()
        return true
    }

    /** Clears PIN and recovery material (e.g. after successful recovery path that re-onboards). */
    fun clearAll() {
        prefs.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH)
            .remove(KEY_RECOVERY_SALT)
            .remove(KEY_RECOVERY_HASH)
            .apply()
    }

    private fun recoveryPayload(profile: InferenceSettings): String =
        recoveryPayloadFromInputs(profile.userProfileName, profile.birthMonth, profile.birthYear)

    private fun recoveryPayloadFromInputs(profileName: String, birthMonth: Int?, birthYear: Int?): String {
        val nickname = profileName.trim().lowercase()
        val m = birthMonth?.toString() ?: ""
        val y = birthYear?.toString() ?: ""
        return "$nickname|$m|$y"
    }

    private fun normalizePin(pin: String): String? {
        val digits = pin.trim().filter { it.isDigit() }
        if (digits.length !in PIN_LENGTH_RANGE) return null
        return digits
    }

    private fun randomSaltB64(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun sha256B64(saltB64: String, payload: String): String {
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(payload.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(md.digest(), Base64.NO_WRAP)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }

    companion object {
        private const val PREFS_NAME = "gyango_pin_store"
        private const val KEY_PIN_SALT = "pin_salt_b64"
        private const val KEY_PIN_HASH = "pin_hash_b64"
        private const val KEY_RECOVERY_SALT = "recovery_salt_b64"
        private const val KEY_RECOVERY_HASH = "recovery_hash_b64"

        private val PIN_LENGTH_RANGE = 4..6
    }
}
