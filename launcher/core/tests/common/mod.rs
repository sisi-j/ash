//! Fixtures shared between the depot and runtime tests.
//!
//! Built here rather than checked in, because every artifact carries a hash
//! ash will check - a stale fixture and a stale hash would drift apart
//! silently.

use std::sync::Arc;

use ash_core::http::{FakeHttp, HttpResponse};
use sha1::{Digest, Sha1};

pub const RUNTIME_INDEX_URL: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

pub const LEGACY_MANIFEST_URL: &str = "https://piston-meta.mojang.com/v1/packages/jre8/manifest.json";
pub const MODERN_MANIFEST_URL: &str = "https://piston-meta.mojang.com/v1/packages/jre21/manifest.json";

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
