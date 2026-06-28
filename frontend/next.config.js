/** @type {import('next').NextConfig} */
// 백엔드 주소를 환경변수로. 로컬은 기본값(localhost), 운영은 BACKEND_URL 주입.
//  - 같은 docker compose 망: http://backend:8080
//  - 프론트가 별도 호스트(Amplify 등): https://api.<도메인>
const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

const nextConfig = {
  output: 'standalone', // 도커 경량 이미지(.next/standalone)
  // Spring Boot 백엔드로 API/업로드 프록시
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: `${BACKEND_URL}/api/:path*`,
      },
      {
        source: '/uploads/:path*',
        destination: `${BACKEND_URL}/uploads/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
