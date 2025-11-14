Goal: Implement Shared Expenses Between Two Users

You are an autonomous software-engineering agent assisting in the development of a fork of Ivy Wallet (https://github.com/Ivy-Apps/ivy-wallet
).
The purpose of this fork is to add shared expense accounts so that two users (e.g., a couple) can see, add, and manage expenses together.

High-Level Objectives

Add a "Shared Account" feature that allows two (later more) users to collaboratively track expenses.

Each user can add expenses, edit them, or delete them — updates must sync to the other user.

Shared expenses should appear in a dedicated UI section: Shared Accounts.

Notifications should be triggered when a partner adds a new expense.

Preserve the privacy and simplicity of Ivy Wallet’s original design.

Specific Functional Requirements
Shared Account Creation

A user can create a shared account and invite another user via:

link

email

QR code

Accepting an invite binds two accounts under a single shared ledger.

Add / Edit / Delete Shared Expenses

Any partner can add an expense to a shared account.

Edits or deletions must sync in real time (or near real time).

Both partners see identical transaction histories.

Synchronization

Use a secure backend synchronization layer (Firebase or a minimal custom backend).

Must work across Android devices.

Local-first UX is still preferred; sync should gracefully merge state.

UI/UX Requirements

Add a new section: Shared Accounts beneath existing accounts.

Show:

Account balance

List of shared transactions

Indicator showing which partner created each expense

Provide a toggle or filter for:

"My expenses"

"Partner expenses"

"All"

Notifications

When one partner adds an expense, the other receives a lightweight notification (push or in-app).

Security

End-to-end data encryption for shared ledgers is strongly preferred if possible.

Only authorized users should access shared account data.

Technical Constraints and Considerations

The existing Ivy Wallet tech stack (Kotlin + Android + Room + possibly Firebase) must be respected.

Avoid breaking existing offline-first behavior.

Reuse Ivy’s architecture patterns (MVVM, repositories, local storage with sync layer).

Write clean, documented code following Kotlin/Jetpack best practices.

Maintain compatibility with existing budget, categories, and reports.

Agent Behaviors

As an agent working with this CLAUDE.md:

You MUST

Understand the existing Ivy Wallet architecture before proposing or generating code.

Propose incremental, mergeable changes (PR-style commits).

Provide runnable code, including:

new models

database schema updates

repository changes

sync layer logic

UI screens, fragments, and composables

dependency additions

Explain migration steps when updating database schemas.

Generate tests for critical logic.

You SHOULD

Suggest architectural improvements only if needed for shared accounts.

Keep the project lightweight — avoid unnecessary complexity.

Provide fallback behavior if the partner is offline.

You MUST NOT

Break existing features.

Introduce heavy external dependencies unless strictly necessary.

Modify licensing terms or remove attributions.

Development Roadmap Outline

The following steps guide the agent’s development process:

Review and map current Ivy Wallet architecture

Design data models for shared accounts

Implement backend or Firebase sync layer

Integrate shared transactions into local database

Build UI for shared accounts list

Add transaction creation flow (shared vs personal)

Real-time updates + conflict resolution

Notifications

Testing & migration scripts

Polish, performance tuning, documentation

Style & Output Requirements

Output should be clear, actionable, and production-ready.

When generating code, follow project directory conventions.

Provide explanations only when helpful; avoid unnecessary verbosity.

Use markdown formatting for clarity.

Primary Success Criteria

A functional, polished prototype where:

You and your wife can create a shared account,

Both of you can add expenses,

Each sees the other’s additions/edits in real time,

The UX feels identical in quality to the native Ivy Wallet experience.


## FIREBASE
This is a recommendation for the backend.
Firebase (recommended for speed / low ops)
Components

Firebase Authentication (email link/passwordless or email+password) for user identity and secure invites.
Firebase
+1

Cloud Firestore for shared-account documents and transaction sync (supports offline persistence on Android). Use Firestore in native mode with offline cache.
Firebase

Firebase Cloud Functions for server-side tasks: invite link creation, membership validation, notification dispatch, conflict resolution helpers (if needed).

FCM (Firebase Cloud Messaging) for push notifications when partner adds/edits/deletes transactions.

Local: Room (existing) remains the canonical local DB; a lightweight sync adapter publishes local changes to Firestore and listens to remote changes to update Room.

Why this choice

