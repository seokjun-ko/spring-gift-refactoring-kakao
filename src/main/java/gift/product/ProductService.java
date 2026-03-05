package gift.product;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gift.category.Category;
import gift.category.CategoryService;

@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryService categoryService;

    public ProductService(ProductRepository productRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
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
        Category category = categoryService.findEntityById(categoryId);
        return productRepository.save(new Product(name, price, imageUrl, category));
    }

    public Product updateProduct(Long id, String name, int price, String imageUrl, Long categoryId) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
        Category category = categoryService.findEntityById(categoryId);
        product.update(name, price, imageUrl, category);
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
        return ProductResponse.from(product);
    }

    public ProductResponse createProduct(ProductRequest request) {
        validateName(request.name());
        Category category = categoryService.findEntityById(request.categoryId());
        Product saved = productRepository.save(request.toEntity(category));
        return ProductResponse.from(saved);
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {
        validateName(request.name());
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + id));
        Category category = categoryService.findEntityById(request.categoryId());
        product.update(request.name(), request.price(), request.imageUrl(), category);
        productRepository.save(product);
        return ProductResponse.from(product);
    }

    private void validateName(String name) {
        List<String> errors = ProductNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }

    @Transactional(readOnly = true)
    public List<Category> findAllCategories() {
        return categoryService.findAllEntities();
    }
}
