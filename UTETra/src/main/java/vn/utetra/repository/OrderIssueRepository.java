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

	Page<OrderIssue> findByOrderBranchIdOrderByCreatedAtDesc(Integer branchId, Pageable pageable);

	long countByOrderBranchId(Integer branchId);

	long countByOrderBranchIdAndStatus(Integer branchId, IssueStatus status);

	@org.springframework.data.jpa.repository.Query("select i.order.id from OrderIssue i where i.user.id = :userId and i.status = :status")
	java.util.List<Integer> findOrderIdsByUserAndStatus(@org.springframework.data.repository.query.Param("userId") Integer userId,
			@org.springframework.data.repository.query.Param("status") IssueStatus status);
}
