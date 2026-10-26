package vn.utetra.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.utetra.service.AdminUserService;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

	private final AdminUserService adminUserService;

	public AdminApiController(AdminUserService adminUserService) {
		this.adminUserService = adminUserService;
	}

	@GetMapping("/dashboard")
	public ResponseEntity<?> getDashboardSummary(Authentication authentication) {
		return ResponseEntity.ok(adminUserService.apiDashboard(authentication.getName()));
	}

	@GetMapping("/users")
	public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
		return ResponseEntity.ok(adminUserService.apiUsers());
	}

	@GetMapping("/users/{id}")
	public ResponseEntity<?> getUserById(@PathVariable Integer id) {
		return adminUserService.apiUser(id).<ResponseEntity<?>>map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy người dùng với ID: " + id)));
	}
}
