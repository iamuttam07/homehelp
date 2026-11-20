package com.uel.homehelp.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	Optional<Category> findBySlug(String slug);

	Optional<Category> findBySlugAndActiveTrue(String slug);

	/** Active categories in display order, each with its number of active services. */
	@Query("""
			select new com.uel.homehelp.catalog.CategoryWithCount(c, count(s))
			from Category c left join HomeService s on s.category = c and s.active = true
			where c.active = true
			group by c
			order by c.sortOrder, c.name""")
	List<CategoryWithCount> findActiveWithServiceCounts();

	@Query("select count(s) from HomeService s where s.category = :category and s.active = true")
	long countActiveServices(Category category);

}
