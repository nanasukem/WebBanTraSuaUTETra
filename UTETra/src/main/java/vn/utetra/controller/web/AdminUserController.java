package vn.utetra.controller.web;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.entity.Role;
import vn.utetra.entity.User;
import vn.utetra.repository.RoleRepository;
import vn.utetra.repository.UserRepository;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;

	public AdminUserController(UserRepository userRepository, RoleRepository roleRepository) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
	}

	/**
	 * Danh sách tài khoản, tìm kiếm và lọc. SecurityConfiguration bảo đảm chỉ ADMIN
	 * được truy cập.
	 */
	@GetMapping
	public String index(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "ALL") String role, @RequestParam(defaultValue = "ALL") String status,
			Model model) {

		List<User> users = keyword.isBlank() ? userRepository.findAllForAdmin()
				: userRepository.searchForAdmin(keyword.trim());

		// Lọc theo vai trò
		if (!"ALL".equalsIgnoreCase(role)) {
			users = users.stream()
					.filter(user -> user.getRole() != null && role.equalsIgnoreCase(user.getRole().getName()))
					.collect(Collectors.toList());
		}

		// Lọc theo trạng thái
		if ("ACTIVE".equalsIgnoreCase(status)) {
			users = users.stream().filter(user -> Boolean.TRUE.equals(user.getIsActive())).collect(Collectors.toList());

		} else if ("INACTIVE".equalsIgnoreCase(status)) {
			users = users.stream().filter(user -> !Boolean.TRUE.equals(user.getIsActive()))
					.collect(Collectors.toList());

		} else if ("VERIFIED".equalsIgnoreCase(status)) {
			users = users.stream().filter(user -> Boolean.TRUE.equals(user.getIsVerified()))
					.collect(Collectors.toList());

		} else if ("UNVERIFIED".equalsIgnoreCase(status)) {
			users = users.stream().filter(user -> !Boolean.TRUE.equals(user.getIsVerified()))
					.collect(Collectors.toList());
		}

		long activeUsers = userRepository.findAll().stream().filter(user -> Boolean.TRUE.equals(user.getIsActive()))
				.count();

		model.addAttribute("users", users);
		model.addAttribute("roles", roleRepository.findAll());

		model.addAttribute("keyword", keyword);
		model.addAttribute("selectedRole", role);
		model.addAttribute("selectedStatus", status);

		model.addAttribute("totalUsers", userRepository.count());
		model.addAttribute("activeUsers", activeUsers);
		model.addAttribute("inactiveUsers", userRepository.count() - activeUsers);

		return "admin/users/index";
	}

	/**
	 * Chi tiết tài khoản.
	 */
	@GetMapping("/{id}")
	public String detail(@PathVariable Integer id, RedirectAttributes redirectAttributes, Model model) {

		User user = userRepository.findById(id).orElse(null);

		if (user == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản.");

			return "redirect:/admin/users";
		}

		model.addAttribute("user", user);

		return "admin/users/detail";
	}

	/**
	 * Đổi vai trò tài khoản. Chỉ ADMIN được thực hiện.
	 */
	@PostMapping("/{id}/role")
	public String changeRole(@PathVariable Integer id, @RequestParam String roleName, Authentication authentication,
			RedirectAttributes redirectAttributes) {

		if (!isAdmin(authentication)) {
			redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền thay đổi vai trò tài khoản.");

			return "redirect:/admin/users";
		}

		User user = userRepository.findById(id).orElse(null);

		if (user == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản.");

			return "redirect:/admin/users";
		}

		String targetRoleName = roleName.trim().toUpperCase(Locale.ROOT);

		List<String> allowedRoles = List.of("ADMIN", "MANAGER", "VENDOR", "SHIPPER", "USER");

		if (!allowedRoles.contains(targetRoleName)) {
			redirectAttributes.addFlashAttribute("errorMessage", "Vai trò được chọn không hợp lệ.");

			return "redirect:/admin/users";
		}

		Role targetRole = roleRepository.findByName(targetRoleName).orElse(null);

		if (targetRole == null) {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Vai trò " + targetRoleName + " chưa tồn tại trong cơ sở dữ liệu.");

			return "redirect:/admin/users";
		}

		String currentRoleName = user.getRole() == null ? "" : user.getRole().getName();

		if (targetRoleName.equalsIgnoreCase(currentRoleName)) {
			redirectAttributes.addFlashAttribute("errorMessage", "Tài khoản đã có vai trò này.");

			return "redirect:/admin/users";
		}

		// Không cho Admin tự thay đổi vai trò của chính mình
		if (isCurrentUser(authentication, user)) {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Bạn không thể tự thay đổi vai trò " + "tài khoản đang đăng nhập.");

			return "redirect:/admin/users";
		}

		// Không cho hạ quyền Admin cuối cùng đang hoạt động
		if ("ADMIN".equalsIgnoreCase(currentRoleName) && Boolean.TRUE.equals(user.getIsActive())
				&& !"ADMIN".equals(targetRoleName) && userRepository.countActiveAdmins() <= 1) {

			redirectAttributes.addFlashAttribute("errorMessage",
					"Không thể hạ quyền Admin cuối cùng " + "đang hoạt động.");

			return "redirect:/admin/users";
		}

		user.setRole(targetRole);
		userRepository.save(user);

		redirectAttributes.addFlashAttribute("successMessage",
				"Đã đổi vai trò tài khoản " + user.getUsername() + " thành " + targetRoleName + ".");

		return "redirect:/admin/users";
	}

	/**
	 * Khóa hoặc mở khóa tài khoản. Chỉ ADMIN được thực hiện.
	 */
	@PostMapping("/{id}/toggle")
	public String toggleStatus(@PathVariable Integer id, Authentication authentication,
			RedirectAttributes redirectAttributes) {

		if (!isAdmin(authentication)) {
			redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền khóa hoặc mở khóa tài khoản.");

			return "redirect:/admin/users";
		}

		User user = userRepository.findById(id).orElse(null);

		if (user == null) {
			redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản.");

			return "redirect:/admin/users";
		}

		// Không cho tự khóa hoặc mở khóa tài khoản đang đăng nhập
		if (isCurrentUser(authentication, user)) {
			redirectAttributes.addFlashAttribute("errorMessage",
					"Bạn không thể khóa hoặc mở khóa " + "tài khoản đang đăng nhập.");

			return "redirect:/admin/users";
		}

		boolean currentlyActive = Boolean.TRUE.equals(user.getIsActive());

		// Không cho khóa Admin cuối cùng đang hoạt động
		if (currentlyActive && user.getRole() != null && "ADMIN".equalsIgnoreCase(user.getRole().getName())
				&& userRepository.countActiveAdmins() <= 1) {

			redirectAttributes.addFlashAttribute("errorMessage", "Không thể khóa Admin cuối cùng " + "đang hoạt động.");

			return "redirect:/admin/users";
		}

		user.setIsActive(!currentlyActive);
		userRepository.save(user);

		redirectAttributes.addFlashAttribute("successMessage",
				currentlyActive ? "Đã khóa tài khoản " + user.getUsername() + "."
						: "Đã mở khóa tài khoản " + user.getUsername() + ".");

		return "redirect:/admin/users";
	}

	/**
	 * Kiểm tra người dùng hiện tại có quyền ADMIN không.
	 */
	private boolean isAdmin(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
	}

	/**
	 * Kiểm tra có đang thao tác trên chính tài khoản đăng nhập không.
	 */
	private boolean isCurrentUser(Authentication authentication, User user) {

		return authentication != null && authentication.getName() != null && user.getUsername() != null
				&& authentication.getName().equalsIgnoreCase(user.getUsername());
	}
}