package vn.utetra.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import vn.utetra.entity.User;
import vn.utetra.exception.BusinessException;
import vn.utetra.service.EmailService;
import vn.utetra.service.NotificationService;
import vn.utetra.service.UserService;

@Controller
public class AccountController {

	private final UserService userService;
	private final EmailService emailService;
	private final NotificationService notificationService;
	private final vn.utetra.service.BranchService branchService;

	public AccountController(UserService userService, EmailService emailService,
			NotificationService notificationService, vn.utetra.service.BranchService branchService) {
		this.userService = userService;
		this.branchService = branchService;
		this.emailService = emailService;
		this.notificationService = notificationService;
	}

	@GetMapping("/account")
	public String account(@RequestParam(defaultValue = "info") String tab, HttpSession session, Model model) {
		User u = sessionUser(session);
		if (u == null) return "redirect:/auth/login";
		if (Boolean.TRUE.equals(u.getMustChangePassword())) return "redirect:/account/change-password";
		String role = roleOf(u);
		if ("USER".equals(role)) return "redirect:/user/profile";
		User fresh = userService.findById(u.getId()).orElse(u);
		model.addAttribute("account", fresh);
		model.addAttribute("roleName", role);
		model.addAttribute("roleLabel", switch (role) {
		case "ADMIN" -> "Quản trị viên (Admin)";
		case "MANAGER" -> "Quản lý chi nhánh (Manager)";
		case "SHIPPER" -> "Nhân viên giao hàng (Shipper)";
		default -> role;
		});
		model.addAttribute("myBranches", branchService.findByOwner(fresh.getId()));
		model.addAttribute("tab", "password".equals(tab) ? "password" : "info");
		return switch (role) {
		case "ADMIN" -> "account/admin";
		case "MANAGER" -> "account/manager";
		default -> "account/staff";
		};
	}

	@PostMapping("/account/profile")
	public String updateProfile(@RequestParam(required = false) String fullName,
			@RequestParam(required = false) String phone,
			@RequestParam(name = "avatar", required = false) org.springframework.web.multipart.MultipartFile avatar,
			HttpSession session, RedirectAttributes ra) {
		User u = sessionUser(session);
		if (u == null) return "redirect:/auth/login";
		try {
			User saved = userService.updateProfile(u.getId(), fullName, phone, avatar);
			session.setAttribute("user", saved);
			ra.addFlashAttribute("successMessage", "Đã cập nhật thông tin cá nhân");
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/account?tab=info";
	}

	@GetMapping("/account/change-password")
	public String form(HttpSession session, Model model) {
		User u = sessionUser(session);
		if (u == null) return "redirect:/auth/login";
		boolean forced = Boolean.TRUE.equals(u.getMustChangePassword());
		if (!forced) return "USER".equals(roleOf(u)) ? "redirect:/user/profile?tab=password" : "redirect:/account?tab=password";
		model.addAttribute("forced", true);
		model.addAttribute("account", u);
		return "account/change-password";
	}

	@PostMapping("/account/change-password")
	public String change(@RequestParam String currentPassword, @RequestParam String newPassword,
			@RequestParam String confirmPassword, HttpSession session, RedirectAttributes ra) {
		User u = sessionUser(session);
		if (u == null) return "redirect:/auth/login";
		try {
			userService.changePassword(u.getId(), currentPassword, newPassword, confirmPassword);
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return Boolean.TRUE.equals(u.getMustChangePassword()) ? "redirect:/account/change-password"
					: "redirect:/account?tab=password";
		}
		boolean forced = Boolean.TRUE.equals(u.getMustChangePassword());
		User fresh = userService.clearTemporaryPassword(u.getId());
		session.setAttribute("user", fresh);

		emailService.send(fresh.getEmail(), "UTETra - Mật khẩu của bạn đã được đổi",
				"Xin chào " + fresh.getFullName() + ",\n\nMật khẩu tài khoản UTETra \"" + fresh.getUsername()
						+ "\" vừa được đổi thành công lúc " + java.time.LocalDateTime.now()
								.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
						+ ".\nNếu không phải bạn đổi, hãy liên hệ ngay quản trị viên UTETra để khoá tài khoản.\n\nUTETra");
		notificationService.create(fresh, null, "USER", "Bạn đã đổi mật khẩu thành công",
				"Đã gửi email xác nhận tới " + fresh.getEmail(), null);

		ra.addFlashAttribute("successMessage", "Đã đổi mật khẩu. Email xác nhận đã gửi tới " + fresh.getEmail() + ".");
		return forced ? "redirect:/login-success" : "redirect:/account?tab=password";
	}

	private static String roleOf(User u) {
		return u.getRole() == null || u.getRole().getName() == null ? "" : u.getRole().getName().trim().toUpperCase();
	}

	private static User sessionUser(HttpSession session) {
		Object o = session.getAttribute("user");
		return o instanceof User u ? u : null;
	}
}
