const admin = require("firebase-admin");
const functions = require("firebase-functions");

admin.initializeApp();

exports.diagnostics = functions
  .region("us-central1")
  .https.onRequest(async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).send("Method not allowed");
      return;
    }

    const token = req.header("X-Firebase-AppCheck");
    if (!token) {
      res.status(401).send("Missing App Check token");
      return;
    }

    try {
      await admin.appCheck().verifyToken(token);
    } catch (err) {
      res.status(401).send("Invalid App Check token");
      return;
    }

    const payload = req.body || {};
    await admin.firestore().collection("diagnostics").add({
      ...payload,
      receivedAt: admin.firestore.FieldValue.serverTimestamp(),
      source: "sshpeaches-android"
    });

    res.status(204).send();
  });
