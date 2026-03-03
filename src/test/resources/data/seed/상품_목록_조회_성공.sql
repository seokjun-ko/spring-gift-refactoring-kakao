-- seed for: 상품_목록_조회_성공
INSERT INTO category (id, name, color, image_url, description)
VALUES (1, '전자기기', '#1E90FF', 'https://example.com/images/electronics.jpg', '테스트용 카테고리');

INSERT INTO product (id, name, price, image_url, category_id)
VALUES (1, '상품A', 10000, 'https://example.com/images/a.jpg', 1);

INSERT INTO product (id, name, price, image_url, category_id)
VALUES (2, '상품B', 20000, 'https://example.com/images/b.jpg', 1);
