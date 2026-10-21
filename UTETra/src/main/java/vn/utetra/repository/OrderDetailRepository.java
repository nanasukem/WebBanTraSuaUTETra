package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.OrderDetail;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.utetra.entity.enums.OrderStatus;

public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {
	List<OrderDetail> findByOrderId(Integer orderId);

	/**
	 * Các ly user đã mua và ĐÃ NHẬN (đơn DELIVERED) - dùng cho đánh giá.
	 * productId = null: mọi món. Mới nhất lên đầu.
	 */
	@Query("SELECT d FROM OrderDetail d WHERE d.order.user.id = :userId AND d.order.status = :status "
			+ "AND (:productId IS NULL OR d.product.id = :productId) ORDER BY d.order.deliveredAt DESC, d.id DESC")
	List<OrderDetail> findPurchased(@Param("userId") Integer userId, @Param("productId") Integer productId,
			@Param("status") OrderStatus status);
}