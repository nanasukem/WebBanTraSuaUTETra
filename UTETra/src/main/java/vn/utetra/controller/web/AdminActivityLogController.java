package vn.utetra.controller.web;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.entity.ActivityLog;
import vn.utetra.repository.ActivityLogRepository;

@Controller
@RequestMapping("/admin/activity-logs")
public class AdminActivityLogController {

	private static final int PAGE_SIZE = 30;
	private final ActivityLogRepository activityLogRepository;

	public AdminActivityLogController(ActivityLogRepository activityLogRepository) {
		this.activityLogRepository = activityLogRepository;
	}

	@GetMapping
	public String index(@RequestParam(required = false) String role, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, Model model) {
		String r = role == null || role.isBlank() ? null : role.trim().toUpperCase();
		String kw = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
		Page<ActivityLog> logs = activityLogRepository.search(r, kw, from == null ? null : from.atStartOfDay(),
				to == null ? null : to.plusDays(1).atStartOfDay(), PageRequest.of(Math.max(page, 0), PAGE_SIZE));
		model.addAttribute("logs", logs);
		model.addAttribute("role", r);
		model.addAttribute("keyword", keyword);
		model.addAttribute("from", from);
		model.addAttribute("to", to);
		return "admin/activity-logs";
	}
}
