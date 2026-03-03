INSERT INTO category (id, name, color, image_url, description)
VALUES (1, '전자기기', '#1E90FF', 'https://example.com/images/electronics.jpg', '테스트용 카테고리');

INSERT INTO product (id, name, price, image_url, category_id)
VALUES (1, '테스트 상품', 10000, 'https://example.com/images/test.jpg', 1);

INSERT INTO options (id, product_id, name, quantity)
VALUES (1, 1, '테스트 옵션', 100);

INSERT INTO member (id, email, password, point)
VALUES (1, 'test@example.com', 'password123', 1000000);

INSERT INTO orders (id, option_id, member_id, quantity, message, order_date_time)
VALUES (1, 1, 1, 2, '첫 번째 주문', '2025-01-01T10:00:00');

INSERT INTO orders (id, option_id, member_id, quantity, message, order_date_time)
VALUES (2, 1, 1, 3, '두 번째 주문', '2025-01-02T10:00:00');
