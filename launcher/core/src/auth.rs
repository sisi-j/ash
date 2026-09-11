//! The Microsoft sign-in chain, hop by hop.
//!
//! Hops 1 and 2 are documented by Microsoft. Hops 3 to 7 are
//! community-reverse-engineered - there is no first-party documentation for
//! `user.auth.xboxlive.com` onward - and the shapes here come from
//! `docs/research/0001-minecraft-launcher-api-access.md`, cross-checked
//! against three shipping launchers.
//!
//! Hops 5 to 7 return `403 Invalid app registration` until ash's client id is
//! on Mojang's allow list, so in practice only hops 1 to 4 can be exercised
//! against the real services today. Everything here is fixture-driven.

use serde::Deserialize;

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest, HttpResponse};

// ---- endpoints -------------------------------------------------------------

/// `consumers`, not `common`: the XboxLive.signin scope requires the personal
/// Microsoft account tenant. All three reference launchers hard-code this.
pub const DEVICE_CODE_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
pub const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
pub const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
pub const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";

/// Two variants of hops 5 and 6 are in live production use. ash takes the
/// Prism/ATLauncher pairing: `PC_LAUNCHER` is what ash actually is, and two
/// independent implementations agree on it. Both sit behind the HTTP port, so
/// switching is a one-line change if Mojang ever retires one.
pub const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/launcher/login";
pub const MC_ENTITLEMENTS_URL: &str = "https://api.minecraftservices.com/entitlements/license";
pub const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

const SCOPES: &str = "XboxLive.signin offline_access";
const DEVICE_CODE_GRANT: &str = "urn:ietf:params:oauth:grant-type:device_code";

// ---- what the chain produces ----------------------------------------------

/// A completed sign-in: who the player is, and what lets ash act as them.
#[derive(Debug, Clone)]
pub(crate) struct Session {
    /// The undashed Minecraft profile UUID. ADR-0009's account key.
    pub profile_id: String,
    pub username: String,
    pub skin_url: Option<String>,
    /// Kept only if Microsoft issued one. Goes to the credential store,
    /// never to a file ash writes.
    pub refresh_token: Option<String>,
    // The Minecraft access token is used by hops 6 and 7 and then dropped.
    // Launch (#8) is the first thing that needs to hold on to it; storing it
    // here before then would be state nothing reads.
}

/// A sign-in waiting on the player to approve it in their browser.
#[derive(Debug, Clone)]
pub(crate) struct Pending {
    pub user_code: String,
    pub verification_uri: String,
    pub expires_at_ms: u64,
    pub interval_secs: u64,
    pub device_code: String,
}

pub(crate) enum Poll {
    /// Still waiting. Carries the interval to wait before asking again,
    /// which the service can raise via `slow_down`.
    Waiting { interval_secs: u64 },
    Complete(Box<Session>),
}

// ---- wire shapes -----------------------------------------------------------

#[derive(Deserialize)]
struct DeviceCodeResponse {
    device_code: String,
    user_code: String,
    verification_uri: String,
    expires_in: u64,
    interval: u64,
}

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    refresh_token: Option<String>,
}

#[derive(Deserialize)]
struct TokenError {
    error: String,
}

#[derive(Deserialize)]
struct XboxResponse {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: XboxDisplayClaims,
}

#[derive(Deserialize)]
struct XboxDisplayClaims {
    xui: Vec<XboxUserHash>,
}

#[derive(Deserialize)]
struct XboxUserHash {
    uhs: String,
}

#[derive(Deserialize)]
struct XstsError {
    #[serde(rename = "XErr")]
    xerr: u64,
}

#[derive(Deserialize)]
struct MinecraftLoginResponse {
    access_token: String,
}

#[derive(Deserialize)]
struct EntitlementsResponse {
    items: Vec<EntitlementItem>,
}

#[derive(Deserialize)]
struct EntitlementItem {
    name: String,
}

#[derive(Deserialize)]
struct ProfileResponse {
    id: String,
    name: String,
    #[serde(default)]
    skins: Vec<ProfileSkin>,
}

#[derive(Deserialize)]
struct ProfileSkin {
    state: String,
    url: String,
}

// ---- helpers ---------------------------------------------------------------

fn decode<T: serde::de::DeserializeOwned>(url: &str, body: &[u8]) -> Result<T, AshError> {
    serde_json::from_slice(body)
        .map_err(|e| AshError::Malformed { url: url.to_owned(), detail: e.to_string() })
}

fn require_success(url: &str, response: &HttpResponse) -> Result<(), AshError> {
    if response.is_success() {
        Ok(())
    } else {
        Err(AshError::UnexpectedStatus { url: url.to_owned(), status: response.status })
    }
}

/// `403 Invalid app registration` is not a generic failure - it is ash's own
/// allow-list gate, and saying "try again" would be actively misleading.
fn minecraft_status(url: &str, response: &HttpResponse) -> Result<(), AshError> {
    match response.status {
        s if (200..300).contains(&s) => Ok(()),
        403 => Err(AshError::NotAllowListed),
        s => Err(AshError::UnexpectedStatus { url: url.to_owned(), status: s }),
    }
}