Fast to implement, real-time listeners, built-in Android offline persistence, good SDKs for Kotlin. Good fit for single-feature extension on a local-first Android app.
Firebase
+1

Data model (Firestore — example)

Collections / Docs:

users/{userId} — basic profile (email, displayName, photoURL)

sharedAccounts/{sharedId} — metadata: {name, createdAt, owners: [uid1, uid2], currency, settings}

sharedAccounts/{sharedId}/transactions/{txId} — {id, amount, currency, categoryId, note, createdByUid, createdAt, updatedAt, deleted:false}

invitations/{inviteId} — {sharedId, inviterUid, inviteeEmail, status, token, expiresAt}

Security rules:

Only members in sharedAccounts.{sharedId}.owners can read/write that account and its transactions.

Use server-generated invite tokens to allow new user creation/join flows.

Sync & conflict resolution

Local writes immediately applied to Room and queued to sync.

Use optimistic updates; when Firestore confirms, mark local item as synced.

Conflict resolution strategy:

For simple banking txs: last-write wins on updatedAt with updatedBy metadata is acceptable for two users.

For safer semantics: detect edit/delete conflicts and surface to UI for reconciliation (show “conflict — choose version”).

Firestore listeners update Room in near real-time.

Invite flow

Implement invites using a Cloud Function that creates an invitations/{inviteId} doc and sends an email with an action link (or dynamic link/QR). When the recipient opens the link, the app validates token and adds the user to sharedAccounts/{sharedId}.owners. See Firebase email action link generation.
Firebase
+1

Security & privacy

Firestore security rules + Firebase Auth required. Optionally encrypt sensitive fields on-device before sending (client-side encryption) if you want end-to-end—this complicates invites and search, so treat as optional.

Offline-first specifics

Firestore Android SDK supports disk persistence by default; combine with Room as the local source-of-truth and a small sync layer to avoid double-read complexity.

## Possible roadmap separated in small PRs

PR 1 — feat(sync): add sync module & sync architecture scaffold

Goal: Add a modular sync layer skeleton to manage push/pull with remote backend (Firestore) and integrate with existing repositories.
Changes

New package app/src/main/java/com/ivy/wallet/sync/

SyncManager.kt (singleton orchestrator)

SyncQueue.kt (queue of pending ops)

RemoteService interface (abstraction over Firestore/Supabase)

DI binding updates (Hilt/Koin as used in repo)
Room / DB: none.
Tests: unit tests for queue behavior (enqueue/dequeue/retry).
Review checklist

clear abstractions, small surface area, no Firebase yet.

DI-friendly for testing.

PR 2 — chore(auth): add Firebase Auth module & basic sign-in UI

Goal: Add Firebase Auth integration and a minimal sign-in screen, using email link or email+password.
Changes

Gradle: add Firebase Auth dependency (document in build.gradle.kts)

New auth package: AuthRepository.kt, FirebaseAuthSource.kt

UI: AuthScreen.kt composable + navigation to main app
Tests: unit tests mocking FirebaseAuth for success/failure flows.
Migration: none.
Review checklist

Secure handling of credentials; connect only to test Firebase project.

Clear README notes on how to configure Firebase project.

(Cite Firebase Auth docs for email link flows in developer docs.)
Firebase

PR 3 — feat(shared-models): add SharedAccount and SharedTransaction models + Room schema changes

Goal: Introduce domain models and Room entities for shared accounts and transactions.
Changes

Entities: SharedAccountEntity, SharedTransactionEntity in Room.

DAOs: SharedAccountDao, SharedTransactionDao.

Repository interfaces: SharedAccountsRepository (CRUD).

Room migration: add new tables — include migration code for existing DB versions.
Migration notes

Add migration script: create shared_accounts and shared_transactions tables. Provide Migration object for Room to apply on open.
Tests: DAO tests using in-memory Room DB.
Review checklist

Entities include syncState fields (isSynced, remoteId, updatedAt, createdAt, deleted) for sync coordination.

PR 4 — feat(ui): add Shared Accounts list screen and navigation entry

Goal: Add UI to view shared accounts and their balances.
Changes

New composable SharedAccountsScreen.kt (list + create button).

Add entry in drawer/bottom nav: Shared Accounts.

Basic empty & loading states.
Tests: UI snapshot tests for screen states (if project uses them) or simple compose unit tests.
Review checklist

Matches app styling, minimal behavior, navigable from main UI.

