package vn.utetra.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Role;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.RoleRepository;
import vn.utetra.repository.UserRepository;

@Service
@Transactional
public class AdminUserService {

	public static final int TEMP_PASSWORD_HOURS = 72;
	public static final List<String> STAFF_ROLES = List.of("ADMIN", "MANAGER", "SHIPPER");
	private static final String LOGIN_URL = "http://localhost:8080/auth/login";

	private static final List<OrderStatus> OPEN_SHIPPER_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
			OrderStatus.READY, OrderStatus.PICKED_UP, OrderStatus.DELIVERING);
	private static final List<OrderStatus> ON_THE_ROAD = List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING);

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final BranchRepository branchRepository;
	private final OrderRepository orderRepository;
	private final PasswordEncoder passwordEncoder;
	private final ShippingFeeService shippingFeeService;
	private final NotificationService notificationService;
	private final EmailService emailService;

	@PersistenceContext
	private EntityManager em;

	public AdminUserService(UserRepository userRepository, RoleRepository roleRepository,
			BranchRepository branchRepository, OrderRepository orderRepository, PasswordEncoder passwordEncoder,
			ShippingFeeService shippingFeeService, NotificationService notificationService, EmailService emailService) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.branchRepository = branchRepository;
		this.orderRepository = orderRepository;
		this.passwordEncoder = passwordEncoder;
		this.shippingFeeService = shippingFeeService;
		this.notificationService = notificationService;
		this.emailService = emailService;
	}

	@Transactional(readOnly = true)
	public List<vn.utetra.entity.Role> roles() {
		return roleRepository.findAll();
	}

	public record SearchResult(List<User> users, Map<Integer, String> branchLabels, String tab, String role,
			String status, String branch, long staffCount, long customerCount, long totalUsers, long activeUsers) {
	}

	@Transactional(readOnly = true)
	public SearchResult search(String keyword, String tab, String role, String status, String branch) {
		String currentTab = "USER".equalsIgnoreCase(tab) ? "USER" : "STAFF";
		String kw = keyword == null ? "" : keyword.trim();
		String st = status == null || status.isBlank() ? "ALL" : status.toUpperCase(Locale.ROOT);
		String br = branch == null ? "" : branch.trim();
		String selRole = "USER".equals(currentTab) || role == null || role.isBlank() ? "ALL"
				: role.toUpperCase(Locale.ROOT);

		List<User> all = kw.isEmpty() ? userRepository.findAllForAdmin() : userRepository.searchForAdmin(kw);
		Predicate<User> isCustomer = u -> u.getRole() != null && "USER".equals(u.getRole().getName());
		long staffCount = all.stream().filter(isCustomer.negate()).count();
		long customerCount = all.stream().filter(isCustomer).count();
		List<User> users = all.stream().filter("USER".equals(currentTab) ? isCustomer : isCustomer.negate())
				.collect(Collectors.toList());

		Map<Integer, List<Branch>> branchesOf = new HashMap<>();
		for (Branch b : branchRepository.findAll()) {
			if (b.getOwner() != null) branchesOf.computeIfAbsent(b.getOwner().getId(), k -> new ArrayList<>()).add(b);
		}
		for (User u : users) {
			if (u.getBranch() != null && hasRole(u, "SHIPPER")) {
				branchesOf.computeIfAbsent(u.getId(), k -> new ArrayList<>()).add(u.getBranch());
			}
		}

		if ("STAFF".equals(currentTab) && !br.isEmpty()) {
			if ("NONE".equals(br)) {
				users = users.stream().filter(u -> !hasRole(u, "ADMIN")
						&& branchesOf.getOrDefault(u.getId(), List.of()).isEmpty()).collect(Collectors.toList());
			} else {
				try {
					Integer bid = Integer.valueOf(br);
					users = users.stream().filter(u -> branchesOf.getOrDefault(u.getId(), List.of()).stream()
							.anyMatch(b -> b.getId().equals(bid))).collect(Collectors.toList());
				} catch (NumberFormatException e) {
					br = "";
				}
			}
		}
		if (!"ALL".equals(selRole)) {
			users = users.stream().filter(u -> hasRole(u, selRole)).collect(Collectors.toList());
		}
		users = switch (st) {
			case "ACTIVE" -> users.stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).collect(Collectors.toList());
			case "INACTIVE" -> users.stream().filter(u -> !Boolean.TRUE.equals(u.getIsActive())).collect(Collectors.toList());
			case "VERIFIED" -> users.stream().filter(u -> Boolean.TRUE.equals(u.getIsVerified())).collect(Collectors.toList());
			case "UNVERIFIED" -> users.stream().filter(u -> !Boolean.TRUE.equals(u.getIsVerified())).collect(Collectors.toList());
			default -> users;
		};

		Map<Integer, String> branchLabels = new HashMap<>();
		for (User u : users) {
			if (hasRole(u, "ADMIN")) {
				branchLabels.put(u.getId(), "Toàn chuỗi");
			} else {
				List<Branch> bs = branchesOf.getOrDefault(u.getId(), List.of());
				branchLabels.put(u.getId(), bs.isEmpty() ? null : bs.stream().map(Branch::getName).collect(Collectors.joining(", ")));
			}
		}

		List<User> everyone = userRepository.findAll();
		long active = everyone.stream().filter(u -> Boolean.TRUE.equals(u.getIsActive())).count();
		return new SearchResult(users, branchLabels, currentTab, selRole, st, br, staffCount, customerCount,
				everyone.size(), active);
	}

	@Transactional(readOnly = true)
	public List<Branch> branches() {
		return branchRepository.findAll().stream().sorted(Comparator.comparing(Branch::getId)).toList();
	}

	@Transactional(readOnly = true)
	public Map<Integer, Long> shipperCounts() {
		Map<Integer, Long> m = new HashMap<>();
		for (User s : userRepository.findByRole_Name("SHIPPER")) {
			if (s.getBranch() != null) m.merge(s.getBranch().getId(), 1L, Long::sum);
		}
		return m;
	}

	@Transactional(readOnly = true)
	public User get(Integer id) {
		return userRepository.findById(id).orElseThrow(() -> new BusinessException("Không tìm thấy tài khoản."));
	}

	@Transactional(readOnly = true)
	public List<Branch> managedBranches(Integer userId) {
		return branchRepository.findByOwnerIdOrderByIdAsc(userId);
	}

	public static boolean tempExpired(User u) {
		return u.getTempPasswordExpiresAt() != null && u.getTempPasswordExpiresAt().isBefore(LocalDateTime.now());
	}

	public record Created(User user, String message) {
	}

	public Created createStaff(String username, String fullName, String email, String phone, String role,
			Integer branchId, String password, String confirmPassword) {
		String u = username == null ? "" : username.trim();
		String name = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
		String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
		String tel = phone == null ? "" : phone.trim();
		String r = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);

		if (!u.matches("[A-Za-z0-9_.]{3,50}"))
			throw new BusinessException("Tên đăng nhập 3–50 ký tự, chỉ gồm chữ không dấu, số, dấu _ hoặc dấu chấm.");
		if (userRepository.existsByUsername(u)) throw new BusinessException("Tên đăng nhập \"" + u + "\" đã có người dùng.");
		if (name.isEmpty() || name.length() > 100) throw new BusinessException("Vui lòng nhập họ tên (tối đa 100 ký tự).");
		if (!mail.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) throw new BusinessException("Email không hợp lệ.");
		if (userRepository.existsByEmail(mail)) throw new BusinessException("Email " + mail + " đã được dùng cho tài khoản khác.");
		if (!tel.isEmpty() && !tel.matches("0\\d{9}")) throw new BusinessException("Số điện thoại gồm 10 số, bắt đầu bằng 0.");
		if (!STAFF_ROLES.contains(r))
			throw new BusinessException("Chỉ tạo được tài khoản nhân viên (Manager, Shipper, Admin). Khách hàng tự đăng ký trên web.");
		Branch branch = branchId == null ? null : branchRepository.findById(branchId).orElse(null);
		if ("SHIPPER".equals(r) && branch == null)
			throw new BusinessException("Chọn chi nhánh làm việc cho shipper (shipper chỉ nhận đơn giao của chi nhánh đó).");
		if (password == null || password.length() < 6) throw new BusinessException("Mật khẩu tối thiểu 6 ký tự.");
		if (!password.equals(confirmPassword)) throw new BusinessException("Mật khẩu nhập lại không khớp.");
		Role targetRole = roleRepository.findByName(r)
				.orElseThrow(() -> new BusinessException("Vai trò " + r + " chưa có trong cơ sở dữ liệu."));

		User user = new User();
		user.setUsername(u);
		user.setFullName(name);
		user.setEmail(mail);
		user.setPhone(tel.isEmpty() ? null : tel);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(targetRole);
		user.setIsVerified(true);
		user.setIsActive(true);
		user.setMustChangePassword(true);
		user.setTempPasswordExpiresAt(LocalDateTime.now().plusHours(TEMP_PASSWORD_HOURS));
		if ("SHIPPER".equals(r)) {
			user.setCarrier(shippingFeeService.shipper());
			user.setBranch(branch);
		}
		userRepository.save(user);

		String next;
		if ("MANAGER".equals(r) && branch != null) {
			String cu = branch.getOwner() == null ? null : branch.getOwner().getFullName();
			branch.setOwner(user);
			branchRepository.save(branch);
			next = " Đã giao chi nhánh " + branch.getName() + (cu == null ? "" : " (thay cho " + cu + ")") + ".";
		} else {
			next = switch (r) {
				case "MANAGER" -> " Chưa giao chi nhánh: vào Quản lý → Chi nhánh → Sửa để giao chi nhánh cho Manager này.";
				case "SHIPPER" -> " Shipper làm ở chi nhánh " + branch.getName()
						+ ", đăng nhập là thấy đơn chờ giao của chi nhánh này.";
				default -> "";
			};
		}
		boolean daGui = emailTempPassword(user, password, true);
		return new Created(user, "Đã tạo tài khoản " + r + " \"" + u + "\" (" + name + ")." + next
				+ (daGui ? " Đã gửi email tên đăng nhập và mật khẩu tạm tới " + mail + "."
						: " Chưa gửi được email, hãy tự gửi mật khẩu tạm cho người dùng.")
				+ " Lần đăng nhập đầu họ phải đổi mật khẩu.");
	}

	public String resetPassword(Integer id, String newPassword, String confirmPassword, String currentUsername) {
		User user = get(id);
		if (isSelf(user, currentUsername)) throw new BusinessException("Đổi mật khẩu của chính bạn ở trang Tài khoản.");
		if (newPassword == null || newPassword.length() < 6) throw new BusinessException("Mật khẩu mới tối thiểu 6 ký tự.");
		if (!newPassword.equals(confirmPassword)) throw new BusinessException("Mật khẩu nhập lại không khớp.");
		user.setPassword(passwordEncoder.encode(newPassword));
		user.setMustChangePassword(true);
		user.setTempPasswordExpiresAt(LocalDateTime.now().plusHours(TEMP_PASSWORD_HOURS));
		userRepository.save(user);
		notificationService.create(user, null, "USER", "Mật khẩu của bạn đã được Admin đặt lại",
				"Đăng nhập bằng mật khẩu tạm (đã gửi qua email) rồi đặt mật khẩu mới.", null);
		boolean daGui = emailTempPassword(user, newPassword, false);
		return "Đã đặt lại mật khẩu tạm cho " + user.getUsername()
				+ ". Mật khẩu cũ không dùng được nữa; lần đăng nhập tới họ phải đổi mật khẩu."
				+ (daGui ? " Đã gửi mật khẩu tạm qua email " + user.getEmail() + "."
						: " Chưa gửi được email, hãy tự gửi mật khẩu tạm cho người dùng.");
	}

	public String delete(Integer id, String currentUsername) {
		User user = get(id);
		if (isSelf(user, currentUsername)) throw new BusinessException("Bạn không thể xoá tài khoản đang đăng nhập.");
		if (hasRole(user, "ADMIN") && Boolean.TRUE.equals(user.getIsActive()) && userRepository.countActiveAdmins() <= 1)
			throw new BusinessException("Không thể xoá Admin cuối cùng đang hoạt động.");

		List<String> lyDo = new ArrayList<>();
		long n;
		if ((n = count("SELECT COUNT(o) FROM Order o WHERE o.user.id = :id", id)) > 0) lyDo.add(n + " đơn đã đặt");
		if ((n = count("SELECT COUNT(o) FROM Order o WHERE o.shipper.id = :id", id)) > 0) lyDo.add(n + " đơn đã giao");
		if ((n = count("SELECT COUNT(o) FROM Order o WHERE o.manager.id = :id", id)) > 0) lyDo.add(n + " đơn đã xử lý");
		if ((n = count("SELECT COUNT(r) FROM Review r WHERE r.user.id = :id", id)
				+ count("SELECT COUNT(r) FROM OrderReview r WHERE r.user.id = :id", id)) > 0) lyDo.add(n + " đánh giá");
		if ((n = count("SELECT COUNT(r) FROM ReviewReply r WHERE r.repliedBy.id = :id", id)) > 0) lyDo.add(n + " phản hồi đánh giá");
		if ((n = count("SELECT COUNT(i) FROM OrderIssue i WHERE i.user.id = :id", id)) > 0) lyDo.add(n + " báo vấn đề");
		if (!branchRepository.findByOwnerIdOrderByIdAsc(id).isEmpty()) lyDo.add("đang quản lý chi nhánh");
		if (!lyDo.isEmpty()) {
			throw new BusinessException("Không xoá được " + user.getUsername() + " vì đã có dữ liệu ("
					+ String.join(", ", lyDo) + "). Hãy KHOÁ tài khoản để giữ lịch sử"
					+ (lyDo.contains("đang quản lý chi nhánh") ? ", và giao chi nhánh cho Manager khác." : "."));
		}

		em.createQuery("DELETE FROM CartItemTopping c WHERE c.cartItem.id IN (SELECT ci.id FROM CartItem ci WHERE ci.user.id = :id)")
				.setParameter("id", id).executeUpdate();
		for (String entity : List.of("CartItem", "Favorite", "ViewedProduct", "Address", "Notification", "RefreshToken")) {
			em.createQuery("DELETE FROM " + entity + " x WHERE x.user.id = :id").setParameter("id", id).executeUpdate();
		}
		String username = user.getUsername();
		em.createQuery("DELETE FROM User u WHERE u.id = :id").setParameter("id", id).executeUpdate();
		return "Đã xoá tài khoản " + username + ".";
	}

	private long count(String jpql, Integer id) {
		return em.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
	}

	public String changeRole(Integer id, String roleName, String currentUsername) {
		User user = get(id);
		String target = roleName == null ? "" : roleName.trim().toUpperCase(Locale.ROOT);
		if (!List.of("ADMIN", "MANAGER", "SHIPPER", "USER").contains(target))
			throw new BusinessException("Vai trò được chọn không hợp lệ.");
		Role targetRole = roleRepository.findByName(target)
				.orElseThrow(() -> new BusinessException("Vai trò " + target + " chưa tồn tại trong cơ sở dữ liệu."));
		String current = user.getRole() == null ? "" : user.getRole().getName();
		if (target.equalsIgnoreCase(current)) throw new BusinessException("Tài khoản đã có vai trò này.");
		if (isSelf(user, currentUsername))
			throw new BusinessException("Bạn không thể tự thay đổi vai trò tài khoản đang đăng nhập.");
		if ("ADMIN".equalsIgnoreCase(current) && Boolean.TRUE.equals(user.getIsActive()) && !"ADMIN".equals(target)
				&& userRepository.countActiveAdmins() <= 1)
			throw new BusinessException("Không thể hạ quyền Admin cuối cùng đang hoạt động.");
		if ("MANAGER".equalsIgnoreCase(current) && !branchRepository.findByOwnerIdOrderByIdAsc(user.getId()).isEmpty())
			throw new BusinessException("Manager " + user.getUsername()
					+ " đang quản lý chi nhánh. Hãy giao chi nhánh cho Manager khác trước khi đổi vai trò.");
		if ("SHIPPER".equalsIgnoreCase(current)) {
			List<Integer> open = orderRepository.findOpenOrderIdsOfShipper(user.getId(), OPEN_SHIPPER_STATUSES);
			if (!open.isEmpty())
				throw new BusinessException("Shipper " + user.getUsername() + " đang được giao đơn chưa xong ("
						+ joinIds(open) + "). Chờ giao xong rồi đổi vai trò.");
		}

		user.setRole(targetRole);
		String them = "";
		if ("SHIPPER".equals(target)) {
			if (user.getCarrier() == null) user.setCarrier(shippingFeeService.shipper());
			if (user.getBranch() == null)
				them = " Vào trang chi tiết tài khoản để chọn chi nhánh làm việc cho shipper này (chưa chọn thì chưa nhận được đơn).";
		} else {
			user.setBranch(null);
			user.setCarrier(null);
		}
		userRepository.save(user);
		return "Đã đổi vai trò tài khoản " + user.getUsername() + " thành " + target + "." + them;
	}

	public String changeShipperBranch(Integer id, Integer branchId) {
		User user = get(id);
		if (!hasRole(user, "SHIPPER"))
			throw new BusinessException("Chỉ shipper mới phân công chi nhánh ở đây. Manager được giao chi nhánh ở trang Chi nhánh.");
		Branch b = branchId == null ? null : branchRepository.findById(branchId).orElse(null);
		if (b == null) throw new BusinessException("Chi nhánh không tồn tại.");
		Branch cu = user.getBranch();
		if (cu != null && cu.getId().equals(b.getId()))
			throw new BusinessException("Shipper đang làm ở chi nhánh " + b.getName() + " rồi.");
		List<Integer> open = orderRepository.findOpenOrderIdsOfShipper(user.getId(), OPEN_SHIPPER_STATUSES);
		if (!open.isEmpty())
			throw new BusinessException("Shipper " + user.getUsername() + " đang được giao đơn chưa xong ("
					+ joinIds(open) + "). Chờ giao xong rồi chuyển chi nhánh.");
		user.setBranch(b);
		userRepository.save(user);
		notificationService.create(user, null, "USER", "Bạn được phân công làm ở chi nhánh " + b.getName(),
				"Từ giờ bạn nhận đơn giao của chi nhánh " + b.getName() + " (" + b.getAddress() + ").", "/shipper/orders");
		return "Đã " + (cu == null ? "phân công" : "chuyển") + " shipper " + user.getUsername()
				+ (cu == null ? "" : " từ " + cu.getName()) + " sang chi nhánh " + b.getName() + ".";
	}

	public String toggleActive(Integer id, String currentUsername) {
		User user = get(id);
		if (isSelf(user, currentUsername))
			throw new BusinessException("Bạn không thể khóa hoặc mở khóa tài khoản đang đăng nhập.");
		boolean active = Boolean.TRUE.equals(user.getIsActive());
		if (active && hasRole(user, "ADMIN") && userRepository.countActiveAdmins() <= 1)
			throw new BusinessException("Không thể khóa Admin cuối cùng đang hoạt động.");

		String traDon = "";
		if (active && hasRole(user, "SHIPPER")) {
			List<Integer> dangGiao = orderRepository.findOpenOrderIdsOfShipper(user.getId(), ON_THE_ROAD);
			if (!dangGiao.isEmpty())
				throw new BusinessException("Shipper " + user.getUsername() + " đang giao đơn " + joinIds(dangGiao)
						+ ". Chờ giao xong rồi khoá.");
			List<Integer> choLay = orderRepository.findOpenOrderIdsOfShipper(user.getId(), OPEN_SHIPPER_STATUSES);
			for (Integer oid : choLay) {
				orderRepository.findById(oid).ifPresent(o -> {
					o.setShipper(null);
					orderRepository.save(o);
					if (o.getBranch() != null && o.getBranch().getOwner() != null) {
						notificationService.create(o.getBranch().getOwner(), o, "ORDER",
								"Đơn #" + o.getId() + " cần shipper khác",
								"Tài khoản shipper " + user.getFullName() + " đã bị khoá, đơn trả lại cho shipper khác nhận.",
								"/manager/orders/" + o.getId());
					}
				});
			}
			if (!choLay.isEmpty()) traDon = " Đơn " + joinIds(choLay) + " đã trả lại cho shipper khác của chi nhánh nhận.";
		}

		user.setIsActive(!active);
		userRepository.save(user);
		return active ? "Đã khóa tài khoản " + user.getUsername() + "." + traDon
				: "Đã mở khóa tài khoản " + user.getUsername() + ".";
	}

	private static boolean hasRole(User u, String role) {
		return u.getRole() != null && role.equalsIgnoreCase(u.getRole().getName());
	}

	private static boolean isSelf(User u, String currentUsername) {
		return currentUsername != null && u.getUsername() != null && currentUsername.equalsIgnoreCase(u.getUsername());
	}

	private static String joinIds(List<Integer> ids) {
		return ids.stream().map(i -> "#" + i).collect(Collectors.joining(", "));
	}

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
						+ user.getTempPasswordExpiresAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
						+ "). Lần đăng nhập đầu, hệ thống sẽ yêu cầu bạn đổi sang mật khẩu của riêng mình."
						+ "\nNếu bạn không yêu cầu việc này, hãy liên hệ quản trị viên.\n\nUTETra");
	}
}
