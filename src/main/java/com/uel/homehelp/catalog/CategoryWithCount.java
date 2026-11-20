package com.uel.homehelp.catalog;

/** Query result: a category and how many active services it has. */
public record CategoryWithCount(Category category, long serviceCount) {
}
