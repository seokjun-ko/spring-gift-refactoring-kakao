-- seed for: 위시_삭제_실패_소유권_없음
INSERT INTO category (id, name, color, image_url, description)
VALUES (1, '전자기기', '#1E90FF', 'https://example.com/images/electronics.jpg', '테스트용 카테고리');

INSERT INTO product (id, name, price, image_url, category_id)
VALUES (1, '테스트 상품', 10000, 'https://example.com/images/test.jpg', 1);

INSERT INTO member (id, email, password, point)
VALUES (1, 'owner@example.com', 'password123', 0);

INSERT INTO member (id, email, password, point)
VALUES (2, 'other@example.com', 'password123', 0);

INSERT INTO wish (id, member_id, product_id)
VALUES (1, 1, 1);
