package vn.utetra.controller.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpSession;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.dto.response.ManagerVoucherRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.BranchProduct;
import vn.utetra.entity.Order;
import vn.utetra.entity.Product;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.service.BranchService;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.ManagerCustomerService;
import vn.utetra.service.VoucherService;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	private static final String NOT_YOUR_BRANCH = "Bạn chỉ được quản lý chi nhánh của mình.";

	private static final String NO_BRANCH = "Tài khoản của bạn chưa được Admin giao chi nhánh nào.";

	private final BranchRepository branchRepository;
	private final BranchProductRepository branchProductRepository;
	private final ProductRepository productRepository;
	private final OrderRepository orderRepository;
	private final VoucherService voucherService;
	private final CurrentUserService currentUserService;
	private final ManagerCustomerService managerCustomerService;
	private final BranchService branchService;
	private final vn.utetra.repository.ToppingRepository toppingRepository;
	private final vn.utetra.service.ToppingService toppingService;
	private final vn.utetra.service.NotificationService notificationService;

	public ManagerController(BranchRepository branchRepository, BranchProductRepository branchProductRepository,
			ProductRepository productRepository, OrderRepository orderRepository, VoucherService voucherService,
			CurrentUserService currentUserService, ManagerCustomerService managerCustomerService,
			BranchService branchService, vn.utetra.repository.ToppingRepository toppingRepository,
			vn.utetra.service.ToppingService toppingService,
			vn.utetra.service.NotificationService notificationService) {

		this.branchRepository = branchRepository;
		this.branchProductRepository = branchProductRepository;
		this.productRepository = productRepository;
		this.orderRepository = orderRepository;
		this.voucherService = voucherService;
		this.currentUserService = currentUserService;
		this.managerCustomerService = managerCustomerService;
		this.branchService = branchService;
		this.toppingRepository = toppingRepository;
		this.toppingService = toppingService;
		this.notificationService = notificationService;
	}

	/** Báo Admin: chi nhánh vừa báo hết / mở bán lại 1 món hoặc topping (bấm vào xem kho của chi nhánh) */
	private void notifyAdminsStock(Branch branch, String what, String name, boolean dangBan) {
		notificationService.notifyAdmins("STOCK",
				branch.getName() + (dangBan ? " mở bán lại: " : " báo hết: ") + name,
				(dangBan ? "Đã có lại " : "Tạm hết ") + what + " " + name + " tại " + branch.getName(),
				"/admin/branches/" + branch.getId() + "/stock", null);
	}

	// =========================================================
	// CHI NHÁNH CỦA MANAGER ĐANG ĐĂNG NHẬP
	// =========================================================

	private Branch myBranch(Principal principal, HttpSession session) {
		Integer managerId = currentUserService.requireUserId(principal, session);

		List<Branch> branches = branchRepository.findByOwnerIdOrderByIdAsc(managerId);

		return branches.isEmpty() ? null : branches.get(0);
	}

	private Branch ownBranch(Integer branchId, Principal principal, HttpSession session) {

		Branch branch = myBranch(principal, session);

		return branch != null && Objects.equals(branch.getId(), branchId) ? branch : null;
	}

	private String denied(RedirectAttributes ra) {
		ra.addFlashAttribute("error", NOT_YOUR_BRANCH);
		return "redirect:/manager/dashboard";
	}

	private List<Order> ordersOf(Integer branchId) {
		return orderRepository.findByBranchIdAndStatusInOrderByCreatedAtAsc(branchId, EnumSet.allOf(OrderStatus.class));
	}

	private String toMyBranch(String suffix, Principal principal, HttpSession session, RedirectAttributes ra) {

		Branch branch = myBranch(principal, session);

		if (branch == null) {
			ra.addFlashAttribute("error", NO_BRANCH);
			return "redirect:/manager/dashboard";
		}

		return "redirect:/manager/branches/" + branch.getId() + suffix;
	}

	@GetMapping({ "/branch", "/branches" })
	public String myBranchPage(Principal principal, HttpSession session, RedirectAttributes ra) {

		return toMyBranch("", principal, session, ra);
	}

	@GetMapping({ "/products", "/menu" })
	public String myProducts(Principal principal, HttpSession session, RedirectAttributes ra) {

		return toMyBranch("/products", principal, session, ra);
	}

	@GetMapping("/vouchers")
	public String myVouchers(Principal principal, HttpSession session, RedirectAttributes ra) {

		return toMyBranch("/vouchers", principal, session, ra);
	}

	@GetMapping("/reports")
	public String myReports(Principal principal, HttpSession session, RedirectAttributes ra) {

		return toMyBranch("/reports", principal, session, ra);
	}

	@GetMapping("/customers")
	public String myCustomers(Principal principal, HttpSession session, RedirectAttributes ra) {

		return toMyBranch("/customers", principal, session, ra);
	}

	// =========================================================
	// DASHBOARD
	// Template: templates/manager/dashboard.html
	// =========================================================

	@GetMapping({ "", "/", "/dashboard" })
	public String dashboard(Principal principal, HttpSession session, Model model) {

		Branch branch = myBranch(principal, session);
		model.addAttribute("branch", branch);

		if (branch == null) {
			model.addAttribute("noBranchMessage", NO_BRANCH);
			return "manager/dashboard";
		}

		List<Order> orders = ordersOf(branch.getId());

		LocalDate today = LocalDate.now();

		LocalDateTime todayStart = today.atStartOfDay();
		LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

		LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

		LocalDateTime nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

		model.addAttribute("totalOrders", orders.size());

		model.addAttribute("pendingOrders", countOrdersByStatus(orders, OrderStatus.PENDING));

		model.addAttribute(
				"processingOrders", orders
						.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED
								|| o.getStatus() == OrderStatus.PICKED_UP || o.getStatus() == OrderStatus.DELIVERING)
						.count());

		model.addAttribute("deliveredOrders", countOrdersByStatus(orders, OrderStatus.DELIVERED));

		model.addAttribute("cancelledOrders", countOrdersByStatus(orders, OrderStatus.CANCELLED));

		model.addAttribute("todayRevenue", sumRevenue(orders, todayStart, tomorrowStart));

		model.addAttribute("monthRevenue", sumRevenue(orders, monthStart, nextMonthStart));

		model.addAttribute("unavailableProducts",
				branchProductRepository.findUnavailableProductIds(branch.getId()).size());

		model.addAttribute("activeVouchers",
				voucherService.branchView(branch.getId()).stream().filter(v -> "RUNNING".equals(v.status())).count());

		return "manager/dashboard";
	}

	// =========================================================
	// CHI TIẾT CHI NHÁNH
	// Template: templates/manager/branches/detail.html
	//
	// Manager chỉ sửa mô tả và ảnh bìa.
	// Tên, địa chỉ, giờ hoạt động và trạng thái do Admin quản lý.
	// =========================================================

	@GetMapping("/branches/{id}")
	public String branchDetail(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {

		Branch branch = ownBranch(id, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		model.addAttribute("branch", branch);
		model.addAttribute("branchProducts", branchProductRepository.findByBranchId(id));

		return "manager/branches/detail";
	}

	// =========================================================
	// CẬP NHẬT MÔ TẢ VÀ ẢNH BÌA CHI NHÁNH
	// POST /manager/branches/{id}/homepage
	// Không cần tạo homepage.html riêng.
	// =========================================================

	@PostMapping(value = "/branches/{id}/homepage", consumes = "multipart/form-data")
	public String updateBranchHomepage(@PathVariable Integer id,
			@RequestParam(name = "description", required = false) String description,
			@RequestParam(name = "imageFile", required = false) MultipartFile imageFile, Principal principal,
			HttpSession session, RedirectAttributes ra) {

		// Kiểm tra chi nhánh thuộc Manager đang đăng nhập.
		Branch branch = ownBranch(id, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		try {
			String username = principal != null ? principal.getName() : null;

			/*
			 * BranchService phải có phương thức:
			 *
			 * updateManagerHomepage( Integer id, String username, String description,
			 * MultipartFile imageFile )
			 *
			 * Service cần kiểm tra quyền sở hữu lần nữa, xử lý upload Cloudinary và lưu
			 * Branch.
			 */
			branchService.updateManagerHomepage(id, username, description, imageFile);

			ra.addFlashAttribute("success", "Đã cập nhật mô tả và ảnh bìa chi nhánh thành công.");

		} catch (ResourceNotFoundException ex) {
			ra.addFlashAttribute("error", ex.getMessage());

		} catch (RuntimeException ex) {
			ra.addFlashAttribute("error", "Không thể cập nhật chi nhánh. " + "Vui lòng kiểm tra dữ liệu và thử lại.");
		}

		return "redirect:/manager/branches/" + id;
	}

	// =========================================================
	// QUẢN LÝ SẢN PHẨM
	// Template: templates/manager/branches/products.html
	// =========================================================

	@GetMapping("/branches/{branchId}/products")
	public String branchProducts(@PathVariable Integer branchId, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		// Topping mua riêng có khu riêng bên dưới (bật / tắt topping là bật / tắt luôn món mua riêng)
		List<Product> products = productRepository.findAll().stream().filter(p -> !p.isToppingItem()).toList();

		List<Integer> unavailableProductIds = branchProductRepository.findUnavailableProductIds(branchId);
		model.addAttribute("toppings", toppingRepository.findAll().stream()
				.sorted(java.util.Comparator.comparing(vn.utetra.entity.Topping::getId)).toList());
		model.addAttribute("unavailableToppingIds", toppingService.unavailableToppingIds(branchId));

		model.addAttribute("branch", branch);
		model.addAttribute("products", products);
		model.addAttribute("branchProducts", branchProductRepository.findByBranchId(branchId));
		model.addAttribute("unavailableProductIds", unavailableProductIds);

		return "manager/branches/products";
	}

	@PostMapping("/branches/{branchId}/products/{productId}/toggle")
	public String toggleBranchProduct(@PathVariable Integer branchId, @PathVariable Integer productId,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		Optional<Product> optionalProduct = productRepository.findById(productId);

		if (optionalProduct.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy sản phẩm.");

			return "redirect:/manager/branches/" + branchId + "/products";
		}

		Product product = optionalProduct.get();

		if (branch.getStatus() != BranchStatus.APPROVED) {
			ra.addFlashAttribute("error", "Chi nhánh đang tạm ngưng, " + "không thể điều chỉnh sản phẩm.");

			return "redirect:/manager/branches/" + branchId + "/products";
		}

		if (!Boolean.TRUE.equals(product.getIsAvailable())) {
			ra.addFlashAttribute("error", "Sản phẩm đang bị khóa toàn hệ thống, " + "không thể mở bán tại chi nhánh.");

			return "redirect:/manager/branches/" + branchId + "/products";
		}

		Optional<BranchProduct> optionalBranchProduct = branchProductRepository.findByBranch_IdAndProduct_Id(branchId,
				productId);

		BranchProduct branchProduct;

		if (optionalBranchProduct.isPresent()) {
			branchProduct = optionalBranchProduct.get();

			branchProduct.setIsAvailable(!Boolean.TRUE.equals(branchProduct.getIsAvailable()));
		} else {
			branchProduct = new BranchProduct();
			branchProduct.setBranch(branch);
			branchProduct.setProduct(product);

			// Lần đầu tạo liên kết: mặc định chưa mở bán.
			branchProduct.setIsAvailable(false);
		}

		branchProductRepository.save(branchProduct);
		notifyAdminsStock(branch, "món", product.getName(), Boolean.TRUE.equals(branchProduct.getIsAvailable()));

		ra.addFlashAttribute("success", (Boolean.TRUE.equals(branchProduct.getIsAvailable()) ? "Đã mở bán lại " : "Đã báo hết ")
				+ product.getName() + " tại chi nhánh.");

		return "redirect:/manager/branches/" + branchId + "/products";
	}

	/** Báo hết / mở bán lại 1 topping tại chi nhánh (cả thêm vào ly lẫn mua riêng) */
	@PostMapping("/branches/{branchId}/toppings/{toppingId}/toggle")
	public String toggleBranchTopping(@PathVariable Integer branchId, @PathVariable Integer toppingId,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);
		if (branch == null) {
			return denied(ra);
		}
		if (branch.getStatus() != BranchStatus.APPROVED) {
			ra.addFlashAttribute("error", "Chi nhánh đang tạm ngưng, không thể điều chỉnh topping.");
			return "redirect:/manager/branches/" + branchId + "/products#toppings";
		}
		try {
			boolean dangBan = toppingService.toggleAtBranch(branch, toppingId);
			notifyAdminsStock(branch, "topping", toppingService.get(toppingId).getName(), dangBan);
			ra.addFlashAttribute("success", dangBan ? "Đã mở bán lại topping tại chi nhánh."
					: "Đã báo hết topping tại chi nhánh (khách không chọn được topping này khi đặt ở chi nhánh bạn).");
		} catch (vn.utetra.exception.BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/manager/branches/" + branchId + "/products#toppings";
	}

	// =========================================================
	// KHÁCH HÀNG CỦA CHI NHÁNH
	// =========================================================

	@GetMapping("/branches/{branchId}/customers")
	public String branchCustomers(@PathVariable Integer branchId, @RequestParam(required = false) String keyword,
			Principal principal, HttpSession session, Model model, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		Integer managerId = currentUserService.requireUserId(principal, session);

		model.addAttribute("branch", branch);
		model.addAttribute("keyword", keyword);
		model.addAttribute("customers", managerCustomerService.customers(managerId, branchId, keyword));

		return "manager/customers/index";
	}

	@GetMapping("/branches/{branchId}/customers/{userId}")
	public String branchCustomerDetail(@PathVariable Integer branchId, @PathVariable Integer userId,
			Principal principal, HttpSession session, Model model, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		try {
			Integer managerId = currentUserService.requireUserId(principal, session);

			model.addAttribute("branch", branch);
			model.addAttribute("data", managerCustomerService.customer(managerId, branchId, userId));

			return "manager/customers/detail";

		} catch (ResourceNotFoundException ex) {
			ra.addFlashAttribute("error", ex.getMessage());

			return "redirect:/manager/branches/" + branchId + "/customers";
		}
	}

	// =========================================================
	// ĐƠN HÀNG - SỬ DỤNG ManagerOrderController
	// =========================================================

	@GetMapping("/branches/{branchId}/orders")
	public String branchOrders(@PathVariable Integer branchId) {

		return "redirect:/manager/orders/history";
	}

	@GetMapping("/branches/{branchId}/orders/{orderId}")
	public String branchOrderDetail(@PathVariable Integer branchId, @PathVariable Integer orderId) {

		return "redirect:/manager/orders/" + orderId;
	}

	// =========================================================
	// ĐẾM ĐƠN THEO TRẠNG THÁI
	// =========================================================

	private long countOrdersByStatus(List<Order> orders, OrderStatus status) {

		return orders.stream().filter(o -> o.getStatus() == status).count();
	}

	// =========================================================
	// VOUCHER CỦA CHI NHÁNH
	// Template: templates/manager/vouchers/index.html
	// =========================================================

	@GetMapping("/branches/{branchId}/vouchers")
	public String branchVouchers(@PathVariable Integer branchId, Model model, Principal principal, HttpSession session,
			RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		List<ManagerVoucherRes> vouchers = voucherService.branchView(branchId);

		model.addAttribute("branch", branch);
		model.addAttribute("vouchers", vouchers);

		model.addAttribute("runningCount", vouchers.stream().filter(v -> "RUNNING".equals(v.status())).count());

		model.addAttribute("usedTotal", vouchers.stream().mapToLong(ManagerVoucherRes::usedAtBranch).sum());

		model.addAttribute("discountTotal",
				vouchers.stream().map(ManagerVoucherRes::discountAtBranch).reduce(BigDecimal.ZERO, BigDecimal::add));

		return "manager/vouchers/index";
	}

	// =========================================================
	// BÁO CÁO DOANH THU
	// Template: templates/manager/reports/index.html
	// =========================================================

	@GetMapping("/branches/{branchId}/reports")
	public String branchReports(@PathVariable Integer branchId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Model model, Principal principal, HttpSession session, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		LocalDate today = LocalDate.now();

		if (startDate == null) {
			startDate = today.withDayOfMonth(1);
		}

		if (endDate == null) {
			endDate = today;
		}

		if (startDate.isAfter(endDate)) {
			LocalDate temp = startDate;
			startDate = endDate;
			endDate = temp;
		}

		LocalDateTime rangeStart = startDate.atStartOfDay();
		LocalDateTime rangeEnd = endDate.plusDays(1).atStartOfDay();

		List<Order> branchOrders = ordersOf(branchId);

		List<Order> deliveredOrders = branchOrders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED)
				.filter(order -> order.getDeliveredAt() != null)
				.filter(order -> !order.getDeliveredAt().isBefore(rangeStart))
				.filter(order -> order.getDeliveredAt().isBefore(rangeEnd))
				.sorted(Comparator.comparing(Order::getDeliveredAt)).collect(Collectors.toList());

		BigDecimal selectedRevenue = BigDecimal.ZERO;

		for (Order order : deliveredOrders) {
			if (order.getSalesAmount() != null) {
				selectedRevenue = selectedRevenue.add(order.getSalesAmount());
			}
		}

		LocalDateTime todayStart = today.atStartOfDay();
		LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

		LocalDate monthStartDate = today.withDayOfMonth(1);

		LocalDateTime monthStart = monthStartDate.atStartOfDay();
		LocalDateTime nextMonthStart = monthStartDate.plusMonths(1).atStartOfDay();

		BigDecimal todayRevenue = sumRevenue(branchOrders, todayStart, tomorrowStart);

		BigDecimal monthRevenue = sumRevenue(branchOrders, monthStart, nextMonthStart);

		long todayOrders = countDeliveredOrders(branchOrders, todayStart, tomorrowStart);

		long monthOrders = countDeliveredOrders(branchOrders, monthStart, nextMonthStart);

		Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();

		for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {

			revenueByDay.put(date, BigDecimal.ZERO);
		}

		for (Order order : deliveredOrders) {
			LocalDate deliveredDate = order.getDeliveredAt().toLocalDate();

			BigDecimal amount = order.getSalesAmount();

			if (amount != null) {
				revenueByDay.merge(deliveredDate, amount, BigDecimal::add);
			}
		}

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");

		List<String> chartLabels = revenueByDay.keySet().stream().map(date -> date.format(formatter))
				.collect(Collectors.toList());

		List<BigDecimal> chartValues = new ArrayList<>(revenueByDay.values());

		BigDecimal maxRevenue = chartValues.stream().filter(Objects::nonNull).max(BigDecimal::compareTo)
				.orElse(BigDecimal.ZERO);

		List<Integer> chartPercentages = new ArrayList<>();

		for (BigDecimal revenue : chartValues) {
			int percentage = 0;

			if (revenue != null && maxRevenue.compareTo(BigDecimal.ZERO) > 0) {

				percentage = revenue.multiply(BigDecimal.valueOf(100)).divide(maxRevenue, 0, RoundingMode.HALF_UP)
						.intValue();
			}

			chartPercentages.add(percentage);
		}

		model.addAttribute("branch", branch);
		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);

		model.addAttribute("todayRevenue", todayRevenue);
		model.addAttribute("monthRevenue", monthRevenue);
		model.addAttribute("selectedRevenue", selectedRevenue);

		model.addAttribute("selectedAverage", deliveredOrders.isEmpty() ? BigDecimal.ZERO
				: selectedRevenue.divide(BigDecimal.valueOf(deliveredOrders.size()), 0, RoundingMode.HALF_UP));

		model.addAttribute("todayOrders", todayOrders);
		model.addAttribute("monthOrders", monthOrders);
		model.addAttribute("selectedOrders", deliveredOrders.size());

		model.addAttribute("allBranchOrders", branchOrders.size());

		model.addAttribute("pendingOrders", countOrdersByStatus(branchOrders, OrderStatus.PENDING));

		model.addAttribute("cancelledOrders", countOrdersByStatus(branchOrders, OrderStatus.CANCELLED));

		model.addAttribute("chartLabels", chartLabels);
		model.addAttribute("chartValues", chartValues);
		model.addAttribute("chartPercentages", chartPercentages);

		return "manager/reports/index";
	}

	// =========================================================
	// TÍNH DOANH THU ĐƠN ĐÃ GIAO TRONG KHOẢNG THỜI GIAN
	// =========================================================

	private BigDecimal sumRevenue(List<Order> orders, LocalDateTime start, LocalDateTime end) {

		return orders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED)
				.filter(order -> order.getDeliveredAt() != null)
				.filter(order -> !order.getDeliveredAt().isBefore(start))
				.filter(order -> order.getDeliveredAt().isBefore(end)).map(Order::getSalesAmount)
				.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// =========================================================
	// ĐẾM ĐƠN ĐÃ GIAO TRONG KHOẢNG THỜI GIAN
	// =========================================================

	private long countDeliveredOrders(List<Order> orders, LocalDateTime start, LocalDateTime end) {

		return orders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED)
				.filter(order -> order.getDeliveredAt() != null)
				.filter(order -> !order.getDeliveredAt().isBefore(start))
				.filter(order -> order.getDeliveredAt().isBefore(end)).count();
	}
}
