package vn.utetra.controller.web;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import vn.utetra.service.ReportService;
import vn.utetra.service.BranchStockService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpSession;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.dto.response.ManagerVoucherRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.BranchService;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.ManagerCustomerService;
import vn.utetra.service.ManagerOrderService;
import vn.utetra.service.VoucherService;

@Controller
@RequestMapping("/manager")
public class ManagerController {

	private static final String NOT_YOUR_BRANCH = "Bạn chỉ được quản lý chi nhánh của mình.";

	private static final String NO_BRANCH = "Tài khoản của bạn chưa được Admin giao chi nhánh nào.";

	private final ManagerOrderService managerOrderService;
	private final VoucherService voucherService;
	private final CurrentUserService currentUserService;
	private final ManagerCustomerService managerCustomerService;
	private final BranchService branchService;
	private final vn.utetra.service.ToppingService toppingService;
	private final vn.utetra.service.NotificationService notificationService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.ReportService reportService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.BranchStockService branchStockService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.BranchShipperService branchShipperService;

	public ManagerController(ManagerOrderService managerOrderService, VoucherService voucherService,
			CurrentUserService currentUserService, ManagerCustomerService managerCustomerService,
			BranchService branchService, vn.utetra.service.ToppingService toppingService,
			vn.utetra.service.NotificationService notificationService) {

		this.managerOrderService = managerOrderService;
		this.voucherService = voucherService;
		this.currentUserService = currentUserService;
		this.managerCustomerService = managerCustomerService;
		this.branchService = branchService;
		this.toppingService = toppingService;
		this.notificationService = notificationService;
	}

