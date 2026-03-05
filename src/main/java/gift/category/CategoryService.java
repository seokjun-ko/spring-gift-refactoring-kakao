package gift.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
            .map(CategoryResponse::from)
            .toList();
    }

    public CategoryResponse findById(Long id) {
        return CategoryResponse.from(findEntityById(id));
    }

    public Category findEntityById(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category saved = categoryRepository.save(request.toEntity());
        return CategoryResponse.from(saved);
    }

    public List<Category> findAllEntities() {
        return categoryRepository.findAll();
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findEntityById(id);
        category.update(request.name(), request.color(), request.imageUrl(), request.description());
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id);
        }
        categoryRepository.deleteById(id);
    }
}
