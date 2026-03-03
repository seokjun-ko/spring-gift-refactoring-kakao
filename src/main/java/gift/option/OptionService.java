package gift.option;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gift.product.Product;
import gift.product.ProductRepository;

@Service
@Transactional(readOnly = true)
public class OptionService {
    private final OptionRepository optionRepository;
    private final ProductRepository productRepository;

    public OptionService(OptionRepository optionRepository, ProductRepository productRepository) {
        this.optionRepository = optionRepository;
        this.productRepository = productRepository;
    }

    public List<OptionResponse> getOptions(Long productId) {
        validateProductExists(productId);
        return optionRepository.findByProductId(productId).stream()
            .map(OptionResponse::from)
            .toList();
    }

    @Transactional
    public OptionResponse createOption(Long productId, OptionRequest request) {
        validateName(request.name());

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NoSuchElementException("Product not found. id=" + productId));

        if (optionRepository.existsByProductIdAndName(productId, request.name())) {
            throw new IllegalArgumentException("이미 존재하는 옵션명입니다.");
        }

        Option saved = optionRepository.save(request.toEntity(product));
        return OptionResponse.from(saved);
    }

    @Transactional
    public void deleteOption(Long productId, Long optionId) {
        validateProductExists(productId);

        List<Option> options = optionRepository.findByProductId(productId);
        if (options.size() <= 1) {
            throw new IllegalArgumentException("옵션이 1개인 상품은 옵션을 삭제할 수 없습니다.");
        }

        Option option = optionRepository.findById(optionId)
            .orElseThrow(() -> new NoSuchElementException("Option not found. id=" + optionId));

        if (!option.getProduct().getId().equals(productId)) {
            throw new NoSuchElementException("Option not found. id=" + optionId);
        }

        optionRepository.delete(option);
    }

    private void validateProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new NoSuchElementException("Product not found. id=" + productId);
        }
    }

    private void validateName(String name) {
        List<String> errors = OptionNameValidator.validate(name);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
    }
}
