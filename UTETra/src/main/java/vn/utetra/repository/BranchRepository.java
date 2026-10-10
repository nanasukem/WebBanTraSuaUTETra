package vn.utetra.repository;

import vn.utetra.entity.Branch;
import vn.utetra.entity.enums.BranchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Integer> {

	List<Branch> findByStatusOrderByNameAsc(BranchStatus status);

	List<Branch> findByOwner_Id(Integer ownerId);

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

	/**
	 * Tìm theo tên/địa chỉ + lọc trạng thái (tham số null = bỏ qua điều kiện đó)
	 */
	@Query(value = """
			SELECT b.* FROM Branches b
			WHERE (:keyword IS NULL
			       OR b.name    COLLATE Vietnamese_CI_AI LIKE N'%' + :keyword + N'%'
			       OR b.address COLLATE Vietnamese_CI_AI LIKE N'%' + :keyword + N'%')
			  AND (:status IS NULL OR b.status = :status)
			ORDER BY b.created_at DESC
			""", nativeQuery = true)
	List<Branch> search(@Param("keyword") String keyword, @Param("status") String status);
}