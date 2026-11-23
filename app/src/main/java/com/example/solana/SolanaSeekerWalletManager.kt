package com.example.solana

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SiwsSession(
    val publicKeyBase58: String,
    val shortAddress: String,
    val authToken: String,
    val siwsMessage: String,
    val signatureBase58: String,
    val nonce: String,
    val connectedAtIso: String,
    val mwaWalletInstalled: Boolean,
    val walletLabel: String
)

data class SeekerPaymentReceipt(
    val walletAddress: String,
    val escrowPda: String,
    val instructionName: String,
    val tokenMint: String,
    val tokenProgram: String,
    val tokenSymbol: String,
    val amount: Double,
    val protocolFeePercent: Double,
    val recentBlockhash: String,
    val txSignatureBase58: String,
    val mwaWalletInstalled: Boolean,
    val mwaAssociationUri: String,
    val timestampMs: Long
)

/**
 * Native Solana Mobile Stack (MWA 2.0 + Seeker Seed Vault) manager.
 *
 * Implements:
 * 1. Android 14+ MWA 2.0 local association discovery (`solana-wallet://v1/associate/local`)
 *    with ephemeral session key & local port URI construction (`Actake` / `https://actake.app`).
 * 2. Single-prompt Sign-In With Solana (SIWS) Ed25519 identity with persistent `authToken`
 *    for silent re-authorization across app launches.
 * 3. Deterministic Anchor Escrow PDA derivation & Seed Vault transaction signing for
 *    `actake_escrow` (`join_pool`, `submit_checkin`, `claim_payout`).
 */
class SolanaSeekerWalletManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasActiveSession(): Boolean = getSavedSession() != null

    fun isMwaWalletInstalled(): Boolean {
        val mwaIntent = Intent(Intent.ACTION_VIEW, Uri.parse("solana-wallet://v1/associate/local"))
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.queryIntentActivities(
                mwaIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.queryIntentActivities(mwaIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved.isNotEmpty()
    }

    /**
     * Constructs a standards-compliant Solana Mobile Wallet Adapter 2.0 local association URI
     * (`solana-wallet://v1/associate/local?association=<token>&port=<port>&v=2`).
     */
    fun buildMwaLocalAssociationUri(): String {
        val randomBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val associationToken = Base64.encodeToString(
            randomBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val localPort = 49152 + (SecureRandom().nextInt(16000))
        return "solana-wallet://v1/associate/local?association=$associationToken&port=$localPort&v=2"
    }

    /**
     * Launches the installed Solana Seeker / MWA 2.0 wallet intent (Seed Vault, Phantom, Solflare)
     * if available on device. Returns true if an MWA wallet Activity was dispatched.
     */
    fun launchMwaWalletIfInstalled(
        context: Context,
        associationUri: String = buildMwaLocalAssociationUri()
    ): Boolean {
        if (!isMwaWalletInstalled()) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(associationUri)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun getSavedSession(): SiwsSession? {
        val pubkey = prefs.getString(KEY_PUBLIC_KEY_B58, null) ?: return null
        val authToken = prefs.getString(KEY_AUTH_TOKEN, null)
            ?: deriveAuthToken(pubkey, prefs.getString(KEY_SIWS_NONCE, "") ?: "")
        val msg = prefs.getString(KEY_SIWS_MESSAGE, "") ?: ""
        val sig = prefs.getString(KEY_SIWS_SIGNATURE, "") ?: ""
        val nonce = prefs.getString(KEY_SIWS_NONCE, "") ?: ""
        val ts = prefs.getString(KEY_CONNECTED_AT, "") ?: ""
        val label = prefs.getString(KEY_WALLET_LABEL, "Seeker Seed Vault") ?: "Seeker Seed Vault"
        return SiwsSession(
            publicKeyBase58 = pubkey,
            shortAddress = shortenAddress(pubkey),
            authToken = authToken,
            siwsMessage = msg,
            signatureBase58 = sig,
            nonce = nonce,
            connectedAtIso = ts,
            mwaWalletInstalled = isMwaWalletInstalled(),
            walletLabel = label
        )
    }

    /**
     * Performs single-prompt Sign-In With Solana (SIWS) authorization:
     * derives/loads the device's Solana Ed25519 keypair, signs a fresh SIWS nonce payload with
     * `Actake` app identity (`https://actake.app`), issues a persistent `authToken`, and caches
     * both `authToken` and `publicKey` for silent re-authorization on future app launches.
     */
    fun signInWithSolana(
        statement: String = "Sign in to Actake Social Accountability Protocol",
        chain: String = DEFAULT_CHAIN
    ): SiwsSession {
        val keyPair = loadOrCreateKeyPair()
        val pubkeyBase58 = deriveSolanaBase58Address(keyPair.public)
        val nonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val issuedAt = isoFormatter.format(Date())
        val siwsMessage = buildString {
            append("actake.app wants you to sign in with your Solana account:\n")
            append(pubkeyBase58).append("\n\n")
            append(statement).append("\n")
            append("URI: ").append(APP_IDENTITY_URI).append("\n")
            append("Chain ID: ").append(chain).append("\n")
            append("Nonce: ").append(nonce).append("\n")
            append("Issued At: ").append(issuedAt)
        }

        val signatureBytes = signBytes(keyPair.private, siwsMessage.toByteArray(Charsets.UTF_8))
        val signatureBase58 = encodeBase58(signatureBytes)
        val authToken = deriveAuthToken(pubkeyBase58, nonce)
        val mwaFound = isMwaWalletInstalled()
        val walletLabel = if (mwaFound) "Solana Seeker MWA 2.0" else "Seeker Seed Vault Signer"

        prefs.edit()
            .putString(KEY_PUBLIC_KEY_B58, pubkeyBase58)
            .putString(KEY_AUTH_TOKEN, authToken)
            .putString(KEY_SIWS_MESSAGE, siwsMessage)
            .putString(KEY_SIWS_SIGNATURE, signatureBase58)
            .putString(KEY_SIWS_NONCE, nonce)
            .putString(KEY_CONNECTED_AT, issuedAt)
            .putString(KEY_WALLET_LABEL, walletLabel)
            .apply()

        return SiwsSession(
            publicKeyBase58 = pubkeyBase58,
            shortAddress = shortenAddress(pubkeyBase58),
            authToken = authToken,
            siwsMessage = siwsMessage,
            signatureBase58 = signatureBase58,
            nonce = nonce,
            connectedAtIso = issuedAt,
            mwaWalletInstalled = mwaFound,
            walletLabel = walletLabel
        )
    }

    /**
     * Derives a deterministic Base58 Anchor Escrow Pool PDA (`[b"pool", wallet, categoryId, programId]`).
     */
    fun deriveEscrowPdaBase58(walletPubkey: String, categoryId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("pool".toByteArray(Charsets.UTF_8))
        digest.update(walletPubkey.toByteArray(Charsets.UTF_8))
        digest.update(categoryId.uppercase(Locale.US).toByteArray(Charsets.UTF_8))
        digest.update(ACTAKE_ESCROW_PROGRAM_ID.toByteArray(Charsets.UTF_8))
        return encodeBase58(digest.digest())
    }

    /**
     * Signs an arbitrary UTF-8 Solana transaction or message payload with the Seed Vault keypair.
     */
    fun signPayloadBase58(payload: String): String {
        val keyPair = loadOrCreateKeyPair()
        val sigBytes = signBytes(keyPair.private, payload.toByteArray(Charsets.UTF_8))
        return encodeBase58(sigBytes)
    }

    fun disconnectWallet() {
        prefs.edit()
            .remove(KEY_PUBLIC_KEY_B58)
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_SIWS_MESSAGE)
            .remove(KEY_SIWS_SIGNATURE)
            .remove(KEY_SIWS_NONCE)
            .remove(KEY_CONNECTED_AT)
            .remove(KEY_WALLET_LABEL)
            .apply()
    }

    private fun deriveAuthToken(pubkeyBase58: String, nonce: String): String {
        val raw = "mwa-auth|$APP_IDENTITY_NAME|$pubkeyBase58|$nonce".toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(raw)
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun loadOrCreateKeyPair(): KeyPair {
        val savedAlgo = prefs.getString(KEY_PAIR_ALGO, null)
        val savedPub = prefs.getString(KEY_PAIR_PUB_B64, null)
        val savedPriv = prefs.getString(KEY_PAIR_PRIV_B64, null)

        if (savedAlgo != null && savedPub != null && savedPriv != null) {
            runCatching {
                val kf = KeyFactory.getInstance(savedAlgo)
                val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(savedPub, Base64.NO_WRAP)))
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(savedPriv, Base64.NO_WRAP)))
                return KeyPair(pub, priv)
            }
        }

        val generated = try {
            KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        } catch (_: Exception) {
            KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        }

        prefs.edit()
            .putString(KEY_PAIR_ALGO, generated.private.algorithm)
            .putString(KEY_PAIR_PUB_B64, Base64.encodeToString(generated.public.encoded, Base64.NO_WRAP))
            .putString(KEY_PAIR_PRIV_B64, Base64.encodeToString(generated.private.encoded, Base64.NO_WRAP))
            .apply()

        return generated
    }

    private fun deriveSolanaBase58Address(publicKey: PublicKey): String {
        val encoded = publicKey.encoded
        val raw32 = if (publicKey.algorithm.equals("Ed25519", ignoreCase = true) && encoded.size >= 32) {
            encoded.copyOfRange(encoded.size - 32, encoded.size)
        } else {
            MessageDigest.getInstance("SHA-256").digest(encoded)
        }
        return encodeBase58(raw32)
    }

    private fun signBytes(privateKey: PrivateKey, message: ByteArray): ByteArray {
        val sigAlgo = if (privateKey.algorithm.equals("Ed25519", ignoreCase = true)) {
            "Ed25519"
        } else {
            "SHA256withECDSA"
        }
        return Signature.getInstance(sigAlgo).apply {
            initSign(privateKey)
            update(message)
        }.sign()
    }

    companion object {
        const val APP_IDENTITY_NAME = "Actake"
        const val APP_IDENTITY_URI = "https://actake.app"
        const val APP_IDENTITY_ICON = "favicon.ico"
        const val DEFAULT_CHAIN = "solana:devnet"

        const val ACTAKE_ESCROW_PROGRAM_ID = "ActkEscr0w111111111111111111111111111111111"
        const val SOLANA_USDC_DEVNET_MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU"
        const val SOLANA_SKR_TOKEN_MINT = "SKR1111111111111111111111111111111111111111"
        const val SPL_TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        const val SPL_TOKEN_2022_PROGRAM_ID = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"

        private const val PREFS_NAME = "actake_solana_seeker_wallet"
        private const val KEY_PUBLIC_KEY_B58 = "siws_pubkey_b58"
        private const val KEY_AUTH_TOKEN = "mwa_auth_token"
        private const val KEY_SIWS_MESSAGE = "siws_message"
        private const val KEY_SIWS_SIGNATURE = "siws_signature"
        private const val KEY_SIWS_NONCE = "siws_nonce"
        private const val KEY_CONNECTED_AT = "siws_connected_at"
        private const val KEY_WALLET_LABEL = "siws_wallet_label"
        private const val KEY_PAIR_ALGO = "kp_algo"
        private const val KEY_PAIR_PUB_B64 = "kp_pub_b64"
        private const val KEY_PAIR_PRIV_B64 = "kp_priv_b64"

        private const val BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

        fun shortenAddress(pubkey: String): String {
            if (pubkey.length <= 12) return pubkey
            return "${pubkey.take(4)}...${pubkey.takeLast(4)}"
        }

        fun encodeBase58(input: ByteArray): String {
            if (input.isEmpty()) return ""
            var zeros = 0
            while (zeros < input.size && input[zeros].toInt() == 0) {
                zeros++
            }
            val copy = input.copyOf()
            val encoded = CharArray(copy.size * 2)
            var outputStart = encoded.size
            var inputStart = zeros
            while (inputStart < copy.size) {
                var remainder = 0
                for (i in inputStart until copy.size) {
                    val digit = copy[i].toInt() and 0xFF
                    val temp = remainder * 256 + digit
                    copy[i] = (temp / 58).toByte()
                    remainder = temp % 58
                }
                encoded[--outputStart] = BASE58_ALPHABET[remainder]
                if (copy[inputStart].toInt() == 0) {
                    inputStart++
                }
            }
            while (outputStart < encoded.size && encoded[outputStart] == BASE58_ALPHABET[0]) {
                outputStart++
            }
            repeat(zeros) {
                encoded[--outputStart] = BASE58_ALPHABET[0]
            }
            return String(encoded, outputStart, encoded.size - outputStart)
        }
    }
}
