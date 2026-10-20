package vn.utetra.controller.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.entity.enums.VoucherApplyTo;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.BranchProductRepository;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.service.VoucherService;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	private final BranchRepository branchRepository;
	private final BranchProductRepository branchProductRepository;
	private final ProductRepository productRepository;
	private final OrderRepository orderRepository;
	private final VoucherService voucherService;

	public ManagerController(BranchRepository branchRepository, BranchProductRepository branchProductRepository,
			ProductRepository productRepository, OrderRepository orderRepository, VoucherService voucherService) {
		this.branchRepository = branchRepository;
		this.branchProductRepository = branchProductRepository;
		this.productRepository = productRepository;
		this.orderRepository = orderRepository;
		this.voucherService = voucherService;
	}

	// =========================================================
	// DASHBOARD
	// =========================================================

	@GetMapping({ "", "/", "/dashboard" })
	public String dashboard(Model model) {

		List<Branch> branches = branchRepository.findAll();

		List<Order> allOrders = orderRepository.findAll();

		model.addAttribute("totalBranches", branches.size());

		model.addAttribute("pendingBranches",
				branches.stream().filter(b -> b.getStatus() == BranchStatus.PENDING).count());

		model.addAttribute("approvedBranches",
				branches.stream().filter(b -> b.getStatus() == BranchStatus.APPROVED).count());

		model.addAttribute("rejectedBranches",
				branches.stream().filter(b -> b.getStatus() == BranchStatus.REJECTED).count());

		model.addAttribute("suspendedBranches",
				branches.stream().filter(b -> b.getStatus() == BranchStatus.SUSPENDED).count());

		model.addAttribute("totalProducts", productRepository.count());
		model.addAttribute("totalOrders", allOrders.size());

		model.addAttribute("pendingOrders",
				allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count());

		model.addAttribute("deliveredOrders",
				allOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count());

		List<Branch> recentBranches = branches.stream()
				.sorted(Comparator.comparing(Branch::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(5).collect(Collectors.toList());

		model.addAttribute("recentBranches", recentBranches);

		return "manager/dashboard";
	}

	// =========================================================
	// QUẢN LÝ CHI NHÁNH
	// =========================================================

	@GetMapping("/branches")
	public String branches(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) BranchStatus status, Model model) {

		String searchKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();

		List<Branch> branches = branchRepository.searchBranches(searchKeyword, status);

		model.addAttribute("branches", branches);
		model.addAttribute("keyword", keyword);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("statuses", BranchStatus.values());

		return "manager/branches/index";
	}

	@GetMapping("/branches/{id}")
	public String branchDetail(@PathVariable Integer id, Model model, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(id);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		Branch branch = optionalBranch.get();

		model.addAttribute("branch", branch);
		model.addAttribute("statuses", BranchStatus.values());
		model.addAttribute("branchProducts", branchProductRepository.findByBranchId(id));

		return "manager/branches/detail";
	}

	@PostMapping("/branches/{id}/approve")
	public String approveBranch(@PathVariable Integer id, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(id);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		Branch branch = optionalBranch.get();

		if (branch.getStatus() != BranchStatus.PENDING) {
			ra.addFlashAttribute("error", "Chỉ có thể duyệt chi nhánh đang chờ duyệt.");
			return "redirect:/manager/branches/" + id;
		}

		branch.setStatus(BranchStatus.APPROVED);
		branchRepository.save(branch);

		ra.addFlashAttribute("success", "Đã duyệt chi nhánh thành công.");

		return "redirect:/manager/branches/" + id;
	}

	@PostMapping("/branches/{id}/reject")
	public String rejectBranch(@PathVariable Integer id, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(id);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		Branch branch = optionalBranch.get();

		if (branch.getStatus() != BranchStatus.PENDING) {
			ra.addFlashAttribute("error", "Chỉ có thể từ chối chi nhánh đang chờ duyệt.");
			return "redirect:/manager/branches/" + id;
		}

		branch.setStatus(BranchStatus.REJECTED);
		branchRepository.save(branch);

		ra.addFlashAttribute("success", "Đã từ chối chi nhánh.");

		return "redirect:/manager/branches/" + id;
	}

	@PostMapping("/branches/{id}/suspend")
	public String suspendBranch(@PathVariable Integer id, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(id);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		Branch branch = optionalBranch.get();

		if (branch.getStatus() != BranchStatus.APPROVED) {
			ra.addFlashAttribute("error", "Chỉ có thể tạm ngưng chi nhánh đang hoạt động.");
			return "redirect:/manager/branches/" + id;
		}

		branch.setStatus(BranchStatus.SUSPENDED);
		branchRepository.save(branch);

		ra.addFlashAttribute("success", "Đã tạm ngưng chi nhánh.");

		return "redirect:/manager/branches/" + id;
	}

	@PostMapping("/branches/{id}/activate")
	public String activateBranch(@PathVariable Integer id, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(id);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		Branch branch = optionalBranch.get();

		if (branch.getStatus() != BranchStatus.SUSPENDED) {
			ra.addFlashAttribute("error", "Chỉ có thể kích hoạt lại chi nhánh đang bị tạm ngưng.");
			return "redirect:/manager/branches/" + id;
		}

		branch.setStatus(BranchStatus.APPROVED);
		branchRepository.save(branch);

		ra.addFlashAttribute("success", "Đã kích hoạt lại chi nhánh.");

		return "redirect:/manager/branches/" + id;
	}

	@PostMapping("/branches/{id}/commission")
	public String updateCommission(@PathVariable Integer id, @RequestParam BigDecimal commissionRate,
			RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(id);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		if (commissionRate == null || commissionRate.compareTo(BigDecimal.ZERO) < 0
				|| commissionRate.compareTo(new BigDecimal("100")) > 0 || commissionRate.scale() > 2) {

			ra.addFlashAttribute("error", "Tỷ lệ hoa hồng phải từ 0 đến 100%, tối đa 2 chữ số thập phân.");

			return "redirect:/manager/branches/" + id;
		}

		Branch branch = optionalBranch.get();
		branch.setCommissionRate(commissionRate);
		branchRepository.save(branch);

		ra.addFlashAttribute("success", "Đã cập nhật tỷ lệ hoa hồng.");

		return "redirect:/manager/branches/" + id;
	}

	// =========================================================
	// QUẢN LÝ SẢN PHẨM THEO CHI NHÁNH
	// =========================================================

	@GetMapping("/branches/{branchId}/products")
	public String branchProducts(@PathVariable Integer branchId, Model model, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		Branch branch = optionalBranch.get();

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
			RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		Optional<Product> optionalProduct = productRepository.findById(productId);

		if (optionalBranch.isEmpty() || optionalProduct.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh hoặc sản phẩm.");

			return "redirect:/manager/branches";
		}

		Branch branch = optionalBranch.get();
		Product product = optionalProduct.get();

		if (branch.getStatus() != BranchStatus.APPROVED) {
			ra.addFlashAttribute("error", "Chỉ có thể điều chỉnh sản phẩm của chi nhánh đang hoạt động.");

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
	// QUẢN LÝ ĐƠN HÀNG THEO CHI NHÁNH
	// =========================================================

	@GetMapping("/branches/{branchId}/orders")
	public String branchOrders(@PathVariable Integer branchId, @RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String paymentStatus,
			@RequestParam(defaultValue = "") String paymentMethod, Model model, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		String normalizedKeyword = keyword.trim().toLowerCase();

		List<Order> allBranchOrders = orderRepository.findAll().stream()
				.filter(o -> o.getBranch() != null && branchId.equals(o.getBranch().getId()))
				.collect(Collectors.toList());

		List<Order> filteredOrders = allBranchOrders.stream().filter(o -> {
			if (normalizedKeyword.isEmpty()) {
				return true;
			}

			boolean matchId = String.valueOf(o.getId()).contains(normalizedKeyword);

			boolean matchCustomer = o.getUser() != null && o.getUser().getFullName() != null
					&& o.getUser().getFullName().toLowerCase().contains(normalizedKeyword);

			boolean matchUsername = o.getUser() != null && o.getUser().getUsername() != null
					&& o.getUser().getUsername().toLowerCase().contains(normalizedKeyword);

			return matchId || matchCustomer || matchUsername;
		}).filter(o -> status.isBlank() || o.getStatus().name().equals(status))
				.filter(o -> paymentStatus.isBlank()
						|| (o.getPaymentStatus() != null && o.getPaymentStatus().name().equals(paymentStatus)))
				.filter(o -> paymentMethod.isBlank()
						|| (o.getPaymentMethod() != null && o.getPaymentMethod().name().equals(paymentMethod)))
				.sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.collect(Collectors.toList());

		model.addAttribute("branch", optionalBranch.get());
		model.addAttribute("orders", filteredOrders);

		model.addAttribute("keyword", keyword);
		model.addAttribute("selectedStatus", status);
		model.addAttribute("selectedPaymentStatus", paymentStatus);
		model.addAttribute("selectedPaymentMethod", paymentMethod);

		model.addAttribute("orderStatuses", OrderStatus.values());
		model.addAttribute("paymentStatuses", PaymentStatus.values());
		model.addAttribute("paymentMethods", PaymentMethod.values());

		model.addAttribute("totalOrders", allBranchOrders.size());
		model.addAttribute("pendingOrders", countOrdersByStatus(allBranchOrders, OrderStatus.PENDING));
		model.addAttribute("deliveredOrders", countOrdersByStatus(allBranchOrders, OrderStatus.DELIVERED));
		model.addAttribute("cancelledOrders", countOrdersByStatus(allBranchOrders, OrderStatus.CANCELLED));

		return "manager/orders/index";
	}

	@GetMapping("/branches/{branchId}/orders/{orderId}")
	public String branchOrderDetail(@PathVariable Integer branchId, @PathVariable Integer orderId, Model model,
			RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		Optional<Order> optionalOrder = orderRepository.findById(orderId);

		if (optionalBranch.isEmpty() || optionalOrder.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh hoặc đơn hàng.");

			return "redirect:/manager/branches";
		}

		Order order = optionalOrder.get();

		if (order.getBranch() == null || !branchId.equals(order.getBranch().getId())) {

			ra.addFlashAttribute("error", "Đơn hàng không thuộc chi nhánh này.");

			return "redirect:/manager/branches/" + branchId + "/orders";
		}

		model.addAttribute("branch", optionalBranch.get());
		model.addAttribute("order", order);
		model.addAttribute("nextStatuses", getNextStatuses(order.getStatus()));

		return "manager/orders/detail";
	}

	@PostMapping("/branches/{branchId}/orders/{orderId}/status")
	public String updateBranchOrderStatus(@PathVariable Integer branchId, @PathVariable Integer orderId,
			@RequestParam OrderStatus status, RedirectAttributes ra) {

		Optional<Order> optionalOrder = orderRepository.findById(orderId);

		if (optionalOrder.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
			return "redirect:/manager/branches/" + branchId + "/orders";
		}

		Order order = optionalOrder.get();

		if (order.getBranch() == null || !branchId.equals(order.getBranch().getId())) {

			ra.addFlashAttribute("error", "Không được cập nhật đơn hàng của chi nhánh khác.");

			return "redirect:/manager/branches/" + branchId + "/orders";
		}

		List<OrderStatus> allowedStatuses = getNextStatuses(order.getStatus());

		if (!allowedStatuses.contains(status)) {
			ra.addFlashAttribute("error", "Trạng thái mới không hợp lệ với trạng thái đơn hàng hiện tại.");

			return "redirect:/manager/branches/" + branchId + "/orders/" + orderId;
		}

		order.setStatus(status);

		if (status == OrderStatus.DELIVERED) {
			order.setDeliveredAt(LocalDateTime.now());
		}

		orderRepository.save(order);

		ra.addFlashAttribute("success", "Đã cập nhật trạng thái đơn hàng.");

		return "redirect:/manager/branches/" + branchId + "/orders/" + orderId;
	}

	private long countOrdersByStatus(List<Order> orders, OrderStatus status) {

		return orders.stream().filter(o -> o.getStatus() == status).count();
	}

	private List<OrderStatus> getNextStatuses(OrderStatus status) {

		List<OrderStatus> statuses = new ArrayList<>();

		if (status == null) {
			return statuses;
		}

		switch (status) {
		case PENDING:
			statuses.add(OrderStatus.CONFIRMED);
			statuses.add(OrderStatus.CANCELLED);
			break;

		case CONFIRMED:
			statuses.add(OrderStatus.PICKED_UP);
			statuses.add(OrderStatus.CANCELLED);
			break;

		case PICKED_UP:
			statuses.add(OrderStatus.DELIVERING);
			statuses.add(OrderStatus.DELIVERED);
			break;

		case DELIVERING:
			statuses.add(OrderStatus.DELIVERED);
			break;

		case DELIVERED:
		case CANCELLED:
		case RETURNED:
			break;
		}

		return statuses;
	}

	// =========================================================
	// QUẢN LÝ TẤT CẢ VOUCHER
	// =========================================================
	// QUẢN LÝ TẤT CẢ VOUCHER
	@GetMapping("/branches/{branchId}/vouchers")
	public String branchVouchers(@PathVariable Integer branchId, Model model, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
		}

		model.addAttribute("branch", optionalBranch.get());
		model.addAttribute("vouchers", voucherService.findByBranch(branchId));

		return "manager/vouchers/index";
	}

	@GetMapping("/branches/{branchId}/vouchers/new")
	public String createVoucherForm(@PathVariable Integer branchId, Model model, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
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
			BindingResult result, Model model, RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
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
			RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
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
			RedirectAttributes ra) {

		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
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
			RedirectAttributes ra) {

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
			RedirectAttributes ra) {

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
			Model model, RedirectAttributes ra) {

		// 1. Kiểm tra chi nhánh
		Optional<Branch> optionalBranch = branchRepository.findById(branchId);

		if (optionalBranch.isEmpty()) {
			ra.addFlashAttribute("error", "Không tìm thấy chi nhánh.");
			return "redirect:/manager/branches";
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
		List<Order> branchOrders = orderRepository.findAll().stream()
				.filter(order -> order.getBranch() != null && branchId.equals(order.getBranch().getId()))
				.collect(Collectors.toList());

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