fn xerr_to_error(code: u64) -> AshError {
    match code {
        2148916227 => AshError::XboxBanned,
        2148916233 => AshError::XboxNoAccount,
        2148916235 => AshError::XboxRegionUnavailable,
        2148916236 | 2148916237 => AshError::XboxAdultVerificationRequired,
        2148916238 => AshError::XboxChildAccount,
        other => AshError::XboxOther { code: other },
    }
}

// ---- hop 1: device code ----------------------------------------------------

pub(crate) async fn begin(
    http: &dyn HttpPort,
    client_id: &str,
    now_ms: u64,
) -> Result<Pending, AshError> {
    let request =
        HttpRequest::post_form(DEVICE_CODE_URL, &[("client_id", client_id), ("scope", SCOPES)]);
    let response = http.send(request).await?;
    require_success(DEVICE_CODE_URL, &response)?;

    let decoded: DeviceCodeResponse = decode(DEVICE_CODE_URL, &response.body)?;
    Ok(Pending {
        user_code: decoded.user_code,
        verification_uri: decoded.verification_uri,
        expires_at_ms: now_ms + decoded.expires_in * 1000,
        interval_secs: decoded.interval,
        device_code: decoded.device_code,
    })
}

// ---- hop 2: poll for the Microsoft token -----------------------------------

pub(crate) async fn poll(
    http: &dyn HttpPort,
    client_id: &str,
    pending: &Pending,
    now_ms: u64,
) -> Result<Poll, AshError> {
    if now_ms >= pending.expires_at_ms {
        return Err(AshError::SignInExpired);
    }

    let request = HttpRequest::post_form(
        TOKEN_URL,
        &[
            ("grant_type", DEVICE_CODE_GRANT),
            ("client_id", client_id),
            ("device_code", &pending.device_code),
        ],
    );
    let response = http.send(request).await?;

    if !response.is_success() {
        let error: TokenError = decode(TOKEN_URL, &response.body)?;
        return match error.error.as_str() {
            "authorization_pending" => Ok(Poll::Waiting { interval_secs: pending.interval_secs }),
            // Not in Microsoft's documented error table, but RFC 8628 defines
            // it and services send it. Backing off is the correct response;
            // treating it as fatal would fail a sign-in that was fine.
            "slow_down" => Ok(Poll::Waiting { interval_secs: pending.interval_secs + 5 }),
            "authorization_declined" => Err(AshError::SignInDeclined),
            "expired_token" => Err(AshError::SignInExpired),
            other => Err(AshError::SignInFailed { detail: other.to_owned() }),
        };
    }

    let token: TokenResponse = decode(TOKEN_URL, &response.body)?;
    let session = complete_chain(http, &token.access_token, token.refresh_token).await?;
    Ok(Poll::Complete(Box::new(session)))
}

// ---- refresh ---------------------------------------------------------------

pub(crate) async fn refresh(
    http: &dyn HttpPort,
    client_id: &str,
    refresh_token: &str,
) -> Result<Session, AshError> {
    let request = HttpRequest::post_form(
        TOKEN_URL,
        &[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", refresh_token),
            ("scope", SCOPES),
        ],
    );
    let response = http.send(request).await?;

    // A refresh token that Microsoft rejects is dead: the player has to sign
    // in again, and no amount of retrying changes that.
    if !response.is_success() {
        return Err(AshError::SessionExpired);
    }

    let token: TokenResponse = decode(TOKEN_URL, &response.body)?;
    complete_chain(http, &token.access_token, token.refresh_token).await
}

// ---- hops 3 to 7 -----------------------------------------------------------

async fn complete_chain(
    http: &dyn HttpPort,
    microsoft_token: &str,
    refresh_token: Option<String>,
) -> Result<Session, AshError> {
    let (xbl_token, _) = xbox_live(http, microsoft_token).await?;
    let (xsts_token, user_hash) = xsts(http, &xbl_token).await?;
    let minecraft_token = minecraft_login(http, &user_hash, &xsts_token).await?;
    check_entitlement(http, &minecraft_token).await?;
    let profile = profile(http, &minecraft_token).await?;

    Ok(Session {
        profile_id: profile.0,
        username: profile.1,
        skin_url: profile.2,
        refresh_token,
    })
}

/// Hop 3. Note the `d=` prefix on the ticket - every reference implementation
/// carries it and sign-in fails silently without it.
async fn xbox_live(http: &dyn HttpPort, microsoft_token: &str) -> Result<(String, String), AshError> {
    let body = serde_json::json!({
        "Properties": {
            "AuthMethod": "RPS",
            "SiteName": "user.auth.xboxlive.com",
            "RpsTicket": format!("d={microsoft_token}"),
        },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT",
    });
    let response = http.send(HttpRequest::post_json(XBL_URL, &body)?).await?;
    require_success(XBL_URL, &response)?;

    let decoded: XboxResponse = decode(XBL_URL, &response.body)?;
    let hash = decoded
        .display_claims
        .xui
        .first()
        .map(|x| x.uhs.clone())
        .ok_or(AshError::SignInFailed { detail: "no user hash in the Xbox response".into() })?;
    Ok((decoded.token, hash))
}

