//! Signing in through the player's browser (ADR-0007, amended): Microsoft's
//! authorization-code flow with PKCE, the code coming back to a one-off
//! listener on this computer's loopback address.
//!
//! One click: the browser usually knows the player's Microsoft account
//! already, so all they do is pick it and approve. The device code stays
//! for when the browser can't come back to ash.
//!
//! The listener answers one sign-in and nothing else: it takes only the
//! redirect carrying this sign-in's `state`, and it closes as soon as that
//! arrives, the sign-in is cancelled, or it has waited too long.

use std::time::{Duration, Instant};

use base64::Engine as _;
use sha2::{Digest, Sha256};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

use crate::depot::Cancel;
use crate::error::AshError;

pub(crate) const AUTHORIZE_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";

/// How long the player has to finish in the browser.
const WAIT: Duration = Duration::from_secs(10 * 60);
/// How often a wait looks at whether it was cancelled.
const CHECK: Duration = Duration::from_millis(250);
/// A redirect's request line and headers fit in far less; anything longer
/// isn't one.
const MAX_REQUEST: usize = 16 * 1024;

/// The secret half of PKCE, the half Microsoft sees, and the `state` that
/// ties the redirect to this sign-in.
pub(crate) struct Pkce {
    pub verifier: String,
    pub challenge: String,
    pub state: String,
}

fn random_text(bytes: usize) -> String {
    let mut buffer = vec![0u8; bytes];
    aws_lc_rs::rand::fill(&mut buffer).expect("the system's random source");
    base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(buffer)
}

pub(crate) fn pkce() -> Pkce {
    let verifier = random_text(32);
    let challenge = base64::engine::general_purpose::URL_SAFE_NO_PAD
        .encode(Sha256::digest(verifier.as_bytes()));
    Pkce { verifier, challenge, state: random_text(16) }
}

/// Where the browser goes: Microsoft's sign-in for ash, asking which
/// account, coming back to `redirect_uri`.
pub(crate) fn authorize_url(
    authorize: &str,
    client_id: &str,
    scopes: &str,
    redirect_uri: &str,
    pkce: &Pkce,
) -> String {
    let query = [
        ("client_id", client_id),
        ("response_type", "code"),
        ("redirect_uri", redirect_uri),
        ("scope", scopes),
        ("code_challenge", &pkce.challenge),
        ("code_challenge_method", "S256"),
        ("state", &pkce.state),
        // Someone signing in a second account mustn't be signed straight in
        // as the first.
        ("prompt", "select_account"),
    ];
    let query: Vec<String> = query.iter().map(|(k, v)| format!("{k}={}", encode(v))).collect();
    format!("{authorize}?{}", query.join("&"))
}

/// The listener, open before the browser is: Microsoft's redirect must find
/// it there.
pub(crate) struct Loopback {
    listener: TcpListener,
    pub redirect_uri: String,
}

pub(crate) async fn listen() -> Result<Loopback, AshError> {
    let listener = TcpListener::bind(("127.0.0.1", 0)).await.map_err(|e| {
        AshError::SignInFailed { detail: format!("listening for the browser: {e}") }
    })?;
    let port =
        listener.local_addr().map_err(|e| AshError::SignInFailed { detail: e.to_string() })?.port();
    // `localhost`, as registered for ash's app: Microsoft matches loopback
    // redirects on the address and ignores the port.
    Ok(Loopback { listener, redirect_uri: format!("http://localhost:{port}") })
}

/// What a redirect said.
enum Answer {
    Code(String),
    Refused(AshError),
}

/// Wait for the browser to come back with this sign-in's code.
pub(crate) async fn wait_for_code(
    loopback: Loopback,
    state: &str,
    cancel: &Cancel,
) -> Result<String, AshError> {
    let deadline = Instant::now() + WAIT;
    loop {
        if cancel.is_cancelled() {
            return Err(AshError::Cancelled);
        }
        if Instant::now() >= deadline {
            return Err(AshError::SignInExpired);
        }
        let Ok(accepted) = tokio::time::timeout(CHECK, loopback.listener.accept()).await else {
            continue;
        };
        let Ok((stream, _)) = accepted else { continue };
        if let Some(answer) = answer(stream, state).await {
            return match answer {
                Answer::Code(code) => Ok(code),
                Answer::Refused(e) => Err(e),
            };
        }
    }
}

