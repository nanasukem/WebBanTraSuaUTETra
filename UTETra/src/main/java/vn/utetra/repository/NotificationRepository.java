package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

	/** 20 thông báo mới nhất của 1 người */
	List<Notification> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Integer userId); // cùng thời điểm: cái tạo sau lên trước

	/** Số thông báo chưa đọc */
	long countByUserIdAndIsReadFalse(Integer userId);

	/** Số thông báo của 1 người về 1 đơn (dùng trong test) */
	long countByUserIdAndOrderId(Integer userId, Integer orderId);

	/** Đã báo việc này cho người này chưa (chống báo trùng) */
	boolean existsByUserIdAndRefKey(Integer userId, String refKey);

	java.util.Optional<Notification> findByIdAndUserId(Integer id, Integer userId);

	/** Đánh dấu đã đọc tất cả */
	@org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
	@org.springframework.data.jpa.repository.Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
	int markAllRead(@org.springframework.data.repository.query.Param("userId") Integer userId);
}