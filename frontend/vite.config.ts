import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
      "/ml": {
        target: "http://localhost:8000",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/ml/, ""),
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) {
            return undefined;
          }
          if (id.includes("recharts") || id.includes("d3-")) {
            return "charts";
          }
          if (id.includes("@tanstack")) {
            return "query";
          }
          if (
            id.includes("react-dom") ||
            id.includes("react-router") ||
            id.includes("scheduler") ||
            id.includes("/react/")
          ) {
            return "react";
          }
          return "vendor";
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts",
    css: true,
    restoreMocks: true,
  },
});
