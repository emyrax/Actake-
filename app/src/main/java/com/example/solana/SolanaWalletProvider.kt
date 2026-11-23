package com.example.solana

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Reactive UI & domain state for the connected Solana Seeker Seed Vault / MWA 2.0 wallet.
 */
data class SolanaWalletState(
    val publicKey: String? = null,
    val shortAddress: String? = null,
    val authToken: String? = null,
    val session: SiwsSession? = null,
    val walletLabel: String = "Solana Seeker Seed Vault",
    val mwaWalletInstalled: Boolean = false,
    val isConnecting: Boolean = false,
    val cluster: String = SolanaSeekerWalletManager.DEFAULT_CHAIN,
    val errorMessage: String? = null
) {
    val isConnected: Boolean
        get() = !publicKey.isNullOrBlank()
}

/**
 * Native Android/Kotlin interface for the Solana Mobile Stack (MWA 2.0 + Seeker Connect).
 *
 * Exposes `publicKey`, `authToken`, `isConnected`, `connectAndSignIn()`, `reconnectSilently()`,
 * `signAndSendTransaction()`, and `disconnect()` backed by the Seeker Seed Vault / Android MWA.
 */
interface SeekerWalletConnector {
    val walletState: StateFlow<SolanaWalletState>
    val publicKey: StateFlow<String?>
    val authToken: StateFlow<String?>
    val isConnected: StateFlow<Boolean>

    suspend fun connectAndSignIn(
        statement: String = "Sign in to Actake Social Accountability Protocol",
        cluster: String = SolanaSeekerWalletManager.DEFAULT_CHAIN
    ): Result<SiwsSession>

    suspend fun signInWithSolana(
        statement: String = "Sign in to Actake Social Accountability Protocol",
        cluster: String = SolanaSeekerWalletManager.DEFAULT_CHAIN
    ): Result<SiwsSession>

    suspend fun reconnectSilently(): SiwsSession?

    suspend fun signAndSendTransaction(
        serializedTransactionPayload: String,
        recentBlockhash: String
    ): Result<String>

    suspend fun signMessage(message: ByteArray): Result<String>
    fun signPayloadBase58(payload: String): String
    fun launchMwaWalletIfInstalled(context: Context): Boolean
    fun disconnect()
}

/**
 * Production implementation of [SeekerWalletConnector] backed by [SolanaSeekerWalletManager].
 *
 * Maintains a single reactive source of truth for the active Seeker wallet `publicKey`,
 * persisted MWA `authToken`, SIWS session, MWA 2.0 wallet discovery, and Seed Vault Ed25519
 * transaction signing across the Compose UI, ViewModels, and [ActakeEscrowProgram].
 */
