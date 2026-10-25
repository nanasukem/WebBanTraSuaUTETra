package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.utetra.entity.User;
import vn.utetra.repository.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

	private final UserRepository userRepository;

	public AdminApiController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/dashboard")
	@Transactional(readOnly = true)
	public ResponseEntity<?> getDashboardSummary(Authentication authentication) {
		long totalUsers = 0;
		long activeUsers = 0;
		for (User u : userRepository.findAll()) {
			totalUsers++;
			if (Boolean.TRUE.equals(u.getIsActive())) {
				activeUsers++;
			}
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("adminUsername", authentication.getName());
		response.put("totalUsers", totalUsers);
		response.put("activeUsers", activeUsers);
		response.put("lockedUsers", totalUsers - activeUsers);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/users")
	@Transactional(readOnly = true)
	public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
		List<Map<String, Object>> result = new ArrayList<>();
		for (User u : userRepository.findAll()) {
			result.add(toSafeUserMap(u));
		}
		return ResponseEntity.ok(result);
	}

	@GetMapping("/users/{id}")
	@Transactional(readOnly = true)
	public ResponseEntity<?> getUserById(@PathVariable Integer id) {
		User user = userRepository.findById(id).orElse(null);
		if (user == null) {
			return ResponseEntity.status(404).body(Map.of("error", "Không tìm thấy người dùng với ID: " + id));
		}
		return ResponseEntity.ok(toSafeUserMap(user));
	}

	private Map<String, Object> toSafeUserMap(User u) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", u.getId());
		map.put("username", u.getUsername());
		map.put("fullName", u.getFullName());
		map.put("email", u.getEmail());
		map.put("role", u.getRole() != null ? u.getRole().getName() : null);
		map.put("isActive", u.getIsActive());
		return map;
	}
}