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
	 * Tổng tiền đơn của khách, dùng cho xét hạng.
	 */
	@Query("SELECT SUM(o.totalAmount) FROM Order o " + "WHERE o.user.id = :userId " + "AND o.status = :status "
			+ "AND o.deliveredAt >= :since")
	BigDecimal sumTotalByUserAndStatusSince(@Param("userId") Integer userId, @Param("status") OrderStatus status,
			@Param("since") LocalDateTime since);

	boolean existsByUserIdAndStatusNot(Integer userId, OrderStatus status);

	List<Order> findByUserIdAndStatusInOrderByCreatedAtDesc(Integer userId, Collection<OrderStatus> statuses);

	@Query("select o.status, count(o) from Order o " + "where o.user.id = :userId group by o.status")
	List<Object[]> countByStatusOfUser(@Param("userId") Integer userId);

	List<Order> findByBranchIdAndStatusInOrderByCreatedAtAsc(Integer branchId, Collection<OrderStatus> statuses);

	List<Order> findTop20ByBranchIdAndStatusInOrderByUpdatedAtDesc(Integer branchId, Collection<OrderStatus> statuses);

	Page<Order> findPageByBranchIdAndStatusOrderByCreatedAtDesc(Integer branchId, OrderStatus status,
			Pageable pageable);

	@Query("select o.status, count(o) from Order o " + "where o.branch.id = :branchId group by o.status")
	List<Object[]> countByStatusOfBranch(@Param("branchId") Integer branchId);

	// ==========================
	// BÁO CÁO DOANH THU ADMIN
	// ==========================

	/**
	 * Lấy các đơn theo trạng thái và thời gian giao hàng. Dùng deliveredAt để thống
	 * kê doanh thu theo ngày giao thành công.
	 */
	List<Order> findByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThanOrderByDeliveredAtAsc(
			OrderStatus status, LocalDateTime start, LocalDateTime end);

	/**
	 * Tổng doanh thu của các đơn đã giao trong khoảng thời gian.
	 */
	@Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o " + "WHERE o.status = :status "
			+ "AND o.deliveredAt >= :start " + "AND o.deliveredAt < :end")
	BigDecimal sumRevenueByStatusAndDeliveredAt(@Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	/**
	 * Tổng hoa hồng của các đơn đã giao trong khoảng thời gian.
	 */
	@Query("SELECT COALESCE(SUM(o.commissionAmount), 0) FROM Order o " + "WHERE o.status = :status "
			+ "AND o.deliveredAt >= :start " + "AND o.deliveredAt < :end")
	BigDecimal sumCommissionByStatusAndDeliveredAt(@Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	/**
     * Đếm số đơn đã giao thành công trong khoảng thời gian.
     */
    long countByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(
            OrderStatus status,
            LocalDateTime start,
            LocalDateTime end);

	/** Mã giảm tiền món dùng tại chi nhánh (đơn không huỷ): [voucherId, số đơn, tổng tiền giảm] */
	@Query("SELECT o.productVoucher.id, COUNT(o), COALESCE(SUM(o.productDiscount), 0) FROM Order o "
			+ "WHERE o.branch.id = :branchId AND o.productVoucher IS NOT NULL AND o.status <> :cancelled "
			+ "GROUP BY o.productVoucher.id")
	List<Object[]> productVoucherStatsOfBranch(@Param("branchId") Integer branchId,
			@Param("cancelled") OrderStatus cancelled);

	/** Mã giảm phí ship dùng tại chi nhánh (đơn không huỷ): [voucherId, số đơn, tổng tiền giảm] */
	@Query("SELECT o.shippingVoucher.id, COUNT(o), COALESCE(SUM(o.shippingDiscount), 0) FROM Order o "
			+ "WHERE o.branch.id = :branchId AND o.shippingVoucher IS NOT NULL AND o.status <> :cancelled "
			+ "GROUP BY o.shippingVoucher.id")
	List<Object[]> shippingVoucherStatsOfBranch(@Param("branchId") Integer branchId,
			@Param("cancelled") OrderStatus cancelled);
}
