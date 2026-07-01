# Caddy DuckDNS 도메인 불일치로 TLS 인증서 발급 실패

- 발생 일시: 2026-06-30
- 영역: infra / docker
- 심각도: high

## 증상

EC2와 Caddy 컨테이너는 실행 중이고 `localhost` 요청은 HTTPS 리다이렉트를 반환했지만, 외부에서 도메인으로 접속할 수 없었다. Caddy 로그에는 Let's Encrypt의 HTTP-01 및 TLS-ALPN-01 검증이 타임아웃됐다고 기록됐다.

## 원인

이전 Caddy 설정이 `devmatch.duckdns.org`를 사용했고, 이 도메인은 현재 EC2 EIP `52.79.118.172`가 아닌 `43.202.250.254`를 가리켰다. 따라서 인증 기관의 검증 요청이 현재 Caddy에 도달하지 못했다.

## 해결 방법

Caddy 사이트 주소를 실제 DuckDNS 호스트인 `devmatch132.duckdns.org`로 맞추고 컨테이너를 재시작했다. 관련 설정은 `Caddyfile:3`이다. 재시작 후 Caddy 로그에서 HTTP-01 검증 성공과 `certificate obtained successfully`를 확인했다.

## 재발 방지 / 메모

- 배포 전에 `Resolve-DnsName <도메인>` 결과와 EC2 EIP가 같은지 확인한다.
- Caddy 로그의 인증서 대상 도메인과 실제 접속 도메인을 함께 확인한다.
- `curl -I http://localhost`가 성공하지만 인증서 검증이 실패하면 애플리케이션보다 DNS와 AWS 인바운드 경로를 먼저 점검한다.
