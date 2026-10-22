package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.utetra.entity.Branch;
import vn.utetra.entity.enums.BranchStatus;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {

	List<Branch> findByStatus(BranchStatus status);

	@Query("SELECT b FROM Branch b WHERE "
			+ "(:keyword IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(b.address) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
			+ "AND (:status IS NULL OR b.status = :status)")
	List<Branch> searchBranches(@Param("keyword") String keyword, @Param("status") BranchStatus status);
	
	/** Chi nhánh được giao cho manager quản lý */
	List<Branch> findByOwnerIdOrderByIdAsc(Integer ownerId);
}