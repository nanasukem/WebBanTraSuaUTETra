package vn.utetra.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

	List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

	List<Order> findByBranchIdAndStatusOrderByCreatedAtDesc(Integer branchId, OrderStatus status);

	List<Order> findByShipperIdAndStatusOrderByCreatedAtDesc(Integer shipperId, OrderStatus status);

	List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime before);

	List<Order> findByPaymentMethodAndPaymentStatusAndStatusNotAndCreatedAtAfter(PaymentMethod paymentMethod,
			PaymentStatus paymentStatus, OrderStatus excludedStatus, LocalDateTime deadline);

	List<Order> findByPaymentMethodAndPaymentStatusAndStatusNotAndCreatedAtBefore(PaymentMethod paymentMethod,
			PaymentStatus paymentStatus, OrderStatus excludedStatus, LocalDateTime deadline);

	@Query("SELECT SUM(o.totalAmount - o.refundAmount) FROM Order o " + "WHERE o.user.id = :userId " + "AND o.status = :status "
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

	Page<Order> findPageByBranchIdAndStatusInOrderByCreatedAtDesc(Integer branchId, Collection<OrderStatus> statuses,
			Pageable pageable);

	@Query("select o from Order o where o.branch.id = :branchId and o.status in :statuses and not exists "
			+ "(select i.id from OrderIssue i where i.order = o and i.status = vn.utetra.entity.enums.IssueStatus.PENDING) "
			+ "order by o.createdAt desc")
	Page<Order> findPageWithoutPendingIssue(@Param("branchId") Integer branchId,
			@Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

	@Query("select o.status, count(o) from Order o " + "where o.branch.id = :branchId group by o.status")
	List<Object[]> countByStatusOfBranch(@Param("branchId") Integer branchId);

	List<Order> findByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThanOrderByDeliveredAtAsc(
			OrderStatus status, LocalDateTime start, LocalDateTime end);

	@Query("SELECT COALESCE(SUM(o.totalAmount - o.refundAmount), 0) FROM Order o " + "WHERE o.status = :status "
			+ "AND o.deliveredAt >= :start " + "AND o.deliveredAt < :end")
	BigDecimal sumRevenueByStatusAndDeliveredAt(@Param("status") OrderStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

	long countByStatusAndDeliveredAtGreaterThanEqualAndDeliveredAtLessThan(OrderStatus status, LocalDateTime start,
			LocalDateTime end);

	@Query("SELECT o.productVoucher.id, COUNT(o), " + "COALESCE(SUM(o.productDiscount), 0) FROM Order o "
			+ "WHERE o.branch.id = :branchId " + "AND o.productVoucher IS NOT NULL " + "AND o.status <> :cancelled "
			+ "GROUP BY o.productVoucher.id")
	List<Object[]> productVoucherStatsOfBranch(@Param("branchId") Integer branchId,
			@Param("cancelled") OrderStatus cancelled);

	long countByBranch_IdAndStatusIn(Integer branchId, Collection<OrderStatus> statuses);

	@Query("SELECT o.id FROM Order o WHERE o.shipper.id = :shipperId AND o.status IN :statuses ORDER BY o.id")
	List<Integer> findOpenOrderIdsOfShipper(@Param("shipperId") Integer shipperId,
			@Param("statuses") Collection<OrderStatus> statuses);

	@Query("SELECT COALESCE(SUM(o.totalAmount - o.refundAmount), 0) FROM Order o WHERE o.status = :status "
			+ "AND o.deliveredAt >= :start AND o.deliveredAt < :end AND (:branchId IS NULL OR o.branch.id = :branchId)")
	BigDecimal sumRevenueOfBranch(@Param("status") OrderStatus status, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("branchId") Integer branchId);

	@Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status "
			+ "AND o.deliveredAt >= :start AND o.deliveredAt < :end AND (:branchId IS NULL OR o.branch.id = :branchId)")
	long countDeliveredOfBranch(@Param("status") OrderStatus status, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("branchId") Integer branchId);

	@Query("SELECT o FROM Order o WHERE o.status = :status AND o.deliveredAt >= :start AND o.deliveredAt < :end "
			+ "AND (:branchId IS NULL OR o.branch.id = :branchId) ORDER BY o.deliveredAt")
	List<Order> findDeliveredOfBranch(@Param("status") OrderStatus status, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("branchId") Integer branchId);

	@Query(value = "SELECT CAST(o.delivered_at AS DATE) AS ngay, SUM(o.total_amount - o.refund_amount) AS doanh_thu, "
			+ "COUNT(*) AS so_don FROM Orders o WHERE o.status = 'DELIVERED' "
			+ "AND o.delivered_at >= :start AND o.delivered_at < :end AND (:branchId = 0 OR o.branch_id = :branchId) "
			+ "GROUP BY CAST(o.delivered_at AS DATE) ORDER BY ngay", nativeQuery = true)
	List<Object[]> revenueByDay(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			@Param("branchId") int branchId);

	@Query("SELECT YEAR(o.deliveredAt), MONTH(o.deliveredAt), COALESCE(SUM(o.totalAmount - o.refundAmount), 0), COUNT(o) "
			+ "FROM Order o WHERE o.status = :status AND o.deliveredAt >= :start AND o.deliveredAt < :end "
			+ "AND (:branchId IS NULL OR o.branch.id = :branchId) "
			+ "GROUP BY YEAR(o.deliveredAt), MONTH(o.deliveredAt) ORDER BY YEAR(o.deliveredAt), MONTH(o.deliveredAt)")
	List<Object[]> revenueByMonth(@Param("status") OrderStatus status, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("branchId") Integer branchId);

	@Query("SELECT o.branch.id, o.branch.name, COALESCE(SUM(o.totalAmount - o.refundAmount), 0), COUNT(o) "
			+ "FROM Order o WHERE o.status = :status AND o.deliveredAt >= :start AND o.deliveredAt < :end "
			+ "GROUP BY o.branch.id, o.branch.name ORDER BY COALESCE(SUM(o.totalAmount - o.refundAmount), 0) DESC")
	List<Object[]> revenueByBranch(@Param("status") OrderStatus status, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end);

	List<Order> findByShipper_IdAndStatusOrderByDeliveredAtDesc(Integer shipperId, OrderStatus status);
}