//! The player's half of the join handshake (ADR-0020).
//!
//! ash's backend proves who a player is the way a Minecraft server does: the
//! player's machine "joins" a server id at Mojang's session server, and the
//! backend asks Mojang whether that profile joined it. The Minecraft token
//! goes to Mojang and nowhere else.
//!
//! The server id is never one the backend hands over. It is derived here from
//! the backend's challenge, under a prefix no Minecraft server's login hash
//! starts from. A backend that could choose the server id could make the
//! player join a real server's login hash, then log in to that server as
//! them - a hole found in exactly this pattern in 2023 (research 0010 §1).

use sha1::{Digest, Sha1};

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest};

const JOIN_URL: &str = "https://sessionserver.mojang.com/session/minecraft/join";

/// What every ash server id is the hash of, before the challenge.
const PREFIX: &str = "ash-account-sign-in:";

/// The server id to join for one of the backend's challenges.
///
/// Minecraft's own digest: SHA-1, read as one signed big-endian number and
/// printed in lowercase hex without leading zeros, with a minus sign when it
/// is negative. The backend computes the same thing from the challenge it
/// issued, so the two can only agree if the player joined *this* challenge.
pub fn server_id_for(challenge: &str) -> String {
    let digest = Sha1::digest(format!("{PREFIX}{challenge}").as_bytes());
    minecraft_hex_digest(&digest)
}

fn minecraft_hex_digest(bytes: &[u8]) -> String {
    let negative = bytes[0] & 0x80 != 0;
    let mut magnitude = bytes.to_vec();
    if negative {
        // Two's complement: invert every bit, then add one.
        let mut carry = true;
        for byte in magnitude.iter_mut().rev() {
            *byte = !*byte;
            if carry {
                let (sum, overflow) = byte.overflowing_add(1);
                *byte = sum;
                carry = overflow;
            }
        }
    }
    let hex: String = magnitude.iter().map(|b| format!("{b:02x}")).collect();
    let trimmed = hex.trim_start_matches('0');
    let digits = if trimmed.is_empty() { "0" } else { trimmed };
    if negative {
        format!("-{digits}")
    } else {
        digits.to_owned()
    }
}

/// Join `server_id` as the profile `minecraft_token` belongs to.
pub(crate) async fn join(
    http: &dyn HttpPort,
    minecraft_token: &str,
    profile_id: &str,
    server_id: &str,
) -> Result<(), AshError> {
    let body = serde_json::json!({
        "accessToken": minecraft_token,
        "selectedProfile": profile_id,
        "serverId": server_id,
    });
    let response = http.send(HttpRequest::post_json(JOIN_URL, &body)?).await?;
    if response.is_success() {
        return Ok(());
    }

    // Mojang names the reason in the body's `error`, as the game shows it.
    let body = String::from_utf8_lossy(&response.body);
    if body.contains("InsufficientPrivilegesException") {
        Err(AshError::MultiplayerDisabled)
    } else if body.contains("UserBannedException") {
        Err(AshError::MultiplayerBanned)
    } else {
        Err(AshError::UnexpectedStatus { url: JOIN_URL.to_owned(), status: response.status })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn digest_of(text: &str) -> String {
        minecraft_hex_digest(&Sha1::digest(text.as_bytes()))
    }

    #[test]
    fn the_digest_matches_minecrafts_published_examples() {
        // The three examples every description of the protocol gives,
        // including one negative and one with a leading zero to drop.
        assert_eq!(digest_of("Notch"), "4ed1f46bbe04bc756bcb17c0c7ce3e4632f06a48");
        assert_eq!(digest_of("jeb_"), "-7c9d5b0044c130109a5d7b5fb5c317c02b4e28c1");
        assert_eq!(digest_of("simon"), "88e16a1019277b15d58faf0541e11910eb756f6");
    }

    #[test]
    fn a_server_id_is_the_digest_of_the_prefixed_challenge_and_nothing_else() {
        let challenge = "4f1c2a9b0e7d4c3a8b6f5e4d3c2b1a09";
        assert_eq!(
            server_id_for(challenge),
            digest_of(&format!("ash-account-sign-in:{challenge}"))
        );
        assert_ne!(
            server_id_for(challenge),
            digest_of(challenge),
            "the prefix is what makes it ash's"
        );
        assert_ne!(server_id_for(challenge), server_id_for("4f1c2a9b0e7d4c3a8b6f5e4d3c2b1a0a"));
    }
}
