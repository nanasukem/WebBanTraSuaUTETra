package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.utetra.entity.Product;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

	/**
	 * Lọc & Sắp xếp sản phẩm (Dành cho trang danh sách/tìm kiếm)
	 */
	@Query(value = "SELECT * FROM Products p WHERE p.is_available = 1 "
			+ "AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "AND (:categoryId IS NULL OR p.category_id = :categoryId) " + "ORDER BY "
			+ "CASE WHEN :sortBy = 'price_asc' THEN p.base_price END ASC, "
			+ "CASE WHEN :sortBy = 'price_desc' THEN p.base_price END DESC, "
			+ "CASE WHEN :sortBy = 'sold' THEN p.sold_count END DESC, "
			+ "CASE WHEN :sortBy = 'newest' OR :sortBy IS NULL OR :sortBy = '' THEN p.created_at END DESC", nativeQuery = true)
	List<Product> filterProducts(@Param("keyword") String keyword, @Param("categoryId") Integer categoryId,
			@Param("sortBy") String sortBy);

	/**
	 * Lấy danh sách sản phẩm bán chạy (có lượt bán > threshold) giảm dần Phục vụ
	 * GuestHomeService.getBestSellerProducts()
	 */
	List<Product> findBySoldCountGreaterThanOrderBySoldCountDesc(Integer soldCountThreshold);

	/**
	 * Lấy sản phẩm theo mã danh mục Phục vụ
	 * GuestHomeService.getProducts(categoryId)
	 */
	List<Product> findByCategoryId(Integer categoryId);
}