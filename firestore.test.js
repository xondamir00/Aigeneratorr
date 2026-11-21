const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_folklore_001";
const BOB_UID = "bob_folklore_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function buildShortPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    folkloreTheme: "Forest Spirits",
    youtubeTitle: "Never Whistle After Midnight In The Pines #Shorts",
    youtubeDescription:
      "An ancient Appalachian folklore warning about the lantern at the crossroads.",
    hashtags: ["#Shorts", "#FolkloreHorror", "#ScaryStories", "#HorrorShorts"],
    scenes: [
      {
        narrationLine: "Never answer when the lantern calls your name.",
        subtitleText: "NEVER ANSWER THE LANTERN",
        imagePrompt: "Dark foggy forest crossroads with glowing red lantern",
        cameraMotion: "ZOOM_IN",
        durationMs: 3750,
      },
    ],
    status: "READY",
    durationSeconds: 15,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function buildSchedulePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    autoEnabled: true,
    publishHour: 19,
    publishMinute: 0,
    defaultFolkloreCategory: "Forest Spirits",
    autoUploadPrivacy: "private",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write shorts or schedules", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("shorts").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("shorts")
      .doc("short_1")
      .set(buildShortPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("schedule")
      .set(buildSchedulePayload(ALICE_UID))
  );
});

test("Owner can create, list, edit SEO, transition state, and delete shorts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const shortRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("shorts")
    .doc("short_001");

  await assertSucceeds(shortRef.set(buildShortPayload(ALICE_UID)));

  // List query matching Kotlin repository observeShorts()
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("shorts")
      .orderBy("createdAt", "desc")
      .get()
  );

  // Action 1: Edit YouTube SEO metadata
  await assertSucceeds(
    shortRef.update({
      youtubeTitle: "The Red Lantern Of Pine Hollow #Shorts",
      youtubeDescription: "Updated folklore tale for tonight's 7:00 drop.",
      hashtags: ["#Shorts", "#HorrorAI", "#FolkloreHorror"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Action 2: Transition to SCHEDULED with YouTube ID & 7:00 publishAt
  await assertSucceeds(
    shortRef.update({
      status: "SCHEDULED",
      youtubeVideoId: "yt_abc123XYZ",
      scheduledPublishAtIso: "2026-11-20T19:00:00Z",
      updatedAt: new Date(Date.now() - 250),
    })
  );

  // Transition to PUBLISHED (terminal state)
  await assertSucceeds(
    shortRef.update({
      status: "PUBLISHED",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Terminal state lock: PUBLISHED short cannot be updated further
  await assertFails(
    shortRef.update({
      youtubeTitle: "Tampered After Publish #Shorts",
      updatedAt: new Date(Date.now() - 50),
    })
  );

  // Owner can still delete from archive
  await assertSucceeds(shortRef.delete());
});

test("Rejects shadow fields, @DocumentId id field, future timestamps, and immutable createdAt tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const shortsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("shorts");

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    shortsCol.doc("short_bad_id").set(
      buildShortPayload(ALICE_UID, { id: "short_bad_id" })
    )
  );

  // Reject shadow/ghost field
  await assertFails(
    shortsCol.doc("short_ghost").set(
      buildShortPayload(ALICE_UID, { isVerified: true })
    )
  );

  // Reject future timestamp
  await assertFails(
    shortsCol.doc("short_future").set(
      buildShortPayload(ALICE_UID, {
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
    )
  );

  // Create valid doc, then attempt immutable createdAt change
  const validRef = shortsCol.doc("short_valid_2");
  await assertSucceeds(validRef.set(buildShortPayload(ALICE_UID)));
  await assertFails(
    validRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Cross-user isolation blocks Bob from accessing Alice's shorts or schedule", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("shorts")
      .doc("alice_short_1")
      .set(buildShortPayload(ALICE_UID));
    await ctx
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("schedule")
      .set(buildSchedulePayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("shorts").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("shorts")
      .doc("alice_short_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("shorts")
      .doc("alice_short_1")
      .update({ youtubeTitle: "Hijacked", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("settings")
      .doc("schedule")
      .get()
  );
});

test("Owner can create and update daily 7:00 automation schedule and YouTube channel metadata", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const schedRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("settings")
    .doc("schedule");

  await assertSucceeds(schedRef.set(buildSchedulePayload(ALICE_UID)));

  // Action 1: Toggle hour from 19:00 to 07:00
  await assertSucceeds(
    schedRef.update({
      publishHour: 7,
      publishMinute: 0,
      autoEnabled: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Action 2: Save connected YouTube channel & run log
  await assertSucceeds(
    schedRef.update({
      connectedChannelTitle: "Midnight Folklore US",
      connectedChannelId: "UC1234567890",
      lastRunStatus: "Scheduled for 07:00",
      lastRunAtIso: "2026-11-20T06:30:00Z",
      updatedAt: new Date(Date.now() - 250),
    })
  );

  // Reject out-of-bounds publishHour
  await assertFails(
    schedRef.update({
      publishHour: 25,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
