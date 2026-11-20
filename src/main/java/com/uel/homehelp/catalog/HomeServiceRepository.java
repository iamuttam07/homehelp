package com.uel.homehelp.catalog;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface HomeServiceRepository extends JpaRepository<HomeService, UUID>, JpaSpecificationExecutor<HomeService> {

	Optional<HomeService> findBySlug(String slug);

	/** Same as the inherited method, but loads each service's category in the same query. */
	@Override
	@EntityGraph(attributePaths = "category")
	Page<HomeService> findAll(Specification<HomeService> spec, Pageable pageable);

	@EntityGraph(attributePaths = "category")
	Optional<HomeService> findBySlugAndActiveTrueAndCategoryActiveTrue(String slug);

}
