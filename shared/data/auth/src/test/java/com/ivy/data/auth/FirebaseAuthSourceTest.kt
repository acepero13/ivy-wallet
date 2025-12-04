package com.ivy.data.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FirebaseAuthSource.
 * Tests authentication operations with mocked FirebaseAuth.
 */
class FirebaseAuthSourceTest {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var authSource: FirebaseAuthSource

    @Before
    fun setup() {
        firebaseAuth = mockk(relaxed = true)
        authSource = FirebaseAuthSource(firebaseAuth)
    }

    @Test
    fun `getCurrentUserOnce returns Success when user is signed in`() = runTest {
        // Given
        val mockUser = mockFirebaseUser(
            uid = "test-uid",
            email = "test@example.com",
            displayName = "Test User",
            isEmailVerified = true
        )
        every { firebaseAuth.currentUser } returns mockUser

        // When
        val result = authSource.getCurrentUserOnce()

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Success>()
        val authUser = (result as com.ivy.data.auth.AuthResult.Success).user
        authUser.uid shouldBe "test-uid"
        authUser.email shouldBe "test@example.com"
        authUser.displayName shouldBe "Test User"
        authUser.isEmailVerified shouldBe true
    }

    @Test
    fun `getCurrentUserOnce returns NotAuthenticated when no user is signed in`() = runTest {
        // Given
        every { firebaseAuth.currentUser } returns null

        // When
        val result = authSource.getCurrentUserOnce()

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.NotAuthenticated>()
    }

    @Test
    fun `signInWithEmailAndPassword returns Success on successful sign in`() = runTest {
        // Given
        val mockUser = mockFirebaseUser(
            uid = "test-uid",
            email = "test@example.com",
            displayName = null,
            isEmailVerified = false
        )
        val mockAuthResult = mockk<AuthResult> {
            every { user } returns mockUser
        }
        val mockTask = mockSuccessTask(mockAuthResult)
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns mockTask

        // When
        val result = authSource.signInWithEmailAndPassword("test@example.com", "password123")

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Success>()
        val authUser = (result as com.ivy.data.auth.AuthResult.Success).user
        authUser.uid shouldBe "test-uid"
        authUser.email shouldBe "test@example.com"
    }

    @Test
    fun `signInWithEmailAndPassword returns Error on failure`() = runTest {
        // Given
        val exception = Exception("Invalid credentials")
        val mockTask = mockFailureTask<AuthResult>(exception)
        every {
            firebaseAuth.signInWithEmailAndPassword(any(), any())
        } returns mockTask

        // When
        val result = authSource.signInWithEmailAndPassword("test@example.com", "wrong-password")

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Error>()
        val error = result as com.ivy.data.auth.AuthResult.Error
        error.message shouldBe "Invalid credentials"
        error.exception shouldBe exception
    }

    @Test
    fun `createUserWithEmailAndPassword returns Success on successful account creation`() = runTest {
        // Given
        val mockUser = mockFirebaseUser(
            uid = "new-uid",
            email = "newuser@example.com",
            displayName = null,
            isEmailVerified = false
        )
        val mockAuthResult = mockk<AuthResult> {
            every { user } returns mockUser
        }
        val mockTask = mockSuccessTask(mockAuthResult)
        val mockUpdateTask = mockSuccessTask<Void?>(null)

        every {
            firebaseAuth.createUserWithEmailAndPassword(any(), any())
        } returns mockTask
        every { mockUser.updateProfile(any()) } returns mockUpdateTask

        // When
        val result = authSource.createUserWithEmailAndPassword(
            email = "newuser@example.com",
            password = "password123",
            displayName = "New User"
        )

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Success>()
        val authUser = (result as com.ivy.data.auth.AuthResult.Success).user
        authUser.uid shouldBe "new-uid"
        authUser.email shouldBe "newuser@example.com"

        // Verify updateProfile was called when displayName is provided
        verify { mockUser.updateProfile(any()) }
    }