	private Branch myBranch(Principal principal, HttpSession session) {
		Integer managerId = currentUserService.requireUserId(principal, session);

		List<Branch> branches = branchService.findByOwner(managerId);

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
		return managerOrderService.branchOrders(branchId);
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

	@GetMapping("/shippers")
	public String myShippers(Principal principal, HttpSession session, RedirectAttributes ra) {

		return toMyBranch("/shippers", principal, session, ra);
	}

	@GetMapping("/customers")
	public String myCustomers(Principal principal, HttpSession session, RedirectAttributes ra) {

		return toMyBranch("/customers", principal, session, ra);
	}

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
						.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED || o.getStatus() == OrderStatus.READY
								|| o.getStatus() == OrderStatus.PICKED_UP || o.getStatus() == OrderStatus.DELIVERING)
						.count());

		model.addAttribute("deliveredOrders", countOrdersByStatus(orders, OrderStatus.DELIVERED));

		model.addAttribute("cancelledOrders", countOrdersByStatus(orders, OrderStatus.CANCELLED));

		ReportService.Point todayStat = reportService.summary(todayStart, tomorrowStart, branch.getId());
		ReportService.Point monthStat = reportService.summary(monthStart, nextMonthStart, branch.getId());
		model.addAttribute("todayStat", todayStat);
		model.addAttribute("monthStat", monthStat);
		model.addAttribute("todayRevenue", todayStat.revenue());

		model.addAttribute("monthRevenue", monthStat.revenue());

		model.addAttribute("unavailableProducts",
				branchStockService.unavailableProductIds(branch.getId()).size());

		model.addAttribute("activeVouchers",
				voucherService.branchView(branch.getId()).stream().filter(v -> "RUNNING".equals(v.status())).count());

		List<ReportService.Point> week = reportService.byDay(today.minusDays(6), today, branch.getId());
		ReportService.Point weekSum = ReportService.total(week);
		model.addAttribute("weekLabels", week.stream().map(ReportService.Point::label).toList());
		model.addAttribute("weekValues", week.stream().map(ReportService.Point::revenue).toList());
		model.addAttribute("weekRevenue", weekSum.revenue());
		model.addAttribute("weekOrders", weekSum.orders());

		return "manager/dashboard";
	}

	@GetMapping("/branches/{id}")
	public String branchDetail(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {

		Branch branch = ownBranch(id, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		model.addAttribute("branch", branch);
		model.addAttribute("branchProducts", branchStockService.branchProducts(id));

		return "manager/branches/detail";
	}

	@PostMapping(value = "/branches/{id}/homepage", consumes = "multipart/form-data")
	public String updateBranchHomepage(@PathVariable Integer id,
			@RequestParam(name = "description", required = false) String description,
			@RequestParam(name = "imageFile", required = false) MultipartFile imageFile, Principal principal,
			HttpSession session, RedirectAttributes ra) {

		Branch branch = ownBranch(id, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		try {
			String username = principal != null ? principal.getName() : null;

			branchService.updateManagerHomepage(id, username, description, imageFile);

			ra.addFlashAttribute("success", "Đã cập nhật mô tả và ảnh bìa chi nhánh thành công.");

		} catch (ResourceNotFoundException ex) {
			ra.addFlashAttribute("error", ex.getMessage());

		} catch (RuntimeException ex) {
			ra.addFlashAttribute("error", "Không thể cập nhật chi nhánh. " + "Vui lòng kiểm tra dữ liệu và thử lại.");
		}

		return "redirect:/manager/branches/" + id;
	}

	@GetMapping("/branches/{branchId}/products")
	public String branchProducts(@PathVariable Integer branchId, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		model.addAttribute("branch", branch);
		model.addAttribute("products", branchStockService.drinks());
		model.addAttribute("unavailableProductIds", branchStockService.unavailableProductIds(branchId));
		model.addAttribute("toppings", branchStockService.toppings());
		model.addAttribute("unavailableToppingIds", branchStockService.unavailableToppingIds(branchId));
		model.addAttribute("branchProducts", branchStockService.branchProducts(branchId));

		return "manager/branches/products";
	}

	@PostMapping("/branches/{branchId}/products/{productId}/toggle")
	public String toggleBranchProduct(@PathVariable Integer branchId, @PathVariable Integer productId,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		try {
			BranchStockService.Toggled t = branchStockService.toggleProduct(branchId, productId, false);
			ra.addFlashAttribute("success", (t.available() ? "Đã mở bán lại " : "Đã báo hết ") + t.name() + " tại chi nhánh.");
		} catch (vn.utetra.exception.BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}

		return "redirect:/manager/branches/" + branchId + "/products";
	}

	@PostMapping("/branches/{branchId}/toppings/{toppingId}/toggle")
	public String toggleBranchTopping(@PathVariable Integer branchId, @PathVariable Integer toppingId,
			Principal principal, HttpSession session, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);
		if (branch == null) {
			return denied(ra);
		}
		try {
			boolean dangBan = branchStockService.toggleTopping(branchId, toppingId, false).available();
			ra.addFlashAttribute("success", dangBan ? "Đã mở bán lại topping tại chi nhánh."
					: "Đã báo hết topping tại chi nhánh (khách không chọn được topping này khi đặt ở chi nhánh bạn).");
		} catch (vn.utetra.exception.BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/manager/branches/" + branchId + "/products#toppings";
	}

	@GetMapping("/branches/{branchId}/shippers")
	public String branchShippers(@PathVariable Integer branchId, Principal principal, HttpSession session,
			Model model, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		List<vn.utetra.service.BranchShipperService.ShipperRow> rows = branchShipperService.rows(branchId);

		model.addAttribute("branch", branch);
		model.addAttribute("shippers", rows);
		model.addAttribute("busyCount", rows.stream().filter(r -> r.active() && !r.deliveringIds().isEmpty()).count());
		model.addAttribute("freeCount", rows.stream().filter(r -> r.active() && r.deliveringIds().isEmpty()).count());

		return "manager/shippers/index";
	}

	@GetMapping("/branches/{branchId}/shippers/{shipperId}")
	public String branchShipperDetail(@PathVariable Integer branchId, @PathVariable Integer shipperId,
			Principal principal, HttpSession session, Model model, RedirectAttributes ra) {

		Branch branch = ownBranch(branchId, principal, session);

		if (branch == null) {
			return denied(ra);
		}

		vn.utetra.service.BranchShipperService.ShipperDetail d = branchShipperService.detail(branchId, shipperId)
				.orElse(null);
		if (d == null) {
			ra.addFlashAttribute("error", "Không tìm thấy shipper này ở chi nhánh");
			return "redirect:/manager/branches/" + branchId + "/shippers";
		}

		model.addAttribute("branch", branch);
		model.addAttribute("shipper", d.shipper());
		model.addAttribute("inBranch", d.inBranch());
		model.addAttribute("delivering", d.delivering());
		model.addAttribute("waiting", d.waiting());
		model.addAttribute("delivered", d.delivered());
		model.addAttribute("deliveredToday", d.deliveredToday());
		model.addAttribute("deliveredMonth", d.deliveredMonth());
		model.addAttribute("failed", d.failed());

		return "manager/shippers/detail";
	}

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

		List<ReportService.Point> days = reportService.byDay(startDate, endDate, branchId);
		ReportService.Point sum = ReportService.total(days);
		List<ReportService.Point> today1 = reportService.byDay(today, today, branchId);
		LocalDate monthStartDate = today.withDayOfMonth(1);
		ReportService.Point month = ReportService.total(reportService.byDay(monthStartDate, today, branchId));
		List<ReportService.Point> months = reportService.byMonth(12, endDate, branchId);

		List<Order> branchOrders = ordersOf(branchId);

		model.addAttribute("branch", branch);
		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);

		model.addAttribute("todayStat", today1.get(0));
		model.addAttribute("monthStat", month);
		model.addAttribute("selectedStat", sum);
		model.addAttribute("monthSales", months.stream().map(ReportService.Point::sales).toList());
		model.addAttribute("monthShipping", months.stream().map(ReportService.Point::shipping).toList());
		model.addAttribute("todayRevenue", today1.get(0).revenue());
		model.addAttribute("todayOrders", today1.get(0).orders());
		model.addAttribute("monthRevenue", month.revenue());
		model.addAttribute("monthOrders", month.orders());
		model.addAttribute("selectedRevenue", sum.revenue());
		model.addAttribute("selectedOrders", sum.orders());
		model.addAttribute("selectedAverage", sum.average());

		model.addAttribute("allBranchOrders", branchOrders.size());
		model.addAttribute("pendingOrders", countOrdersByStatus(branchOrders, OrderStatus.PENDING));
		model.addAttribute("cancelledOrders", countOrdersByStatus(branchOrders, OrderStatus.CANCELLED));

		model.addAttribute("chartLabels", days.stream().map(ReportService.Point::label).toList());
		model.addAttribute("chartValues", days.stream().map(ReportService.Point::revenue).toList());
		model.addAttribute("monthLabels", months.stream().map(ReportService.Point::label).toList());
		model.addAttribute("monthValues", months.stream().map(ReportService.Point::revenue).toList());
		model.addAttribute("monthCounts", months.stream().map(ReportService.Point::orders).toList());
		model.addAttribute("topProducts", reportService.topProducts(startDate, endDate, branchId, 10));

		return "manager/reports/index";
	}

	private long countDeliveredOrders(List<Order> orders, LocalDateTime start, LocalDateTime end) {

		return orders.stream().filter(order -> order.getStatus() == OrderStatus.DELIVERED)
				.filter(order -> order.getDeliveredAt() != null)
				.filter(order -> !order.getDeliveredAt().isBefore(start))
				.filter(order -> order.getDeliveredAt().isBefore(end)).count();
	}
}
