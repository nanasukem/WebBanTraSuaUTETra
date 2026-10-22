package vn.utetra.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.OrderIssue;
import vn.utetra.entity.enums.IssueStatus;

import java.util.Optional;

public interface OrderIssueRepository extends JpaRepository<OrderIssue, Integer> {

	Optional<OrderIssue> findByOrderId(Integer orderId);

	boolean existsByOrderId(Integer orderId);

	/** Báo cáo của 1 chi nhánh, mới nhất lên đầu (tab "Báo vấn đề" của Manager) */
	Page<OrderIssue> findByOrderBranchIdOrderByCreatedAtDesc(Integer branchId, Pageable pageable);

	long countByOrderBranchIdAndStatus(Integer branchId, IssueStatus status);

	/** Id các đơn của khách đang có báo cáo chờ quán xử lý (tab "Đang giải quyết") */
	@org.springframework.data.jpa.repository.Query("select i.order.id from OrderIssue i where i.user.id = :userId and i.status = :status")
	java.util.List<Integer> findOrderIdsByUserAndStatus(@org.springframework.data.repository.query.Param("userId") Integer userId,
			@org.springframework.data.repository.query.Param("status") IssueStatus status);
}
