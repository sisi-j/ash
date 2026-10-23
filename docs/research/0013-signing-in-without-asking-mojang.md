# Research 0013: signing in without asking Mojang

23 October 2026. Why the join handshake (ADR-0020) couldn't sign anyone in from Cloudflare, and how players prove who they are instead.

## 1. Mojang refuses Cloudflare Workers

The join handshake ends with the backend calling Mojang's `hasJoined`. From the deployed Worker, every call failed in about 0.15 s, on both production and staging. Once the backend kept Mojang's answer (sisi-j/ash-backend#15, #16), it was **403** with an HTML page titled **"Service unavailable"**. The User-Agent made no difference.

The same request from a home connection answered normally: 204, with or without a User-Agent. So the refusal depends on where the request comes from.

This matches a known pattern. Mojang's APIs sit behind Microsoft Azure's firewall, which blocks some cloud providers' addresses. Others have reported the same 403 "The request is blocked" page from cloud hosts ([Railway](https://station.railway.com/questions/microsoft-mojang-api-requests-bouncing-a13486c8), [a launcher log on Mojang's tracker](https://bugs.mojang.com/api/issue-attachment-get?attachmentId=556875)). Mojang hasn't said anything about it officially. Cloudflare can't change it: a block on a Worker's outbound requests is the target site's rule ([Cloudflare community](https://community.cloudflare.com/t/cloudflare-blocking-cloudflare-workers-from-making-requests/528590)).

So nothing on Cloudflare can ask Mojang anything. That includes the public keys, which is why section 4 bundles them.

## 2. The key Mojang gives every player

Since 1.19, Mojang gives every player a key pair for signed chat, and certifies the public half. Read from the game's own code (1.21.11, named jar, `javap`) and authlib 7.0.61's sources:

| Part | Where | What |
| --- | --- | --- |
| The certificate | `POST api.minecraftservices.com/player/certificates`, with the Minecraft token | `keyPair.privateKey` and `keyPair.publicKey` as PEM, `publicKeySignatureV2` as base64, and `expiresAt` and `refreshedAfter` as ISO 8601 (authlib `KeyPairResponse`) |
| The private key | `Crypt.stringToPemRsaPrivateKey` | Labelled `RSA PRIVATE KEY`, but PKCS#8 inside (`PKCS8EncodedKeySpec`), read with Java's MIME base64 decoder |
| The public key | `Crypt.byteToPublicKey` | X.509 SubjectPublicKeyInfo (`X509EncodedKeySpec`), labelled `RSA PUBLIC KEY` |
| What Mojang signs | `ProfilePublicKey.Data.signedPayload(UUID)` | The UUID's most then least significant 8 bytes, then `expiresAt.toEpochMilli()` as 8 bytes, then the public key's X.509 bytes, all big-endian |
| How Mojang signs it | authlib `YggdrasilServicesKeyInfo` | SHA1withRSA, with one of the `playerCertificateKeys` from `api.minecraftservices.com/publickeys` (X.509, base64) |
| How the player signs | `Crypt.SIGNING_ALGORITHM` | SHA256withRSA |
| The expiry | `AccountProfileKeyPairManager` | `Instant.parse(expiresAt)`, signed to the millisecond and rounded down. Mojang writes microseconds |

## 3. The sign-in

1. The launcher asks ash's backend for a challenge, as before.
2. It gets the player's certificate from Mojang with the Minecraft token. This is the call the game makes at every launch, made from the player's own connection.
3. It signs `ash-account-sign-in:` followed by the challenge with the player's private key, SHA256withRSA.
4. It sends the backend the challenge, the UUID, the name, the public key, the expiry in milliseconds, Mojang's signature, and its own signature.
5. The backend checks, in order:
   - the challenge (its own, unused, unexpired);
   - the key's expiry;
   - Mojang's signature over the UUID, expiry and key, against Mojang's keys;
   - the player's signature over the challenge, with that key.

   Only then does it claim the challenge and issue a session.

What this gives:
- **Only that UUID's key holder can pass.** Mojang certified the key for that UUID only, and the challenge is signed with the private half, which never leaves the player's machine.
- **Nothing is asked of Mojang by the backend**, so its firewall doesn't matter.
- **No Minecraft or Microsoft token reaches ash**, as before. The token goes to Mojang, for the certificate.
- **The signature can't be passed off as chat.** The game signs chat over bytes that begin with a version number (an int, 1); ash's signed text begins `ash-account-sign-in:`. And the key only ever signs chat, so it has no other power for ash to misuse.
- **The username is the player's word.** The certificate carries no name, and the backend can't look one up. It's kept for display only; nothing is keyed or decided on it.

## 4. Mojang's keys

`publickeys` currently lists two `playerCertificateKeys`, each a 4096-bit RSA key. The backend bundles them (`src/mojang-keys.json`, fetched 23 October 2026), since it can't fetch them itself. They are the keys every Minecraft server uses to check chat, so a change would break chat everywhere. If they ever change, sign-ins fail with `certificate_invalid`, and the fix is to fetch them again from any home connection and redeploy.

## 5. Checked

- **Backend:** in workerd, with a fake Mojang that really signs. Tests cover:
  - a known-answer test of the signed bytes;
  - the bundled keys importing as SHA-1 verify keys;
  - impostor keys, a changed expiry, an expired key, a key offered for another UUID, and a challenge signed by another key or over another challenge.
- **Launcher:**
  - reading a Mojang-shaped answer (Mojang's labels, a microsecond expiry);
  - passing on the key, Mojang's signature and the expiry exactly;
  - a signature checked with an independent verifier;
  - the token going to Mojang only.

Not yet checked: a real certificate end to end. The first real sign-in after this ships is that check, and the launcher's log line `ash-sign-in` says the outcome, with the backend's reason if it fails.
