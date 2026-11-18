const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any OpenShorts collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("clip_jobs").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("ai_shorts").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("clip_jobs").doc("job_1").set({
      userId: ALICE_UID,
      sourceType: "video",
      sourceLabel: "demo.mp4",
      durationSec: 45.0,
      status: "completed",
      clipsCount: 1,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, update, and list ClipJobs and child GeneratedClips", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("clip_jobs").doc("job_1").set({
      userId: ALICE_UID,
      sourceType: "video",
      sourceLabel: "podcast_ep12.mp4",
      durationSec: 180.5,
      status: "completed",
      summary: "Great tech founders interview",
      clipsCount: 1,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("users").doc(ALICE_UID)
      .collection("clip_jobs").doc("job_1")
      .collection("clips").doc("clip_1")
      .set({
        userId: ALICE_UID,
        jobId: "job_1",
        clipIndex: 0,
        startSec: 12.5,
        endSec: 42.0,
        youtubeTitle: "Why 90% of Founders Miss This",
        tiktokCaption: "Save this for your next launch #startup",
        instagramCaption: "Save this for your next launch",
        viralHookText: "POV: You just found the bottleneck",
        hookPosition: "top",
        hookSize: "M",
        hookAnimation: "spring",
        hookDurationSec: 5,
        subtitlePosition: "bottom",
        subtitleAnimation: "pop",
        subtitleFont: "Plus Jakarta Sans",
        subtitleColorHex: "#FFFFFF",
        subtitleHighlightHex: "#FFDD00",
        subtitleText: "Most people think growth comes from ads...",
        viralScore: 92,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // Partial update on clip
  await assertSucceeds(
    aliceDb
      .collection("users").doc(ALICE_UID)
      .collection("clip_jobs").doc("job_1")
      .collection("clips").doc("clip_1")
      .update({
        viralHookText: "Stop making this startup mistake!",
        hookPosition: "center",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb
      .collection("users").doc(ALICE_UID)
      .collection("clip_jobs").doc("job_1")
      .update({
        isVerified: true,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb
      .collection("users").doc(ALICE_UID)
      .collection("clip_jobs").doc("job_1")
      .update({
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // List subcollections works cleanly
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("clip_jobs").get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("clip_jobs").doc("job_1").collection("clips").get()
  );
});

test("Cross-user access is denied across all user-scoped collections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_shorts").doc("short_1").set({
      userId: ALICE_UID,
      productName: "OpenShorts",
      videoMode: "lowcost",
      style: "ugc",
      language: "en",
      actorGender: "female",
      scriptTitle: "Stop Editing Manually",
      fullNarration: "I used to spend 4 hours cutting shorts...",
      hashtags: ["#shorts", "#ai"],
      durationSeconds: 30,
      estimatedCostUsd: 0.8,
      status: "completed",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_shorts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("ai_shorts").doc("short_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("ai_shorts").doc("short_1").update({
      scriptTitle: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
