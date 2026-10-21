package vn.utetra.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.utetra.entity.OrderReview;

public interface OrderReviewRepository extends JpaRepository<OrderReview, Integer> {

	Optional<OrderReview> findByOrderId(Integer orderId);

	boolean existsByOrderId(Integer orderId);
}
