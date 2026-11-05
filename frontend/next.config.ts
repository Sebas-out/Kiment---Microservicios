import type { NextConfig } from "next";
const config: NextConfig = {
  poweredByHeader: false,
  distDir: process.env.NODE_ENV === "development" ? ".next-dev" : ".next-build",
  experimental: { cpus: 1, webpackBuildWorker: false },
  turbopack: { root: process.cwd() },
  async headers() {
    return [{ source: "/(.*)", headers: [
      { key: "X-Content-Type-Options", value: "nosniff" },
      { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      { key: "X-Frame-Options", value: "DENY" },
      { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
      ...(process.env.KIMENTS_ENABLE_HSTS === "1"
        ? [{ key: "Strict-Transport-Security", value: "max-age=31536000" }]
        : []),
      ...(process.env.KIMENTS_CSP_REPORT_ONLY === "1" ? [{
        key: "Content-Security-Policy-Report-Only",
        value: "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'",
      }] : []),
    ] }];
  },
};
export default config;

