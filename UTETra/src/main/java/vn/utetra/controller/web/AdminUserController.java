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
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.RoleRepository;
import vn.utetra.repository.UserRepository;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final BranchRepository branchRepository;
	private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
	private final vn.utetra.service.ShippingFeeService shippingFeeService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.NotificationService notificationService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.EmailService emailService;
	@jakarta.persistence.PersistenceContext
	private jakarta.persistence.EntityManager em;

	private static final String LOGIN_URL = "http://localhost:8080/auth/login";
	/** Mật khẩu tạm có hiệu lực 72 giờ; quá hạn chưa đổi thì phải nhờ Admin cấp lại */
	public static final int TEMP_PASSWORD_HOURS = 72;

	/** Gửi email mật khẩu tạm cho nhân viên (tạo mới hoặc Admin đặt lại) */
	private boolean emailTempPassword(User user, String tempPassword, boolean isNew) {
		String role = user.getRole() == null ? "" : user.getRole().getName();
		return emailService.send(user.getEmail(),
				isNew ? "UTETra - Tài khoản " + role + " của bạn đã được tạo" : "UTETra - Mật khẩu của bạn đã được đặt lại",
				"Xin chào " + user.getFullName() + ",\n\n"
						+ (isNew ? "Quản trị viên UTETra đã tạo tài khoản " + role + " cho bạn."
								: "Quản trị viên UTETra đã đặt lại mật khẩu tài khoản của bạn. Mật khẩu cũ không dùng được nữa.")
						+ "\n\nTên đăng nhập: " + user.getUsername() + "\nMật khẩu tạm: " + tempPassword
						+ "\nĐăng nhập tại: " + LOGIN_URL
						+ "\n\nMật khẩu tạm có hiệu lực " + TEMP_PASSWORD_HOURS + " giờ (tới "
						+ user.getTempPasswordExpiresAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
						+ "). Lần đăng nhập đầu, hệ thống sẽ yêu cầu bạn đổi sang mật khẩu của riêng mình."
						+ "\nNếu bạn không yêu cầu việc này, hãy liên hệ quản trị viên.\n\nUTETra");
	}

	public AdminUserController(UserRepository userRepository, RoleRepository roleRepository,
			BranchRepository branchRepository,
			org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
			vn.utetra.service.ShippingFeeService shippingFeeService) {
		this.passwordEncoder = passwordEncoder;
		this.shippingFeeService = shippingFeeService;
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.branchRepository = branchRepository;
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
		// mật khẩu tạm đã quá 72 giờ chưa đổi
		model.addAttribute("tempExpired", user.getTempPasswordExpiresAt() != null
				&& user.getTempPasswordExpiresAt().isBefore(java.time.LocalDateTime.now()));

		return "admin/users/detail";
	}

	/**
	 * Đổi vai trò tài khoản. Chỉ ADMIN được thực hiện.
	 */
	// ================= ADMIN TẠO TÀI KHOẢN (Manager, Shipper, Admin, khách) =================

	/** Form tạo tài khoản; ?role=MANAGER để chọn sẵn vai trò */
	@GetMapping("/new")
	public String createForm(@RequestParam(defaultValue = "MANAGER") String role, Model model) {
		if (!model.containsAttribute("form")) {
			model.addAttribute("form", java.util.Map.of("role", role.toUpperCase(Locale.ROOT)));
		}
		model.addAttribute("branches", branchRepository.findAll().stream()
				.sorted(java.util.Comparator.comparing(vn.utetra.entity.Branch::getId)).toList());
		return "admin/users/form";
	}

	@PostMapping("/new")
	public String create(@RequestParam String username, @RequestParam String fullName, @RequestParam String email,
			@RequestParam(required = false) String phone, @RequestParam String role,
			@RequestParam(required = false) Integer branchId, @RequestParam String password,
			@RequestParam String confirmPassword, Authentication authentication, RedirectAttributes ra) {

		java.util.Map<String, String> form = new java.util.HashMap<>();
		form.put("username", username);
		form.put("fullName", fullName);
		form.put("email", email);
		form.put("phone", phone == null ? "" : phone);
		form.put("role", role);
		form.put("branchId", branchId == null ? "" : String.valueOf(branchId));

		if (!isAdmin(authentication)) {
			ra.addFlashAttribute("errorMessage", "Bạn không có quyền tạo tài khoản.");
			return "redirect:/admin/users";
		}

		String u = username == null ? "" : username.trim();
		String name = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
		String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		String tel = phone == null ? "" : phone.trim();
		String r = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);

		String error = null;
		if (!u.matches("[A-Za-z0-9_.]{3,50}")) {
			error = "Tên đăng nhập 3–50 ký tự, chỉ gồm chữ không dấu, số, dấu _ hoặc dấu chấm.";
		} else if (userRepository.existsByUsername(u)) {
			error = "Tên đăng nhập \"" + u + "\" đã có người dùng.";
		} else if (name.isEmpty() || name.length() > 100) {
			error = "Vui lòng nhập họ tên (tối đa 100 ký tự).";
		} else if (!mail.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
			error = "Email không hợp lệ.";
		} else if (userRepository.existsByEmail(mail)) {
			error = "Email " + mail + " đã được dùng cho tài khoản khác.";
		} else if (!tel.isEmpty() && !tel.matches("0\\d{9}")) {
			error = "Số điện thoại gồm 10 số, bắt đầu bằng 0.";
		} else if (!List.of("ADMIN", "MANAGER", "SHIPPER").contains(r)) {
			error = "Chỉ tạo được tài khoản nhân viên (Manager, Shipper, Admin). Khách hàng tự đăng ký trên web.";
		} else if (password == null || password.length() < 6) {
			error = "Mật khẩu tối thiểu 6 ký tự.";
		} else if (!password.equals(confirmPassword)) {
			error = "Mật khẩu nhập lại không khớp.";
		}
		Role targetRole = error == null ? roleRepository.findByName(r).orElse(null) : null;
		if (error == null && targetRole == null) {
			error = "Vai trò " + r + " chưa có trong cơ sở dữ liệu.";
		}
		if (error != null) {
			ra.addFlashAttribute("errorMessage", error);
			ra.addFlashAttribute("form", form);
			return "redirect:/admin/users/new";
		}

		User user = new User();
		user.setUsername(u);
		user.setFullName(name);
		user.setEmail(mail);
		user.setPhone(tel.isEmpty() ? null : tel);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(targetRole);
		user.setIsVerified(true); // Admin tạo nên không cần xác minh email
		user.setIsActive(true);
		user.setMustChangePassword(true); // mật khẩu tạm -> lần đăng nhập đầu bắt buộc đổi
		user.setTempPasswordExpiresAt(java.time.LocalDateTime.now().plusHours(TEMP_PASSWORD_HOURS));
		if ("SHIPPER".equals(r)) {
			user.setCarrier(shippingFeeService.shipper()); // shipper của quán (Shipper UTETra)
		}
		userRepository.save(user);

		// Manager: giao chi nhánh luôn (chi nhánh đang có Manager khác thì chuyển sang Manager mới)
		String giao = null;
		if ("MANAGER".equals(r) && branchId != null) {
			vn.utetra.entity.Branch b = branchRepository.findById(branchId).orElse(null);
			if (b != null) {
				String cu = b.getOwner() == null ? null : b.getOwner().getFullName();
				b.setOwner(user);
				branchRepository.save(b);
				giao = " Đã giao chi nhánh " + b.getName() + (cu == null ? "" : " (thay cho " + cu + ")") + ".";
			}
		}

		String next = giao != null ? giao : switch (r) {
		case "MANAGER" -> " Chưa giao chi nhánh: vào Quản lý → Chi nhánh → Sửa để giao chi nhánh cho Manager này.";
		case "SHIPPER" -> " Shipper đăng nhập là thấy đơn chờ giao.";
		default -> "";
		};
		boolean daGui = emailTempPassword(user, password, true);
		ra.addFlashAttribute("successMessage", "Đã tạo tài khoản " + r + " \"" + u + "\" (" + name + ")." + next
				+ (daGui ? " Đã gửi email tên đăng nhập và mật khẩu tạm tới " + mail + "."
						: " Chưa gửi được email, hãy tự gửi mật khẩu tạm cho người dùng.")
				+ " Lần đăng nhập đầu họ phải đổi mật khẩu.");
		return "redirect:/admin/users";
	}

	/**
	 * Admin ĐẶT LẠI MẬT KHẨU cho 1 tài khoản (nhân viên quên mật khẩu, hoặc Admin cần lấy lại quyền vào tài khoản).
	 * Mật khẩu cũ mất tác dụng ngay; người dùng được báo trên chuông. Admin không xem được mật khẩu đang dùng của ai.
	 */
	@PostMapping("/{id}/reset-password")
	public String resetPassword(@PathVariable Integer id, @RequestParam String newPassword,
			@RequestParam String confirmPassword, Authentication authentication, RedirectAttributes ra) {
		if (!isAdmin(authentication)) {
			ra.addFlashAttribute("errorMessage", "Bạn không có quyền đặt lại mật khẩu.");
			return "redirect:/admin/users";
		}
		User user = userRepository.findById(id).orElse(null);
		if (user == null) {
			ra.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản.");
			return "redirect:/admin/users";
		}
		if (isCurrentUser(authentication, user)) {
			ra.addFlashAttribute("errorMessage", "Đổi mật khẩu của chính bạn ở trang Hồ sơ.");
			return "redirect:/admin/users/" + id;
		}
		if (newPassword == null || newPassword.length() < 6) {
			ra.addFlashAttribute("errorMessage", "Mật khẩu mới tối thiểu 6 ký tự.");
			return "redirect:/admin/users/" + id;
		}
		if (!newPassword.equals(confirmPassword)) {
			ra.addFlashAttribute("errorMessage", "Mật khẩu nhập lại không khớp.");
			return "redirect:/admin/users/" + id;
		}
		user.setPassword(passwordEncoder.encode(newPassword));
		user.setMustChangePassword(true); // mật khẩu tạm -> lần đăng nhập tới bắt buộc đổi
		user.setTempPasswordExpiresAt(java.time.LocalDateTime.now().plusHours(TEMP_PASSWORD_HOURS));
		userRepository.save(user);
		notificationService.create(user, null, "USER", "Mật khẩu của bạn đã được Admin đặt lại",
				"Đăng nhập bằng mật khẩu tạm (đã gửi qua email) rồi đặt mật khẩu mới.", null);
		boolean daGui = emailTempPassword(user, newPassword, false);
		ra.addFlashAttribute("successMessage", "Đã đặt lại mật khẩu tạm cho " + user.getUsername()
				+ ". Mật khẩu cũ không dùng được nữa; lần đăng nhập tới họ phải đổi mật khẩu."
				+ (daGui ? " Đã gửi mật khẩu tạm qua email " + user.getEmail() + "."
						: " Chưa gửi được email, hãy tự gửi mật khẩu tạm cho người dùng."));
		return "redirect:/admin/users/" + id;
	}

	/**
	 * XOÁ TÀI KHOẢN - như hệ thống thật: chỉ xoá hẳn được tài khoản CHƯA có dữ liệu nghiệp vụ
	 * (tạo nhầm, chưa từng đặt / giao đơn, chưa đánh giá, không quản lý chi nhánh).
	 * Đã có dữ liệu thì chỉ KHOÁ để giữ lịch sử đơn hàng, doanh thu, đánh giá.
	 */
	@PostMapping("/{id}/delete")
	@org.springframework.transaction.annotation.Transactional
	public String delete(@PathVariable Integer id, Authentication authentication, RedirectAttributes ra) {
		if (!isAdmin(authentication)) {
			ra.addFlashAttribute("errorMessage", "Bạn không có quyền xoá tài khoản.");
			return "redirect:/admin/users";
		}
		User user = userRepository.findById(id).orElse(null);
		if (user == null) {
			ra.addFlashAttribute("errorMessage", "Không tìm thấy tài khoản.");
			return "redirect:/admin/users";
		}
		if (isCurrentUser(authentication, user)) {
			ra.addFlashAttribute("errorMessage", "Bạn không thể xoá tài khoản đang đăng nhập.");
			return "redirect:/admin/users/" + id;
		}
		String roleName = user.getRole() == null ? "" : user.getRole().getName();
		if ("ADMIN".equalsIgnoreCase(roleName) && Boolean.TRUE.equals(user.getIsActive())
				&& userRepository.countActiveAdmins() <= 1) {
			ra.addFlashAttribute("errorMessage", "Không thể xoá Admin cuối cùng đang hoạt động.");
			return "redirect:/admin/users/" + id;
		}

		java.util.List<String> lyDo = new java.util.ArrayList<>();
		long n;
		if ((n = count("SELECT COUNT(o) FROM Order o WHERE o.user.id = :id", id)) > 0) lyDo.add(n + " đơn đã đặt");
		if ((n = count("SELECT COUNT(o) FROM Order o WHERE o.shipper.id = :id", id)) > 0) lyDo.add(n + " đơn đã giao");
		if ((n = count("SELECT COUNT(r) FROM Review r WHERE r.user.id = :id", id)
				+ count("SELECT COUNT(r) FROM OrderReview r WHERE r.user.id = :id", id)) > 0) lyDo.add(n + " đánh giá");
		if ((n = count("SELECT COUNT(r) FROM ReviewReply r WHERE r.repliedBy.id = :id", id)) > 0) lyDo.add(n + " phản hồi đánh giá");
		if ((n = count("SELECT COUNT(i) FROM OrderIssue i WHERE i.user.id = :id", id)) > 0) lyDo.add(n + " báo vấn đề");
		if (!branchRepository.findByOwnerIdOrderByIdAsc(id).isEmpty()) lyDo.add("đang quản lý chi nhánh");
		if (!lyDo.isEmpty()) {
			ra.addFlashAttribute("errorMessage", "Không xoá được " + user.getUsername() + " vì đã có dữ liệu ("
					+ String.join(", ", lyDo) + "). Hãy KHOÁ tài khoản để giữ lịch sử"
					+ (lyDo.contains("đang quản lý chi nhánh") ? ", và giao chi nhánh cho Manager khác." : "."));
			return "redirect:/admin/users/" + id;
		}

		// Dữ liệu cá nhân không có giá trị lưu lại: giỏ hàng, yêu thích, đã xem, địa chỉ, thông báo, phiên đăng nhập
		em.createQuery("DELETE FROM CartItemTopping c WHERE c.cartItem.id IN (SELECT ci.id FROM CartItem ci WHERE ci.user.id = :id)")
				.setParameter("id", id).executeUpdate();
		for (String entity : java.util.List.of("CartItem", "Favorite", "ViewedProduct", "Address", "Notification", "RefreshToken")) {
			em.createQuery("DELETE FROM " + entity + " x WHERE x.user.id = :id").setParameter("id", id).executeUpdate();
		}
		em.createQuery("DELETE FROM User u WHERE u.id = :id").setParameter("id", id).executeUpdate();
		ra.addFlashAttribute("successMessage", "Đã xoá tài khoản " + user.getUsername() + ".");
		return "redirect:/admin/users";
	}

	private long count(String jpql, Integer id) {
		return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
	}

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

		List<String> allowedRoles = List.of("ADMIN", "MANAGER", "SHIPPER", "USER");

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

		// Manager đang quản lý chi nhánh: phải giao chi nhánh cho Manager khác trước khi đổi vai trò
		if ("MANAGER".equalsIgnoreCase(currentRoleName) && !branchRepository.findByOwnerIdOrderByIdAsc(user.getId()).isEmpty()) {
			redirectAttributes.addFlashAttribute("errorMessage", "Manager " + user.getUsername()
					+ " đang quản lý chi nhánh. Hãy giao chi nhánh cho Manager khác trước khi đổi vai trò.");

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