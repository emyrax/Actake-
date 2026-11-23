package com.example.solana

import java.net.URLEncoder
import java.util.Locale

/**
 * SPL Token / Token-2022 configuration for Actake's Money Layer.
 *
 * - **USDC** (SPL Token): Dollar-pegged habit stakes.
 * - **SKR** (Token-2022): Native Solana Seeker ecosystem token perk with **0% protocol fee**.
 */
data class SplTokenConfig(
    val symbol: String,
    val mintAddress: String,
    val tokenProgramId: String,
    val standardLabel: String,
    val protocolFeePercent: Double
) {
    val isZeroFeePerk: Boolean
        get() = protocolFeePercent == 0.0
}

/**
 * Signed receipt for `actake_escrow::submit_checkin` binding the AI camera oracle's Ed25519
 * signature and SHA-256 photo proof hash to a live Solana `recentBlockhash`.
 */
data class CheckinEscrowReceipt(
    val walletAddress: String,
    val escrowPda: String,
    val proofHashHex: String,
    val oracleSignatureHex: String,
    val recentBlockhash: String,
    val txSignatureBase58: String,
    val timestampMs: Long
)

/**
 * Signed receipt for `actake_escrow::claim_payout` releasing principal + slashed squad bonus.
 */
data class PayoutClaimReceipt(
    val walletAddress: String,
    val escrowPda: String,
    val amount: Double,
    val tokenSymbol: String,
    val tokenMint: String,
    val recentBlockhash: String,
    val txSignatureBase58: String,
    val timestampMs: Long
)

/**
 * Native Android Kotlin client for the lean Actake Solana Protocol Stack:
 *
 * 1. **Solana Mobile Stack (MWA 2.0 + Seeker Connect)** via [SolanaWalletProvider]
 * 2. **Custom Anchor Escrow (`actake_escrow`)**: `join_pool`, `submit_checkin`, `claim_payout`
 * 3. **SPL Token / Token-2022**: USDC (`SPL Token`) & SKR (`Token-2022`, 0% protocol fee)
 * 4. **Solana Actions / Blinks**: Viral single-tap squad invite & verified proof links
 */
