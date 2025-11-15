import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();

/**
 * Cloud Function to create an invitation
 * Called by the inviter to create a new invitation document
 */
export const createInvitation = functions.https.onCall(async (data, context) => {
  // Ensure user is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to create invitations"
    );
  }

  const {sharedAccountId, inviteeEmail, token, expiresAt} = data;

  // Validate required fields
  if (!sharedAccountId || !inviteeEmail || !token || !expiresAt) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required fields: sharedAccountId, inviteeEmail, token, expiresAt"
    );
  }

  const inviterUid = context.auth.uid;

  try {
    // Verify the inviter is an owner of the shared account
    const sharedAccountDoc = await db
      .collection("sharedAccounts")
      .doc(sharedAccountId)
      .get();

    if (!sharedAccountDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Shared account not found"
      );
    }

    const sharedAccountData = sharedAccountDoc.data();
    const owners = sharedAccountData?.owners || [];

    if (!owners.includes(inviterUid)) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "User is not an owner of this shared account"
      );
    }

    // Create invitation document
    const invitationRef = db.collection("invitations").doc();
    const invitationData = {
      id: invitationRef.id,
      sharedAccountId,
      inviterUid,
      inviteeEmail,
      token,
      status: "PENDING",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      expiresAt: admin.firestore.Timestamp.fromMillis(expiresAt),
      acceptedAt: null,
      acceptedBy: null,
    };

    await invitationRef.set(invitationData);

    // Optionally send email notification
    // This would require setting up Firebase Extensions or a custom email service
    // For now, we'll just log it
    functions.logger.info(
      `Invitation created: ${invitationRef.id} for ${inviteeEmail}`
    );

    return {
      success: true,
      invitationId: invitationRef.id,
      message: "Invitation created successfully",
    };
  } catch (error) {
    functions.logger.error("Error creating invitation:", error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      "internal",
      "Failed to create invitation"
    );
  }
});

/**
 * Cloud Function to accept an invitation
 * Called by the invitee to accept an invitation and join the shared account
 */
export const acceptInvitation = functions.https.onCall(async (data, context) => {
  // Ensure user is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to accept invitations"
    );
  }

  const {token} = data;

  if (!token) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required field: token"
    );
  }

  const acceptedByUid = context.auth.uid;
  const acceptedByEmail = context.auth.token.email;

  try {
    // Find invitation by token
    const invitationsSnapshot = await db
      .collection("invitations")
      .where("token", "==", token)
      .limit(1)
      .get();

    if (invitationsSnapshot.empty) {
      throw new functions.https.HttpsError(
        "not-found",
        "Invitation not found"
      );
    }

    const invitationDoc = invitationsSnapshot.docs[0];
    const invitation = invitationDoc.data();

    // Validate invitation status
    if (invitation.status !== "PENDING") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        `Invitation is not pending (status: ${invitation.status})`
      );
    }

    // Validate expiration
    const now = admin.firestore.Timestamp.now();
    if (invitation.expiresAt.toMillis() < now.toMillis()) {
      // Mark as expired
      await invitationDoc.ref.update({
        status: "EXPIRED",
      });
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Invitation has expired"
      );
    }

    // Validate email match (optional - if you want strict email validation)
    if (acceptedByEmail && invitation.inviteeEmail !== acceptedByEmail) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Invitation email does not match authenticated user"
      );
    }

    // Use a transaction to ensure atomicity
    await db.runTransaction(async (transaction) => {
      // Update invitation status
      transaction.update(invitationDoc.ref, {
        status: "ACCEPTED",
        acceptedAt: admin.firestore.FieldValue.serverTimestamp(),
        acceptedBy: acceptedByUid,
      });

      // Add user to shared account owners
      const sharedAccountRef = db
        .collection("sharedAccounts")
        .doc(invitation.sharedAccountId);

      const sharedAccountDoc = await transaction.get(sharedAccountRef);
      if (!sharedAccountDoc.exists) {
        throw new functions.https.HttpsError(
          "not-found",
          "Shared account not found"
        );
      }

      const sharedAccountData = sharedAccountDoc.data();
      const owners = sharedAccountData?.owners || [];

      // Only add if not already an owner
      if (!owners.includes(acceptedByUid)) {
        transaction.update(sharedAccountRef, {
          owners: admin.firestore.FieldValue.arrayUnion(acceptedByUid),
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      }
    });

    functions.logger.info(
      `Invitation ${invitationDoc.id} accepted by ${acceptedByUid}`
    );

    return {
      success: true,
      sharedAccountId: invitation.sharedAccountId,
      message: "Invitation accepted successfully",
    };
  } catch (error) {
    functions.logger.error("Error accepting invitation:", error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      "internal",
      "Failed to accept invitation"
    );
  }
});

/**
 * Cloud Function to revoke an invitation
 * Called by the inviter to revoke a pending invitation
 */
export const revokeInvitation = functions.https.onCall(async (data, context) => {
  // Ensure user is authenticated
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "User must be authenticated to revoke invitations"
    );
  }

  const {invitationId} = data;

  if (!invitationId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required field: invitationId"
    );
  }

  const revokerUid = context.auth.uid;

  try {
    const invitationRef = db.collection("invitations").doc(invitationId);
    const invitationDoc = await invitationRef.get();

    if (!invitationDoc.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Invitation not found"
      );
    }

    const invitation = invitationDoc.data();

    // Validate that only the inviter can revoke
    if (invitation?.inviterUid !== revokerUid) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Only the invitation creator can revoke it"
      );
    }

    // Validate status
    if (invitation.status !== "PENDING") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        `Can only revoke pending invitations (current status: ${invitation.status})`
      );
    }

    // Update status to REVOKED
    await invitationRef.update({
      status: "REVOKED",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    functions.logger.info(`Invitation ${invitationId} revoked by ${revokerUid}`);

    return {
      success: true,
      message: "Invitation revoked successfully",
    };
  } catch (error) {
    functions.logger.error("Error revoking invitation:", error);
    if (error instanceof functions.https.HttpsError) {
      throw error;
    }
    throw new functions.https.HttpsError(
      "internal",
      "Failed to revoke invitation"
    );
  }
});

/**
 * Scheduled function to expire old invitations
 * Runs daily to mark expired invitations
 */
export const expireOldInvitations = functions.pubsub
  .schedule("every 24 hours")
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();

    try {
      // Find all pending invitations that have expired
      const expiredInvitations = await db
        .collection("invitations")
        .where("status", "==", "PENDING")
        .where("expiresAt", "<", now)
        .get();

      if (expiredInvitations.empty) {
        functions.logger.info("No expired invitations found");
        return null;
      }

      // Batch update expired invitations
      const batch = db.batch();
      expiredInvitations.docs.forEach((doc) => {
        batch.update(doc.ref, {
          status: "EXPIRED",
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
      });

      await batch.commit();

      functions.logger.info(
        `Expired ${expiredInvitations.size} invitations`
      );

      return null;
    } catch (error) {
      functions.logger.error("Error expiring invitations:", error);
      throw error;
    }
  });
