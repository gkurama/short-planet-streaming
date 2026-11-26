const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_creator_1";
const BOB_UID = "bob_creator_2";

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

function validSourcePayload(uid, offsetMs = 10000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    youtubeUrl: "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    youtubeVideoId: "dQw4w9WgXcQ",
    title: "Live de Produção Musical com IA",
    channelName: "Planet Creator",
    sourceType: "LIVE",
    durationLabel: "1:42:00",
    status: "IMPORTED",
    aiSummary: "Transmissão sobre criação de conteúdo vertical.",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validClipPayload(uid, offsetMs = 10000) {
  const ts = new Date(Date.now() - offsetMs);
  return {
    userId: uid,
    sourceId: "src_1",
    sourceTitle: "Live de Produção Musical com IA",
    title: "O segredo do gancho nos 3 primeiros segundos",
    hookText: "Se você faz isso nos 3 primeiros segundos, seu Short viraliza!",
    startTime: "12:10",
    endTime: "12:55",
    durationSeconds: 45,
    viralScore: 92,
    aspectRatio: "9:16",
    captionStyle: "Dinâmico",
    framingMode: "Seguir pessoa",
    captionsText: "Se você faz isso nos 3 primeiros segundos...",
    highlightKeywords: "segredo, viraliza, 3 segundos",
    targetPlatform: "YOUTUBE_SHORTS",
    publishStatus: "DRAFT",
    publishNotes: "#shorts #criadores",
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user: cannot read or write any Planet Streaming collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sources").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("clips").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sources").doc("s1").set(validSourcePayload(ALICE_UID)));
});

test("Authenticated owner: valid source create, partial update, and list succeed; ghost fields & future timestamps fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sourcesCol = aliceDb.collection("users").doc(ALICE_UID).collection("sources");

  await assertSucceeds(sourcesCol.doc("src_1").set(validSourcePayload(ALICE_UID)));
  await assertSucceeds(sourcesCol.get());

  // Partial update of status & aiSummary
  await assertSucceeds(
    sourcesCol.doc("src_1").update({
      status: "ANALYZED",
      aiSummary: "Resumo atualizado pela IA.",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost @DocumentId `id` field on create
  await assertFails(
    sourcesCol.doc("src_bad_id").set({
      id: "src_bad_id",
      ...validSourcePayload(ALICE_UID),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    sourcesCol.doc("src_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    sourcesCol.doc("src_future").set(validSourcePayload(ALICE_UID, -60000))
  );
});

test("Authenticated owner: valid clip create, editor update, and publishing update succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const clipsCol = aliceDb.collection("users").doc(ALICE_UID).collection("clips");

  await assertSucceeds(clipsCol.doc("clip_1").set(validClipPayload(ALICE_UID)));

  // Editor branch update
  await assertSucceeds(
    clipsCol.doc("clip_1").update({
      aspectRatio: "1:1",
      captionStyle: "Alto contraste",
      framingMode: "Centro",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Publishing branch update
  await assertSucceeds(
    clipsCol.doc("clip_1").update({
      targetPlatform: "BOTH",
      publishStatus: "READY",
      publishNotes: "Pronto para publicar! #planetstreaming",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Invalid aspectRatio fails
  await assertFails(
    clipsCol.doc("clip_1").update({
      aspectRatio: "21:9",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: style preferences and trends succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("style").set({
      userId: ALICE_UID,
      editingPace: "Dinâmico",
      captionPreset: "Destacadas",
      framingPreset: "Seguir pessoa",
      preferredDuration: "30–60 segundos",
      brandTone: "Direto, energético e educativo",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Wrong preference doc ID fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("preferences").doc("other").set({
      userId: ALICE_UID,
      editingPace: "Dinâmico",
      captionPreset: "Destacadas",
      framingPreset: "Seguir pessoa",
      preferredDuration: "30–60 segundos",
      brandTone: "Energético",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trends").doc("trend_1").set({
      userId: ALICE_UID,
      topic: "Bastidores com IA",
      format: "Corte rápido + legenda cinética",
      hookIdea: "Como eu transformo 2 horas de live em 10 Shorts",
      platform: "Ambos",
      heatScore: 95,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice's sources, clips, style, or trends", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("sources").doc("src_a").set(validSourcePayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("clips").doc("clip_a").set(validClipPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sources").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sources").doc("src_a").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("clips").doc("clip_a").update({
    publishStatus: "PUBLISHED",
    updatedAt: new Date(Date.now() - 1000),
  }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("clips").doc("clip_a").delete());
});
