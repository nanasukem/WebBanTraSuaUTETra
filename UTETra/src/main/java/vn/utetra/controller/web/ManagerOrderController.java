package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.ManagerOrderService;

import java.security.Principal;
import java.util.List;

@Controller
public class ManagerOrderController {

	private static final List<String> CANCEL_REASONS = List.of(
			"Quán hết nguyên liệu / hết món",
			"Quán đang quá tải, không kịp làm",
			"Ngoài giờ phục vụ",
			"Không liên lạc được với khách",
			"Khách không đến lấy món");
	private static final String OTHER = "OTHER";

	private final ManagerOrderService managerOrderService;
	private final CurrentUserService currentUserService;

	private final vn.utetra.service.OrderIssueService orderIssueService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.BranchShipperService branchShipperService;

	public ManagerOrderController(ManagerOrderService managerOrderService, CurrentUserService currentUserService,
			vn.utetra.service.OrderIssueService orderIssueService) {
		this.managerOrderService = managerOrderService;
		this.currentUserService = currentUserService;
		this.orderIssueService = orderIssueService;
	}

	@GetMapping("/manager/orders")
	public String page(@RequestParam(required = false) Integer branchId, Principal principal, HttpSession session,
			Model model) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		model.addAttribute("branches", managerOrderService.myBranches(managerId));
		model.addAttribute("cancelReasons", CANCEL_REASONS);
		try {
			model.addAttribute("branchId", managerOrderService.resolveBranchId(managerId, branchId));
		} catch (BusinessException | ResourceNotFoundException e) {
			model.addAttribute("branchId", null);
			model.addAttribute("error", e.getMessage());
		}
		return "manager/orders";
	}

	@GetMapping("/manager/orders/history")
	public String history(@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			Principal principal, HttpSession session, Model model) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		model.addAttribute("branches", managerOrderService.myBranches(managerId));
		try {
			model.addAttribute("data", managerOrderService.history(managerId, branchId, status, page));
		} catch (BusinessException | ResourceNotFoundException e) {
			model.addAttribute("data", null);
			model.addAttribute("error", e.getMessage());
		}
		return "manager/history";
	}

	@GetMapping("/manager/orders/{id}")
	public String detail(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		try {
			model.addAttribute("order", managerOrderService.orderDetail(managerId, id));
			model.addAttribute("card", managerOrderService.orderCard(managerId, id));
			model.addAttribute("cancelReasons", CANCEL_REASONS);
			addShipperInfo(id, model);
			return "manager/order-detail";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/manager/orders/history";
		}
	}

	@PostMapping("/manager/orders/{id}/status")
	public String changeStatus(@PathVariable Integer id, @RequestParam String status,
			@RequestParam(required = false) String reason, @RequestParam(required = false) String otherReason,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		String finalReason = OTHER.equals(reason) ? (otherReason == null ? null : otherReason.trim()) : reason;
		try {
			OrderStatus to = parseStatus(status);
			managerOrderService.changeStatus(managerId, id, to, finalReason);
			ra.addFlashAttribute("success", OrderStatus.actionText(to, id, finalReason));
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/manager/orders/" + id;
	}

	@PostMapping("/manager/orders/{id}/issue")
	public String resolveIssue(@PathVariable Integer id, @RequestParam String action,
			@RequestParam(required = false) java.math.BigDecimal amount, @RequestParam(required = false) String note,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		try {
			managerOrderService.orderCard(managerId, id);
			orderIssueService.resolve(id, action, amount, note);
			ra.addFlashAttribute("success", "Đã xử lý báo cáo của khách cho đơn #" + id);
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/manager/orders/" + id;
	}

	private void addShipperInfo(Integer orderId, Model model) {
		vn.utetra.service.BranchShipperService.ShipperBox box = branchShipperService.box(orderId);
		model.addAttribute("shipperBox", box.show());
		if (!box.show()) return;
		model.addAttribute("orderShipper", box.shipper());
		model.addAttribute("shipperBranchId", box.branchId());
		model.addAttribute("canAssignShipper", box.canAssign());
		model.addAttribute("branchShippers", box.options());
	}

	@PostMapping("/manager/orders/{id}/shipper")
	public String assignShipper(@PathVariable Integer id, @RequestParam(required = false) Integer shipperId,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		try {
			managerOrderService.orderCard(managerId, id);
			vn.utetra.entity.User s = branchShipperService.assign(id, shipperId);
			ra.addFlashAttribute("success", s == null
					? "Đã bỏ chỉ định shipper đơn #" + id + ": shipper nào của quán lấy hàng trước thì nhận"
					: "Đã giao đơn #" + id + " cho shipper " + s.getFullName());
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/manager/orders/" + id;
	}

	private OrderStatus parseStatus(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return OrderStatus.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}