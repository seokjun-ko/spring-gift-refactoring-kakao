package gift.wish;

import gift.product.Product;
import gift.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class WishService {

    public record AddWishResult(WishResponse wish, boolean created) {
    }
    private final WishRepository wishRepository;
    private final ProductRepository productRepository;

    public WishService(WishRepository wishRepository, ProductRepository productRepository) {
        this.wishRepository = wishRepository;
        this.productRepository = productRepository;
    }

    public Page<WishResponse> getWishes(Long memberId, Pageable pageable) {
        return wishRepository.findByMemberId(memberId, pageable).map(WishResponse::from);
    }

    @Transactional
    public AddWishResult addWish(Long memberId, Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new NoSuchElementException("상품이 존재하지 않습니다. id=" + productId));

        var existing = wishRepository.findByMemberIdAndProductId(memberId, product.getId());
        if (existing.isPresent()) {
            return new AddWishResult(WishResponse.from(existing.get()), false);
        }

        var saved = wishRepository.save(new Wish(memberId, product));
        return new AddWishResult(WishResponse.from(saved), true);
    }

    @Transactional
    public void removeWish(Long memberId, Long wishId) {
        Wish wish = wishRepository.findById(wishId)
            .orElseThrow(() -> new NoSuchElementException("위시가 존재하지 않습니다. id=" + wishId));

        if (!wish.getMemberId().equals(memberId)) {
            throw new IllegalArgumentException("해당 위시에 대한 권한이 없습니다.");
        }

        wishRepository.delete(wish);
    }
}
