# ===== Build Stage =====
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# Next.js rewrites()는 빌드타임에 routes-manifest 로 구워진다.
# 따라서 백엔드 주소는 런타임 env가 아니라 build arg 로 주입해야 반영된다.
ARG BACKEND_URL=http://localhost:8080
ENV BACKEND_URL=$BACKEND_URL
RUN npm run build

# ===== Run Stage =====
FROM node:20-alpine AS run
WORKDIR /app
ENV NODE_ENV=production
# output: 'standalone' 산출물만 복사 (경량)
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
# 참고: 이 프로젝트엔 public/ 디렉터리가 없어 복사하지 않음.
# 추후 frontend/public 을 추가하면 아래 줄을 살릴 것:
#   COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
