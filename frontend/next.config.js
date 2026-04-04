/** @type {import('next').NextConfig} */
const nextConfig = {
  // Spring Boot 백엔드로 API 프록시
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://localhost:8081/api/:path*',
      },
    ];
  },
};

module.exports = nextConfig;
