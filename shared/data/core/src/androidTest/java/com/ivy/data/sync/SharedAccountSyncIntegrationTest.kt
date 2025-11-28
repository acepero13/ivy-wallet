package com.ivy.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ivy.base.TestDispatchersProvider
import com.ivy.data.DataObserver
import com.ivy.data.db.IvyRoomDatabase
import com.ivy.data.db.dao.read.SharedAccountDao
import com.ivy.data.db.dao.read.SharedTransactionDao
import com.ivy.data.db.dao.write.WriteSharedAccountDao
import com.ivy.data.db.dao.write.WriteSharedTransactionDao
import com.ivy.data.model.SharedAccount
import com.ivy.data.model.SharedAccountId
import com.ivy.data.model.SharedTransaction
import com.ivy.data.model.SharedTransactionId
import com.ivy.data.model.SharedTransactionType
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.RepositoryMemoFactory
import com.ivy.data.repository.SharedAccountRepository
import com.ivy.data.repository.SharedTransactionRepository
import com.ivy.data.repository.mapper.SharedAccountMapper
import com.ivy.data.repository.mapper.SharedTransactionMapper
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Integration test for the sync flow without touching Firebase.
 *
 * Tests the complete data flow:
 * 1. Write to Room database
 * 2. Observe via Flow
 * 3. Verify reactive updates work
 *
 * This simulates what happens when:
 * - User creates transaction locally
 * - Firestore listener receives remote change
 * - UI updates reactively
 */
@RunWith(AndroidJUnit4::class)
class SharedAccountSyncIntegrationTest {

    private lateinit var database: IvyRoomDatabase
    private lateinit var sharedAccountDao: SharedAccountDao
    private lateinit var writeSharedAccountDao: WriteSharedAccountDao
    private lateinit var sharedTransactionDao: SharedTransactionDao
    private lateinit var writeSharedTransactionDao: WriteSharedTransactionDao

