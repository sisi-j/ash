import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Tauri owns the console output, so don't let Vite wipe it.
export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  server: { port: 5173, strictPort: true },
  build: { outDir: "dist", emptyOutDir: true, target: "esnext" },
});
