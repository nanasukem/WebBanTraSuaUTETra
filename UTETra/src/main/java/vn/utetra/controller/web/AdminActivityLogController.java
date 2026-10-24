package vn.utetra.controller.web;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.entity.ActivityLog;

@Controller
@RequestMapping("/admin/activity-logs")
public class AdminActivityLogController {

	private static final int PAGE_SIZE = 30;
	private final vn.utetra.service.ActivityLogService activityLogService;

	public AdminActivityLogController(vn.utetra.service.ActivityLogService activityLogService) {
		this.activityLogService = activityLogService;
	}

	@GetMapping
	public String index(@RequestParam(required = false) String role, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, Model model) {
		String r = vn.utetra.service.ActivityLogService.normalizeRole(role);
		Page<ActivityLog> logs = activityLogService.search(role, keyword, from, to, page, PAGE_SIZE);
		model.addAttribute("logs", logs);
		model.addAttribute("role", r);
		model.addAttribute("keyword", keyword);
		model.addAttribute("from", from);
		model.addAttribute("to", to);
		return "admin/activity-logs";
	}
}