    private lateinit var sharedAccountRepository: SharedAccountRepository
    private lateinit var sharedTransactionRepository: SharedTransactionRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Create an in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            IvyRoomDatabase::class.java
        ).build()

        sharedAccountDao = database.sharedAccountDao
        writeSharedAccountDao = database.writeSharedAccountDao
        sharedTransactionDao = database.sharedTransactionDao
        writeSharedTransactionDao = database.writeSharedTransactionDao

        val memoFactory = RepositoryMemoFactory(
            dataObserver = DataObserver(),
            dispatchers = TestDispatchersProvider
        )

        sharedAccountRepository = SharedAccountRepository(
            mapper = SharedAccountMapper(),
            sharedAccountDao = sharedAccountDao,
            writeSharedAccountDao = writeSharedAccountDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = memoFactory
        )

        sharedTransactionRepository = SharedTransactionRepository(
            mapper = SharedTransactionMapper(),
            sharedTransactionDao = sharedTransactionDao,
            writeSharedTransactionDao = writeSharedTransactionDao,
            dispatchersProvider = TestDispatchersProvider,
            memoFactory = memoFactory,
            transactionRepository = dagger.Lazy {
                // Mock - not used in this test
                throw UnsupportedOperationException("TransactionRepository not needed in this test")
            },
            sharedAccountRepository = dagger.Lazy {
                sharedAccountRepository
            }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test_sharedAccount_observeById_reactsToInsert() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        // Use truncatedTo(ChronoUnit.MILLIS) because Room truncates microseconds
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

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

        // Start observing BEFORE inserting
        val flow = sharedAccountRepository.observeById(accountId)

        // when - insert the account
        sharedAccountRepository.save(account)

        // Give Room a moment to emit
        delay(100)

        // then - Flow should emit the account
        val result = flow.first()
        result shouldBe account
    }

    @Test
    fun test_sharedTransaction_observeBySharedAccountId_reactsToInsert() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val transactionId = SharedTransactionId(UUID.randomUUID())
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

        // Create account first
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
        sharedAccountRepository.save(account)

        val transaction = SharedTransaction(
            id = transactionId,
            sharedAccountId = accountId,
            type = SharedTransactionType.EXPENSE,
            amount = 100.0,
            title = NotBlankTrimmedString.unsafe("Groceries"),
            description = null,
            category = null,
            time = now,
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            updatedBy = "user1",
            deleted = false
        )

        // Start observing BEFORE inserting
        val flow = sharedTransactionRepository.observeBySharedAccountId(accountId, deleted = false)

        // when - insert the transaction
        sharedTransactionRepository.save(transaction)

        // Give Room a moment to emit
        delay(100)

        // then - Flow should emit the transaction
        val result = flow.first()
        result.size shouldBe 1
        result[0].id shouldBe transactionId
        result[0].amount shouldBe 100.0
    }

    @Test
    fun test_multipleTransactions_observeBySharedAccountId_reactsToMultipleInserts() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

        // Create account
        val account = SharedAccount(
            id = accountId,
            name = NotBlankTrimmedString.unsafe("Family Account"),
            currency = AssetCode.unsafe("USD"),
            owners = listOf("user1", "user2"),
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            linkedAccountId = null
        )
        sharedAccountRepository.save(account)

        // Start observing
        val flow = sharedTransactionRepository.observeBySharedAccountId(accountId, deleted = false)

        delay(100) // Let initial emission happen

        // Collect first emission (should be empty)
        val initial = flow.first()
        initial.size shouldBe 0

        // when - insert first transaction (simulating local user action)
        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 50.0,
                title = NotBlankTrimmedString.unsafe("Lunch"),
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

        delay(100)

        // Check after first insert
        val afterFirst = flow.first()
        afterFirst.size shouldBe 1
        afterFirst[0].amount shouldBe 50.0

        // when - insert second transaction (simulating remote Firestore sync)
        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.INCOME,
                amount = 1000.0,
                title = NotBlankTrimmedString.unsafe("Salary"),
                description = null,
                category = null,
                time = now.plusSeconds(60),
                createdBy = "user2",
                createdAt = now.plusSeconds(60),
                updatedAt = now.plusSeconds(60),
                updatedBy = "user2",
                deleted = false
            )
        )

        delay(100)

        // then - Check after second insert
        val afterSecond = flow.first()
        afterSecond.size shouldBe 2
        // Transactions are sorted by time DESC, so newest (1000) should be first
        afterSecond[0].amount shouldBe 1000.0
        afterSecond[1].amount shouldBe 50.0
    }

    @Test
    fun test_balanceCalculation_withReactiveUpdates() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

        // Create account
        val account = SharedAccount(
            id = accountId,
            name = NotBlankTrimmedString.unsafe("Budget Account"),
            currency = AssetCode.unsafe("USD"),
            owners = listOf("user1"),
            createdBy = "user1",
            createdAt = now,
            updatedAt = now,
            linkedAccountId = null
        )
        sharedAccountRepository.save(account)

        // Start observing
        val flow = sharedTransactionRepository.observeBySharedAccountId(accountId, deleted = false)

        // when - add income
        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.INCOME,
                amount = 5000.0,
                title = NotBlankTrimmedString.unsafe("Salary"),
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

        delay(100)

        // when - add expenses
        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 1200.0,
                title = NotBlankTrimmedString.unsafe("Rent"),
                description = null,
                category = null,
                time = now.plusSeconds(60),
                createdBy = "user1",
                createdAt = now.plusSeconds(60),
                updatedAt = now.plusSeconds(60),
                updatedBy = "user1",
                deleted = false
            )
        )

        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 300.0,
                title = NotBlankTrimmedString.unsafe("Groceries"),
                description = null,
                category = null,
                time = now.plusSeconds(120),
                createdBy = "user1",
                createdAt = now.plusSeconds(120),
                updatedAt = now.plusSeconds(120),
                updatedBy = "user1",
                deleted = false
            )
        )

        delay(100)

        // then - calculate balance from latest emission
        val transactions = flow.first()
        val income = transactions.filter { it.type == SharedTransactionType.INCOME }.sumOf { it.amount }
        val expense = transactions.filter { it.type == SharedTransactionType.EXPENSE }.sumOf { it.amount }
        val balance = income - expense

        transactions.size shouldBe 3
        income shouldBe 5000.0
        expense shouldBe 1500.0
        balance shouldBe 3500.0
    }

    @Test
    fun test_deletedTransactions_areFilteredOut() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val transactionId = SharedTransactionId(UUID.randomUUID())
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

        // Create account
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
        sharedAccountRepository.save(account)

        // Insert a transaction
        sharedTransactionRepository.save(
            SharedTransaction(
                id = transactionId,
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 100.0,
                title = NotBlankTrimmedString.unsafe("Test"),
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

        delay(100)

        // Verify it's there
        val flow = sharedTransactionRepository.observeBySharedAccountId(accountId, deleted = false)
        flow.first().size shouldBe 1

        // when - mark as deleted (simulating remote deletion synced from Firestore)
        sharedTransactionRepository.markAsDeleted(transactionId, deleted = true)

        delay(100)

        // then - should no longer appear when filtering deleted=false
        val afterDelete = flow.first()
        afterDelete.size shouldBe 0
    }

    @Test
    fun test_observeAll_reactsToSharedAccountChanges() = runTest {
        // given
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

        // Start observing
        val flow = sharedAccountRepository.observeAll()

        // when - create first account
        sharedAccountRepository.save(
            SharedAccount(
                id = SharedAccountId(UUID.randomUUID()),
                name = NotBlankTrimmedString.unsafe("Account 1"),
                currency = AssetCode.unsafe("USD"),
                owners = listOf("user1"),
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                linkedAccountId = null
            )
        )

        delay(100)

        // when - create second account
        sharedAccountRepository.save(
            SharedAccount(
                id = SharedAccountId(UUID.randomUUID()),
                name = NotBlankTrimmedString.unsafe("Account 2"),
                currency = AssetCode.unsafe("EUR"),
                owners = listOf("user2"),
                createdBy = "user2",
                createdAt = now.plusSeconds(60),
                updatedAt = now.plusSeconds(60),
                linkedAccountId = null
            )
        )

        delay(100)

        // then - should see both accounts
        val accounts = flow.first()
        accounts.size shouldBe 2
        accounts[0].name.value shouldBe "Account 2" // Sorted by createdAt DESC
        accounts[1].name.value shouldBe "Account 1"
    }

    @Test
    fun test_transactionSorting_byTimeDescending() = runTest {
        // given
        val accountId = SharedAccountId(UUID.randomUUID())
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)

        // Create account
        sharedAccountRepository.save(
            SharedAccount(
                id = accountId,
                name = NotBlankTrimmedString.unsafe("Test Account"),
                currency = AssetCode.unsafe("USD"),
                owners = listOf("user1"),
                createdBy = "user1",
                createdAt = now,
                updatedAt = now,
                linkedAccountId = null
            )
        )

        // Insert transactions in random order
        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 100.0,
                title = NotBlankTrimmedString.unsafe("Middle"),
                description = null,
                category = null,
                time = now.plusSeconds(60),
                createdBy = "user1",
                createdAt = now.plusSeconds(60),
                updatedAt = now.plusSeconds(60),
                updatedBy = "user1",
                deleted = false
            )
        )

        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 200.0,
                title = NotBlankTrimmedString.unsafe("Newest"),
                description = null,
                category = null,
                time = now.plusSeconds(120),
                createdBy = "user1",
                createdAt = now.plusSeconds(120),
                updatedAt = now.plusSeconds(120),
                updatedBy = "user1",
                deleted = false
            )
        )

        sharedTransactionRepository.save(
            SharedTransaction(
                id = SharedTransactionId(UUID.randomUUID()),
                sharedAccountId = accountId,
                type = SharedTransactionType.EXPENSE,
                amount = 50.0,
                title = NotBlankTrimmedString.unsafe("Oldest"),
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

        delay(100)

        // then - should be sorted by time DESC (DAO query does this)
        val flow = sharedTransactionRepository.observeBySharedAccountId(accountId, deleted = false)
        val transactions = flow.first()

        transactions.size shouldBe 3
        transactions[0].title?.value shouldBe "Newest"
        transactions[1].title?.value shouldBe "Middle"
        transactions[2].title?.value shouldBe "Oldest"
    }
}
