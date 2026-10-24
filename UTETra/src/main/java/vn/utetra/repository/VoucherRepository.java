package vn.utetra.repository;

import vn.utetra.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	List<Voucher> findByBranchIsNullOrderByCreatedAtDesc();

	List<Voucher> findAllByOrderByCreatedAtDesc();

	@Query("SELECT v FROM Voucher v WHERE v.branch IS NULL OR v.branch.id = :branchId ORDER BY v.endDate DESC, v.id DESC")
	List<Voucher> findApplicableToBranch(@Param("branchId") Integer branchId);

	List<Voucher> findByBranch_IdOrderByCreatedAtDesc(Integer branchId);

	@Query("""
			SELECT v FROM Voucher v
			WHERE v.isActive = true
			  AND v.startDate <= :now AND v.endDate >= :now
			  AND v.usedCount < v.quantity
			  AND (v.branch IS NULL OR v.branch.id = :branchId)
			ORDER BY v.endDate ASC
			""")
	List<Voucher> findUsable(@Param("branchId") Integer branchId, @Param("now") LocalDateTime now);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE Voucher v SET v.usedCount = v.usedCount + 1
			WHERE v.id = :id AND v.isActive = true AND v.usedCount < v.quantity
			""")
	int increaseUsedCount(@Param("id") Integer id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Voucher v set v.usedCount = v.usedCount - 1 where v.id = :id and v.usedCount > 0")
	int decreaseUsedCount(@Param("id") Integer id);
}