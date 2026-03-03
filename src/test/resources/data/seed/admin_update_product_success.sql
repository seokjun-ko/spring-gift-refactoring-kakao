-- seed for: admin_update_product_success
INSERT INTO category (id, name, color, image_url, description)
VALUES (1, '전자기기', '#1E90FF', 'https://example.com/images/electronics.jpg', '테스트용 카테고리');

INSERT INTO category (id, name, color, image_url, description)
VALUES (2, '도서', '#32CD32', 'https://example.com/images/books.jpg', '도서 카테고리');

INSERT INTO product (id, name, price, image_url, category_id)
VALUES (1, '기존 상품', 10000, 'https://example.com/images/old.jpg', 1);
