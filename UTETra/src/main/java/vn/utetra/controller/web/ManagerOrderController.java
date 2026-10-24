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
	private vn.utetra.repository.OrderRepository orderRepository;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.repository.UserRepository userRepository;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.NotificationService notificationService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.OrderNotifier orderNotifier;

	private static final List<OrderStatus> ASSIGNABLE = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
			OrderStatus.READY);
	private static final List<OrderStatus> ON_THE_ROAD = List.of(OrderStatus.PICKED_UP, OrderStatus.DELIVERING);

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
		vn.utetra.entity.Order o = orderRepository.findById(orderId).orElse(null);
		if (o == null || o.getDeliveryType() != vn.utetra.entity.enums.DeliveryType.DELIVERY) {
			model.addAttribute("shipperBox", false);
			return;
		}
		model.addAttribute("shipperBox", true);
		model.addAttribute("orderShipper", o.getShipper());
		model.addAttribute("canAssignShipper", ASSIGNABLE.contains(o.getStatus()));
		List<ShipperOption> options = new java.util.ArrayList<>();
		if (o.getBranch() != null) {
			for (vn.utetra.entity.User s : userRepository.findShippersOfBranch(o.getBranch().getId())) {
				if (!Boolean.TRUE.equals(s.getIsActive())) continue;
				int busy = orderRepository.findOpenOrderIdsOfShipper(s.getId(), ON_THE_ROAD).size();
				options.add(new ShipperOption(s.getId(), s.getFullName(), s.getPhone(), busy));
			}
		}
		model.addAttribute("branchShippers", options);
	}

	public record ShipperOption(Integer id, String name, String phone, int busy) {
	}

	@PostMapping("/manager/orders/{id}/shipper")
	@org.springframework.transaction.annotation.Transactional
	public String assignShipper(@PathVariable Integer id, @RequestParam(required = false) Integer shipperId,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer managerId = currentUserService.requireUserId(principal, session);
		try {
			managerOrderService.orderCard(managerId, id);
			vn.utetra.entity.Order o = orderRepository.findById(id)
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + id));
			if (o.getDeliveryType() != vn.utetra.entity.enums.DeliveryType.DELIVERY) {
				throw new BusinessException("Đơn #" + id + " là đơn khách tự đến lấy, không cần shipper");
			}
			if (!ASSIGNABLE.contains(o.getStatus())) {
				throw new BusinessException("Shipper đã lấy hàng hoặc đơn đã kết thúc, không đổi shipper được nữa");
			}
			vn.utetra.entity.User old = o.getShipper();
			vn.utetra.entity.User s = null;
			if (shipperId != null) {
				s = userRepository.findById(shipperId).orElse(null);
				if (s == null || s.getRole() == null || !"SHIPPER".equals(s.getRole().getName())
						|| s.getBranch() == null || o.getBranch() == null
						|| !s.getBranch().getId().equals(o.getBranch().getId())) {
					throw new BusinessException("Shipper này không thuộc chi nhánh của đơn");
				}
				if (!Boolean.TRUE.equals(s.getIsActive())) {
					throw new BusinessException("Tài khoản shipper " + s.getFullName() + " đang bị khoá");
				}
			}
			if (old == null && s == null || old != null && s != null && old.getId().equals(s.getId())) {
				throw new BusinessException("Đơn #" + id + " không có gì thay đổi");
			}
			o.setShipper(s);
			orderRepository.save(o);
			orderNotifier.onShipperChanged(o);
			if (s != null) {
				notificationService.create(s, o, "ORDER", "Quán giao đơn #" + id + " cho bạn",
						"Đơn giao tới " + o.getDeliveryAddress() + ". Pha xong là đơn hiện ở mục Chờ lấy tại quán.",
						"/shipper/orders");
			}
			if (old != null) {
				notificationService.create(old, o, "ORDER", "Quán đã chuyển đơn #" + id + " cho shipper khác",
						"Bạn không cần giao đơn này nữa.", "/shipper/orders");
			}
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