package vn.utetra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.utetra.entity.BranchTopping;

public interface BranchToppingRepository extends JpaRepository<BranchTopping, Integer> {

	Optional<BranchTopping> findByBranch_IdAndTopping_Id(Integer branchId, Integer toppingId);

	@Query("SELECT bt.topping.id FROM BranchTopping bt WHERE bt.branch.id = :branchId AND bt.isAvailable = false")
	List<Integer> findUnavailableToppingIds(@Param("branchId") Integer branchId);

	@Query("SELECT bt.topping.id, bt.branch.name FROM BranchTopping bt WHERE bt.isAvailable = false ORDER BY bt.branch.id")
	List<Object[]> findAllUnavailable();
}
