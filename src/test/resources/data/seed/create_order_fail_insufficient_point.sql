INSERT INTO category (id, name, color, image_url, description)
VALUES (1, '테스트 카테고리', '#000000', 'https://example.com/cat.jpg', '테스트용');

INSERT INTO product (id, name, price, image_url, category_id)
VALUES (1, '테스트 상품', 10000, 'https://example.com/prod.jpg', 1);

INSERT INTO options (id, product_id, name, quantity)
VALUES (1, 1, '기본 옵션', 10);

INSERT INTO member (id, email, password, point)
VALUES (1, 'test@example.com', 'password123', 100);
