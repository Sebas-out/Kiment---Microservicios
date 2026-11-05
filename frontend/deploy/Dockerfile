# syntax=docker/dockerfile:1
FROM node:24-bookworm-slim AS base
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1

FROM base AS build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --include=dev
COPY next.config.ts postcss.config.mjs tsconfig.json ./
COPY public ./public
COPY src ./src
ARG KIMENTS_CSP_REPORT_ONLY=0
RUN KIMENTS_CSP_REPORT_ONLY="$KIMENTS_CSP_REPORT_ONLY" npm run build && rm -rf .next-build/cache

FROM base AS production-deps
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --omit=dev

FROM base AS runtime
ENV NODE_ENV=production PORT=3000
COPY --from=production-deps /app/node_modules ./node_modules
COPY --from=build /app/package.json /app/next.config.ts ./
COPY --from=build /app/public ./public
COPY --from=build --chown=node:node /app/.next-build ./.next-build
USER node
EXPOSE 3000
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:3000/',{redirect:'manual',signal:AbortSignal.timeout(3500)}).then(r=>{process.exitCode=r.status===200?0:1;return r.body?.cancel()}).catch(()=>{process.exitCode=1})"]
CMD ["node", "node_modules/next/dist/bin/next", "start", "--hostname", "0.0.0.0", "--port", "3000"]