    @Test
    fun `createUserWithEmailAndPassword returns Error on failure`() = runTest {
        // Given
        val exception = Exception("Email already exists")
        val mockTask = mockFailureTask<AuthResult>(exception)
        every {
            firebaseAuth.createUserWithEmailAndPassword(any(), any())
        } returns mockTask

        // When
        val result = authSource.createUserWithEmailAndPassword(
            email = "existing@example.com",
            password = "password123"
        )

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Error>()
        val error = result as com.ivy.data.auth.AuthResult.Error
        error.message shouldBe "Email already exists"
    }

    @Test
    fun `sendSignInLinkToEmail returns Success on successful email send`() = runTest {
        // Given
        val mockTask = mockSuccessTask<Void?>(null)
        every {
            firebaseAuth.sendSignInLinkToEmail(any(), any())
        } returns mockTask

        // When
        val result = authSource.sendSignInLinkToEmail("test@example.com")

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Success>()
        val authUser = (result as com.ivy.data.auth.AuthResult.Success).user
        authUser.email shouldBe "test@example.com"
    }

    @Test
    fun `sendSignInLinkToEmail returns Error on failure`() = runTest {
        // Given
        val exception = Exception("Network error")
        val mockTask = mockFailureTask<Void>(exception)
        every {
            firebaseAuth.sendSignInLinkToEmail(any(), any())
        } returns mockTask

        // When
        val result = authSource.sendSignInLinkToEmail("test@example.com")

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Error>()
        val error = result as com.ivy.data.auth.AuthResult.Error
        error.message shouldBe "Network error"
    }

    @Test
    fun `signOut returns NotAuthenticated on success`() = runTest {
        // Given
        every { firebaseAuth.signOut() } returns Unit

        // When
        val result = authSource.signOut()

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.NotAuthenticated>()
        verify { firebaseAuth.signOut() }
    }

    @Test
    fun `deleteAccount returns NotAuthenticated on successful deletion`() = runTest {
        // Given
        val mockUser = mockFirebaseUser(
            uid = "test-uid",
            email = "test@example.com",
            displayName = null,
            isEmailVerified = false
        )
        val mockTask = mockSuccessTask<Void?>(null)
        every { firebaseAuth.currentUser } returns mockUser
        every { mockUser.delete() } returns mockTask

        // When
        val result = authSource.deleteAccount()

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.NotAuthenticated>()
        verify { mockUser.delete() }
    }

    @Test
    fun `deleteAccount returns Error when no user is signed in`() = runTest {
        // Given
        every { firebaseAuth.currentUser } returns null

        // When
        val result = authSource.deleteAccount()

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Error>()
        val error = result as com.ivy.data.auth.AuthResult.Error
        error.message shouldBe "Cannot delete account: No user is signed in"
    }

    @Test
    fun `deleteAccount returns Error on failure`() = runTest {
        // Given
        val mockUser = mockFirebaseUser(
            uid = "test-uid",
            email = "test@example.com",
            displayName = null,
            isEmailVerified = false
        )
        val exception = Exception("Requires recent login")
        val mockTask = mockFailureTask<Void>(exception)
        every { firebaseAuth.currentUser } returns mockUser
        every { mockUser.delete() } returns mockTask

        // When
        val result = authSource.deleteAccount()

        // Then
        result.shouldBeInstanceOf<com.ivy.data.auth.AuthResult.Error>()
        val error = result as com.ivy.data.auth.AuthResult.Error
        error.message shouldBe "Requires recent login"
    }

    // Helper functions for mocking Firebase types

    private fun mockFirebaseUser(
        uid: String,
        email: String?,
        displayName: String?,
        isEmailVerified: Boolean
    ): FirebaseUser {
        return mockk {
            every { this@mockk.uid } returns uid
            every { this@mockk.email } returns email
            every { this@mockk.displayName } returns displayName
            every { this@mockk.isEmailVerified } returns isEmailVerified
        }
    }

    private fun <T> mockSuccessTask(result: T): Task<T> {
        return mockk {
            every { isComplete } returns true
            every { isSuccessful } returns true
            every { isCanceled } returns false
            every { exception } returns null
            every { getResult() } returns result
        }
    }

    private fun <T> mockFailureTask(exception: Exception): Task<T> {
        return mockk {
            every { isComplete } returns true
            every { isSuccessful } returns false
            every { isCanceled } returns false
            every { this@mockk.exception } returns exception
            every { getResult() } throws exception
        }
    }
}
