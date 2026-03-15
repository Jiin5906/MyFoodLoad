-- V10: restaurants 테이블에 전화번호 및 카카오 장소 URL 추가
-- phone         : 카카오 로컬 API 반환 전화번호 (예: "02-1234-5678")
-- kakao_place_url: 카카오 장소 상세 웹 URL (예: https://place.map.kakao.com/12345678)

ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS phone          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS kakao_place_url VARCHAR(300);
