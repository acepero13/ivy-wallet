package com.ivy.data.sync.impl

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.ivy.data.sync.SyncOperation
import com.ivy.data.sync.SyncOperationType
import com.ivy.data.sync.SyncResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FirestoreRemoteServiceTest {

    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val currentUser = mockk<FirebaseUser>(relaxed = true)

    private lateinit var service: FirestoreRemoteService

    @Before
    fun setup() {
        // Setup authenticated user
        every { auth.currentUser } returns currentUser
        every { currentUser.uid } returns "test-user-id"

        service = FirestoreRemoteService(
            firestore = firestore,
            auth = auth
        )
    }

    @Test
    fun `pushOperation - should fail when user not authenticated`() = runTest {
        // given
        every { auth.currentUser } returns null
        val operation = SyncOperation(
            type = SyncOperationType.CREATE,
            entityType = "sharedAccount",
            entityId = "account-123",
            data = mapOf("name" to "Test Account")
        )

        // when
        val result = service.pushOperation(operation)

        // then
        result.shouldBeInstanceOf<SyncResult.Failure>()
        (result as SyncResult.Failure).error.message shouldBe "User not authenticated"
    }

    @Test
    fun `pushOperation - should successfully push shared account creation`() = runTest {
        // given
        val accountRef = mockk<DocumentReference>(relaxed = true)
        val accountsCollection = mockk<CollectionReference>(relaxed = true)

        every { firestore.collection("sharedAccounts") } returns accountsCollection
        every { accountsCollection.document("account-123") } returns accountRef
        every { accountRef.set(any(), any()) } returns Tasks.forResult(null)

        val operation = SyncOperation(
            type = SyncOperationType.CREATE,
            entityType = "sharedAccount",
            entityId = "account-123",
            data = mapOf(
                "name" to "Family Account",
                "currency" to "USD",
                "owners" to listOf("test-user-id")
            )
        )

        // when
        val result = service.pushOperation(operation)

        // then
        result.shouldBeInstanceOf<SyncResult.Success>()
        verify { accountRef.set(any(), any()) }
    }

    @Test
    fun `pushOperation - should successfully push shared transaction`() = runTest {
        // given
        val transactionRef = mockk<DocumentReference>(relaxed = true)
        val transactionsCollection = mockk<CollectionReference>(relaxed = true)
        val accountRef = mockk<DocumentReference>(relaxed = true)
        val accountsCollection = mockk<CollectionReference>(relaxed = true)

        every { firestore.collection("sharedAccounts") } returns accountsCollection
        every { accountsCollection.document("account-123") } returns accountRef
        every { accountRef.collection("transactions") } returns transactionsCollection
        every { transactionsCollection.document("txn-456") } returns transactionRef
        every { transactionRef.set(any(), any()) } returns Tasks.forResult(null)

        val operation = SyncOperation(
            type = SyncOperationType.CREATE,
            entityType = "sharedTransaction",
            entityId = "txn-456",
            data = mapOf(
                "sharedAccountId" to "account-123",
                "amount" to 100.0,
                "type" to "EXPENSE",
                "title" to "Groceries"
            )
        )

        // when
        val result = service.pushOperation(operation)

        // then
        result.shouldBeInstanceOf<SyncResult.Success>()
        verify { transactionRef.set(any(), any()) }
    }

    @Test
    fun `pushOperation - should fail when sharedAccountId missing for transaction`() = runTest {
        // given
        val operation = SyncOperation(
            type = SyncOperationType.CREATE,
            entityType = "sharedTransaction",
            entityId = "txn-456",
            data = mapOf(
                "amount" to 100.0,
                "type" to "EXPENSE"
            )
        )

        // when
        val result = service.pushOperation(operation)

        // then
        result.shouldBeInstanceOf<SyncResult.Failure>()
        (result as SyncResult.Failure).error.message shouldBe "sharedAccountId is required"
    }

    @Test
    fun `pushOperation - should handle DELETE operation with soft delete`() = runTest {
        // given
        val accountRef = mockk<DocumentReference>(relaxed = true)
        val accountsCollection = mockk<CollectionReference>(relaxed = true)

        every { firestore.collection("sharedAccounts") } returns accountsCollection
        every { accountsCollection.document("account-123") } returns accountRef
        every { accountRef.update(any<Map<String, Any>>()) } returns Tasks.forResult(null)

        val operation = SyncOperation(
            type = SyncOperationType.DELETE,
            entityType = "sharedAccount",
            entityId = "account-123"
        )

        // when
        val result = service.pushOperation(operation)

        // then
        result.shouldBeInstanceOf<SyncResult.Success>()

        val updateMapSlot = slot<Map<String, Any>>()
        verify { accountRef.update(capture(updateMapSlot)) }
        updateMapSlot.captured["deleted"] shouldBe true
    }

    @Test
    fun `pushOperation - should fail for unknown entity type`() = runTest {
        // given
        val operation = SyncOperation(
            type = SyncOperationType.CREATE,
            entityType = "unknownType",
            entityId = "some-id",
            data = mapOf("test" to "data")
        )

        // when
        val result = service.pushOperation(operation)

        // then
        result.shouldBeInstanceOf<SyncResult.Failure>()
        (result as SyncResult.Failure).error.message shouldBe "Unknown entity type: unknownType"
    }

    @Test
    fun `isAvailable - should return true when user authenticated`() = runTest {
        // given
        every { firestore.firestoreSettings } returns mockk()
        every { auth.currentUser } returns currentUser

        // when
        val available = service.isAvailable()

        // then
        available shouldBe true
    }

    @Test
    fun `isAvailable - should return false when user not authenticated`() = runTest {
        // given
        every { firestore.firestoreSettings } returns mockk()
        every { auth.currentUser } returns null

        // when
        val available = service.isAvailable()

        // then
        available shouldBe false
    }

    @Test
    fun `pullChanges - should return empty list when user not authenticated`() = runTest {
        // given
        every { auth.currentUser } returns null

        // when
        val changes = service.pullChanges("sharedAccount")

        // then
        changes shouldBe emptyList()
    }

    @Test
    fun `pullChanges - should return empty list for unknown entity type`() = runTest {
        // given
        every { auth.currentUser } returns currentUser

        // when
        val changes = service.pullChanges("unknownType")

        // then
        changes shouldBe emptyList()
    }
}
