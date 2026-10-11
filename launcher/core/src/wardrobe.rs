//! The launcher's Wardrobe (spec 0004, step 4): the cosmetics catalogue, what
//! the active player wears, and equipping - with the player's own skin and
//! every cosmetic's texture handed to the window as `data:` URLs, so the 3D
//! preview never makes a cross-origin image request (research 0012).

use std::collections::HashMap;
use std::sync::Mutex;

use serde::{Deserialize, Serialize};

use crate::error::AshError;
use crate::http::{HttpPort, HttpRequest, HttpResponse};

/// One cosmetic, as the Wardrobe shows it.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Cosmetic {
    pub id: String,
    /// `cape`, `head` or `back`.
    pub slot: String,
    pub name: String,
    /// The texture, as a `data:image/png;base64,...` URL.
    pub texture: String,
}

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Equipped {
    pub cape: Option<String>,
    pub head: Option<String>,
    pub back: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct Wardrobe {
    /// Signed in to ash: without it the catalogue shows, and nothing can be worn.
    pub signed_in: bool,
    pub cosmetics: Vec<Cosmetic>,
    pub equipped: Equipped,
    /// The active player's own skin, as a `data:` URL, for the preview.
    pub skin: Option<String>,
}

#[derive(Deserialize)]
struct CatalogueBody {
    cosmetics: Vec<CatalogueItem>,
}

#[derive(Deserialize)]
struct CatalogueItem {
    id: String,
    slot: String,
    name: String,
    texture_url: String,
}

#[derive(Deserialize)]
struct WardrobeBody {
    equipped: Equipped,
}

#[derive(Serialize)]
struct Equip<'a> {
    slot: &'a str,
    cosmetic_id: Option<&'a str>,
}

/// Images fetched once per run: cosmetic textures are immutable at their
/// address, and a skin changes rarely enough that a restart catches it.
#[derive(Default)]
pub(crate) struct Images(Mutex<HashMap<String, String>>);

impl Images {
    async fn data_url(&self, http: &dyn HttpPort, url: &str) -> Result<String, AshError> {
        if let Some(known) = self.0.lock().expect("images").get(url) {
            return Ok(known.clone());
        }
        if !url.starts_with("https://") {
            return Err(AshError::Malformed {
                url: url.to_owned(),
                detail: "not an https address".into(),
            });
        }
        let response = http.send(HttpRequest::get(url)).await?;
        if !response.is_success() {
            return Err(AshError::UnexpectedStatus {
                url: url.to_owned(),
                status: response.status,
            });
        }
        let data = format!("data:image/png;base64,{}", base64(&response.body));
        self.0.lock().expect("images").insert(url.to_owned(), data.clone());
        Ok(data)
    }
}

fn decode<T: serde::de::DeserializeOwned>(
    url: &str,
    response: &HttpResponse,
) -> Result<T, AshError> {
    if !response.is_success() {
        return Err(AshError::UnexpectedStatus { url: url.to_owned(), status: response.status });
    }
    serde_json::from_slice(&response.body)
        .map_err(|e| AshError::Malformed { url: url.to_owned(), detail: e.to_string() })
}

pub(crate) async fn catalogue(
    http: &dyn HttpPort,
    base: &str,
    images: &Images,
) -> Result<Vec<Cosmetic>, AshError> {
    let url = format!("{base}/v1/cosmetics");
    let body: CatalogueBody = decode(&url, &http.send(HttpRequest::get(&url)).await?)?;
    let mut cosmetics = Vec::new();
    for item in body.cosmetics {
        // A texture that can't be fetched leaves that cosmetic out, not the page.
        if let Ok(texture) = images.data_url(http, &item.texture_url).await {
            cosmetics.push(Cosmetic { id: item.id, slot: item.slot, name: item.name, texture });
        }
    }
    Ok(cosmetics)
}

pub(crate) async fn equipped(
    http: &dyn HttpPort,
    base: &str,
    token: &str,
) -> Result<Equipped, AshError> {
    let url = format!("{base}/v1/wardrobe");
    let body: WardrobeBody = decode(&url, &http.send(HttpRequest::get(&url).bearer(token)).await?)?;
    Ok(body.equipped)
}

pub(crate) async fn equip(
    http: &dyn HttpPort,
    base: &str,
    token: &str,
    slot: &str,
    cosmetic_id: Option<&str>,
) -> Result<Equipped, AshError> {
    let url = format!("{base}/v1/wardrobe");
    let request = HttpRequest::put_json(&url, &Equip { slot, cosmetic_id })?.bearer(token);
    let body: WardrobeBody = decode(&url, &http.send(request).await?)?;
    Ok(body.equipped)
}

pub(crate) async fn skin(
    http: &dyn HttpPort,
    images: &Images,
    skin_url: Option<&str>,
) -> Option<String> {
    images.data_url(http, skin_url?).await.ok()
}

/// Standard base64, with padding.
fn base64(bytes: &[u8]) -> String {
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let mut out = String::with_capacity(bytes.len().div_ceil(3) * 4);
    for chunk in bytes.chunks(3) {
        let b = [chunk[0], *chunk.get(1).unwrap_or(&0), *chunk.get(2).unwrap_or(&0)];
        let n = (u32::from(b[0]) << 16) | (u32::from(b[1]) << 8) | u32::from(b[2]);
        for i in 0..4 {
            if i <= chunk.len() {
                out.push(ALPHABET[((n >> (18 - 6 * i)) & 63) as usize] as char);
            } else {
                out.push('=');
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::base64;

    #[test]
    fn base64_matches_the_rfc_4648_examples() {
        for (input, expected) in [
            ("", ""),
            ("f", "Zg=="),
            ("fo", "Zm8="),
            ("foo", "Zm9v"),
            ("foob", "Zm9vYg=="),
            ("fooba", "Zm9vYmE="),
            ("foobar", "Zm9vYmFy"),
        ] {
            assert_eq!(base64(input.as_bytes()), expected, "{input}");
        }
    }
}
