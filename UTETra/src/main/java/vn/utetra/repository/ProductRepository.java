
package vn.utetra.repository;

import java.util.List;

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

	/**
	 * Giữ nguyên truy vấn cũ để trang Guest và các chức năng khác tiếp tục dùng.
	 */
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

	/**
	 * Phân trang danh sách sản phẩm cho USER. Mỗi trang sẽ có số lượng phần tử do
	 * Pageable quy định.
	 */
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

	/**a
	 * Lấy danh sách sản phẩm bán chạy.
	 */
	List<Product> findBySoldCountGreaterThanOrderBySoldCountDesc(Integer soldCountThreshold);

	/**
	 * Lấy sản phẩm theo mã danh mục.
	 */
	List<Product> findByCategoryId(Integer categoryId);

	/**
	 * Trang chủ Guest (đề): món đã bán trên minSold ly, xếp từ nhiều đến ít, có phân trang.
	 */
	Page<Product> findBySoldCountGreaterThanAndIsAvailableTrueOrderBySoldCountDescIdAsc(Integer minSold,
			Pageable pageable);

	/** Số món đang bán theo danh mục: [categoryId, số món] (ô danh mục trên trang chủ User) */
	@Query("SELECT p.category.id, COUNT(p) FROM Product p WHERE p.isAvailable = true GROUP BY p.category.id")
	List<Object[]> countAvailableByCategory();

	/** Điểm đánh giá theo món: [productId, điểm trung bình, số lượt đánh giá] (trang Thực đơn - Đánh giá cao) */
	@Query("SELECT r.product.id, AVG(r.rating), COUNT(r) FROM Review r GROUP BY r.product.id")
	List<Object[]> ratingStats();

	/** Lượt thích theo món: [productId, số lượt thích] (trang Thực đơn - Yêu thích nhất) */
	@Query("SELECT f.product.id, COUNT(f) FROM Favorite f GROUP BY f.product.id")
	List<Object[]> favoriteStats();

	/**
	 * Đơn giao xong: tăng số lượng đã bán.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Product p SET p.soldCount = p.soldCount + :qty " + "WHERE p.id = :id")
	int increaseSoldCount(@Param("id") Integer id, @Param("qty") Integer qty);
}
