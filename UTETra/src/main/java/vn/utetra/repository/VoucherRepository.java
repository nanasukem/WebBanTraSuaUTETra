package vn.utetra.repository;

import vn.utetra.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

	Optional<Voucher> findByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);

	/** Voucher của app (Manager/Admin) */
	List<Voucher> findByBranchIsNullOrderByCreatedAtDesc();

	/** Voucher riêng của 1 shop (Vendor) */
	List<Voucher> findByBranch_IdOrderByCreatedAtDesc(Integer branchId);

	/**
	 * Voucher khách chọn được khi đặt hàng ở shop branchId: voucher app + voucher
	 * của shop đó
	 */
	@Query("""
			SELECT v FROM Voucher v
			WHERE v.isActive = true
			  AND v.startDate <= :now AND v.endDate >= :now
			  AND v.usedCount < v.quantity
			  AND (v.branch IS NULL OR v.branch.id = :branchId)
			ORDER BY v.endDate ASC
			""")
	List<Voucher> findUsable(@Param("branchId") Integer branchId, @Param("now") LocalDateTime now);
}