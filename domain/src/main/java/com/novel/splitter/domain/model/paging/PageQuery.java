package com.novel.splitter.domain.model.paging;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 领域分页查询参数，不依赖任何框架分页模型。
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class PageQuery {
    private final int page;
    private final int size;
}
