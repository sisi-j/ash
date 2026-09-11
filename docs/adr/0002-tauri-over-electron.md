# Tauri over Electron for the launcher

Performance and minimalism are the product's stated differentiators, and shipping a launcher that idles at hundreds of megabytes would undercut that claim before the game even starts. Tauri uses the OS webview instead of bundling Chromium, giving a far smaller binary and lower idle footprint.

## Consequences

- Rendering differs between WebView2 on Windows and WKWebView on macOS; the UI needs testing on both rather than against one bundled Chromium.
- Anything touching the filesystem, process spawning or the OS keychain is Rust, not JavaScript.
- Electron remains the fallback if native integration work proves disproportionately expensive.
