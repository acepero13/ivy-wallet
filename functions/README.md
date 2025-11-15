# Ivy Wallet Firebase Cloud Functions

This directory contains Firebase Cloud Functions for managing shared account invitations.

## Setup

1. Install dependencies:
```bash
cd functions
npm install
```

2. Configure Firebase project:
```bash
firebase use <your-project-id>
```

## Functions

### `createInvitation`
Creates a new invitation for a shared account.

**Input:**
- `sharedAccountId`: The ID of the shared account
- `inviteeEmail`: Email of the person being invited
- `token`: Secure token for the invitation
- `expiresAt`: Expiration timestamp (milliseconds)

**Returns:**
- `invitationId`: The created invitation ID
- `success`: Boolean indicating success

**Security:**
- Requires authentication
- Validates that the caller is an owner of the shared account

### `acceptInvitation`
Accepts an invitation and adds the user to the shared account.

**Input:**
- `token`: The invitation token

**Returns:**
- `sharedAccountId`: The ID of the shared account joined
- `success`: Boolean indicating success

**Security:**
- Requires authentication
- Validates invitation status (PENDING)
- Validates expiration
- Optionally validates email match
- Uses transaction for atomicity

### `revokeInvitation`
Revokes a pending invitation.

**Input:**
- `invitationId`: The ID of the invitation to revoke

**Returns:**
- `success`: Boolean indicating success

**Security:**
- Requires authentication
- Only the invitation creator can revoke
- Only PENDING invitations can be revoked

### `expireOldInvitations` (Scheduled)
Runs daily to expire old invitations.

**Schedule:** Every 24 hours

## Testing Locally

Use the Firebase emulator:
```bash
npm run serve
```

## Deployment

Deploy to Firebase:
```bash
npm run deploy
```

## Security Rules

Ensure Firestore security rules are set up properly to restrict direct database access. All invitation operations should go through these Cloud Functions for proper validation.
