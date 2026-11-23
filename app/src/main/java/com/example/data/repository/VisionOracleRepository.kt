package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.RandomizedTargetPrompt
import com.example.data.model.VisionVerificationResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "VisionOracleRepo"

data class SolanaChainSyncSnapshot(
    val confirmedSlot: Long,
    val balanceSol: Double? = null,
    val syncedAtMillis: Long = System.currentTimeMillis()
)

class VisionOracleRepository {

    private val verificationSchema = Schema.obj(
        properties = mapOf(
            "verified" to Schema.boolean(description = "True only if this is a genuine live photograph showing the requested target object or action"),
            "confidence" to Schema.double(description = "Confidence score between 0.0 and 1.0"),
            "livePhotoConfirmed" to Schema.boolean(description = "True if the photo appears captured live in the real world rather than from a monitor, phone screen, or printed picture"),
            "detectedObject" to Schema.string(description = "Short 2-4 word label of what is seen in the camera frame"),
            "reason" to Schema.string(description = "Concise one-sentence explanation of why verification passed or failed")
        )
    )

    private val targetRandomizerSchema = Schema.obj(
        properties = mapOf(
            "targetObject" to Schema.string(description = "Specific everyday 3D physical object matching the habit category, 2-5 words (e.g. 'Ceramic Coffee Mug' or 'Pen on Open Notebook')"),
            "visualCue" to Schema.string(description = "Short actionable 1-sentence instruction for photographing the object in good lighting"),
            "referenceImagePrompt" to Schema.string(description = "Minimalist Apple-style studio product photography prompt of the exact object on a clean light gray surface, no text"),
            "antiCheatRule" to Schema.string(description = "Short anti-spoof requirement (must be a real 3D physical object, not a screen photo)"),
            "presetAssetKey" to Schema.enumeration(
                values = listOf("mug", "notebook", "dumbbell", "outdoors", "custom"),
                description = "Pick 'mug' for mugs/cups/kitchen/morning wakeup drinkware, 'notebook' for notebooks/books/pens/study, 'dumbbell' for gym/fitness/bottles, 'outdoors' for grass/trees/sky/outdoors, or 'custom' for any custom or unique object"
            )
        )
    )

