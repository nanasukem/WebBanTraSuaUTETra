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
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.dto.request.VoucherReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.BranchProduct;
import vn.utetra.entity.Order;
import vn.utetra.entity.Product;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.DiscountType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.VoucherApplyTo;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.ManagerCustomerService;
import vn.utetra.service.VoucherService;

/**
 * Khu vực MANAGER (SecurityConfiguration: /manager/** chỉ ROLE_MANAGER).
 *
 * Chuỗi tự mở chi nhánh: Admin mở chi nhánh và chỉ định 1 Manager quản lý.
 * Manager CHỈ quản lý chi nhánh của mình: mọi đường dẫn /manager/branches/{branchId}/...
 * đều kiểm tra branchId phải là chi nhánh của Manager đang đăng nhập.
 * Duyệt/tạm ngưng chi nhánh, đổi chiết khấu là việc của Admin (/admin/branches).
 *
 * - /manager/dashboard                     : tổng quan chi nhánh của tôi
 * - /manager/branches/{id}                 : thông tin chi nhánh
 * - /manager/branches/{id}/products        : bật/tắt món tại chi nhánh
 * - /manager/branches/{id}/vouchers        : voucher riêng của chi nhánh
 * - /manager/branches/{id}/reports         : doanh thu chi nhánh
 * - /manager/branches/{id}/customers       : (Chức năng mở rộng) khách hàng của chi nhánh, chỉ xem
 * - Đơn hàng: ManagerOrderController (/manager/orders, /manager/orders/history, /manager/orders/{id})
 */
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

	public ManagerController(BranchRepository branchRepository, BranchProductRepository branchProductRepository,
			ProductRepository productRepository, OrderRepository orderRepository, VoucherService voucherService,
			CurrentUserService currentUserService, ManagerCustomerService managerCustomerService) {
		this.branchRepository = branchRepository;
		this.branchProductRepository = branchProductRepository;
		this.productRepository = productRepository;
		this.orderRepository = orderRepository;
		this.voucherService = voucherService;
		this.currentUserService = currentUserService;
		this.managerCustomerService = managerCustomerService;
	}

	// =========================================================
	// CHI NHÁNH CỦA MANAGER ĐANG ĐĂNG NHẬP
	// =========================================================

	/** Chi nhánh Admin giao cho Manager đang đăng nhập (null nếu chưa được giao) */
	private Branch myBranch(Principal principal, HttpSession session) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		List<Branch> branches = branchRepository.findByOwnerIdOrderByIdAsc(managerId);
		return branches.isEmpty() ? null : branches.get(0);
	}

	/** Chỉ trả về chi nhánh khi branchId đúng là chi nhánh của mình, ngược lại null */
	private Branch ownBranch(Integer branchId, Principal principal, HttpSession session) {
		Branch branch = myBranch(principal, session);
		return branch != null && branch.getId().equals(branchId) ? branch : null;
	}

	/** Không phải chi nhánh của mình -> về Tổng quan kèm thông báo */
	private String denied(RedirectAttributes ra) {
		ra.addFlashAttribute("error", NOT_YOUR_BRANCH);
		return "redirect:/manager/dashboard";
	}

	/** Tất cả đơn của 1 chi nhánh */
	private List<Order> ordersOf(Integer branchId) {
		return orderRepository.findByBranchIdAndStatusInOrderByCreatedAtAsc(branchId, EnumSet.allOf(OrderStatus.class));
	}

	/** Đường dẫn ngắn (menu, header): tự chuyển sang chi nhánh của mình */
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
	// DASHBOARD: TỔNG QUAN CHI NHÁNH CỦA TÔI
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
		model.addAttribute("processingOrders", orders.stream()
				.filter(o -> o.getStatus() == OrderStatus.CONFIRMED || o.getStatus() == OrderStatus.PICKED_UP
						|| o.getStatus() == OrderStatus.DELIVERING)
				.count());
		model.addAttribute("deliveredOrders", countOrdersByStatus(orders, OrderStatus.DELIVERED));
		model.addAttribute("cancelledOrders", countOrdersByStatus(orders, OrderStatus.CANCELLED));
		model.addAttribute("todayRevenue", sumRevenue(orders, todayStart, tomorrowStart));
		model.addAttribute("monthRevenue", sumRevenue(orders, monthStart, nextMonthStart));
		model.addAttribute("unavailableProducts", branchProductRepository.findUnavailableProductIds(branch.getId()).size());
		model.addAttribute("activeVouchers", voucherService.findByBranch(branch.getId()).stream()
				.filter(v -> Boolean.TRUE.equals(v.getIsActive())).count());

		return "manager/dashboard";
	}

	// =========================================================
	// THÔNG TIN CHI NHÁNH (chỉ xem; sửa thông tin/trạng thái/chiết khấu do Admin làm)
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
	// QUẢN LÝ SẢN PHẨM THEO CHI NHÁNH
	// =========================================================

	@GetMapping("/branches/{branchId}/products")
	public String branchProducts(@PathVariable Integer branchId, Principal principal, HttpSession session,
			Model model, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);
		if (branch == null) {
			return denied(ra);
		}

		List<Product> products = productRepository.findAll();

		List<Integer> unavailableProductIds = branchProductRepository.findUnavailableProductIds(branchId);

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
			ra.addFlashAttribute("error", "Chi nhánh đang tạm ngưng, không thể điều chỉnh sản phẩm.");

			return "redirect:/manager/branches/" + branchId + "/products";
		}

		if (!Boolean.TRUE.equals(product.getIsAvailable())) {
			ra.addFlashAttribute("error", "Sản phẩm đang bị khóa toàn hệ thống, không thể mở bán tại chi nhánh.");

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
			branchProduct.setIsAvailable(false);
		}

		branchProductRepository.save(branchProduct);

		ra.addFlashAttribute("success", "Đã cập nhật trạng thái sản phẩm tại chi nhánh.");

		return "redirect:/manager/branches/" + branchId + "/products";
	}

	// =========================================================
	// (CHỨC NĂNG MỞ RỘNG) KHÁCH HÀNG CỦA CHI NHÁNH: chỉ xem, không sửa / khoá tài khoản khách
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
	// ĐƠN HÀNG: dùng chung màn hình của ManagerOrderController
	// (đổi trạng thái qua OrderFlowService: hoàn voucher, thông báo realtime, cộng lượt bán...)
	// =========================================================

	@GetMapping("/branches/{branchId}/orders")
	public String branchOrders(@PathVariable Integer branchId) {
		return "redirect:/manager/orders/history";
	}

	@GetMapping("/branches/{branchId}/orders/{orderId}")
	public String branchOrderDetail(@PathVariable Integer branchId, @PathVariable Integer orderId) {
		return "redirect:/manager/orders/" + orderId;
	}

	private long countOrdersByStatus(List<Order> orders, OrderStatus status) {

		return orders.stream().filter(o -> o.getStatus() == status).count();
	}

	// =========================================================
	// VOUCHER RIÊNG CỦA CHI NHÁNH
	// =========================================================
	@GetMapping("/branches/{branchId}/vouchers")
	public String branchVouchers(@PathVariable Integer branchId, Model model, Principal principal, HttpSession session, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = Optional.ofNullable(ownBranch(branchId, principal, session));

		if (optionalBranch.isEmpty()) {
			return denied(ra);
		}

		model.addAttribute("branch", optionalBranch.get());
		model.addAttribute("vouchers", voucherService.findByBranch(branchId));

		return "manager/vouchers/index";
	}

	@GetMapping("/branches/{branchId}/vouchers/new")
	public String createVoucherForm(@PathVariable Integer branchId, Model model, Principal principal, HttpSession session, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = Optional.ofNullable(ownBranch(branchId, principal, session));

		if (optionalBranch.isEmpty()) {
			return denied(ra);
		}

		VoucherReq req = new VoucherReq();
		req.setBranchId(branchId);
		req.setMinOrderValue(BigDecimal.ZERO);
		req.setQuantity(100);

		LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

		req.setStartDate(now);
		req.setEndDate(now.plusDays(7));

		model.addAttribute("branch", optionalBranch.get());
		model.addAttribute("voucherReq", req);
		model.addAttribute("voucherId", null);

		addVoucherFormOptions(model);

		return "manager/vouchers/form";
	}

	@PostMapping("/branches/{branchId}/vouchers")
	public String createVoucher(@PathVariable Integer branchId, @Valid @ModelAttribute("voucherReq") VoucherReq req,
			BindingResult result, Model model, Principal principal, HttpSession session, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = Optional.ofNullable(ownBranch(branchId, principal, session));

		if (optionalBranch.isEmpty()) {
			return denied(ra);
		}

		if (result.hasErrors()) {
			model.addAttribute("branch", optionalBranch.get());
			model.addAttribute("voucherId", null);
			addVoucherFormOptions(model);
			return "manager/vouchers/form";
		}

		try {
			// Luôn gán chi nhánh từ URL, không tin branchId gửi từ form.
			req.setBranchId(branchId);

			voucherService.create(req);

			ra.addFlashAttribute("success", "Đã tạo voucher cho chi nhánh thành công.");

			return "redirect:/manager/branches/" + branchId + "/vouchers";

		} catch (BusinessException ex) {
			model.addAttribute("branch", optionalBranch.get());
			model.addAttribute("voucherId", null);
			model.addAttribute("businessError", ex.getMessage());
			addVoucherFormOptions(model);

			return "manager/vouchers/form";
		}
	}

	@GetMapping("/branches/{branchId}/vouchers/{voucherId}/edit")
	public String editVoucherForm(@PathVariable Integer branchId, @PathVariable Integer voucherId, Model model,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = Optional.ofNullable(ownBranch(branchId, principal, session));

		if (optionalBranch.isEmpty()) {
			return denied(ra);
		}

		try {
			Voucher voucher = voucherService.findById(voucherId);

			if (!belongsToBranch(voucher, branchId)) {
				ra.addFlashAttribute("error", "Voucher không thuộc chi nhánh này.");

				return "redirect:/manager/branches/" + branchId + "/vouchers";
			}

			VoucherReq req = voucherService.toReq(voucher);
			req.setBranchId(branchId);

			model.addAttribute("branch", optionalBranch.get());
			model.addAttribute("voucherReq", req);
			model.addAttribute("voucherId", voucherId);

			addVoucherFormOptions(model);

			return "manager/vouchers/form";

		} catch (BusinessException ex) {
			ra.addFlashAttribute("error", ex.getMessage());

			return "redirect:/manager/branches/" + branchId + "/vouchers";
		}
	}

	@PostMapping("/branches/{branchId}/vouchers/{voucherId}")
	public String updateVoucher(@PathVariable Integer branchId, @PathVariable Integer voucherId,
			@Valid @ModelAttribute("voucherReq") VoucherReq req, BindingResult result, Model model,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = Optional.ofNullable(ownBranch(branchId, principal, session));

		if (optionalBranch.isEmpty()) {
			return denied(ra);
		}

		Voucher voucher;

		try {
			voucher = voucherService.findById(voucherId);
		} catch (BusinessException ex) {
			ra.addFlashAttribute("error", ex.getMessage());

			return "redirect:/manager/branches/" + branchId + "/vouchers";
		}

		if (!belongsToBranch(voucher, branchId)) {
			ra.addFlashAttribute("error", "Voucher không thuộc chi nhánh này.");

			return "redirect:/manager/branches/" + branchId + "/vouchers";
		}

		if (result.hasErrors()) {
			model.addAttribute("branch", optionalBranch.get());
			model.addAttribute("voucherId", voucherId);
			addVoucherFormOptions(model);

			return "manager/vouchers/form";
		}

		try {
			req.setBranchId(branchId);

			voucherService.update(voucherId, req);

			ra.addFlashAttribute("success", "Đã cập nhật voucher thành công.");

			return "redirect:/manager/branches/" + branchId + "/vouchers";

		} catch (BusinessException ex) {
			model.addAttribute("branch", optionalBranch.get());
			model.addAttribute("voucherId", voucherId);
			model.addAttribute("businessError", ex.getMessage());
			addVoucherFormOptions(model);

			return "manager/vouchers/form";
		}
	}

	@PostMapping("/branches/{branchId}/vouchers/{voucherId}/toggle")
	public String toggleVoucher(@PathVariable Integer branchId, @PathVariable Integer voucherId,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		if (ownBranch(branchId, principal, session) == null) {
			return denied(ra);
		}

		try {
			Voucher voucher = voucherService.findById(voucherId);

			if (!belongsToBranch(voucher, branchId)) {
				ra.addFlashAttribute("error", "Voucher không thuộc chi nhánh này.");

				return "redirect:/manager/branches/" + branchId + "/vouchers";
			}

			voucherService.toggleActive(voucherId);

			ra.addFlashAttribute("success", "Đã thay đổi trạng thái voucher.");

		} catch (BusinessException ex) {
			ra.addFlashAttribute("error", ex.getMessage());
		}

		return "redirect:/manager/branches/" + branchId + "/vouchers";
	}

	@PostMapping("/branches/{branchId}/vouchers/{voucherId}/delete")
	public String deleteVoucher(@PathVariable Integer branchId, @PathVariable Integer voucherId,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		if (ownBranch(branchId, principal, session) == null) {
			return denied(ra);
		}

		try {
			Voucher voucher = voucherService.findById(voucherId);

			if (!belongsToBranch(voucher, branchId)) {
				ra.addFlashAttribute("error", "Voucher không thuộc chi nhánh này.");

				return "redirect:/manager/branches/" + branchId + "/vouchers";
			}

			voucherService.delete(voucherId);

			ra.addFlashAttribute("success", "Đã xử lý xóa voucher. Voucher đã được sử dụng có thể chỉ được tắt.");

		} catch (BusinessException ex) {
			ra.addFlashAttribute("error", ex.getMessage());
		}

		return "redirect:/manager/branches/" + branchId + "/vouchers";
	}

	private boolean belongsToBranch(Voucher voucher, Integer branchId) {
		return voucher != null && voucher.getBranch() != null && branchId != null
				&& branchId.equals(voucher.getBranch().getId());
	}

	private void addVoucherFormOptions(Model model) {
		model.addAttribute("discountTypes", DiscountType.values());
		model.addAttribute("applyToOptions", VoucherApplyTo.values());
		model.addAttribute("paymentMethods", PaymentMethod.values());
	}

	// =========================================================
	// THỐNG KÊ, BÁO CÁO THEO CHI NHÁNH
	// =========================================================
	@GetMapping("/branches/{branchId}/reports")
	public String branchReports(@PathVariable Integer branchId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			Model model, Principal principal, HttpSession session, RedirectAttributes ra) {

		// 1. Kiểm tra chi nhánh
		Optional<Branch> optionalBranch = Optional.ofNullable(ownBranch(branchId, principal, session));

		if (optionalBranch.isEmpty()) {
			return denied(ra);
		}

		LocalDate today = LocalDate.now();

		// 2. Thiết lập khoảng ngày mặc định
		if (startDate == null) {
			startDate = today.withDayOfMonth(1);
		}

		if (endDate == null) {
			endDate = today;
		}

		// Nếu ngày bắt đầu lớn hơn ngày kết thúc thì đổi lại
		if (startDate.isAfter(endDate)) {
			LocalDate temp = startDate;
			startDate = endDate;
			endDate = temp;
		}

		LocalDateTime rangeStart = startDate.atStartOfDay();
		LocalDateTime rangeEnd = endDate.plusDays(1).atStartOfDay();

		// 3. Lấy đơn hàng thuộc chi nhánh
		List<Order> branchOrders = ordersOf(branchId);

		// 4. Lọc đơn đã giao trong khoảng thời gian được chọn
		List<Order> deliveredOrders = branchOrders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED)
				.filter(order -> order.getDeliveredAt() != null)
				.filter(order -> !order.getDeliveredAt().isBefore(rangeStart))
				.filter(order -> order.getDeliveredAt().isBefore(rangeEnd))
				.sorted(Comparator.comparing(Order::getDeliveredAt)).collect(Collectors.toList());

		// 5. Tính doanh thu và hoa hồng trong kỳ
		BigDecimal selectedRevenue = BigDecimal.ZERO;
		BigDecimal selectedCommission = BigDecimal.ZERO;

		for (Order order : deliveredOrders) {
			if (order.getTotalAmount() != null) {
				selectedRevenue = selectedRevenue.add(order.getTotalAmount());
			}

			if (order.getCommissionAmount() != null) {
				selectedCommission = selectedCommission.add(order.getCommissionAmount());
			}
		}

		// 6. Tính doanh thu và số đơn hôm nay
		LocalDateTime todayStart = today.atStartOfDay();
		LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

		// 7. Tính doanh thu và số đơn trong tháng hiện tại
		LocalDate monthStartDate = today.withDayOfMonth(1);

		LocalDateTime monthStart = monthStartDate.atStartOfDay();
		LocalDateTime nextMonthStart = monthStartDate.plusMonths(1).atStartOfDay();

		BigDecimal todayRevenue = sumRevenue(branchOrders, todayStart, tomorrowStart);

		BigDecimal monthRevenue = sumRevenue(branchOrders, monthStart, nextMonthStart);

		long todayOrders = countDeliveredOrders(branchOrders, todayStart, tomorrowStart);

		long monthOrders = countDeliveredOrders(branchOrders, monthStart, nextMonthStart);

		// 8. Tạo dữ liệu doanh thu theo từng ngày
		Map<LocalDate, BigDecimal> revenueByDay = new LinkedHashMap<>();

		for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {

			revenueByDay.put(date, BigDecimal.ZERO);
		}

		for (Order order : deliveredOrders) {
			LocalDate deliveredDate = order.getDeliveredAt().toLocalDate();

			BigDecimal amount = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();

			revenueByDay.merge(deliveredDate, amount, BigDecimal::add);
		}

		// 9. Chuyển ngày thành nhãn hiển thị
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM");

		List<String> chartLabels = revenueByDay.keySet().stream().map(date -> date.format(formatter))
				.collect(Collectors.toList());

		List<BigDecimal> chartValues = new ArrayList<>(revenueByDay.values());

		// 10. Tính phần trăm cho biểu đồ
		// Ngày có doanh thu cao nhất tương ứng 100%
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

		// 11. Truyền dữ liệu sang Thymeleaf
		model.addAttribute("branch", optionalBranch.get());

		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);

		model.addAttribute("todayRevenue", todayRevenue);
		model.addAttribute("monthRevenue", monthRevenue);

		model.addAttribute("selectedRevenue", selectedRevenue);
		model.addAttribute("selectedCommission", selectedCommission);

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

	// Tính doanh thu các đơn đã giao trong khoảng thời gian
	private BigDecimal sumRevenue(List<Order> orders, LocalDateTime start, LocalDateTime end) {

		return orders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED)
				.filter(order -> order.getDeliveredAt() != null)
				.filter(order -> !order.getDeliveredAt().isBefore(start))
				.filter(order -> order.getDeliveredAt().isBefore(end)).map(Order::getTotalAmount)
				.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	// Đếm đơn đã giao trong khoảng thời gian
	private long countDeliveredOrders(List<Order> orders, LocalDateTime start, LocalDateTime end) {

		return orders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED)
				.filter(order -> order.getDeliveredAt() != null)
				.filter(order -> !order.getDeliveredAt().isBefore(start))
				.filter(order -> order.getDeliveredAt().isBefore(end)).count();
	}
}