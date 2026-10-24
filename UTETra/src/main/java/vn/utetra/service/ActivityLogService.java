package vn.utetra.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.ActivityLog;
import vn.utetra.repository.ActivityLogRepository;

@Service
@Transactional(readOnly = true)
public class ActivityLogService {

	private final ActivityLogRepository activityLogRepository;

	public ActivityLogService(ActivityLogRepository activityLogRepository) {
		this.activityLogRepository = activityLogRepository;
	}

	public static String normalizeRole(String role) {
		return role == null || role.isBlank() ? null : role.trim().toUpperCase();
	}

	public Page<ActivityLog> search(String role, String keyword, LocalDate from, LocalDate to, int page, int size) {
		String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
		return activityLogRepository.search(normalizeRole(role), kw, from == null ? null : from.atStartOfDay(),
				to == null ? null : to.plusDays(1).atStartOfDay(), PageRequest.of(Math.max(page, 0), size));
	}
}
