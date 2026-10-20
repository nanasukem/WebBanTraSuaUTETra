package vn.utetra.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
	List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

	List<Order> findByBranchIdAndStatusOrderByCreatedAtDesc(Integer branchId, OrderStatus status);

	List<Order> findByShipperIdAndStatusOrderByCreatedAtDesc(Integer shipperId, OrderStatus status);

	/**
	 * Tổng tiền các đơn có trạng thái status của user, giao từ thời điểm since (xét
	 * hạng). Không có đơn -> null
	 */
	@Query("SELECT SUM(o.totalAmount) FROM Order o "
			+ "WHERE o.user.id = :userId AND o.status = :status AND o.deliveredAt >= :since")
	BigDecimal sumTotalByUserAndStatusSince(@Param("userId") Integer userId, @Param("status") OrderStatus status,
			@Param("since") LocalDateTime since);

	/** User đã có đơn nào không bị huỷ chưa (xét "tài khoản mới") */
	boolean existsByUserIdAndStatusNot(Integer userId, OrderStatus status);

	/** Đơn của khách theo nhóm trạng thái (1 tab), mới nhất lên đầu */
	List<Order> findByUserIdAndStatusInOrderByCreatedAtDesc(Integer userId, Collection<OrderStatus> statuses);

	/** Đếm đơn theo trạng thái để hiện số trên tab */
	@Query("select o.status, count(o) from Order o where o.user.id = :userId group by o.status")
	List<Object[]> countByStatusOfUser(@Param("userId") Integer userId);

	/**
	 * Quầy đơn hàng: đơn đang làm của chi nhánh, cũ nhất lên đầu (đặt trước làm
	 * trước)
	 */
	List<Order> findByBranchIdAndStatusInOrderByCreatedAtAsc(Integer branchId, Collection<OrderStatus> statuses);

	/** Quầy đơn hàng: 20 đơn xong / huỷ gần nhất */
	List<Order> findTop20ByBranchIdAndStatusInOrderByUpdatedAtDesc(Integer branchId, Collection<OrderStatus> statuses);

	/** Đơn hàng của shop: 1 trạng thái, mới nhất lên đầu, có phân trang */
	Page<Order> findPageByBranchIdAndStatusOrderByCreatedAtDesc(Integer branchId, OrderStatus status, Pageable pageable);

	/** Số đơn của chi nhánh theo từng trạng thái (số trên 7 tab) */
	@Query("select o.status, count(o) from Order o where o.branch.id = :branchId group by o.status")
	List<Object[]> countByStatusOfBranch(@Param("branchId") Integer branchId);
}