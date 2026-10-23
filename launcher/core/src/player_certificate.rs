//! The player's half of signing in to ash (ADR-0020, amended 2026-10-23).
//!
//! Mojang gives every player a key for signed chat, and certifies it: it
//! signs the player's UUID, the key's expiry and the key, with a key of its
//! own that it publishes. ash's backend checks that certificate and a
//! signature over its challenge made with the player's key, offline. It never
//! asks Mojang, which matters because Mojang's firewall refuses Cloudflare.
//!
//! The Minecraft token goes to Mojang, for the certificate, and nowhere else.
//! What reaches ash is a public key, Mojang's signature and ash's own
//! challenge signed - none of which signs anyone in to anything but ash. The
//! signed text starts `ash-account-sign-in:`, which no chat message's signed
//! bytes can (they start with a version number), so the signature can't be
//! passed off as the player having said something.
//!
//! The certificate's parts are read as the game reads them (1.21.11,
//! `AccountProfileKeyPairManager` and `Crypt`): PEM keys whose private half
//! is PKCS#8 under an "RSA PRIVATE KEY" label, an ISO 8601 expiry taken to
//! the millisecond, and SHA256withRSA for what the player signs.

use aws_lc_rs::rand::SystemRandom;
use aws_lc_rs::signature::{RsaKeyPair, RSA_PKCS1_SHA256};
use base64::engine::general_purpose::STANDARD;
use base64::Engine;
use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest};

pub(crate) const CERTIFICATES_URL: &str = "https://api.minecraftservices.com/player/certificates";

/// What the player signs, before ash's challenge.
const SIGNED_PREFIX: &str = "ash-account-sign-in:";

/// What `/player/certificates` answers, the parts ash uses.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct CertificateBody {
    key_pair: KeyPairBody,
    public_key_signature_v2: String,
    expires_at: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct KeyPairBody {
    private_key: String,
    public_key: String,
}

/// The player's key, and Mojang's word for it.
pub(crate) struct Certificate {
    private_key: Vec<u8>,
    /// The public key's X.509 bytes, as Mojang signed them.
    public_key: Vec<u8>,
    /// Milliseconds since the epoch, as Mojang signed it.
    expires_at: i64,
    /// Mojang's signature, `publicKeySignatureV2`.
    mojang_signature: Vec<u8>,
}

/// What goes to ash's backend: the certificate, and the challenge signed.
#[derive(Debug, Serialize)]
pub(crate) struct Proof {
    pub certificate: ProofCertificate,
    pub signature: String,
}

#[derive(Debug, Serialize)]
pub(crate) struct ProofCertificate {
    pub public_key: String,
    pub expires_at: i64,
    pub signature: String,
}

/// The player's certificate, from Mojang, with their Minecraft token.
pub(crate) async fn fetch(
    http: &dyn HttpPort,
    minecraft_token: &str,
) -> Result<Certificate, AshError> {
    let request =
        HttpRequest::post_json(CERTIFICATES_URL, &serde_json::json!({}))?.bearer(minecraft_token);
    let response = http.send(request).await?;
    if !response.is_success() {
        // Mojang names the reason in the body's `error`, as the game shows it.
        let body = String::from_utf8_lossy(&response.body);
        return Err(if body.contains("InsufficientPrivilegesException") {
            AshError::MultiplayerDisabled
        } else if body.contains("UserBannedException") {
            AshError::MultiplayerBanned
        } else {
            AshError::UnexpectedStatus { url: CERTIFICATES_URL.to_owned(), status: response.status }
        });
    }
    let body: CertificateBody = serde_json::from_slice(&response.body).map_err(malformed)?;
    read(body)
}

fn read(body: CertificateBody) -> Result<Certificate, AshError> {
    Ok(Certificate {
        private_key: pem(&body.key_pair.private_key)?,
        public_key: pem(&body.key_pair.public_key)?,
        expires_at: epoch_millis(&body.expires_at)?,
        mojang_signature: STANDARD
            .decode(body.public_key_signature_v2.trim())
            .map_err(|e| malformed(format!("publicKeySignatureV2: {e}")))?,
    })
}

/// The certificate, and ash's challenge signed with its key.
pub(crate) fn prove(certificate: &Certificate, challenge: &str) -> Result<Proof, AshError> {
    let key = RsaKeyPair::from_pkcs8(&certificate.private_key)
        .map_err(|e| malformed(format!("the player's private key: {e}")))?;
    let mut signature = vec![0; key.public_modulus_len()];
    key.sign(
        &RSA_PKCS1_SHA256,
        &SystemRandom::new(),
        format!("{SIGNED_PREFIX}{challenge}").as_bytes(),
        &mut signature,
    )
    .map_err(|_| malformed("signing with the player's key failed"))?;
    Ok(Proof {
        certificate: ProofCertificate {
            public_key: STANDARD.encode(&certificate.public_key),
            expires_at: certificate.expires_at,
            signature: STANDARD.encode(&certificate.mojang_signature),
        },
        signature: STANDARD.encode(signature),
    })
}

/// A PEM block's bytes, whatever its label, as the game's MIME decoder reads it.
fn pem(text: &str) -> Result<Vec<u8>, AshError> {
    let body: String = text
        .lines()
        .filter(|line| !line.starts_with("-----"))
        .flat_map(|line| line.chars())
        .filter(|c| !c.is_whitespace())
        .collect();
    if body.is_empty() {
        return Err(malformed("a PEM key with nothing in it"));
    }
    STANDARD.decode(body).map_err(|e| malformed(format!("a PEM key: {e}")))
}

/// An ISO 8601 time as milliseconds since the epoch, rounded down as Java's
/// `Instant.toEpochMilli` does - the number Mojang signed.
fn epoch_millis(text: &str) -> Result<i64, AshError> {
    let at = time::OffsetDateTime::parse(text, &time::format_description::well_known::Rfc3339)
        .map_err(|e| malformed(format!("expiresAt {text:?}: {e}")))?;
    Ok(at.unix_timestamp_nanos().div_euclid(1_000_000) as i64)
}

fn malformed(detail: impl std::fmt::Display) -> AshError {
    AshError::Malformed { url: CERTIFICATES_URL.to_owned(), detail: detail.to_string() }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_expiry_is_taken_to_the_millisecond_rounding_down_as_java_does() {
        assert_eq!(epoch_millis("1970-01-01T00:00:01.999999Z").unwrap(), 1_999);
        assert_eq!(epoch_millis("2026-10-25T16:20:53Z").unwrap(), 1_792_945_253_000);
        assert_eq!(epoch_millis("2026-10-25T16:20:53.123456Z").unwrap(), 1_792_945_253_123);
        assert!(epoch_millis("next tuesday").is_err());
    }

    #[test]
    fn a_pem_key_is_read_whatever_its_label_and_line_breaks() {
        let wrapped = "-----BEGIN RSA PUBLIC KEY-----\nAAEC\nAwQ=\n-----END RSA PUBLIC KEY-----\n";
        assert_eq!(pem(wrapped).unwrap(), vec![0, 1, 2, 3, 4]);
        assert_eq!(
            pem("-----BEGIN X-----\r\nAAECAwQ=\r\n-----END X-----").unwrap(),
            vec![0, 1, 2, 3, 4]
        );
        assert!(
            pem("-----BEGIN X-----
-----END X-----")
            .is_err(),
            "no key at all"
        );
    }
}
