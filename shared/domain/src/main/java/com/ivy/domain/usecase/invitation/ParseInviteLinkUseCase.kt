package com.ivy.domain.usecase.invitation

import android.net.Uri
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import javax.inject.Inject

/**
 * Use case for parsing invitation deep links
 * Extracts the token and email from invitation URLs
 */
class ParseInviteLinkUseCase @Inject constructor() {
    /**
     * Parses an invitation deep link to extract the token
     * Supports both custom scheme (ivywallet://invite) and HTTPS links
     * @param uriString The invitation link URI
     * @return Either an error message or the invitation data
     */
    operator fun invoke(uriString: String): Either<String, InvitationLinkData> = either {
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            raise("Invalid URI: ${e.message}")
        }

        // Validate scheme
        val scheme = uri.scheme?.lowercase()
        ensure(scheme == "ivywallet" || scheme == "https") {
            "Invalid scheme: $scheme. Expected 'ivywallet' or 'https'"
        }

        // Validate host
        val host = uri.host?.lowercase()
        val validHost = when (scheme) {
            "ivywallet" -> host == "invite"
            "https" -> host == "ivywallet.app"
            else -> false
        }
        ensure(validHost) {
            "Invalid host: $host for scheme: $scheme"
        }

        // Validate path for HTTPS links
        if (scheme == "https") {
            val path = uri.path
            ensure(path?.startsWith("/invite") == true) {
                "Invalid path: $path. Expected /invite"
            }
        }

        // Extract token
        val token = uri.getQueryParameter("token")
        ensure(!token.isNullOrBlank()) {
            "Missing or empty token parameter"
        }

        // Extract email (optional)
        val email = uri.getQueryParameter("email")

        InvitationLinkData(
            token = token,
            inviteeEmail = email
        )
    }
}

/**
 * Data extracted from an invitation link
 */
data class InvitationLinkData(
    val token: String,
    val inviteeEmail: String?
)
