package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {

	List<Notification> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Integer userId);

	long countByUserIdAndIsReadFalse(Integer userId);

	long countByUserIdAndOrderId(Integer userId, Integer orderId);

	boolean existsByUserIdAndRefKey(Integer userId, String refKey);

	java.util.Optional<Notification> findByIdAndUserId(Integer id, Integer userId);

	@org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
	@org.springframework.data.jpa.repository.Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
	int markAllRead(@org.springframework.data.repository.query.Param("userId") Integer userId);
}