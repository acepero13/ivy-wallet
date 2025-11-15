package com.ivy.domain.usecase.invitation

import android.net.Uri
import arrow.core.Either
import arrow.core.right
import com.ivy.data.model.Invitation
import javax.inject.Inject

/**
 * Use case for generating invitation deep links
 */
class GenerateInviteLinkUseCase @Inject constructor() {
    /**
     * Generates a deep link for an invitation
     * @param invitation The invitation to generate a link for
     * @param baseUrl The base URL for deep links (default: ivywallet://invite)
     * @return The invitation deep link
     */
    operator fun invoke(
        invitation: Invitation,
        baseUrl: String = "ivywallet://invite"
    ): Either<String, String> {
        val uri = Uri.parse(baseUrl)
            .buildUpon()
            .appendQueryParameter("token", invitation.token)
            .appendQueryParameter("email", invitation.inviteeEmail)
            .build()

        return uri.toString().right()
    }

    /**
     * Generates a shareable HTTPS link for an invitation
     * @param invitation The invitation to generate a link for
     * @param domain The domain for the web link (default: ivywallet.app)
     * @return The invitation web link
     */
    fun generateWebLink(
        invitation: Invitation,
        domain: String = "ivywallet.app"
    ): Either<String, String> {
        val uri = Uri.parse("https://$domain/invite")
            .buildUpon()
            .appendQueryParameter("token", invitation.token)
            .appendQueryParameter("email", invitation.inviteeEmail)
            .build()

        return uri.toString().right()
    }
}
