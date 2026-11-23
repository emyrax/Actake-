# Actake — Native Solana Seeker Social Accountability & AI-Verified Habit Protocol

**Stake USDC or SKR on your daily habits. When your alarm rings, AI pops up a randomized item image & description. Tap Verify Proof to open the live camera, prove you are pointing at that exact item, and claim your unlocked stake + slashed squad rewards directly in your Solana Seeker wallet.**

Actake is a lean, rock-solid native Android (**Kotlin + Jetpack Compose**) accountability protocol built specifically for the **Solana Seeker** mobile ecosystem. It combines the **Solana Mobile Stack (MWA 2.0 + Seeker Seed Vault SIWS)**, a **Custom Anchor Escrow (`actake_escrow`)**, **SPL Token / Token-2022 (USDC & 0%-fee SKR)**, **Solana Actions / Blinks**, and **Firebase AI Logic multimodal verification with runtime studio reference image generation** to turn daily alarms into verifiable onchain commitments.

---

## Table of Contents

1. [The Problem & How Actake Solves It](#1-the-problem--how-actake-solves-it)
2. [Lean Solana Protocol Stack](#2-lean-solana-protocol-stack)
3. [Feature-by-Feature Navigation Guide](#3-feature-by-feature-navigation-guide)
4. [Anti-Cheat Alarm Pop-Up & Live Camera Verification Flow](#4-anti-cheat-alarm-pop-up--live-camera-verification-flow)
5. [End-to-End User Flow](#5-end-to-end-user-flow)
6. [Data Persistence, Privacy & Zero-Trust Security](#6-data-persistence-privacy--zero-trust-security)
7. [Architecture, Tech Stack & Project Structure](#7-architecture-tech-stack--project-structure)
8. [Judge's 3-Minute Evaluation Walkthrough](#8-judges-3-minute-evaluation-walkthrough)
9. [Build & Setup Instructions](#9-build--setup-instructions)

---

## 1. The Problem & How Actake Solves It

Traditional habit trackers and alarm clocks fail because they have **zero real stakes** and are **trivial to cheat** (snoozing, verifying early before the alarm, or keeping a pre-staged object next to your pillow).

Actake eliminates all three failure modes natively on Android / Solana Seeker:

| Failure Mode | Actake Solution |
| --- | --- |
| **No real skin in the game** | Users lock **USDC (SPL Token)** or **SKR (Token-2022, 0% protocol fee)** inside the `actake_escrow` Anchor program via Solana Seeker's native Seed Vault / MWA 2.0 transaction signing bound to a live Solana Devnet blockhash. |
| **Early check-in & pre-staging** | The **Verify Proof** option is **disabled on the dashboard** until the scheduled alarm actually goes off. When the alarm rings, a non-dismissible **Alarm Challenge Pop-up** appears with a freshly randomized everyday object, a **runtime AI-generated studio reference image**, and clear capture instructions. Tapping **Verify Proof** redirects to the live CameraX scanner locked onto that exact item. |
| **Photo spoofing & onboarding friction** | **Zero email or password required.** First launch uses single-prompt **Sign-In With Solana (SIWS)** with persisted `authToken` silent re-authorization; Google Cloud Backup is strictly optional in Settings. CameraX blocks gallery uploads and Gemini Vision compares the live photo directly against the initially displayed AI reference image and description to reject screen/print spoofs. |

---

## 2. Lean Solana Protocol Stack

Actake keeps its onchain architecture focused strictly on four core Solana protocols—zero bloat or unnecessary middleware:

| Protocol / Standard | Role in Actake | Native Android Implementation |
| --- | --- | --- |
| **1. Solana Mobile Stack (MWA 2.0 + Seeker Connect)** | **Core Wallet & Auth (P0)** — Connects directly to the Solana Seeker phone's built-in hardware Seed Vault (falling back to installed Android MWA wallets like Phantom/Solflare). Handles single-prompt SIWS authentication, persistent `authToken` silent re-authorization, and biometric transaction signing. | `SolanaWalletProvider.kt` + `SolanaSeekerWalletManager.kt` + `AndroidManifest.xml` (`<queries>` for `solana-wallet://v1/associate/local`, `appIdentity = { name: "Actake", uri: "https://actake.app", icon: "favicon.ico" }`, Ed25519 Base58 keypair & SIWS `authToken` persistence). |
| **2. Custom Anchor Escrow (`actake_escrow`)** | **Core Staking & Payouts (P0)** — Holds locked USDC / SKR stakes in deterministic Escrow Pool PDAs, verifies the AI camera oracle's Ed25519 signature on check-in, and splits forfeited funds among verified winners. | `ActakeEscrowProgram.kt` implementing `joinPool` (`actake_escrow::join_pool`), `submitCheckin` (`actake_escrow::submit_checkin`), and `claimPayout` (`actake_escrow::claim_payout`), each bound to a fresh Solana Devnet `getLatestBlockhash` from `VisionOracleRepository.kt`. |
| **3. SPL Token / Token-2022 (USDC & SKR)** | **Money Layer (P0)** — Supports dollar-pegged **USDC** (`SPL Token`, mint `4zMMC9srt5Ri5X14GAgXhaHii3GnPAEERYPJgZJDncDU`) and native Solana Seeker **SKR** (`Token-2022`, mint `SKR1111111111111111111111111111111111111111`) with **0% protocol fee** perks. | `ActakeEscrowProgram.USDC_TOKEN` / `SKR_TOKEN` (`SplTokenConfig`) surfaced across `MissionsScreen.kt`, `CelebrationScreen.kt`, and `HistoryScreen.kt`. |
| **4. Solana Actions / Blinks (`@solana/actions`)** | **Viral Squad Invites (P1)** — Share a single Blink link (`https://dial.to/?action=solana-action:...`) in WhatsApp, Telegram, or X so friends tap once to stake and join a squad challenge. | `ActakeEscrowProgram.buildSquadBlinkUrl(...)` wired into `SquadsScreen.kt`, `CelebrationScreen.kt`, and `WalletAccountSheet.kt`. |

### Native Onchain Transaction Sequence
1. **Stake Lock (`actake_escrow::join_pool`)**: Fetches a live confirmed Solana Devnet `recentBlockhash`, derives the category's deterministic Escrow Pool PDA (`SHA-256("pool" || wallet || categoryId || ACTAKE_ESCROW_PROGRAM_ID)`), builds the versioned transaction payload for USDC (SPL Token) or SKR (Token-2022, 0% fee), and signs via `SolanaWalletProvider.signAndSendTransaction(...)`.
2. **Verified Check-In (`actake_escrow::submit_checkin`)**: Once Gemini Vision confirms the live CameraX photo matches the initially displayed alarm item (`confidence >= 0.80` + live 3D check), Actake binds the SHA-256 photo hash + Ed25519 oracle attestation signature + live `recentBlockhash` and signs `actake_escrow::submit_checkin`.
3. **Payout Claim (`actake_escrow::claim_payout`)**: On the Celebration screen, tapping **Redeem to Seeker Wallet** fetches a live `recentBlockhash` and signs `actake_escrow::claim_payout` to release principal + slashed squad bonus.

---

## 3. Feature-by-Feature Navigation Guide

Actake features a clean Material 3 floating navigation dock (**Home**, **Missions**, **Hunts**, **Squads**, **History**) plus dedicated full-screen flows for **Onboarding**, **Ringing Alarm Pop-Up & Live CameraX Verification**, **Payout Celebration**, and **Alarm Sound & Strictness Settings**.

### 1. Seeker-First Onboarding (`OnboardingScreen.kt`)
- **One-Swipe SIWS Connect**: Swipe **"Swipe to Connect Seeker >>>"** to call `SolanaWalletProvider.connectAndSignIn()`, authenticating via Sign-In With Solana in one prompt.
- **Silent `authToken` Re-Authorization**: Persists `authToken` and `publicKey` locally so subsequent app launches reconnect silently without prompting again.
- **No Mandatory Google Wall**: Google Sign-In is strictly optional inside Settings / Wallet Account Sheet.

### 2. Main Habit Dashboard (`HomeScreen.kt` + `MainDashboard.kt` + `WalletAccountSheet.kt`)
- **Active Habit Stakes Feed**: Lists all active habit stakes sorted by **soonest upcoming deadline**, with live auto-refreshing countdowns (`tnum` tabular numerals), urgency highlights, locked stake amounts (`USDC` / `SKR`), and completion progress bars.
- **Verify Proof Locked Until Alarm**: Both the **Next Stake** hero card and every active stake row display **Verify Proof** in a **disabled, locked state** (`Opens when alarm rings` / `Unlocks at <time> alarm`) so users cannot bypass their scheduled alarm.
- **Pull-to-Refresh Live Solana Sync**: Material 3 `PullToRefreshBox` queries live **Solana Devnet JSON-RPC** (`getSlot` + `getBalance`) to refresh confirmed slot and SOL balance in place.
- **Wallet & Account Sheet**: Inspect Base58 Seeker address, live Devnet SOL balance, share a **Solana Blink** squad invite, link **Optional Google Cloud Backup**, or disconnect wallet.

### 3. Anti-Cheat Category Staking & Custom Deadline Clock (`MissionsScreen.kt` + `DeadlineClockPickerDialog.kt`)
- **Category-Only Selection**: Choose from built-in habit categories (**Morning Wakeup**, **Reading & Study**, **Gym & Fitness**, **Outdoors & Grass**, **Hydration & Kitchen**) or create unlimited **Custom Categories** with a custom environment hint.
- **Custom Clock Time Picker**: Tap **Pick Clock** to select any exact hour and minute using a Material 3 `TimePicker` dial (with 1-tap switch to numeric keyboard input) alongside quick morning presets.
- **Anchor Escrow & SPL Token Breakdown**: Displays your connected Seeker wallet / Seed Vault status, **USDC (SPL Token)** vs **SKR (Token-2022 · 0% Protocol Fee)** toggle, optional native MWA wallet launcher, and **`Pay & Lock $X via Seeker Wallet`**.

### 4. Ringing Alarm Pop-Up → Live CameraX Verification (`AlarmChallengePopupDialog.kt` + `ActiveAlarmScreen.kt`)
- **Automatic Alarm Trigger**: Both Android `AlarmManager` / `AlarmReceiver` (handling cold launch and `onNewIntent`) and an in-app foreground clock watcher fire automatically the moment any active stake's scheduled alarm minute arrives.
- **Alarm Challenge Pop-Up**: A non-dismissible pop-up appears over the app while the loud alarm tone rings, displaying:
  - A freshly randomized everyday household item from `OBJECT_POOL` (avoiding the last 7 verified items)
  - A **runtime AI-generated studio reference image** (`gemini-nano-banana-2.1`)
  - A clear **capture description** (`visualCue`) and anti-cheat rule
  - A primary **`Verify Proof`** button
- **Redirect to Locked Live Camera**: Clicking **`Verify Proof`** in the pop-up redirects straight to `ActiveAlarmScreen` (CameraX live viewfinder) **locked onto the exact item initially displayed in the pop-up** (no re-rolling away, no gallery uploads).
- **Retake & Confirm + Controlled Back**: Captured photos enter an explicit **`Retake`** / **`Confirm & Verify`** review step. Hardware/gesture back is locked during active alarms, while a dedicated top-left circular **Back** button exits safely (with Strict Mode confirmation guard).

### 5. Payout Celebration, Geo-Hunts, Squads & History
- **Celebration (`CelebrationScreen.kt`)**: Displays verified target, streak badge, `$ACT` points, `actake_escrow::submit_checkin` signature, SHA-256 / Ed25519 proof hash, 1-tap **`Redeem to Seeker Wallet`** (`actake_escrow::claim_payout`), and **Share Proof Blink**.
- **GPS Geo-Hunts (`HuntsScreen.kt`)**: Real-time `FusedLocationProviderClient` + Haversine geofence distance verification paired with live photo proof.
- **Accountability Squads (`SquadsScreen.kt`)**: Shared habit pools with custom clock deadlines, live check-in feeds, emoji reactions, and single-tap **Solana Blinks** invite sharing.
- **Stake History (`HistoryScreen.kt`)**: Permanent payout ledger of completed habit stakes with date, habit title, token (`USDC` / `SKR`), and amount earned.

---

## 4. Anti-Cheat Alarm Pop-Up & Live Camera Verification Flow

Actake uses **Firebase AI Logic** (`com.google.firebase.ai`) protected by **Firebase App Check** (no embedded API keys).

```text
Scheduled Alarm Time Arrives (AlarmReceiver or Foreground Clock Watcher)
        │
        ▼
[1] Alarm Challenge Pop-Up Appears (AlarmChallengePopupDialog) + Loud Alarm Rings
    • gemini-3.8-flash samples a randomized everyday item avoiding the last 7 verified targets
      and generates a clear description + anti-cheat rule
    • gemini-nano-banana-2.1 synthesizes a fresh studio reference image at runtime
    • Pop-up displays BOTH the AI-generated image and description
        │
        ▼  (User taps "Verify Proof" inside the Pop-Up)
[2] Redirect to Live CameraX Scanner (ActiveAlarmScreen)
    • Locked onto the exact item initially displayed in the pop-up
    • Live hardware capture only (gallery blocked) → Retake or Confirm & Verify
        │
        ▼
[3] Multimodal Vision Verification (gemini-3.8-flash)
    • Receives BOTH Image 1 (initially displayed AI reference image) & Image 2 (live camera frame)
    • Confirms Image 2 is a real 3D physical photo pointing at the exact displayed item
      (rejects screens, screenshots, prints; requires verified && liveConfirmed && confidence >= 0.80)
        │
        ▼
[4] Anchor Escrow Settlement & Alarm Silence
    • Signs SHA-256 photo hash + Ed25519 oracle attestation
    • Executes actake_escrow::submit_checkin bound to live Solana Devnet recentBlockhash
    • Silences alarm → Celebration screen → 1-tap actake_escrow::claim_payout
```

---

## 5. End-to-End User Flow

```text
1. ONBOARDING (Seeker Mobile Stack)
   Swipe "Connect Seeker" → Single-prompt SIWS → authToken + publicKey persisted
        │
        ▼
2. MAIN DASHBOARD (Home)
   • View active stakes sorted by soonest deadline + live countdowns
   • Pull-to-refresh live Solana Devnet slot & SOL balance
   • "Verify Proof" buttons remain disabled/locked until each stake's alarm rings
        │
        ▼
3. CREATE STAKE (Missions)
   • Pick Habit Category (or create Custom Category) + pick exact time on Clock Picker
   • Choose USDC (SPL Token) or SKR (Token-2022 · 0% Protocol Fee)
   • Tap "Pay & Lock" → live getLatestBlockhash → actake_escrow::join_pool signed via Seed Vault
        │
        ▼
4. ALARM GOES OFF → POP-UP → LIVE CAM VERIFICATION
   • Pop-up appears showing AI-generated image + description of randomized item
   • User clicks "Verify Proof" → redirected to live CameraX scanner
   • Live photo verified against initially displayed image & description (confidence >= 80%)
        │
        ▼
5. CELEBRATION, PAYOUT CLAIM & HISTORY
   • actake_escrow::submit_checkin recorded; alarm silences; +$ACT points & streak updated
   • Tap "Redeem to Seeker Wallet" → actake_escrow::claim_payout signed
   • Share Solana Blink invite & inspect payout in History Ledger
```

---

## 6. Data Persistence, Privacy & Zero-Trust Security

- **Seeker Wallet-Only Mode (Default — Zero OAuth Required)**: Backed by `LocalActakeStore.kt` scoped to the user's Solana public key. All stakes, custom categories, history, streaks, and alarm preferences work immediately without Google Sign-In.
- **Optional Google Cloud Backup (`Wallet Sheet → Account Linking`)**: Powered by **Jetpack Credential Manager** (`GetSignInWithGoogleOption`), **Firebase Auth**, and **Cloud Firestore** (`ActakeRepository.kt`), protected by zero-trust `firestore.rules` (default deny-all, strict owner-only access, field whitelists, zero anonymous auth).

---

## 7. Architecture, Tech Stack & Project Structure

### Tech Stack
- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3 (`PullToRefreshBox`, `TimePicker`, `ModalBottomSheet`, Edge-to-Edge `WindowInsets`)
- **Architecture**: MVVM + Unidirectional StateFlow (`ActakeViewModel`, `SolanaWalletProviderHost` CompositionLocal)
- **Solana Stack**: Solana Mobile Stack (MWA 2.0 `solana-wallet://` + Seed Vault SIWS `authToken`), Custom Anchor Escrow (`actake_escrow`), SPL Token / Token-2022 (`USDC` & `SKR`), Solana Actions / Blinks, OkHttp Devnet JSON-RPC (`api.devnet.solana.com`)
- **Camera, Alarms & GPS**: AndroidX CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`), `AlarmManager`, `AlarmAudioPlayer`, `FusedLocationProviderClient`
- **AI Verification**: Firebase AI Logic (`gemini-3.8-flash` + `gemini-nano-banana-2.1`) + Firebase App Check

### Project Structure

```text
app/src/main/
├── AndroidManifest.xml                  # Permissions (CAMERA, LOCATION, ALARM) & MWA 2.0 <queries>
└── java/com/example/
    ├── MainActivity.kt                  # Entry point, onNewIntent & clock alarm watcher, NavHost, Alarm Pop-Up
    ├── solana/
    │   ├── SolanaWalletProvider.kt      # SeekerWalletConnector: publicKey, authToken, connectAndSignIn, signAndSendTransaction
    │   ├── SolanaSeekerWalletManager.kt # Seed Vault Ed25519 SIWS, MWA 2.0 URI/launcher, authToken persistence, Escrow PDA
    │   └── ActakeEscrowProgram.kt       # actake_escrow (join_pool, submit_checkin, claim_payout), SPL/Token-2022 & Blinks
    ├── alarm/
    │   ├── AlarmAudioPlayer.kt          # High-priority alarm audio loop & tone previews
    │   └── AlarmScheduler.kt            # Per-stake exact daily alarms & single-top AlarmReceiver delivery
    ├── data/
    │   ├── model/ActakeModels.kt        # StakeCommitment, CustomCategory, RandomizedTargetPrompt, CelebrationReceipt
    │   └── repository/
    │       ├── ActakeDataSource.kt      # Unified storage contract (Local Seeker vs. Optional Firestore Backup)
    │       ├── LocalActakeStore.kt      # Seeker wallet-first local persistence
    │       ├── ActakeRepository.kt      # Optional Google-authenticated Firestore sync
    │       └── VisionOracleRepository.kt# Gemini 3.8 Flash randomizer/dual-image verifier, Nano Banana 2.1 images, Devnet RPC
    ├── firebase/                        # AppCheckHelper, GoogleAuthHelper (optional), FirestoreErrorHandler
    ├── viewmodel/ActakeViewModel.kt     # Central state machine for wallet, escrow, alarm pop-up, AI & verification
    └── ui/
        ├── theme/                       # Material 3 ColorScheme, Typography (tnum), Shapes
        ├── components/
        │   ├── MainDashboard.kt         # Active stakes dashboard, live countdowns, PullToRefreshBox & locked Verify Proof
        │   ├── AlarmChallengePopupDialog.kt # Non-dismissible ringing alarm pop-up with AI image, description & Verify Proof CTA
        │   ├── DeadlineClockPickerDialog.kt # Material 3 Clock Dial + Keyboard TimePicker
        │   ├── WalletAccountSheet.kt    # Seeker wallet inspector, Devnet SOL, Solana Blink share & optional Google backup
        │   └── ActakeComponents.kt      # Shared tactile M3 pills, icons, cards & floating navigation dock
        └── screens/
            ├── OnboardingScreen.kt      # Swipe-to-connect Solana Seeker SIWS onboarding
            ├── HomeScreen.kt            # Dashboard host, weekly streak strip, locked Next Stake card & Squad orb
            ├── MissionsScreen.kt        # Habit categories, custom categories & Anchor Escrow / SPL Token stake sheet
            ├── ActiveAlarmScreen.kt     # Live CameraX scanner locked to displayed alarm item, Retake/Confirm & Back guard
            ├── CelebrationScreen.kt     # Proof receipt, actake_escrow::claim_payout & Solana Blink share
            ├── HuntsScreen.kt           # Live GPS Haversine Geo-Hunts
            ├── SquadsScreen.kt          # Accountability squads, live check-in feed & Solana Blinks
            ├── HistoryScreen.kt         # Completed habit stakes payout ledger
            └── AlarmSettingsScreen.kt   # Alarm ringtone auditioner & Strict Mode controls
```

---

## 8. Judge's 3-Minute Evaluation Walkthrough

1. **One-Swipe Solana Seeker SIWS Onboarding (15s)**
   - Launch Actake and swipe **"Swipe to Connect Seeker >>>"** — no Google login wall required.
2. **Pull-to-Refresh Solana Devnet Sync & Locked Dashboard (20s)**
   - Pull down on **Home** to sync confirmed Solana Devnet slot & SOL balance.
   - Notice **Verify Proof** is disabled/locked on all dashboard stake cards until the alarm goes off.
3. **Lock a Stake via Custom Clock + `actake_escrow::join_pool` (45s)**
   - Open **Missions**, pick a category (or create a Custom Category), tap **Pick Clock** and set the deadline **1 minute ahead** of current time.
   - Toggle between **USDC (SPL Token)** and **SKR (Token-2022 · 0% Protocol Fee)**, then tap **`Pay & Lock`** to sign `actake_escrow::join_pool`.
4. **Alarm Goes Off → AI Image & Description Pop-Up (45s)**
   - When the clock hits your scheduled minute, the **Alarm Challenge Pop-up** appears automatically while the alarm rings, generating and displaying a randomized item's **AI studio image + description**.
   - Click **`Verify Proof`** in the pop-up to redirect directly to the live CameraX viewfinder.
5. **Live Camera Verification Against Displayed Item (35s)**
   - Point the live camera at the exact displayed item, capture a photo, test **`Retake`**, then tap **`Confirm & Verify`**.
   - Gemini Vision compares your live photo against the initially displayed reference image and description, signs `actake_escrow::submit_checkin`, silences the alarm, and transitions to **Celebration**.
6. **Claim Payout & Share Solana Blink (20s)**
   - Tap **`Redeem to Seeker Wallet`** (`actake_escrow::claim_payout`), share a **Solana Blink**, and inspect the settled payout in **History**.

---

## 9. Build & Setup Instructions

### Prerequisites
- **Android Studio** Ladybug+ (JDK 11+, Android SDK 36, `minSdk 24`)
- Firebase project configured with **Firebase AI Logic**, **App Check**, and optional **Firestore + Google Auth** (`app/google-services.json`)
- Physical **Solana Seeker** device or Android Emulator (optionally with Solana Mobile's official MWA `fakewallet` APK installed for testing external MWA intent bridging)

### Build & Test Commands
```bash
# Assemble debug APK
gradle :app:assembleDebug

# Run local JVM unit tests
gradle :app:testDebugUnitTest
```

### Android Manifest Permissions (Least-Privilege)
- `INTERNET` — Solana Devnet JSON-RPC & Firebase AI Logic
- `CAMERA` — Hardware-only live CameraX verification (zero gallery/broad storage permissions)
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS Geo-Hunt Haversine distance checks
- `SCHEDULE_EXACT_ALARM` / `VIBRATE` / `POST_NOTIFICATIONS` — Exact habit alarm delivery
- `<queries>` (`solana-wallet://`) — Android 11–14+ package visibility for Solana Seeker Seed Vault & MWA 2.0 wallets
