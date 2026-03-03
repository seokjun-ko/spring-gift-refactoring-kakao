package gift.product;

import gift.category.Category;
import gift.category.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
    }

    public Product createProduct(String name, int price, String imageUrl, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + categoryId));
        return productRepository.save(new Product(name, price, imageUrl, category));
    }

    public Product updateProduct(Long id, String name, int price, String imageUrl, Long categoryId) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + categoryId));
        product.update(name, price, imageUrl, category);
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Category> findAllCategories() {
        return categoryRepository.findAll();
    }
}
