INSERT INTO category (id, name, color, image_url, description)
VALUES (1, '테스트 카테고리', '#FF0000', 'https://example.com/cat.jpg', '테스트용');

INSERT INTO product (id, name, price, image_url, category_id)
VALUES (1, '테스트 상품', 10000, 'https://example.com/prod.jpg', 1);
