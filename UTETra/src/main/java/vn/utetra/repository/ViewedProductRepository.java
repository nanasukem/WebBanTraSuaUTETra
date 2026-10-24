package vn.utetra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.utetra.entity.ViewedProduct;

public interface ViewedProductRepository extends JpaRepository<ViewedProduct, Integer> {

	Optional<ViewedProduct> findByUserIdAndProductId(Integer userId, Integer productId);

	List<ViewedProduct> findByUserIdOrderByViewedAtDescIdDesc(Integer userId, Pageable pageable);

	long countByUserId(Integer userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM ViewedProduct v WHERE v.user.id = :userId")
	int deleteAllOfUser(@Param("userId") Integer userId);
}
