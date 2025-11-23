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
- i see this: 920  5513  5530 I System.out: === OCR BLOCKS (Spatial Parser) ===
11-23 17:15:19.920  5513  5530 I System.out: Block 0: 'D51 Ungezieferfal le
11-23 17:15:19.920  5513  5530 I System.out: aminWur&
11-23 17:15:19.920  5513  5530 I System.out: 731551 Ungezieferfal | e
11-23 17:15:19.920  5513  5533 D TransportRuntime.SQLiteEventStore: Storing event with priority=VERY_LOW, name=FIREBASE_ML_SDK for destination cct
11-23 17:15:19.921  5513  5530 I System.out: 837620 Tyrol int 90g' | X:964-1860 Y:-72-281
11-23 17:15:19.921  5513  5530 I System.out: Block 1: '823916 Batterien 20 Stk' | X:994-1868 Y:189-337
11-23 17:15:19.921  5513  5530 I System.out: Block 2: '827650 Batteri en
11-23 17:15:19.921  5513  5530 I System.out: 818035 Zucchini lose' | X:956-1753 Y:257-463
11-23 17:15:19.921  5513  5530 I System.out: Block 3: '0,318 kg x 1,79 EUR/Kg
11-23 17:15:19.921  5513  5530 I System.out: 42200 Suessr ahmbutt 2509' | X:1026-2023 Y:301-618
11-23 17:15:19.921  5513  5530 I System.out: Block 4: '826500 Kiwi gold Stüuck
11-23 17:15:19.921  5513  5530 I System.out: 826500 Kiwi gold Stück
11-23 17:15:19.921  5513  5530 I System.out: 826500 Kiwi gold Stuck
11-23 17:15:19.921  5513  5530 I System.out: 61897 Bio-Kart 1.5kø' | X:931-1866 Y:508-874
11-23 17:15:19.921  5513  5530 I System.out: Block 5: 'Betrag' | X:747-999 Y:1281-1347
11-23 17:15:19.921  5513  5530 I System.out: Block 6: 'K-U-N-D-E-N-B-E-L-E-G' | X:1141-2018 Y:860-1000
11-23 17:15:19.921  5513  5530 I System.out: Block 7: '10.11.2025
11-23 17:15:19.921  5513  5530 I System.out: TA-Nr. 115574
11-23 17:15:19.921  5513  5530 I System.out: Kartennr.' | X:738-1303 Y:1442-1699
11-23 17:15:19.921  5513  5530 I System.out: Block 8: 'Kontakt los Chip
11-23 17:15:19.921  5513  5530 I System.out: VU-Nummer' | X:741-1390 Y:1706-1876
11-23 17:15:19.921  5513  5530 I System.out: Block 9: 'Bezahl ung Mastercard' | X:1210-2024 Y:1101-1214
11-23 17:15:19.921  5513  5530 I System.out: Block 10: 'EMV-Daten:' | X:743-1154 Y:2072-2149
11-23 17:15:19.921  5513  5530 I System.out: Block 11: '09:43' | X:1444-1654 Y:1452-1518
11-23 17:15:19.921  5513  5530 I System.out: Block 12: 'Autorisierungsantwortcode' | X:744-1804 Y:1974-2064
11-23 17:15:19.921  5513  5530 I System.out: Block 13: '34 Artikei
11-23 17:15:19.921  5513  5530 I System.out: MasterCard' | X:695-1189 Y:3006-3229
11-23 17:15:19.921  5513  5530 I System.out: Block 14: 'AS-Proc-C0de = 00 075 00
11-23 17:15:19.921  5513  5530 I System.out: Capt. -Ref = 0000
11-23 17:15:19.921  5513  5530 I System.out: APPROVED' | X:727-1756 Y:2390-2714
11-23 17:15:19.921  5513  5530 I System.out: Block 15: 'Zahlung erfolgt' | X:1236-1877 Y:2756-2878
11-23 17:15:19.921  5513  5530 I System.out: Block 16: 'Autorisierungsnumterw2AC9 099763' | X:769-2456 Y:1847-2012
11-23 17:15:19.921  5513  5530 I System.out: Block 17: 'A 07,0% Netto
11-23 17:15:19.921  5513  5530 I System.out: B 19,O% Net to' | X:699-1279 Y:3205-3447
11-23 17:15:19.921  5513  5530 I System.out: Block 18: '7.' | X:2091-2148 Y:169-228
11-23 17:15:19.921  5513  5530 I System.out: Block 19: 'T-ID 65332840
11-23 17:15:19.921  5513  5530 I System.out: Beleg-Nr. 9800
11-23 17:15:19.921  5513  5530 I System.out: ############7766 01' | X:1632-2455 Y:1399-1734
11-23 17:15:19.921  5513  5530 I System.out: Block 20: 'ViRHA EUR' | X:1212-1710 Y:3036-3180
11-23 17:15:19.921  5513  5530 I System.out: Block 21: '0,5' | X:2089-2216 Y:213-312
11-23 17:15:19.921  5513  5530 I System.out: Block 22: 'S19' | X:1938-2309 Y:651-847
11-23 17:15:19.921  5513  5530 I System.out: Block 23: '37,44 MwSt
11-23 17:15:19.921  5513  5530 I System.out: 9,65 MwSt' | X:1447-1877 Y:3200-3393
11-23 17:15:19.921  5513  5530 I System.out: Block 24: '1' | X:2114-2149 Y:410-483
11-23 17:15:19.921  5513  5530 I System.out: Block 25: '0000048001/0000//I/420300//0000000002202
11-23 17:15:19.921  5513  5530 I System.out: 00000048001000001/AB97D975/80' | X:742-2450 Y:2136-2331
11-23 17:15:19.921  5513  5530 I System.out: Block 26: '059' | X:2125-2258 Y:514-609
11-23 17:15:19.921  5513  5530 I System.out: Block 27: '51,54 ER' | X:2067-2436 Y:1238-1351
11-23 17:15:19.921  5513  5530 I System.out: Block 28: 'SunLo2151,54' | X:697-2389 Y:2944-3051
11-23 17:15:19.921  5513  5530 I System.out: Block 29: 'Ur nsere OTTungs2ei ten:
11-23 17:15:19.921  5513  5530 I System.out: Mo - Sa: 8:00 U9s' | X:936-2006 Y:3365-3592
11-23 17:15:19.921  5513  5530 I System.out: Block 30: 'Onl ine
11-23 17:15:19.921  5513  5530 I System.out: 4556601800' | X:2031-2451 Y:1685-1861
11-23 17:15:19.921  5513  5530 I System.out: Block 31: 'EaA. 51,54' | X:1962-2358 Y:3106-3240
11-23 17:15:19.921  5513  5530 I System.out: Block 32: '20:00 Uhr' | X:1746-2109 Y:3504-3570
11-23 17:15:19.921  5513  5530 I System.out: Block 33: '*4169 08/01300 10. 11.25 09: d 83' | X:700-2303 Y:3339-3523
11-23 17:15:19.921  5513  5530 I System.out: Block 34: '2.62' | X:2127-2307 Y:3290-3361
11-23 17:15:19.921  5513  5530 I System.out: Block 35: '00' | X:2371-2456 Y:1971-2040
11-23 17:15:19.921  5513  5530 I System.out: Block 36: 'UST-ID N . DE 120353452
11-23 17:15:19.921  5513  5530 I System.out: VIELEN DANK FOR DEINEN EINKAUF
11-23 17:15:19.922  5513  5530 I System.out: ALDI SUD Gutes fur alle.' | X:1024-2182 Y:3574-3929
11-23 17:15:19.925  5513  5533 D TransportRuntime.JobInfoScheduler: Upload for context TransportContext(cct, VERY_LOW, MSRodHRwczovL2ZpcmViYXNlbG9nZ2luZy5nb29nbGVhcGlzLmNvbS92MGNjL2xvZy9iYXRjaD9mb3JtYXQ9anNvbl9wcm90bzNc) is already scheduled. Returning...
11-23 17:15:19.950  5513  5533 D TransportRuntime.SQLiteEventStore: Storing event with priority=VERY_LOW, name=FIREBASE_ML_SDK for destination cct
11-23 17:15:19.954  5513  5533 D TransportRuntime.JobInfoScheduler: Upload for context TransportContext(cct, VERY_LOW, MSRodHRwczovL2ZpcmViYXNlbG9nZ2luZy5nb29nbGVhcGlzLmNvbS92MGNjL2xvZy9iYXRjaD9mb3JtYXQ9anNvbl9wcm90bzNc) is already scheduled. Returning...
11-23 17:15:19.968  5513  5530 I System.out: === SPATIAL PARSER RESULT ===
11-23 17:15:19.969  5513  5530 I System.out: Total: 0.0
11-23 17:15:19.969  5513  5530 I System.out: Currency: EUR
11-23 17:15:20.048  5513  5530 E TestRunner: failed: testParseReceiptFromAssets_NewPic_SpatialParser(com.ivy.receipts.parser.ReceiptImageParsingTest)
11-23 17:15:20.049  5513  5530 E TestRunner: ----- begin exception -----