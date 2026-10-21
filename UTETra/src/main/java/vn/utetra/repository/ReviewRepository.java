package vn.utetra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.utetra.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Integer> {

	/** Đánh giá của 1 món, mới nhất lên đầu */
	List<Review> findByProductIdOrderByCreatedAtDescIdDesc(Integer productId);

	boolean existsByOrderDetailId(Integer orderDetailId);

	List<Review> findByOrderDetailIdIn(List<Integer> orderDetailIds);
}