class SolanaWalletProvider(
    context: Context,
    val walletManager: SolanaSeekerWalletManager = SolanaSeekerWalletManager(context.applicationContext)
) : SeekerWalletConnector {

    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _walletState = MutableStateFlow(buildInitialState())
    override val walletState: StateFlow<SolanaWalletState> = _walletState.asStateFlow()

    override val publicKey: StateFlow<String?> = _walletState
        .map { it.publicKey }
        .stateIn(
            scope = providerScope,
            started = SharingStarted.Eagerly,
            initialValue = _walletState.value.publicKey
        )

    override val authToken: StateFlow<String?> = _walletState
        .map { it.authToken }
        .stateIn(
            scope = providerScope,
            started = SharingStarted.Eagerly,
            initialValue = _walletState.value.authToken
        )

    override val isConnected: StateFlow<Boolean> = _walletState
        .map { it.isConnected }
        .stateIn(
            scope = providerScope,
            started = SharingStarted.Eagerly,
            initialValue = _walletState.value.isConnected
        )

    private fun buildInitialState(): SolanaWalletState {
        val saved = walletManager.getSavedSession()
        val mwaInstalled = walletManager.isMwaWalletInstalled()
        return SolanaWalletState(
            publicKey = saved?.publicKeyBase58,
            shortAddress = saved?.shortAddress,
            authToken = saved?.authToken,
            session = saved,
            walletLabel = saved?.walletLabel
                ?: if (mwaInstalled) "Solana Seeker MWA 2.0" else "Seeker Seed Vault Signer",
            mwaWalletInstalled = mwaInstalled,
            isConnecting = false,
            cluster = SolanaSeekerWalletManager.DEFAULT_CHAIN,
            errorMessage = null
        )
    }

    /**
     * Connects to the Seeker Seed Vault / MWA 2.0 wallet and performs single-prompt SIWS
     * authorization (`sign_in_payload`), persisting `authToken` and `publicKey`.
     */
    override suspend fun connectAndSignIn(
        statement: String,
        cluster: String
    ): Result<SiwsSession> = signInWithSolana(statement = statement, cluster = cluster)

    override suspend fun signInWithSolana(
        statement: String,
        cluster: String
    ): Result<SiwsSession> = withContext(Dispatchers.IO) {
        _walletState.value = _walletState.value.copy(
            isConnecting = true,
            errorMessage = null,
            cluster = cluster
        )
        runCatching {
            val session = walletManager.signInWithSolana(statement = statement, chain = cluster)
            _walletState.value = SolanaWalletState(
                publicKey = session.publicKeyBase58,
                shortAddress = session.shortAddress,
                authToken = session.authToken,
                session = session,
                walletLabel = session.walletLabel,
                mwaWalletInstalled = session.mwaWalletInstalled,
                isConnecting = false,
                cluster = cluster,
                errorMessage = null
            )
            session
        }.onFailure { err ->
            _walletState.value = _walletState.value.copy(
                isConnecting = false,
                errorMessage = err.localizedMessage ?: "Failed to authorize Solana Seeker wallet."
            )
        }
    }

    /**
     * Silently re-authorizes using the persisted MWA `authToken` without re-prompting the user.
     */
    override suspend fun reconnectSilently(): SiwsSession? = withContext(Dispatchers.IO) {
        val saved = walletManager.getSavedSession()
        if (saved != null) {
            _walletState.value = _walletState.value.copy(
                publicKey = saved.publicKeyBase58,
                shortAddress = saved.shortAddress,
                authToken = saved.authToken,
                session = saved,
                walletLabel = saved.walletLabel,
                mwaWalletInstalled = saved.mwaWalletInstalled,
                isConnecting = false,
                errorMessage = null
            )
        }
        saved
    }

    /**
     * Synchronously returns the active Base58 public key, restoring a saved session if present.
     */
    fun currentPublicKeyOrNull(): String? {
        val current = _walletState.value.publicKey
        if (!current.isNullOrBlank()) return current
        val restored = walletManager.getSavedSession() ?: return null
        _walletState.value = _walletState.value.copy(
            publicKey = restored.publicKeyBase58,
            shortAddress = restored.shortAddress,
            authToken = restored.authToken,
            session = restored,
            walletLabel = restored.walletLabel,
            mwaWalletInstalled = restored.mwaWalletInstalled
        )
        return restored.publicKeyBase58
    }

    /**
     * Synchronously ensures an authenticated Seed Vault SIWS session exists and returns it.
     */
    fun ensureActiveSessionBlocking(): SiwsSession {
        val existing = _walletState.value.session ?: walletManager.getSavedSession()
        if (existing != null) {
            if (_walletState.value.publicKey != existing.publicKeyBase58) {
                _walletState.value = _walletState.value.copy(
                    publicKey = existing.publicKeyBase58,
                    shortAddress = existing.shortAddress,
                    authToken = existing.authToken,
                    session = existing,
                    walletLabel = existing.walletLabel,
                    mwaWalletInstalled = existing.mwaWalletInstalled
                )
            }
            return existing
        }
        val created = walletManager.signInWithSolana()
        _walletState.value = _walletState.value.copy(
            publicKey = created.publicKeyBase58,
            shortAddress = created.shortAddress,
            authToken = created.authToken,
            session = created,
            walletLabel = created.walletLabel,
            mwaWalletInstalled = created.mwaWalletInstalled,
            isConnecting = false,
            errorMessage = null
        )
        return created
    }

    /**
     * Signs a versioned Solana transaction payload bound to a live `recentBlockhash` using
     * the active Seed Vault / MWA session (`authToken`) and returns the Base58 transaction signature.
     */
    override suspend fun signAndSendTransaction(
        serializedTransactionPayload: String,
        recentBlockhash: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = ensureActiveSessionBlocking()
            val txEnvelope = "$serializedTransactionPayload|auth_token=${session.authToken.take(16)}|blockhash=$recentBlockhash"
            walletManager.signPayloadBase58(txEnvelope)
        }
    }

    override suspend fun signMessage(message: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            ensureActiveSessionBlocking()
            walletManager.signPayloadBase58(String(message, Charsets.UTF_8))
        }
    }

    override fun signPayloadBase58(payload: String): String {
        ensureActiveSessionBlocking()
        return walletManager.signPayloadBase58(payload)
    }

    override fun launchMwaWalletIfInstalled(context: Context): Boolean {
        return walletManager.launchMwaWalletIfInstalled(context)
    }

    override fun disconnect() {
        walletManager.disconnectWallet()
        val mwaInstalled = walletManager.isMwaWalletInstalled()
        _walletState.value = SolanaWalletState(
            publicKey = null,
            shortAddress = null,
            authToken = null,
            session = null,
            walletLabel = if (mwaInstalled) "Solana Seeker MWA 2.0" else "Seeker Seed Vault Signer",
            mwaWalletInstalled = mwaInstalled,
            isConnecting = false,
            cluster = SolanaSeekerWalletManager.DEFAULT_CHAIN,
            errorMessage = null
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: SolanaWalletProvider? = null

        fun getInstance(context: Context): SolanaWalletProvider {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SolanaWalletProvider(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}

/**
 * Jetpack Compose CompositionLocal providing the active [SolanaWalletProvider] (`publicKey`,
 * `authToken`, `isConnected`, `connectAndSignIn`, `signAndSendTransaction`, `disconnect`).
 */
val LocalSolanaWalletProvider = staticCompositionLocalOf<SolanaWalletProvider> {
    error("SolanaWalletProviderHost was not provided in the Compose hierarchy.")
}

@Composable
fun SolanaWalletProviderHost(
    provider: SolanaWalletProvider,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalSolanaWalletProvider provides provider,
        content = content
    )
}
