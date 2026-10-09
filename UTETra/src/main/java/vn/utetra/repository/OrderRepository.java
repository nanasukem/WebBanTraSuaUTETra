package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
	List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

	List<Order> findByBranchIdAndStatusOrderByCreatedAtDesc(Integer branchId, OrderStatus status);

	List<Order> findByShipperIdAndStatusOrderByCreatedAtDesc(Integer shipperId, OrderStatus status);
}