PR 5 — feat(trx-ui): add Shared Transaction list and transaction composer

Goal: Allow adding a transaction to a shared account from UI.
Changes

SharedTransactionList.kt composable, AddSharedTransactionDialog.kt or screen.

Hook into SharedAccountsRepository to persist locally (Room) as new transaction with isSynced=false.
Tests: unit test for repository save + UI test for adding flow.
Review checklist

Local-first: transaction immediately visible in UI after creation.

PR 6 — feat(sync/firestore): implement FirestoreRemoteService + pusher/listener

Goal: Implement RemoteService for Firestore: push local changes and subscribe to remote changes.
Changes

FirestoreRemoteService.kt (implements RemoteService)

Uses Firestore SDK to:

create/update/delete sharedAccounts and sharedAccounts/{id}/transactions

listen to collection snapshot changes and translate into local operations

Wire listeners to SyncManager.
Tests: integration tests partially mocked (use FakeFirestore or mocked SDK).
Review checklist

Proper error handling and exponential backoff for network errors.

Avoid leaking listeners when app backgrounded; lifecycle-aware listeners.

(Docs: Firestore offline persistence and listeners.)
Firebase

PR 7 — feat(sync/queue): publish local ops to Firestore and mark synced

Goal: Connect Room changes to sync queue; implement push flow and mark rows as synced on success.
Changes

When SharedTransaction inserted locally: set syncState=Pending and enqueue op.

SyncManager processes queue: calls FirestoreRemoteService to apply remote change, writes back remoteId, isSynced=true.
Tests: unit tests verifying queue -> remote -> mark synced.
Review checklist

Idempotency (use UUIDs for transactions, ensure replays do not duplicate).

PR 8 — feat(invites): implement invite flow (create + accept) + backend Cloud Function

Goal: Allow creating invites (email/link/QR) and accepting them to join a shared account.
Changes

Cloud Function (Node/TypeScript) createInvite:

Creates invitations/{inviteId} with token and expiry and optionally sends email via Firebase Admin.

Client: UI to invite by email / generate QR (deep link) and accept invite screen that validates token and calls Cloud Function to add user to sharedAccounts.owners.
Tests: unit tests for function logic (local emulator), client acceptance flow tests.
Review checklist

Invite token security, expiry, and single-use enforcement.

Deep links / dynamic links tested.

(Docs: Firebase admin email action links & dynamic link usage.)
Firebase
+1

PR 9 — feat(conflict): conflict detection UI + simple merge policy

Goal: When a remote update conflicts with a local unsynced change, show a small conflict UI allowing the user to pick version or accept remote.
Changes

ConflictResolver.kt core logic: detect updatedAt mismatch and set conflict state in Room.

ConflictScreen.kt small dialog enumerating local vs remote changes and action buttons.
Tests: unit tests for conflict detection logic.
Review checklist

UX for conflicts is minimal but clear; defaults use lastModified with user confirmation for ambiguous cases.

PR 10 — feat(push): add FCM integration to notify partner on create/edit/delete

Goal: Send push notifications (FCM) to other shared account member(s) when an event happens.
Changes

Cloud Function triggers on sharedAccounts/{sharedId}/transactions create/update/delete and sends FCM via topics or per-user tokens.

Client registers for FCM and handles notification navigation (tap opens shared account screen).
Tests: function unit tests (emulator), client notification handling tests.
Review checklist

Throttle notifications (dedupe rapid changes), consider in-app notification alternative.

PR 11 — chore(security): Firestore security rules + unit tests

Goal: Add strict Firestore security rules and tests for them.
Changes

firestore.rules added with:

read/write guarded by request.auth.uid in resource.data.owners or membership check via parent doc.

validation of fields (e.g., amount is number, createdBy == request.auth.uid on create).

Unit test suite (Firestore rules unit testing or emulator) validating rules.
Review checklist

Validate no broad allow read/write left open; test negative cases.

PR 12 — test: end-to-end smoke tests + docs

Goal: Add smoke/integration tests and documentation for setting up Firebase test project & migration steps.
Changes

Integration test flow (create user A, create shared account, invite user B, user B accepts, add transactions, verify sync) using Firebase emulator suite.

docs/shared_accounts.md with setup steps, Firebase config, environment variables, and migration steps for Room.
Review checklist

Tests pass in CI (simulate with emulator). Docs are clear for reviewers to set up a test Firebase project.