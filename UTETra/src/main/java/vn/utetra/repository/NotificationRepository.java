package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

	/** 20 thông báo mới nhất của 1 người */
	List<Notification> findTop20ByUserIdOrderByCreatedAtDesc(Integer userId);

	/** Số thông báo chưa đọc */
	long countByUserIdAndIsReadFalse(Integer userId);

	/** Số thông báo của 1 người về 1 đơn (dùng trong test) */
	long countByUserIdAndOrderId(Integer userId, Integer orderId);
}