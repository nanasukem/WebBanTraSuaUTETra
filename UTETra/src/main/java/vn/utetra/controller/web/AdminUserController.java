package vn.utetra.controller.web;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.entity.User;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.RoleRepository;
import vn.utetra.service.AdminUserService;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

	private final AdminUserService adminUserService;
	private final RoleRepository roleRepository;

	public AdminUserController(AdminUserService adminUserService, RoleRepository roleRepository) {
		this.adminUserService = adminUserService;
		this.roleRepository = roleRepository;
	}

	@GetMapping
	public String index(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "ALL") String role, @RequestParam(defaultValue = "ALL") String status,
			@RequestParam(defaultValue = "STAFF") String tab, @RequestParam(defaultValue = "") String branch,
			Model model) {
		AdminUserService.SearchResult r = adminUserService.search(keyword, tab, role, status, branch);
		model.addAttribute("users", r.users());
		model.addAttribute("branchLabels", r.branchLabels());
		model.addAttribute("tab", r.tab());
		model.addAttribute("selectedRole", r.role());
		model.addAttribute("selectedStatus", r.status());
		model.addAttribute("selectedBranch", r.branch());
		model.addAttribute("staffCount", r.staffCount());
		model.addAttribute("customerCount", r.customerCount());
		model.addAttribute("totalUsers", r.totalUsers());
		model.addAttribute("activeUsers", r.activeUsers());
		model.addAttribute("inactiveUsers", r.totalUsers() - r.activeUsers());
		model.addAttribute("keyword", keyword);
		model.addAttribute("branches", adminUserService.branches());
		model.addAttribute("roles", roleRepository.findAll());
		return "admin/users/index";
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Integer id, RedirectAttributes ra, Model model) {
		try {
			User user = adminUserService.get(id);
			model.addAttribute("user", user);
			model.addAttribute("managedBranches", adminUserService.managedBranches(id));
			model.addAttribute("branches", adminUserService.branches());
			model.addAttribute("shipperCounts", adminUserService.shipperCounts());
			model.addAttribute("roles", roleRepository.findAll());
			model.addAttribute("tempExpired", AdminUserService.tempExpired(user));
			return "admin/users/detail";
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/users";
		}
	}

	@GetMapping("/new")
	public String createForm(@RequestParam(defaultValue = "MANAGER") String role, Model model) {
		if (!model.containsAttribute("form")) {
			model.addAttribute("form", Map.of("role", role.toUpperCase(Locale.ROOT)));
		}
		model.addAttribute("branches", adminUserService.branches());
		model.addAttribute("shipperCounts", adminUserService.shipperCounts());
		return "admin/users/form";
	}

	@PostMapping("/new")
	public String create(@RequestParam String username, @RequestParam String fullName, @RequestParam String email,
			@RequestParam(required = false) String phone, @RequestParam String role,
			@RequestParam(required = false) Integer branchId, @RequestParam String password,
			@RequestParam String confirmPassword, RedirectAttributes ra) {
		try {
			AdminUserService.Created c = adminUserService.createStaff(username, fullName, email, phone, role, branchId,
					password, confirmPassword);
			RequestContextHolder.currentRequestAttributes().setAttribute("activityLink",
					"/admin/users/" + c.user().getId(), RequestAttributes.SCOPE_REQUEST);
			ra.addFlashAttribute("successMessage", c.message());
			return "redirect:/admin/users";
		} catch (BusinessException e) {
			Map<String, String> form = new HashMap<>();
			form.put("username", username);
			form.put("fullName", fullName);
			form.put("email", email);
			form.put("phone", phone == null ? "" : phone);
			form.put("role", role);
			form.put("branchId", branchId == null ? "" : String.valueOf(branchId));
			ra.addFlashAttribute("form", form);
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/users/new";
		}
	}

	@PostMapping("/{id}/reset-password")
	public String resetPassword(@PathVariable Integer id, @RequestParam String newPassword,
			@RequestParam String confirmPassword, Authentication auth, RedirectAttributes ra) {
		return run(ra, "redirect:/admin/users/" + id,
				() -> adminUserService.resetPassword(id, newPassword, confirmPassword, name(auth)));
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Integer id, Authentication auth, RedirectAttributes ra) {
		try {
			ra.addFlashAttribute("successMessage", adminUserService.delete(id, name(auth)));
			return "redirect:/admin/users";
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/users/" + id;
		}
	}

	@PostMapping("/{id}/role")
	public String changeRole(@PathVariable Integer id, @RequestParam String roleName, Authentication auth,
			RedirectAttributes ra) {
		return run(ra, "redirect:/admin/users", () -> adminUserService.changeRole(id, roleName, name(auth)));
	}

	@PostMapping("/{id}/branch")
	public String changeShipperBranch(@PathVariable Integer id, @RequestParam Integer branchId, RedirectAttributes ra) {
		return run(ra, "redirect:/admin/users/" + id, () -> adminUserService.changeShipperBranch(id, branchId));
	}

	@PostMapping("/{id}/toggle")
	public String toggleStatus(@PathVariable Integer id, Authentication auth, RedirectAttributes ra) {
		return run(ra, "redirect:/admin/users", () -> adminUserService.toggleActive(id, name(auth)));
	}

	private String run(RedirectAttributes ra, String back, java.util.function.Supplier<String> action) {
		try {
			ra.addFlashAttribute("successMessage", action.get());
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return back;
	}

	private static String name(Authentication auth) {
		return auth == null ? null : auth.getName();
	}
}
