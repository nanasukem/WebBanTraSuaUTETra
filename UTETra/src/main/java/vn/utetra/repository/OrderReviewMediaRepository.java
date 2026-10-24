package vn.utetra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.utetra.entity.OrderReviewMedia;

public interface OrderReviewMediaRepository extends JpaRepository<OrderReviewMedia, Integer> {
	List<OrderReviewMedia> findByOrderReviewIdOrderByIdAsc(Integer orderReviewId);

	List<OrderReviewMedia> findByOrderReviewIdInOrderByIdAsc(List<Integer> orderReviewIds);
}
