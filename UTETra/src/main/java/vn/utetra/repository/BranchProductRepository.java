package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.utetra.entity.BranchProduct;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchProductRepository extends JpaRepository<BranchProduct, Integer> {

	Optional<BranchProduct> findByBranch_IdAndProduct_Id(Integer branchId, Integer productId);

	List<BranchProduct> findByBranchId(Integer branchId);

	@Query("SELECT bp.product.id FROM BranchProduct bp WHERE bp.branch.id = :branchId AND bp.isAvailable = false")
	List<Integer> findUnavailableProductIds(@Param("branchId") Integer branchId);
}