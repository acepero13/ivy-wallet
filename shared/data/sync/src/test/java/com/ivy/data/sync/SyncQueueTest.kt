package com.ivy.data.sync

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class SyncQueueTest {

    private lateinit var syncQueue: SyncQueue

    @Before
    fun setup() {
        syncQueue = SyncQueue()
    }

    @Test
    fun `enqueue adds operation to queue`() = runTest {
        // given
        val operation = createTestOperation()

        // when
        syncQueue.enqueue(operation)

        // then
        syncQueue.size() shouldBe 1
        syncQueue.operations.value shouldContain operation
    }

    @Test
    fun `dequeue returns next pending operation`() = runTest {
        // given
        val operation = createTestOperation()
        syncQueue.enqueue(operation)

        // when
        val dequeued = syncQueue.dequeue()

        // then
        dequeued shouldNotBe null
        dequeued?.id shouldBe operation.id
        dequeued?.status shouldBe SyncOperationStatus.IN_PROGRESS
    }

    @Test
    fun `dequeue returns null when queue is empty`() = runTest {
        // when
        val dequeued = syncQueue.dequeue()

        // then
        dequeued shouldBe null
    }

    @Test
    fun `dequeue marks operation as in progress`() = runTest {
        // given
        val operation = createTestOperation()
        syncQueue.enqueue(operation)

        // when
        syncQueue.dequeue()

        // then
        val operations = syncQueue.operations.value
        operations.first().status shouldBe SyncOperationStatus.IN_PROGRESS
    }

    @Test
    fun `markCompleted removes operation from queue`() = runTest {
        // given
        val operation = createTestOperation()
        syncQueue.enqueue(operation)

        // when
        syncQueue.markCompleted(operation.id)

        // then
        syncQueue.isEmpty() shouldBe true
    }

    @Test
    fun `markFailed increments retry count and resets to pending`() = runTest {
        // given
        val operation = createTestOperation()
        syncQueue.enqueue(operation)
        syncQueue.dequeue() // Mark as in progress

        // when
        syncQueue.markFailed(operation.id, Exception("Test error"))

        // then
        val operations = syncQueue.operations.value
        operations shouldHaveSize 1
        operations.first().status shouldBe SyncOperationStatus.PENDING
        operations.first().retryCount shouldBe 1
    }

    @Test
    fun `markFailed removes operation after max retries`() = runTest {
        // given
        val operation = createTestOperation(retryCount = 2) // One less than max
        syncQueue.enqueue(operation)
        syncQueue.dequeue()

        // when
        syncQueue.markFailed(operation.id, Exception("Test error"))

        // then
        val operations = syncQueue.operations.value
        operations shouldHaveSize 1
        operations.first().status shouldBe SyncOperationStatus.FAILED
        operations.first().retryCount shouldBe 3
    }

    @Test
    fun `getPendingOperations returns only pending operations`() = runTest {
        // given
        val pending1 = createTestOperation()
        val pending2 = createTestOperation()
        syncQueue.enqueue(pending1)
        syncQueue.enqueue(pending2)
        syncQueue.dequeue() // Mark first as in progress

        // when
        val pending = syncQueue.getPendingOperations()

        // then
        pending shouldHaveSize 1
        pending.first().id shouldBe pending2.id
    }

    @Test
    fun `getFailedOperations returns only failed operations`() = runTest {
        // given
        val operation = createTestOperation(retryCount = 2)
        syncQueue.enqueue(operation)
        syncQueue.dequeue()
        syncQueue.markFailed(operation.id, Exception("Test error"))

        // when
        val failed = syncQueue.getFailedOperations()

        // then
        failed shouldHaveSize 1
        failed.first().status shouldBe SyncOperationStatus.FAILED
    }

    @Test
    fun `clear removes all operations`() = runTest {
        // given
        syncQueue.enqueue(createTestOperation())
        syncQueue.enqueue(createTestOperation())

        // when
        syncQueue.clear()

        // then
        syncQueue.isEmpty() shouldBe true
        syncQueue.operations.value.shouldBeEmpty()
    }

    @Test
    fun `retry resets failed operation to pending`() = runTest {
        // given
        val operation = createTestOperation(retryCount = 2)
        syncQueue.enqueue(operation)
        syncQueue.dequeue()
        syncQueue.markFailed(operation.id, Exception("Test error"))

        // when
        syncQueue.retry(operation.id)

        // then
        val operations = syncQueue.operations.value
        operations.first().status shouldBe SyncOperationStatus.PENDING
        operations.first().retryCount shouldBe 0
    }

    @Test
    fun `retryAll resets all failed operations`() = runTest {
        // given
        val op1 = createTestOperation(retryCount = 2)
        val op2 = createTestOperation(retryCount = 2)
        syncQueue.enqueue(op1)
        syncQueue.enqueue(op2)
        syncQueue.dequeue()
        syncQueue.dequeue()
        syncQueue.markFailed(op1.id, Exception("Error 1"))
        syncQueue.markFailed(op2.id, Exception("Error 2"))

        // when
        syncQueue.retryAll()

        // then
        val operations = syncQueue.operations.value
        operations shouldHaveSize 2
        operations.forEach { op ->
            op.status shouldBe SyncOperationStatus.PENDING
            op.retryCount shouldBe 0
        }
    }

    @Test
    fun `queue maintains FIFO order for pending operations`() = runTest {
        // given
        val op1 = createTestOperation()
        val op2 = createTestOperation()
        val op3 = createTestOperation()
        syncQueue.enqueue(op1)
        syncQueue.enqueue(op2)
        syncQueue.enqueue(op3)

        // when
        val dequeued1 = syncQueue.dequeue()
        val dequeued2 = syncQueue.dequeue()
        val dequeued3 = syncQueue.dequeue()

        // then
        dequeued1?.id shouldBe op1.id
        dequeued2?.id shouldBe op2.id
        dequeued3?.id shouldBe op3.id
    }

    @Test
    fun `size returns correct count`() = runTest {
        // given
        syncQueue.enqueue(createTestOperation())
        syncQueue.enqueue(createTestOperation())
        syncQueue.enqueue(createTestOperation())

        // then
        syncQueue.size() shouldBe 3
    }

    @Test
    fun `isEmpty returns true for empty queue`() = runTest {
        // then
        syncQueue.isEmpty() shouldBe true
    }

    @Test
    fun `isEmpty returns false for non-empty queue`() = runTest {
        // given
        syncQueue.enqueue(createTestOperation())

        // then
        syncQueue.isEmpty() shouldBe false
    }

    // Helper function to create test operations
    private fun createTestOperation(retryCount: Int = 0) = SyncOperation(
        type = SyncOperationType.CREATE,
        entityType = "test_entity",
        entityId = "test_id_${System.nanoTime()}",
        data = mapOf("key" to "value"),
        retryCount = retryCount
    )
}
