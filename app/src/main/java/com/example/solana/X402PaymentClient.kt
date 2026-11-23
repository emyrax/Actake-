package com.example.solana

import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

data class X402Challenge(
    val httpStatus: Int = 402,
    val endpoint: String,
    val amountMicroUnits: Long,
    val amountUsdc: Double,
    val tokenMint: String,
    val recipientTreasury: String,
    val network: String,
    val challengeId: String
)

data class X402PaymentReceipt(
    val endpoint: String,
    val feeUsdc: Double,
    val sponsoredBySkr: Boolean,
    val xPaymentHeader: String,
    val settlementHash: String,
    val network: String,
    val timestampMs: Long
)

/**
 * Solana x402 (HTTP 402 Payment Required) autonomous micropayment client for Actake.
 *
 * - Metered Verification Rail (`POST /api/x402/verify-checkin`):
 *   - SKR stakers receive automatic 0-fee Treasury-sponsored x402 settlement vouchers.
 *   - Standard USDC check-ins settle via a cryptographically signed $0.005 USDC x402 voucher.
 * - Agent-Readable Habit Proof Endpoint (`GET /api/x402/attestation/<wallet>`):
 *   - Exposes signed streak & $ACT points attestations ($0.002 USDC per external agent query)
 *     for Solana AI agents (SeekerClaw / OpenClaw).
 */
class X402PaymentClient(
    private val walletManager: SolanaSeekerWalletManager
) {
    fun buildVerifyChallenge(): X402Challenge = X402Challenge(
        httpStatus = 402,
        endpoint = "/api/x402/verify-checkin",
        amountMicroUnits = VERIFY_FEE_MICRO_USDC,
        amountUsdc = VERIFY_FEE_MICRO_USDC / 1_000_000.0,
        tokenMint = SOLANA_USDC_DEVNET_MINT,
        recipientTreasury = ACTAKE_TREASURY_ADDRESS,
        network = "solana-devnet",
        challengeId = UUID.randomUUID().toString().take(12)
    )

    fun settleVerifyCheckinRail(isSkrBoosted: Boolean): X402PaymentReceipt {
        val challenge = buildVerifyChallenge()
        val nowMs = System.currentTimeMillis()
        val session = walletManager.getSavedSession() ?: walletManager.signInWithSolana()

        return if (isSkrBoosted) {
            val voucherBody = "x402|endpoint=${challenge.endpoint}|payer=${session.publicKeyBase58}|" +
                "sponsor=actake-skr-treasury|amount=0|challenge=${challenge.challengeId}|ts=$nowMs"
            val sig = walletManager.signPayloadBase58(voucherBody)
            val hash = sha256Hex(voucherBody)
            X402PaymentReceipt(
                endpoint = challenge.endpoint,
                feeUsdc = 0.0,
                sponsoredBySkr = true,
                xPaymentHeader = "solana-x402;network=${challenge.network};sponsor=skr-0fee;sig=${sig.take(28)}",
                settlementHash = hash,
                network = challenge.network,
                timestampMs = nowMs
            )
        } else {
            val voucherBody = "x402|endpoint=${challenge.endpoint}|payer=${session.publicKeyBase58}|" +
                "recipient=${challenge.recipientTreasury}|mint=${challenge.tokenMint}|" +
                "amount=${challenge.amountMicroUnits}|challenge=${challenge.challengeId}|ts=$nowMs"
            val sig = walletManager.signPayloadBase58(voucherBody)
            val hash = sha256Hex(voucherBody)
            X402PaymentReceipt(
                endpoint = challenge.endpoint,
                feeUsdc = challenge.amountUsdc,
                sponsoredBySkr = false,
                xPaymentHeader = "solana-x402;network=${challenge.network};amount=${challenge.amountMicroUnits};" +
                    "payer=${session.shortAddress};sig=${sig.take(28)}",
                settlementHash = hash,
                network = challenge.network,
                timestampMs = nowMs
            )
        }
    }

    fun buildAgentAttestationJson(
        walletAddress: String,
        streakCount: Int,
        actPoints: Int,
        recentVerifiedObject: String
    ): String {
        val safeWallet = walletAddress.ifBlank {
            walletManager.getSavedSession()?.publicKeyBase58 ?: "unconnected"
        }
        val ts = System.currentTimeMillis()
        val bodyToSign = "x402_attest|$safeWallet|$streakCount|$actPoints|$ts"
        val sig = walletManager.signPayloadBase58(bodyToSign)
        return JSONObject().apply {
            put("protocol", "x402-solana-v1")
            put("endpoint", "GET /api/x402/attestation/$safeWallet")
            put("queryPriceUsdc", String.format(Locale.US, "%.3f", AGENT_QUERY_FEE_USDC))
            put("wallet", safeWallet)
            put("verifiedStreakDays", streakCount)
            put("actPoints", actPoints)
            put("lastVerifiedTarget", recentVerifiedObject.ifBlank { "Pending first proof" })
            put("ed25519Attestation", sig.take(44))
            put("timestampMs", ts)
        }.toString(2)
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val VERIFY_FEE_MICRO_USDC = 5_000L // $0.005 USDC
        const val AGENT_QUERY_FEE_USDC = 0.002
        const val SOLANA_USDC_DEVNET_MINT = "4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU"
        const val ACTAKE_TREASURY_ADDRESS = "ActkTre5uryVau1tSo1anaSeekerX402Rail11111111"
    }
}
