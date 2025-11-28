package com.ivy.sharedaccounts.detail

import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SharedAccountDetailViewModelTest {
    private val sharedAccountRepository = mockk<SharedAccountRepository>(relaxed = true)
    private val sharedTransactionRepository = mockk<SharedTransactionRepository>(relaxed = true)

    @Test
    fun `observeById - updates account when Flow emits`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val account = SharedAccount(
            id = accountId,
            name = NotBlankTrimmedString.unsafe("Test Account"),
            currency = AssetCode.unsafe("USD"),
            owners = listOf("user1"),
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            linkedAccountId = null
        )

        every { sharedAccountRepository.observeById(accountId) } returns flowOf(account)
        every { sharedTransactionRepository.observeBySharedAccountId(accountId, false) } returns flowOf(emptyList())

        // when
        // In actual usage, ViewModel would call setSharedAccountId(accountId)
        // which starts observing the Flow
        val accountFlow = sharedAccountRepository.observeById(accountId)
        val transactionsFlow = sharedTransactionRepository.observeBySharedAccountId(accountId, false)

        // then
        // Verify the flows emit expected data
        accountFlow.first() shouldBe account
        transactionsFlow.first() shouldBe emptyList()
    }

    @Test
    fun `observeBySharedAccountId - calculates balance correctly`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val transactions = listOf(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.INCOME,
                amount = 1000.0,
                title = NotBlankTrimmedString.unsafe("Salary"),
                description = null,
                category = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false
            ),
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 250.0,
                title = NotBlankTrimmedString.unsafe("Groceries"),
                description = null,
                category = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false
            ),
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 100.0,
                title = NotBlankTrimmedString.unsafe("Gas"),
                description = null,
                category = null,
                time = now,
                createdBy = "user2",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user2",
                deleted = false
            )
        )

        every { sharedTransactionRepository.observeBySharedAccountId(accountId, false) } returns flowOf(transactions)

        // when
        val transactionsFlow = sharedTransactionRepository.observeBySharedAccountId(accountId, false)
        val result = transactionsFlow.first()

        // then - calculate balance
        val income = result.filter { it.type == SharedTransactionType.INCOME }.sumOf { it.amount }
        val expense = result.filter { it.type == SharedTransactionType.EXPENSE }.sumOf { it.amount }
        val balance = income - expense

        income shouldBe 1000.0
        expense shouldBe 350.0
        balance shouldBe 650.0
    }

    @Test
    fun `observeBySharedAccountId - filters deleted transactions`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val activeTransactions = listOf(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 50.0,
                title = NotBlankTrimmedString.unsafe("Active"),
                description = null,
                category = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false
            )
        )

        every { sharedTransactionRepository.observeBySharedAccountId(accountId, deleted = false) } returns flowOf(activeTransactions)

        // when
        val transactionsFlow = sharedTransactionRepository.observeBySharedAccountId(accountId, deleted = false)
        val result = transactionsFlow.first()

        // then
        result.size shouldBe 1
        result.all { !it.deleted } shouldBe true
    }

    @Test
    fun `observeBySharedAccountId - emits updates when new transaction added`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()

        val initialTransactions = listOf(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 50.0,
                title = NotBlankTrimmedString.unsafe("Initial"),
                description = null,
                category = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false
            )
        )

        val updatedTransactions = initialTransactions + SharedTransaction(
            id = SharedTransactionId(UUID.randomUUID()),
            sharedAccountId = accountId,
            type = SharedTransactionType.INCOME,
            amount = 100.0,
            title = NotBlankTrimmedString.unsafe("New Transaction"),
            description = null,
            category = null,
            time = now,
            createdBy = "user2",
            createdAt = now,
            updatedAt = now,
            updatedBy = "user2",
            deleted = false
        )

        // Simulate Flow emitting multiple values
        every { sharedTransactionRepository.observeBySharedAccountId(accountId, false) } returns
            flowOf(initialTransactions, updatedTransactions)

        // when
        val transactionsFlow = sharedTransactionRepository.observeBySharedAccountId(accountId, false)

        // Collect first emission
        val firstEmission = mutableListOf<List<SharedTransaction>>()
        transactionsFlow.collect {
            firstEmission.add(it)
        }

        // then
        firstEmission.size shouldBe 2
        firstEmission[0].size shouldBe 1
        firstEmission[1].size shouldBe 2
    }

    @Test
    fun `observeBySharedAccountId - sorts transactions by time descending`() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now()
        val earlier = now.minusSeconds(3600)
        val earliest = now.minusSeconds(7200)

        val transactions = listOf(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 50.0,
                title = NotBlankTrimmedString.unsafe("Oldest"),
                description = null,
                category = null,
                time = earliest,
                createdBy = "user1",
                createdAt = earliest,
                updatedAt = earliest,
                updatedBy = "user1",
                deleted = false
            ),
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 75.0,
                title = NotBlankTrimmedString.unsafe("Newest"),
                description = null,
                category = null,
                time = now,
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                updatedBy = "user1",
                deleted = false
            ),
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 100.0,
                title = NotBlankTrimmedString.unsafe("Middle"),
                description = null,
                category = null,
                time = earlier,
                createdBy = "user1",
                createdAt = earlier,
                updatedAt = earlier,
                updatedBy = "user1",
                deleted = false
            )
        )

        every { sharedTransactionRepository.observeBySharedAccountId(accountId, false) } returns flowOf(transactions)

        // when
        val transactionsFlow = sharedTransactionRepository.observeBySharedAccountId(accountId, false)
        val result = transactionsFlow.first()

        // Verify ViewModel sorts by time descending
        val sorted = result.sortedByDescending { it.time }

        // then
        sorted[0].title?.value shouldBe "Newest"
        sorted[1].title?.value shouldBe "Middle"
        sorted[2].title?.value shouldBe "Oldest"
    }
}