/// Read one request and reply to it. `None` for anything that isn't this
/// sign-in's redirect - a favicon, a stray tab, a wrong `state` - which is
/// turned away while the wait goes on.
async fn answer(mut stream: TcpStream, state: &str) -> Option<Answer> {
    let target = read_target(&mut stream).await;
    let params = target.as_deref().and_then(|t| t.strip_prefix("/?")).map(parse_query);
    let Some(params) = params.filter(|p| param(p, "state") == Some(state)) else {
        let _ = reply(&mut stream, "404 Not Found", "Nothing here.").await;
        return None;
    };

    if let Some(code) = param(&params, "code") {
        let _ = reply(
            &mut stream,
            "200 OK",
            "You're signed in. You can close this tab and go back to ash.",
        )
        .await;
        return Some(Answer::Code(code.to_owned()));
    }
    let error = param(&params, "error").unwrap_or("no code");
    let _ =
        reply(&mut stream, "200 OK", "Sign-in didn't finish. Go back to ash to try again.").await;
    Some(Answer::Refused(match error {
        "access_denied" => AshError::SignInDeclined,
        other => AshError::SignInFailed {
            detail: match param(&params, "error_description") {
                Some(description) => format!("{other}: {description}"),
                None => other.to_owned(),
            },
        },
    }))
}

/// The request line's target, from `GET <target> HTTP/1.1`.
async fn read_target(stream: &mut TcpStream) -> Option<String> {
    let mut request = Vec::new();
    let mut chunk = [0u8; 2048];
    while !request.windows(4).any(|w| w == b"\r\n\r\n") {
        let n = tokio::time::timeout(Duration::from_secs(5), stream.read(&mut chunk))
            .await
            .ok()?
            .ok()?;
        if n == 0 || request.len() + n > MAX_REQUEST {
            break;
        }
        request.extend_from_slice(&chunk[..n]);
    }
    let text = String::from_utf8_lossy(&request);
    let line = text.lines().next()?;
    let mut parts = line.split(' ');
    (parts.next()? == "GET").then(|| parts.next().map(str::to_owned)).flatten()
}

async fn reply(stream: &mut TcpStream, status: &str, message: &str) -> std::io::Result<()> {
    let body = format!(
        "<!doctype html><meta charset=\"utf-8\"><title>ash</title>\
         <body style=\"margin:0;display:grid;place-items:center;height:100vh;background:#0b0b0d;\
         color:#fff;font:16px system-ui,sans-serif\"><p>{message}</p></body>"
    );
    let response = format!(
        "HTTP/1.1 {status}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\n\
         Cache-Control: no-store\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    stream.write_all(response.as_bytes()).await?;
    stream.shutdown().await
}

fn parse_query(query: &str) -> Vec<(String, String)> {
    query
        .split('&')
        .filter_map(|pair| pair.split_once('='))
        .map(|(k, v)| (decode(k), decode(v)))
        .collect()
}

fn param<'a>(params: &'a [(String, String)], name: &str) -> Option<&'a str> {
    params.iter().find(|(k, _)| k == name).map(|(_, v)| v.as_str())
}

/// Query-string encoding: what isn't unreserved is %-escaped.
fn encode(value: &str) -> String {
    value
        .bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                (b as char).to_string()
            }
            _ => format!("%{b:02X}"),
        })
        .collect()
}

/// `%XX` and `+` back to what they stand for.
fn decode(value: &str) -> String {
    let bytes = value.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'+' => out.push(b' '),
            b'%' if i + 2 < bytes.len() => {
                let hex = std::str::from_utf8(&bytes[i + 1..i + 3]).ok();
                match hex.and_then(|h| u8::from_str_radix(h, 16).ok()) {
                    Some(b) => {
                        out.push(b);
                        i += 2;
                    }
                    None => out.push(b'%'),
                }
            }
            b => out.push(b),
        }
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_challenge_is_the_verifier_s_sha256_and_each_sign_in_is_its_own() {
        let one = pkce();
        let expected = base64::engine::general_purpose::URL_SAFE_NO_PAD
            .encode(Sha256::digest(one.verifier.as_bytes()));
        assert_eq!(one.challenge, expected);
        assert!(one.verifier.len() >= 43, "PKCE needs 43 to 128 characters");
        assert_ne!(one.state, pkce().state);
    }

    #[test]
    fn the_authorize_url_carries_everything_microsoft_needs() {
        let p = Pkce { verifier: "v".into(), challenge: "CH".into(), state: "ST".into() };
        let url = authorize_url(
            AUTHORIZE_URL,
            "app",
            "XboxLive.signin offline_access",
            "http://localhost:5000",
            &p,
        );
        assert!(url.starts_with(AUTHORIZE_URL));
        for part in [
            "client_id=app",
            "response_type=code",
            "redirect_uri=http%3A%2F%2Flocalhost%3A5000",
            "scope=XboxLive.signin%20offline_access",
            "code_challenge=CH",
            "code_challenge_method=S256",
            "state=ST",
            "prompt=select_account",
        ] {
            assert!(url.contains(part), "{part} missing from {url}");
        }
        assert!(!url.contains("v&"), "the verifier never leaves this computer");
    }

    #[test]
    fn query_values_are_decoded() {
        let params = parse_query("code=M.C1%2Fab&error_description=the+user+said+no");
        assert_eq!(param(&params, "code"), Some("M.C1/ab"));
        assert_eq!(param(&params, "error_description"), Some("the user said no"));
        assert_eq!(decode("100%"), "100%");
    }
}
