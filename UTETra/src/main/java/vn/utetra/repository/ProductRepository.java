package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.utetra.entity.Product;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

	List<Product> findByIsAvailableTrue();

	List<Product> findByCategoryIdAndIsAvailableTrue(Integer categoryId);

	List<Product> findByBranchIdAndIsAvailableTrue(Integer branchId);

	// Lọc sản phẩm đa năng: từ khóa, danh mục, khoảng giá
	@Query("SELECT p FROM Product p WHERE p.isAvailable = true "
			+ "AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "AND (:categoryId IS NULL OR p.category.id = :categoryId) "
			+ "AND (:minPrice IS NULL OR p.basePrice >= :minPrice) "
			+ "AND (:maxPrice IS NULL OR p.basePrice <= :maxPrice)")
	List<Product> filterProducts(@Param("keyword") String keyword, @Param("categoryId") Integer categoryId,
			@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);
}