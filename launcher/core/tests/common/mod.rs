//! Fixtures shared between the depot, runtime and launch tests.
//!
//! Built here rather than checked in, because every artifact carries a hash
//! ash will check - a stale fixture and a stale hash would drift apart
//! silently.

// Each test binary gets its own copy of this module and uses part of it.
#![allow(dead_code)]

use std::sync::Arc;

use ash_core::http::{FakeHttp, HttpResponse};
use sha1::{Digest, Sha1};

pub const RUNTIME_INDEX_URL: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

pub const LEGACY_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/v1/packages/jre8/manifest.json";
pub const MODERN_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/v1/packages/jre21/manifest.json";

pub const JAVA_8_BIN: &[u8] = b"pretend this is a java 8 binary";
pub const JAVA_21_BIN: &[u8] = b"pretend this is a java 21 binary";
pub const SHARED_LIB: &[u8] = b"pretend this is a jvm shared library";

pub const JAVA_8_URL: &str = "https://piston-data.mojang.com/v1/objects/j8/java";
pub const JAVA_21_URL: &str = "https://piston-data.mojang.com/v1/objects/j21/java";
pub const LIB_8_URL: &str = "https://piston-data.mojang.com/v1/objects/l8/jvm.dll";
pub const LIB_21_URL: &str = "https://piston-data.mojang.com/v1/objects/l21/jvm.dll";

pub fn sha1(bytes: &[u8]) -> String {
    let mut h = Sha1::new();
    h.update(bytes);
    h.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/// One runtime's file listing. The java binary sits under `bin/` on Windows
/// and under the bundle on macOS, which is why ash searches rather than
/// hardcoding a path.
fn runtime_manifest(binary: &[u8], binary_url: &str, lib: &[u8], lib_url: &str) -> String {
    format!(
        r#"{{"files":{{
          "bin":{{"type":"directory"}},
          "bin/java.exe":{{"type":"file","executable":true,
            "downloads":{{"raw":{{"sha1":"{bin_sha}","size":{bin_size},"url":"{binary_url}"}}}}}},
          "bin/server/jvm.dll":{{"type":"file","executable":false,
            "downloads":{{"raw":{{"sha1":"{lib_sha}","size":{lib_size},"url":"{lib_url}"}}}}}},
          "legal/LICENSE":{{"type":"link","target":"../LICENSE"}}
        }}}}"#,
        bin_sha = sha1(binary),
        bin_size = binary.len(),
        lib_sha = sha1(lib),
        lib_size = lib.len(),
    )
}

pub fn legacy_manifest() -> String {
    runtime_manifest(JAVA_8_BIN, JAVA_8_URL, SHARED_LIB, LIB_8_URL)
}

pub fn modern_manifest() -> String {
    runtime_manifest(JAVA_21_BIN, JAVA_21_URL, SHARED_LIB, LIB_21_URL)
}

/// Mojang's index, with every platform ash might be built for so the tests
/// pass wherever they run.
pub fn runtime_index() -> String {
    let legacy = legacy_manifest();
    let modern = modern_manifest();
    let entry = |url: &str, body: &str, version: &str| {
        format!(
            r#"[{{"availability":{{"group":1,"progress":100}},
                 "manifest":{{"sha1":"{}","size":{},"url":"{url}"}},
                 "version":{{"name":"{version}","released":"2024-01-01T00:00:00+00:00"}}}}]"#,
            sha1(body.as_bytes()),
            body.len()
        )
    };

    let components = format!(
        r#"{{"jre-legacy":{legacy_entry},"java-runtime-delta":{modern_entry}}}"#,
        legacy_entry = entry(LEGACY_MANIFEST_URL, &legacy, "8.0.412"),
        modern_entry = entry(MODERN_MANIFEST_URL, &modern, "21.0.3"),
    );

    format!(
        r#"{{"windows-x64":{c},"windows-arm64":{c},"mac-os":{c},"mac-os-arm64":{c}}}"#,
        c = components
    )
}

// ---- sign-in ---------------------------------------------------------------
//
// A single happy path, for tests that need a signed-in player but are not
// about signing in. `tests/auth.rs` keeps its own hop-by-hop fixtures,
// because those exist to be overridden one hop at a time.

pub const DEVICE_CODE_URL: &str =
    "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
pub const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
pub const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
pub const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
pub const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/launcher/login";
pub const MC_ENTITLEMENTS_URL: &str = "https://api.minecraftservices.com/entitlements/license";
pub const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

/// The Minecraft access token the fake chain issues. Tests assert on where
/// this does and does not appear.
pub const MC_TOKEN: &str = "MC-ACCESS-TOKEN";
pub const PLAYER_NAME: &str = "oogz";
pub const PLAYER_UUID: &str = "99bffcc8ae2549a0a70481c9c3db7ced";
pub const PLAYER_XUID: &str = "2535412345678901";

pub fn with_auth_routes(http: Arc<FakeHttp>) -> Arc<FakeHttp> {
    http.route(
        DEVICE_CODE_URL,
        HttpResponse::ok(
            r#"{"device_code":"DEV","user_code":"WXYZ-ABCD",
                "verification_uri":"https://microsoft.com/link",
                "expires_in":900,"interval":5}"#,
        ),
    )
    .route(
        TOKEN_URL,
        HttpResponse::ok(r#"{"access_token":"MS-ACCESS","refresh_token":"MS-REFRESH"}"#),
    )
    .route(
        XBL_URL,
        HttpResponse::ok(r#"{"Token":"XBL","DisplayClaims":{"xui":[{"uhs":"USERHASH"}]}}"#),
    )
    .route(
        XSTS_URL,
        HttpResponse::ok(format!(
            r#"{{"Token":"XSTS","DisplayClaims":{{"xui":[{{"uhs":"USERHASH","xid":"{PLAYER_XUID}"}}]}}}}"#
        )),
    )
    .route(
        MC_LOGIN_URL,
        HttpResponse::ok(format!(r#"{{"access_token":"{MC_TOKEN}","expires_in":86400}}"#)),
    )
    .route(
        MC_ENTITLEMENTS_URL,
        HttpResponse::ok(r#"{"items":[{"name":"product_minecraft"},{"name":"game_minecraft"}]}"#),
    )
    .route(
        MC_PROFILE_URL,
        HttpResponse::ok(format!(
            r#"{{"id":"{PLAYER_UUID}","name":"{PLAYER_NAME}",
                "skins":[{{"state":"ACTIVE","url":"https://textures/abc"}}]}}"#
        )),
    )
}

/// Add every route runtime provisioning needs.
pub fn with_runtime_routes(http: Arc<FakeHttp>) -> Arc<FakeHttp> {
    http.route(RUNTIME_INDEX_URL, HttpResponse::ok(runtime_index()))
        .route(LEGACY_MANIFEST_URL, HttpResponse::ok(legacy_manifest()))
        .route(MODERN_MANIFEST_URL, HttpResponse::ok(modern_manifest()))
        .route(JAVA_8_URL, HttpResponse::ok(JAVA_8_BIN))
        .route(JAVA_21_URL, HttpResponse::ok(JAVA_21_BIN))
        .route(LIB_8_URL, HttpResponse::ok(SHARED_LIB))
        .route(LIB_21_URL, HttpResponse::ok(SHARED_LIB))
}
