package gift.category;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CategoryServiceTest {

    @Autowired
    CategoryService categoryService;

    @Test
    @Sql(scripts = {"/data/truncate.sql", "/data/seed/find_category_by_id.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하는_ID로_엔티티를_조회하면_카테고리를_반환한다() {
        Category category = categoryService.findEntityById(1L);

        assertThat(category.getId()).isEqualTo(1L);
        assertThat(category.getName()).isEqualTo("전자기기");
        assertThat(category.getColor()).isEqualTo("#1E90FF");
        assertThat(category.getImageUrl()).isEqualTo("https://example.com/images/electronics.jpg");
        assertThat(category.getDescription()).isEqualTo("전자기기 카테고리");
    }

    @Test
    @Sql(scripts = {"/data/truncate.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void 존재하지_않는_ID로_엔티티를_조회하면_예외가_발생하고_메시지에_ID가_포함된다() {
        assertThatThrownBy(() -> categoryService.findEntityById(999L))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("999");
    }
}