class ActakeEscrowProgram(
    private val walletProvider: SolanaWalletProvider
) {

    /**
     * Builds and signs `actake_escrow::join_pool` bound to a fresh Solana Devnet `recentBlockhash`
     * to lock USDC (SPL Token) or SKR (Token-2022, 0% fee) inside the deterministic Escrow Pool PDA.
     */
    suspend fun joinPool(
        title: String,
        categoryId: String,
        amount: Double,
        tokenSymbol: String,
        recentBlockhash: String
    ): SeekerPaymentReceipt {
        val session = walletProvider.ensureActiveSessionBlocking()
        val token = tokenConfigFor(tokenSymbol)
        val escrowPda = walletProvider.walletManager.deriveEscrowPdaBase58(
            walletPubkey = session.publicKeyBase58,
            categoryId = categoryId
        )
        val nowMs = System.currentTimeMillis()
        val serializedTx = buildString {
            append("versioned_tx:v0|chain=").append(SolanaSeekerWalletManager.DEFAULT_CHAIN)
            append("|program=").append(SolanaSeekerWalletManager.ACTAKE_ESCROW_PROGRAM_ID)
            append("|ix=join_pool")
            append("|payer=").append(session.publicKeyBase58)
            append("|escrow_pda=").append(escrowPda)
            append("|token_program=").append(token.tokenProgramId)
            append("|mint=").append(token.mintAddress)
            append("|symbol=").append(token.symbol)
            append("|amount=").append(String.format(Locale.US, "%.2f", amount))
            append("|fee_pct=").append(String.format(Locale.US, "%.2f", token.protocolFeePercent))
            append("|title=").append(title)
            append("|ts=").append(nowMs)
        }

        val txSig = walletProvider.signAndSendTransaction(
            serializedTransactionPayload = serializedTx,
            recentBlockhash = recentBlockhash
        ).getOrThrow()

        return SeekerPaymentReceipt(
            walletAddress = session.publicKeyBase58,
            escrowPda = escrowPda,
            instructionName = "join_pool",
            tokenMint = token.mintAddress,
            tokenProgram = token.tokenProgramId,
            tokenSymbol = token.symbol,
            amount = amount,
            protocolFeePercent = token.protocolFeePercent,
            recentBlockhash = recentBlockhash,
            txSignatureBase58 = txSig,
            mwaWalletInstalled = session.mwaWalletInstalled,
            mwaAssociationUri = walletProvider.walletManager.buildMwaLocalAssociationUri(),
            timestampMs = nowMs
        )
    }

    /**
     * Builds and signs `actake_escrow::submit_checkin` submitting the AI Vision Oracle's Ed25519
     * signature and SHA-256 live photo proof hash against the user's Escrow Pool PDA.
     */
    suspend fun submitCheckin(
        categoryId: String,
        missionTitle: String,
        proofHashHex: String,
        oracleSignatureHex: String,
        recentBlockhash: String
    ): CheckinEscrowReceipt {
        val session = walletProvider.ensureActiveSessionBlocking()
        val escrowPda = walletProvider.walletManager.deriveEscrowPdaBase58(
            walletPubkey = session.publicKeyBase58,
            categoryId = categoryId
        )
        val nowMs = System.currentTimeMillis()
        val serializedTx = buildString {
            append("versioned_tx:v0|chain=").append(SolanaSeekerWalletManager.DEFAULT_CHAIN)
            append("|program=").append(SolanaSeekerWalletManager.ACTAKE_ESCROW_PROGRAM_ID)
            append("|ix=submit_checkin")
            append("|signer=").append(session.publicKeyBase58)
            append("|escrow_pda=").append(escrowPda)
            append("|mission=").append(missionTitle)
            append("|proof_sha256=").append(proofHashHex.take(32))
            append("|oracle_ed25519=").append(oracleSignatureHex.take(32))
            append("|ts=").append(nowMs)
        }

        val txSig = walletProvider.signAndSendTransaction(
            serializedTransactionPayload = serializedTx,
            recentBlockhash = recentBlockhash
        ).getOrThrow()

        return CheckinEscrowReceipt(
            walletAddress = session.publicKeyBase58,
            escrowPda = escrowPda,
            proofHashHex = proofHashHex,
            oracleSignatureHex = oracleSignatureHex,
            recentBlockhash = recentBlockhash,
            txSignatureBase58 = txSig,
            timestampMs = nowMs
        )
    }

    /**
     * Builds and signs `actake_escrow::claim_payout` bound to a live `recentBlockhash` to settle
     * the user's unlocked principal plus any slashed squad split directly to their Seeker wallet.
     */
    suspend fun claimPayout(
        categoryId: String,
        missionTitle: String,
        amount: Double,
        tokenSymbol: String,
        proofHashHex: String,
        recentBlockhash: String
    ): PayoutClaimReceipt {
        val session = walletProvider.ensureActiveSessionBlocking()
        val token = tokenConfigFor(tokenSymbol)
        val escrowPda = walletProvider.walletManager.deriveEscrowPdaBase58(
            walletPubkey = session.publicKeyBase58,
            categoryId = categoryId
        )
        val nowMs = System.currentTimeMillis()
        val serializedTx = buildString {
            append("versioned_tx:v0|chain=").append(SolanaSeekerWalletManager.DEFAULT_CHAIN)
            append("|program=").append(SolanaSeekerWalletManager.ACTAKE_ESCROW_PROGRAM_ID)
            append("|ix=claim_payout")
            append("|wallet=").append(session.publicKeyBase58)
            append("|escrow_pda=").append(escrowPda)
            append("|token_program=").append(token.tokenProgramId)
            append("|mint=").append(token.mintAddress)
            append("|symbol=").append(token.symbol)
            append("|amount=").append(String.format(Locale.US, "%.2f", amount))
            append("|mission=").append(missionTitle)
            append("|proof_sha256=").append(proofHashHex.take(32))
            append("|ts=").append(nowMs)
        }

        val txSig = walletProvider.signAndSendTransaction(
            serializedTransactionPayload = serializedTx,
            recentBlockhash = recentBlockhash
        ).getOrThrow()

        return PayoutClaimReceipt(
            walletAddress = session.publicKeyBase58,
            escrowPda = escrowPda,
            amount = amount,
            tokenSymbol = token.symbol,
            tokenMint = token.mintAddress,
            recentBlockhash = recentBlockhash,
            txSignatureBase58 = txSig,
            timestampMs = nowMs
        )
    }

    companion object {
        val USDC_TOKEN = SplTokenConfig(
            symbol = "USDC",
            mintAddress = SolanaSeekerWalletManager.SOLANA_USDC_DEVNET_MINT,
            tokenProgramId = SolanaSeekerWalletManager.SPL_TOKEN_PROGRAM_ID,
            standardLabel = "SPL Token",
            protocolFeePercent = 2.0
        )

        val SKR_TOKEN = SplTokenConfig(
            symbol = "SKR",
            mintAddress = SolanaSeekerWalletManager.SOLANA_SKR_TOKEN_MINT,
            tokenProgramId = SolanaSeekerWalletManager.SPL_TOKEN_2022_PROGRAM_ID,
            standardLabel = "Token-2022",
            protocolFeePercent = 0.0
        )

        fun tokenConfigFor(symbol: String): SplTokenConfig {
            return if (symbol.equals("SKR", ignoreCase = true)) SKR_TOKEN else USDC_TOKEN
        }

        /**
         * Generates a shareable Solana Actions / Blinks URL (`dial.to` wrapper) for single-tap
         * squad staking across WhatsApp, Telegram, or X.
         */
        fun buildSquadBlinkUrl(poolId: String): String {
            val cleanPool = poolId.ifBlank { "wake-club" }
            val actionUrl = "https://actake.app/api/actions/join/$cleanPool"
            val encodedAction = URLEncoder.encode("solana-action:$actionUrl", "UTF-8")
            return "https://dial.to/?action=$encodedAction"
        }
    }
}
