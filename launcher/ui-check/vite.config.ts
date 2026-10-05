import { readFileSync } from "node:fs";
import path from "node:path";
import { defineConfig, normalizePath, type Plugin } from "vite";
import react from "@vitejs/plugin-react";

const launcher = path.resolve(import.meta.dirname, "..");
const realApi = normalizePath(path.join(launcher, "src", "api.ts"));
const fakeApi = normalizePath(path.join(launcher, "ui-check", "fake-api.ts"));

/**
 * Every screen's `import ... from "./api"` gets the fake instead, except the
 * fake's own, which builds on the real module's types and helpers.
 */
function fakeTheApi(): Plugin {
  return {
    name: "ash-fake-api",
    enforce: "pre",
    async resolveId(source, importer, options) {
      if (!importer || normalizePath(importer) === fakeApi) return null;
      const resolved = await this.resolve(source, importer, { ...options, skipSelf: true });
      return resolved && normalizePath(resolved.id) === realApi ? fakeApi : null;
    },
  };
}

/** The app's own content security policy, so a font that would be blocked in the app is blocked here too. */
const csp: string = JSON.parse(readFileSync(path.join(launcher, "src-tauri", "tauri.conf.json"), "utf8")).app
  .security.csp;

export default defineConfig({
  root: launcher,
  plugins: [react(), fakeTheApi()],
  logLevel: "warn",
  build: { outDir: path.join(launcher, "ui-check", "dist"), emptyOutDir: true, target: "esnext" },
  preview: { port: 5174, strictPort: true, headers: { "Content-Security-Policy": csp } },
});
