package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.VisionOracleRepository
import com.example.solana.ActakeEscrowProgram
import com.example.solana.SolanaSeekerWalletManager
import com.example.solana.SolanaWalletProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SolanaProtocolStackTest {

    private lateinit var context: Context
    private lateinit var walletProvider: SolanaWalletProvider
    private lateinit var escrowProgram: ActakeEscrowProgram

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        walletProvider = SolanaWalletProvider(context)
        walletProvider.disconnect()
        escrowProgram = ActakeEscrowProgram(walletProvider)
    }

    @Test
    fun solanaWalletProvider_connectAndSignIn_persistsAuthTokenAndPublicKey() = runBlocking {
        assertNull(walletProvider.currentPublicKeyOrNull())
        assertFalse(walletProvider.walletState.value.isConnected)

        val session = walletProvider.connectAndSignIn().getOrThrow()
        assertTrue(session.publicKeyBase58.length in 32..44)
        assertTrue(session.authToken.isNotBlank())
        assertEquals(session.publicKeyBase58, walletProvider.walletState.value.publicKey)
        assertEquals(session.authToken, walletProvider.walletState.value.authToken)
        assertTrue(walletProvider.walletState.value.isConnected)

        // Silent reconnect restores cached authToken & publicKey
        val reconnected = walletProvider.reconnectSilently()
        assertNotNull(reconnected)
        assertEquals(session.authToken, reconnected?.authToken)

        walletProvider.disconnect()
        assertNull(walletProvider.walletState.value.publicKey)
        assertNull(walletProvider.walletState.value.authToken)
        assertFalse(walletProvider.walletState.value.isConnected)
    }

    @Test
    fun anchorEscrowProgram_signsJoinPoolSubmitCheckinAndClaimPayout_withSplAndToken2022() = runBlocking {
        walletProvider.connectAndSignIn().getOrThrow()
        val blockhash = "TestDevnetBlockhash111111111111111111111111"

        // SKR uses Token-2022 with 0% protocol fee
        val joinReceipt = escrowProgram.joinPool(
            title = "Morning Wakeup",
            categoryId = "WAKEUP",
            amount = 15.0,
            tokenSymbol = "SKR",
            recentBlockhash = blockhash
        )
        assertEquals("join_pool", joinReceipt.instructionName)
        assertEquals(SolanaSeekerWalletManager.SPL_TOKEN_2022_PROGRAM_ID, joinReceipt.tokenProgram)
        assertEquals(0.0, joinReceipt.protocolFeePercent, 0.0001)
        assertTrue(joinReceipt.txSignatureBase58.isNotBlank())

        val checkinReceipt = escrowProgram.submitCheckin(
            categoryId = "WAKEUP",
            missionTitle = "Morning Wakeup",
            proofHashHex = "abcdef1234567890abcdef1234567890",
            oracleSignatureHex = "9876543210fedcba9876543210fedcba",
            recentBlockhash = blockhash
        )
        assertEquals(joinReceipt.escrowPda, checkinReceipt.escrowPda)
        assertTrue(checkinReceipt.txSignatureBase58.isNotBlank())

        val claimReceipt = escrowProgram.claimPayout(
            categoryId = "WAKEUP",
            missionTitle = "Morning Wakeup",
            amount = 19.20,
            tokenSymbol = "SKR",
            proofHashHex = checkinReceipt.proofHashHex,
            recentBlockhash = blockhash
        )
        assertEquals(joinReceipt.escrowPda, claimReceipt.escrowPda)
        assertTrue(claimReceipt.txSignatureBase58.isNotBlank())

        val blinkUrl = ActakeEscrowProgram.buildSquadBlinkUrl("campus-7am")
        assertTrue(blinkUrl.startsWith("https://dial.to/?action="))
    }

    @Test
    fun randomizedObjectPool_avoidsRecentlyVerifiedItems() {
        val avoidList = listOf(
            "ceramic coffee cup or mug",
            "toothbrush",
            "spoon or fork",
            "set of house keys",
            "eyeglasses or sunglasses",
            "wristwatch or wall clock"
        )
        val picked = VisionOracleRepository.sampleRandomizedSeedObject("Morning Wakeup", avoidList)
        assertTrue(picked.contains("faucet", ignoreCase = true))
        assertFalse(picked.contains("money", ignoreCase = true))
    }
}
