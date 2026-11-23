const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_seeker_123";
const BOB_UID = "bob_seeker_456";
const CHARLIE_UID = "charlie_seeker_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "Alex Seeker",
    walletAddress: "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU",
    streakCount: 43,
    usdcBalance: 142.5,
    skrBalance: 500.0,
    lockedBalance: 15.0,
    weekBitmap: 62,
    alarmSound: "Siren",
    strictnessEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validStake(uid, ts) {
  return {
    userId: uid,
    title: "Toothbrush Hunt",
    missionType: "OBJECT_SNAP",
    mode: "SOLO",
    targetPrompt: "A real toothbrush held in hand or in a bathroom cup",
    stakeAmount: 15.0,
    tokenSymbol: "USDC",
    skrBoosted: false,
    wakeTimeLabel: "06:00 AM",
    wakeHour: 6,
    wakeMinute: 0,
    durationDays: 7,
    completedDays: 0,
    alarmSound: "Siren",
    status: "ACTIVE",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validSquad(creatorUid, memberUids, ts) {
  return {
    creatorId: creatorUid,
    name: "7AM Campus Wake Club",
    wakeTimeLabel: "07:00 AM",
    missionTitle: "Toothbrush Hunt",
    stakeAmount: 10.0,
    tokenSymbol: "USDC",
    potAmount: 185.0,
    slashedAmount: 30.0,
    participantCount: 45,
    checkedInCount: 35,
    memberUids,
    joinable: true,
    status: "ACTIVE",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCheckin(squadId, userId, memberUids, ts) {
  return {
    squadId,
    userId,
    memberUids,
    userName: "Alex Seeker",
    missionTitle: "Toothbrush Scanned",
    verifiedTimeLabel: "06:00 AM",
    streakCount: 44,
    status: "VERIFIED",
    aiConfidence: 0.96,
    aiReason: "Live real-world photograph of toothbrush confirmed.",
    proofHash: "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
    oracleSignature: "ed25519_sig_aabbccddeeff00112233445566778899",
    payoutEarned: 18.4,
    thumbsCount: 0,
    fireCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID, ts))
  );
  await assertFails(unauthDb.collection("squads").get());
});

test("UserProfile: owner create, partial update, cross-user denial, and ghost field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID, ts))
  );

  // Partial update of alarm sound, strictness, actPoints, and recentVerifiedObjects succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      alarmSound: "Rooster",
      strictnessEnabled: false,
      actPoints: 200,
      recentVerifiedObjects: ["Ceramic Coffee Mug"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Custom category create/list by owner succeeds; cross-user read fails
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("categories")
      .doc("cat_lab")
      .set({
        userId: ALICE_UID,
        name: "Electronics Lab",
        contextHint: "Hardware tools and breadboards on my desk",
        iconKey: "custom",
        createdAt: ts,
        updatedAt: ts,
      })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("categories").doc("cat_lab").get()
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow/ghost field injection fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Tampered future timestamp fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      streakCount: 99,
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user read & write fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      streakCount: 0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User stakes subcollection: create, list, verify check-in, terminal lock, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Empty list query by owner succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("stakes").get());

  // Valid stake create (no @DocumentId `id` field)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stakes")
      .doc("stake_1")
      .set(validStake(ALICE_UID, ts))
  );

  // Reject extra `id` field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stakes")
      .doc("stake_2")
      .set({ ...validStake(ALICE_UID, ts), id: "stake_2" })
  );

  // Valid check-in update to COMPLETED with earnedAmount
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stakes")
      .doc("stake_1")
      .update({
        completedDays: 1,
        status: "COMPLETED",
        proofHash: "abc123def456",
        oracleSignature: "sig_789",
        earnedAmount: 18.4,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // History view query: owner querying COMPLETED stakes succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stakes")
      .where("status", "==", "COMPLETED")
      .get()
  );

  // Reject negative earnedAmount on create
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stakes")
      .doc("stake_neg")
      .set({ ...validStake(ALICE_UID, ts), earnedAmount: -5.0 })
  );

  // Terminal state lock: cannot update COMPLETED stake
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stakes")
      .doc("stake_1")
      .update({
        completedDays: 2,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Cross-user cannot list or read Alice's completed stakes
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("stakes")
      .where("status", "==", "COMPLETED")
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("stakes").doc("stake_1").get()
  );
});

test("Squads & Checkins: member list query, join action, check-in create, reactions, and non-member denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Alice creates a squad
  await assertSucceeds(
    aliceDb.collection("squads").doc("squad_wake_7am").set(validSquad(ALICE_UID, [ALICE_UID], ts))
  );

  // Filtered memberUids query succeeds; unfiltered query fails
  await assertSucceeds(
    aliceDb.collection("squads").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("squads").get());

  // Bob joins the joinable squad (valid Action 2)
  await assertSucceeds(
    bobDb.collection("squads").doc("squad_wake_7am").update({
      memberUids: [ALICE_UID, BOB_UID],
      participantCount: 46,
      potAmount: 195.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice posts a verified check-in to the squad
  await assertSucceeds(
    aliceDb
      .collection("squads")
      .doc("squad_wake_7am")
      .collection("checkins")
      .doc("checkin_1")
      .set(validCheckin("squad_wake_7am", ALICE_UID, [ALICE_UID, BOB_UID], ts))
  );

  // Bob (member) can query checkins with memberUids filter and react with fire
  await assertSucceeds(
    bobDb
      .collection("squads")
      .doc("squad_wake_7am")
      .collection("checkins")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );
  await assertSucceeds(
    bobDb
      .collection("squads")
      .doc("squad_wake_7am")
      .collection("checkins")
      .doc("checkin_1")
      .update({
        fireCount: 1,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Charlie (non-member) cannot read checkins or post a check-in
  await assertFails(
    charlieDb
      .collection("squads")
      .doc("squad_wake_7am")
      .collection("checkins")
      .doc("checkin_1")
      .get()
  );
  await assertFails(
    charlieDb
      .collection("squads")
      .doc("squad_wake_7am")
      .collection("checkins")
      .doc("checkin_spoof")
      .set(validCheckin("squad_wake_7am", CHARLIE_UID, [CHARLIE_UID], ts))
  );
});
