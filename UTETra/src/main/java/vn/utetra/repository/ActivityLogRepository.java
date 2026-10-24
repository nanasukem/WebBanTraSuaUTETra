package vn.utetra.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.utetra.entity.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Integer> {

	@Query("SELECT a FROM ActivityLog a WHERE (:role IS NULL OR a.actorRole = :role) "
			+ "AND (:kw IS NULL OR LOWER(a.actorName) LIKE :kw OR LOWER(a.action) LIKE :kw) "
			+ "AND (:from IS NULL OR a.createdAt >= :from) AND (:to IS NULL OR a.createdAt < :to) "
			+ "ORDER BY a.createdAt DESC, a.id DESC")
	Page<ActivityLog> search(@Param("role") String role, @Param("kw") String keyword,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
