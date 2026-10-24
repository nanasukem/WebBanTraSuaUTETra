package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.OrderDetail;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.utetra.entity.enums.OrderStatus;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {
	List<OrderDetail> findByOrderId(Integer orderId);

	@Query("SELECT d FROM OrderDetail d WHERE d.order.user.id = :userId AND d.order.status = :status "
			+ "AND (:productId IS NULL OR d.product.id = :productId) ORDER BY d.order.deliveredAt DESC, d.id DESC")
	List<OrderDetail> findPurchased(@Param("userId") Integer userId, @Param("productId") Integer productId,
			@Param("status") OrderStatus status);

	@Query("SELECT d.product.id, d.product.name, SUM(d.quantity), COALESCE(SUM(d.subtotal), 0), COUNT(DISTINCT d.order.id) "
			+ "FROM OrderDetail d WHERE d.order.status = :status "
			+ "AND d.order.deliveredAt >= :start AND d.order.deliveredAt < :end "
			+ "AND (:branchId IS NULL OR d.order.branch.id = :branchId) "
			+ "GROUP BY d.product.id, d.product.name ORDER BY SUM(d.quantity) DESC, COALESCE(SUM(d.subtotal), 0) DESC")
	List<Object[]> topProducts(@Param("status") OrderStatus status, @Param("start") java.time.LocalDateTime start,
			@Param("end") java.time.LocalDateTime end, @Param("branchId") Integer branchId,
			org.springframework.data.domain.Pageable pageable);
}