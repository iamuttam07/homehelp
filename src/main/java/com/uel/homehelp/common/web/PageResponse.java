package com.uel.homehelp.common.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * One page of results. A stable JSON shape for every paginated endpoint, rather
 * than serializing Spring Data's {@code Page} directly.
 *
 * @param items the results on this page
 * @param page zero-based page number
 * @param size requested page size
 * @param totalItems number of results across all pages
 * @param totalPages number of pages
 * @param hasNext whether a following page exists
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages(), page.hasNext());
	}

}
