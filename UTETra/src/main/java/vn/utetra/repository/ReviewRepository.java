package vn.utetra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.utetra.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Integer> {

	long countByProduct_Id(Integer productId);

	List<Review> findByProductIdOrderByCreatedAtDescIdDesc(Integer productId);

	boolean existsByOrderDetailId(Integer orderDetailId);

	List<Review> findByOrderDetailIdIn(List<Integer> orderDetailIds);

	long countByOrderDetailIdIn(List<Integer> orderDetailIds);
}
