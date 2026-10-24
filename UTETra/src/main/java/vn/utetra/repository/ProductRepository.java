
package vn.utetra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.utetra.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

	@Query(value = "SELECT * FROM Products p WHERE p.is_available = 1 " + "AND (:keyword IS NULL OR :keyword = '' "
			+ "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "AND (:categoryId IS NULL OR p.category_id = :categoryId) " + "ORDER BY "
			+ "CASE WHEN :sortBy = 'price_asc' THEN p.base_price END ASC, "
			+ "CASE WHEN :sortBy = 'price_desc' THEN p.base_price END DESC, "
			+ "CASE WHEN :sortBy = 'sold' THEN p.sold_count END DESC, "
			+ "CASE WHEN :sortBy = 'newest' OR :sortBy IS NULL "
			+ "OR :sortBy = '' THEN p.created_at END DESC", nativeQuery = true)
	List<Product> filterProducts(@Param("keyword") String keyword, @Param("categoryId") Integer categoryId,
			@Param("sortBy") String sortBy);

	@Query(value = "SELECT * FROM Products p " + "WHERE p.is_available = 1 " + "AND (:keyword IS NULL OR :keyword = '' "
			+ "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "AND (:categoryId IS NULL OR p.category_id = :categoryId) " + "ORDER BY "
			+ "CASE WHEN :sortBy = 'price_asc' " + "THEN p.base_price END ASC, " + "CASE WHEN :sortBy = 'price_desc' "
			+ "THEN p.base_price END DESC, " + "CASE WHEN :sortBy = 'sold' " + "THEN p.sold_count END DESC, "
			+ "CASE WHEN :sortBy = 'newest' " + "OR :sortBy IS NULL OR :sortBy = '' " + "THEN p.created_at END DESC, "
			+ "p.id DESC", countQuery = "SELECT COUNT(*) FROM Products p " + "WHERE p.is_available = 1 "
					+ "AND (:keyword IS NULL OR :keyword = '' "
					+ "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
					+ "AND (:categoryId IS NULL OR p.category_id = :categoryId)", nativeQuery = true)
	Page<Product> filterProductsPaged(@Param("keyword") String keyword, @Param("categoryId") Integer categoryId,
			@Param("sortBy") String sortBy, Pageable pageable);

	List<Product> findBySoldCountGreaterThanOrderBySoldCountDesc(Integer soldCountThreshold);

	List<Product> findByCategoryId(Integer categoryId);

	Page<Product> findBySoldCountGreaterThanAndIsAvailableTrueOrderBySoldCountDescIdAsc(Integer minSold,
			Pageable pageable);

	@Query("SELECT p.category.id, COUNT(p) FROM Product p WHERE p.isAvailable = true GROUP BY p.category.id")
	List<Object[]> countAvailableByCategory();

	@Query("SELECT r.product.id, AVG(r.rating), COUNT(r) FROM Review r GROUP BY r.product.id")
	List<Object[]> ratingStats();

	@Query("SELECT f.product.id, COUNT(f) FROM Favorite f GROUP BY f.product.id")
	List<Object[]> favoriteStats();

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Product p SET p.soldCount = p.soldCount + :qty " + "WHERE p.id = :id")
	int increaseSoldCount(@Param("id") Integer id, @Param("qty") Integer qty);

	Optional<Product> findByTopping_Id(Integer toppingId);

	List<Product> findByToppingIsNotNull();

	@Query(value = "SELECT * FROM Products p WHERE (:keyword IS NULL OR :keyword = '' "
			+ "OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "AND (:categoryId IS NULL OR p.category_id = :categoryId) ORDER BY p.is_available DESC, "
			+ "CASE WHEN :sortBy = 'price_asc' THEN p.base_price END ASC, "
			+ "CASE WHEN :sortBy = 'price_desc' THEN p.base_price END DESC, "
			+ "CASE WHEN :sortBy = 'sold' THEN p.sold_count END DESC, "
			+ "CASE WHEN :sortBy = 'newest' OR :sortBy IS NULL OR :sortBy = '' THEN p.created_at END DESC, "
			+ "p.id ASC", nativeQuery = true)
	List<Product> menuProducts(@Param("keyword") String keyword, @Param("categoryId") Integer categoryId,
			@Param("sortBy") String sortBy);

	@Query(value = "SELECT * FROM Products p WHERE (:categoryId IS NULL OR p.category_id = :categoryId) "
			+ "ORDER BY p.is_available DESC, "
			+ "CASE WHEN :sortBy = 'price_asc' THEN p.base_price END ASC, "
			+ "CASE WHEN :sortBy = 'price_desc' THEN p.base_price END DESC, "
			+ "CASE WHEN :sortBy = 'sold' THEN p.sold_count END DESC, "
			+ "CASE WHEN :sortBy = 'newest' THEN p.created_at END DESC, p.id DESC",
			countQuery = "SELECT COUNT(*) FROM Products p WHERE (:categoryId IS NULL OR p.category_id = :categoryId)",
			nativeQuery = true)
	Page<Product> menuProductsPaged(@Param("categoryId") Integer categoryId, @Param("sortBy") String sortBy,
			Pageable pageable);
}
