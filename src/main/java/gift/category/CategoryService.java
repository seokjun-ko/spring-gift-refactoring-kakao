package gift.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
<<<<<<< HEAD
@Transactional
=======
@Transactional(readOnly = true)
>>>>>>> f92c18f9634eb430e252befb312b2da79e395864
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

<<<<<<< HEAD
    @Transactional(readOnly = true)
=======
>>>>>>> f92c18f9634eb430e252befb312b2da79e395864
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
            .map(CategoryResponse::from)
            .toList();
    }

<<<<<<< HEAD
    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
        return CategoryResponse.from(category);
    }

    public CategoryResponse create(CategoryRequest request) {
        Category category = categoryRepository.save(request.toEntity());
        return CategoryResponse.from(category);
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id));
=======
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category saved = categoryRepository.save(request.toEntity());
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Category not found: " + id));
>>>>>>> f92c18f9634eb430e252befb312b2da79e395864
        category.update(request.name(), request.color(), request.imageUrl(), request.description());
        return CategoryResponse.from(category);
    }

<<<<<<< HEAD
    public void delete(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new NoSuchElementException("카테고리가 존재하지 않습니다. id=" + id);
        }
=======
    @Transactional
    public void delete(Long id) {
>>>>>>> f92c18f9634eb430e252befb312b2da79e395864
        categoryRepository.deleteById(id);
    }
}