/// Hop 4. A 401 here carries an `XErr` code, and those map to real,
/// user-facing situations - several of which no retry will fix.
async fn xsts(http: &dyn HttpPort, xbl_token: &str) -> Result<(String, String), AshError> {
    let body = serde_json::json!({
        "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT",
    });
    let response = http.send(HttpRequest::post_json(XSTS_URL, &body)?).await?;

    if response.status == 401 {
        let error: XstsError = decode(XSTS_URL, &response.body)?;
        return Err(xerr_to_error(error.xerr));
    }
    require_success(XSTS_URL, &response)?;

    let decoded: XboxResponse = decode(XSTS_URL, &response.body)?;
    let hash = decoded
        .display_claims
        .xui
        .first()
        .map(|x| x.uhs.clone())
        .ok_or(AshError::SignInFailed { detail: "no user hash in the XSTS response".into() })?;
    Ok((decoded.token, hash))
}

/// Hop 5. The first hop the allow list gates.
async fn minecraft_login(
    http: &dyn HttpPort,
    user_hash: &str,
    xsts_token: &str,
) -> Result<String, AshError> {
    let body = serde_json::json!({
        "xtoken": format!("XBL3.0 x={user_hash};{xsts_token}"),
        "platform": "PC_LAUNCHER",
    });
    let response = http.send(HttpRequest::post_json(MC_LOGIN_URL, &body)?).await?;
    minecraft_status(MC_LOGIN_URL, &response)?;

    let decoded: MinecraftLoginResponse = decode(MC_LOGIN_URL, &response.body)?;
    Ok(decoded.access_token)
}

/// Hop 6. The mandatory entitlement check, and the only hop that can tell
/// "signed in but owns no copy" apart from "sign-in failed".
async fn check_entitlement(http: &dyn HttpPort, minecraft_token: &str) -> Result<(), AshError> {
    let url = format!("{MC_ENTITLEMENTS_URL}?requestId={}", uuid::Uuid::new_v4());
    let response = http.send(HttpRequest::get(&url).bearer(minecraft_token)).await?;
    minecraft_status(MC_ENTITLEMENTS_URL, &response)?;

    let decoded: EntitlementsResponse = decode(MC_ENTITLEMENTS_URL, &response.body)?;
    let owns_game = decoded
        .items
        .iter()
        .any(|item| item.name == "product_minecraft" || item.name == "game_minecraft");

    if owns_game {
        Ok(())
    } else {
        Err(AshError::NotEntitled)
    }
}

/// Hop 7. Returns the undashed profile UUID, the username, and the active
/// skin if there is one.
async fn profile(
    http: &dyn HttpPort,
    minecraft_token: &str,
) -> Result<(String, String, Option<String>), AshError> {
    let response = http.send(HttpRequest::get(MC_PROFILE_URL).bearer(minecraft_token)).await?;

    // A 404 here means the account is entitled but has no profile yet - the
    // Game Pass case. Telling that player they do not own the game would be
    // wrong and unactionable.
    if response.status == 404 {
        return Err(AshError::ProfileUnavailable);
    }
    minecraft_status(MC_PROFILE_URL, &response)?;

    let decoded: ProfileResponse = decode(MC_PROFILE_URL, &response.body)?;
    let skin = decoded.skins.iter().find(|s| s.state == "ACTIVE").map(|s| s.url.clone());
    Ok((normalise_uuid(&decoded.id), decoded.name, skin))
}

/// Mojang returns the profile id undashed here and dashed elsewhere. ash
/// stores one form, always, because it is ADR-0009's primary key.
fn normalise_uuid(raw: &str) -> String {
    raw.chars().filter(|c| *c != '-').flat_map(|c| c.to_lowercase()).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn uuid_is_normalised_to_undashed_lowercase() {
        assert_eq!(
            normalise_uuid("986DEC87-B7EC-47FF-89FF-033FDB95C4B5"),
            "986dec87b7ec47ff89ff033fdb95c4b5"
        );
        assert_eq!(
            normalise_uuid("986dec87b7ec47ff89ff033fdb95c4b5"),
            "986dec87b7ec47ff89ff033fdb95c4b5"
        );
    }

    #[test]
    fn known_xerr_codes_map_to_actionable_errors() {
        assert!(matches!(xerr_to_error(2148916233), AshError::XboxNoAccount));
        assert!(matches!(xerr_to_error(2148916238), AshError::XboxChildAccount));
        assert!(matches!(xerr_to_error(999), AshError::XboxOther { code: 999 }));
    }
}
