package com.novel.splitter.domain.model.paging;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 领域分页结果，不暴露 Spring Data Page。
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class PagedResult<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }

    public <R> PagedResult<R> map(Function<? super T, ? extends R> mapper) {
        if (content == null || content.isEmpty()) {
            return PagedResult.of(Collections.emptyList(), page, size, totalElements);
        }
        return PagedResult.of(
                content.stream().map(mapper).collect(Collectors.toList()),
                page,
                size,
                totalElements
        );
    }
}
