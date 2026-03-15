# MyFoodLoad 기능 추가 계획

## 🟢 즉시 구현 가능 (가성비 최상)

### A-1. 검색 화면
- 백엔드 `GET /api/restaurants?q=&category=` API 이미 존재
- Android SearchScreen + ViewModel 추가만 필요

### A-2. 즐겨찾기 목록 화면
- 백엔드 `GET /api/favorites` API 이미 존재
- FavoriteScreen + FavoriteViewModel 추가만 필요

### A-4. 길찾기 / 전화 연결 (딥링크)
- Android Intent 한 줄로 카카오맵 길찾기 or 전화 앱 연결
- DetailScreen에 버튼 추가

### B-4. 추천 이유 스토리텔링
- Gemini 맛집 추출 프롬프트에 "추천 이유 한 줄" 필드 추가
- `restaurants` 테이블에 `recommendation_reason TEXT` 컬럼 추가 (Flyway)
- `RestaurantDto`에 `recommendationReason` 필드 추가

### C-2. "이미 간 맛집" 필터
- `user_visits` 테이블 (user_id, restaurant_id, visited_at) 추가 (Flyway)
- 추천 API에 `excludeVisited=true` 파라미터 추가
- DetailScreen에 "방문 완료" 체크 버튼 추가

### D-1. 공유 기능
- Android `Intent.ACTION_SEND`로 맛집 이름+주소 텍스트 공유
- DetailScreen에 공유 아이콘 추가

### D-3. 유튜버 연결
- `VideoMetadata`에서 channelId 이미 보유
- DetailScreen에 "YouTube에서 보기" 딥링크 버튼 추가 (`youtube://...`)

### E-3. 온보딩 화면
- 첫 실행 시 DataStore `onboarding_completed` 플래그 확인
- 3~4페이지 HorizontalPager 온보딩 (앱 설명 + YouTube 권한 이유)

### F-3. 다크 모드
- `Theme.kt` `darkColorScheme` 완성
- `isSystemInDarkTheme()` 연동

## 🟡 데이터/비용 타협 필요

### A-3. 영업시간
- 카카오 로컬 API 무료 플랜: 영업시간 미제공
- 대안: Google Places API(유료) 또는 UI 디자인으로 커버 (blurred thumbnail)

### B-3. 사진 갤러리
- 카카오 로컬 API: 여러 장 사진 미제공
- 대안: YouTube Shorts 썸네일 블러 배경 처리로 UI 개선

### B-1/C-1/E-2. 리뷰, 피드백 루프, 알림
- 핵심 추천 파이프라인 안정화 후 2차 스펙으로 구현

## 🔴 포기 (비현실적)

### B-2. 메뉴/가격 상세
- 무료 API 없음, 망고플레이트급 데이터 수집 인프라 불가

### E-1. 예약 연동
- 캐치테이블/네이버 예약 비공개 API, 개인 개발자 접근 불가

### E-4. 오프라인 지도
- 카카오맵 SDK 약관상 오프라인 캐싱 금지

### D-2. 소셜/친구
- MAU 1만 미만에서는 유령 기능, 서버 비용만 소모

## 구현 순서 (우선순위)
1. A-4 길찾기/전화 딥링크 (Detail Screen 버튼 - 1시간)
2. D-1 공유 기능 (Detail Screen 공유 아이콘 - 30분)
3. D-3 유튜버 연결 (Detail Screen YouTube 딥링크 - 30분)
4. A-2 즐겨찾기 목록 화면 (신규 화면 - 2시간)
5. A-1 검색 화면 (신규 화면 - 3시간)
6. B-4 추천 이유 스토리텔링 (백엔드+프론트 - 3시간)
7. C-2 방문 완료 필터 (백엔드+프론트 - 2시간)
8. E-3 온보딩 화면 (프론트 - 2시간)
9. F-3 다크 모드 (프론트 - 1시간)