    private val verifyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = verificationSchema
                temperature = 0.1f
            }
        )
    }

    private val randomizerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = targetRandomizerSchema
                temperature = 0.9f
            }
        )
    }

    private val referenceImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val oracleKeyPair: KeyPair by lazy {
        try {
            KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        } catch (_: Exception) {
            KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Anti-Cheat AI Target Randomizer: dynamically selects a fresh everyday physical object
     * matching the chosen Habit Category while avoiding the user's last 7 verified objects.
     */
    suspend fun generateRandomizedTarget(
        categoryTitle: String,
        categoryContextHint: String,
        isCustomCategory: Boolean,
        recentVerifiedObjects: List<String>
    ): RandomizedTargetPrompt {
        val recentBlock = if (recentVerifiedObjects.isEmpty()) {
            "None yet."
        } else {
            recentVerifiedObjects.takeLast(7).joinToString(", ")
        }

        val prompt = buildString {
            append("You are Actake's anti-cheat morning habit target randomizer. ")
            append("The user staked money on the broad category: \"$categoryTitle\". ")
            if (categoryContextHint.isNotBlank()) {
                append("Category context: $categoryContextHint. ")
            }
            append("Recently verified items to AVOID repeating: $recentBlock. ")
            append("Pick ONE concrete, easy-to-find real-world physical everyday object matching this category ")
            append("that requires the user to get up and move rather than pre-stage items next to their pillow. ")
            if (isCustomCategory) {
                append("Because this is a Custom Category, set presetAssetKey to \"custom\" so a tailored studio reference preview can be generated. ")
            }
            append("Return strict JSON matching the schema.")
        }

        val response = try {
            withTimeout(45_000L) {
                randomizerModel.generateContent(prompt)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI target randomization timed out", e)
            throw IllegalStateException("AI target generator timed out. Tap Retry to roll a new target.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI target randomization failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to roll randomized habit target.", e)
        }

        val jsonText = response.text
            ?: throw IllegalStateException("AI target randomizer returned an empty response.")
        val json = JSONObject(jsonText)

        return RandomizedTargetPrompt(
            targetObject = json.optString("targetObject", "Ceramic Coffee Mug").ifBlank { "Ceramic Coffee Mug" },
            visualCue = json.optString("visualCue", "Hold the physical item clearly in bright lighting."),
            referenceImagePrompt = json.optString(
                "referenceImagePrompt",
                "Minimalist Apple-style studio product photo of a clean ceramic coffee mug on a neutral alabaster background, no text"
            ),
            antiCheatRule = json.optString(
                "antiCheatRule",
                "Must be a real 3D physical object in your environment, never a screen or printed photo."
            ),
            presetAssetKey = if (isCustomCategory) {
                "custom"
            } else {
                json.optString("presetAssetKey", "mug")
            }
        )
    }

    /**
     * Generates a clean Apple-style reference preview image at runtime for Custom Categories
     * using Firebase AI Logic (`gemini-nano-banana-2.1`). Fails visibly on error (no procedural mocks).
     */
    suspend fun generateReferencePreviewBitmap(referenceImagePrompt: String): Bitmap {
        val response = try {
            withTimeout(60_000L) {
                referenceImageModel.generateContent(
                    "$referenceImagePrompt. Clean minimalist Apple studio lighting, centered object, soft warm light gray background, no text, no watermark."
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Reference image generation timed out", e)
            throw IllegalStateException("Reference image generation timed out after 60s.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reference image generation failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Could not generate reference image.", e)
        }

        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()

        return imagePart?.image
            ?: throw IllegalStateException("Reference image model returned no image bytes.")
    }

    suspend fun verifyLivePhoto(
        bitmap: Bitmap,
        missionType: String,
        targetPrompt: String,
        poolId: String,
        userId: String,
        distanceMeters: Double? = null,
        maxRadiusMeters: Double? = null
    ): VisionVerificationResult {
        val startedAt = System.currentTimeMillis()

        // Step 1: GPS Geofence check if GEO_HUNT
        if (missionType == "GEO_HUNT" && distanceMeters != null && maxRadiusMeters != null) {
            if (distanceMeters > maxRadiusMeters) {
                return VisionVerificationResult(
                    verified = false,
                    confidence = 0.0,
                    reason = "Outside target geofence (${distanceMeters.toInt()}m away, need within ${maxRadiusMeters.toInt()}m).",
                    detectedObject = "Out of Zone",
                    livePhotoConfirmed = true,
                    proofHashHex = "",
                    oracleSignatureHex = "",
                    settlementMs = System.currentTimeMillis() - startedAt
                )
            }
        }

        // Scale down bitmap to stay well below inline payload limits and fast for Vision AI
        val scaledBitmap = scaleBitmapForVision(bitmap, maxDim = 960)

        val prompt = buildString {
            append("You are an anti-cheat accountability verifier for Actake. ")
            append("Inspect this live camera photo carefully. ")
            append("1) Confirm it is a live real-world photograph (reject photos of computer monitors, phone screens, or printed pictures). ")
            append("2) Confirm the AI-randomized target object/action is clearly visible: ")
            append(targetPrompt)
            append(". Mission type: ").append(missionType).append(". ")
            append("Return JSON with verified, confidence (0.0 to 1.0), livePhotoConfirmed, detectedObject, and reason.")
        }

        val response = try {
            withTimeout(60_000L) {
                verifyModel.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Vision AI verification timed out", e)
            throw IllegalStateException("Vision AI verification timed out after 60s. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Vision AI verification failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Vision AI verification failed.", e)
        }

        val jsonText = response.text
            ?: throw IllegalStateException("Vision AI returned an empty response.")

        val json = JSONObject(jsonText)
        val verified = json.optBoolean("verified", false)
        val confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
        val liveConfirmed = json.optBoolean("livePhotoConfirmed", false)
        val detectedObject = json.optString("detectedObject", "Scene")
        val reason = json.optString("reason", "Inspection complete.")

        val passed = verified && liveConfirmed && confidence >= 0.75

        val (proofHashHex, signatureHex) = if (passed) {
            signOracleAttestation(scaledBitmap, poolId, userId, startedAt)
        } else {
            "" to ""
        }

        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(120L)
        return VisionVerificationResult(
            verified = passed,
            confidence = confidence,
            reason = reason,
            detectedObject = detectedObject,
            livePhotoConfirmed = liveConfirmed,
            proofHashHex = proofHashHex,
            oracleSignatureHex = signatureHex,
            settlementMs = elapsed
        )
    }

    private fun scaleBitmapForVision(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source
        val ratio = maxDim.toFloat() / maxOf(width, height).toFloat()
        return Bitmap.createScaledBitmap(
            source,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun signOracleAttestation(
        bitmap: Bitmap,
        poolId: String,
        userId: String,
        timestampMs: Long
    ): Pair<String, String> {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(baos.toByteArray())
        digest.update(timestampMs.toString().toByteArray())
        val proofHash = digest.digest()
        val proofHashHex = proofHash.joinToString("") { "%02x".format(it) }

        val canonicalMessage = "$poolId|$userId|$timestampMs|$proofHashHex".toByteArray()
        val sigAlgo = if (oracleKeyPair.private.algorithm.equals("Ed25519", ignoreCase = true)) {
            "Ed25519"
        } else {
            "SHA256withECDSA"
        }
        val signatureBytes = Signature.getInstance(sigAlgo).apply {
            initSign(oracleKeyPair.private)
            update(canonicalMessage)
        }.sign()
        val signatureHex = signatureBytes.joinToString("") { "%02x".format(it) }
        return proofHashHex to signatureHex
    }

    /**
     * Queries live Solana Devnet JSON-RPC getBalance for any user-supplied Base58 wallet pubkey.
     */
    suspend fun fetchSolanaDevnetBalanceSol(walletAddress: String): Result<Double> =
        withContext(Dispatchers.IO) {
            try {
                val cleaned = walletAddress.trim()
                require(cleaned.length in 32..44) { "Invalid Solana Base58 wallet address." }
                val rpcPayload = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "getBalance")
                    put("params", JSONArray().put(cleaned))
                }
                val request = Request.Builder()
                    .url("https://api.devnet.solana.com")
                    .post(rpcPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Solana Devnet RPC returned HTTP ${resp.code}")
                    }
                    val body = resp.body?.string().orEmpty()
                    val json = JSONObject(body)
                    if (json.has("error")) {
                        val errMsg = json.getJSONObject("error").optString("message", "RPC error")
                        throw IllegalStateException(errMsg)
                    }
                    val lamports = json.getJSONObject("result").optLong("value", 0L)
                    Result.success(lamports / 1_000_000_000.0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Solana Devnet RPC balance lookup failed: ${e.message}")
                Result.failure(e)
            }
        }

    /**
     * Performs a live Solana Devnet JSON-RPC sync (getSlot + optional getBalance) for dashboard pull-to-refresh.
     */
    suspend fun syncSolanaChainSnapshot(walletAddress: String): Result<SolanaChainSyncSnapshot> =
        withContext(Dispatchers.IO) {
            try {
                val slotPayload = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "getSlot")
                    put("params", JSONArray().put(JSONObject().put("commitment", "confirmed")))
                }
                val slotReq = Request.Builder()
                    .url("https://api.devnet.solana.com")
                    .post(slotPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val confirmedSlot = httpClient.newCall(slotReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Solana RPC returned HTTP ${resp.code}")
                    }
                    val json = JSONObject(resp.body?.string().orEmpty())
                    if (json.has("error")) {
                        throw IllegalStateException(json.getJSONObject("error").optString("message", "RPC error"))
                    }
                    json.optLong("result", 0L)
                }

                val cleaned = walletAddress.trim()
                val balanceSol = if (cleaned.length in 32..44) {
                    fetchSolanaDevnetBalanceSol(cleaned).getOrNull()
                } else {
                    null
                }

                Result.success(
                    SolanaChainSyncSnapshot(
                        confirmedSlot = confirmedSlot,
                        balanceSol = balanceSol,
                        syncedAtMillis = System.currentTimeMillis()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Solana chain snapshot sync failed: ${e.message}")
                Result.failure(e)
            }
        }

    companion object {
        fun haversineMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val earthRadiusMeters = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadiusMeters * c
        }

        fun offsetCoordinate(
            baseLat: Double,
            baseLon: Double,
            northMeters: Double,
            eastMeters: Double
        ): Pair<Double, Double> {
            val dLat = northMeters / 111_111.0
            val dLon = eastMeters / (111_111.0 * cos(Math.toRadians(baseLat)).coerceAtLeast(0.01))
            return (baseLat + dLat) to (baseLon + dLon)
        }
    }
}
