package io.github.riseoftech.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    void copiesPageMetadata() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        var response = PageResponse.from(page);

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void mapsItems() {
        var page = new PageImpl<>(List.of(1, 2), PageRequest.of(0, 20), 2);

        var response = PageResponse.from(page, i -> "#" + i);

        assertThat(response.items()).containsExactly("#1", "#2");
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalPages()).isEqualTo(1);
    }

    @Test
    void emptyPage() {
        var response = PageResponse.from(new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
