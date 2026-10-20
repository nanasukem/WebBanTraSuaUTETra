package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
	List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

	List<Order> findByBranchIdAndStatusOrderByCreatedAtDesc(Integer branchId, OrderStatus status);

	List<Order> findByShipperIdAndStatusOrderByCreatedAtDesc(Integer shipperId, OrderStatus status);

	/** Tổng tiền các đơn có trạng thái status của user, giao từ thời điểm since (xét hạng). Không có đơn -> null */
	@Query("SELECT SUM(o.totalAmount) FROM Order o "
			+ "WHERE o.user.id = :userId AND o.status = :status AND o.deliveredAt >= :since")
	BigDecimal sumTotalByUserAndStatusSince(@Param("userId") Integer userId, @Param("status") OrderStatus status,
			@Param("since") LocalDateTime since);

	/** User đã có đơn nào không bị huỷ chưa (xét "tài khoản mới") */
	boolean existsByUserIdAndStatusNot(Integer userId, OrderStatus status);
}