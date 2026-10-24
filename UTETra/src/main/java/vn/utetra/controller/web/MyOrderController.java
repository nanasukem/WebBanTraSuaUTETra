package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.ReviewService;
import vn.utetra.service.CartService;
import vn.utetra.dto.response.ReorderRes;
import vn.utetra.dto.request.OrderReviewReq;
import vn.utetra.dto.request.ReviewItemReq;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import java.util.ArrayList;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/orders")
public class MyOrderController {

	private static final List<String> CANCEL_REASONS = List.of(
			"Muốn thay đổi địa chỉ / số điện thoại nhận hàng",
			"Muốn thay đổi món, size hoặc topping",
			"Muốn dùng mã giảm giá khác",
			"Đặt nhầm, không còn nhu cầu");
	private static final String OTHER = "OTHER";

	private final OrderFlowService orderFlowService;
	private final CurrentUserService currentUserService;
	private final ReviewService reviewService;
	private final CartService cartService;
	private final vn.utetra.service.OrderIssueService orderIssueService;

	public MyOrderController(OrderFlowService orderFlowService, CurrentUserService currentUserService,
			ReviewService reviewService, CartService cartService,
			vn.utetra.service.OrderIssueService orderIssueService) {
		this.orderIssueService = orderIssueService;
		this.cartService = cartService;
		this.orderFlowService = orderFlowService;
		this.currentUserService = currentUserService;
		this.reviewService = reviewService;
	}

	@GetMapping
	public String list(@RequestParam(required = false) String tab, Principal principal, HttpSession session, Model model) {
		Integer userId = currentUserService.requireUserId(principal, session);
		OrderTab current = OrderTab.parse(tab);
		model.addAttribute("tabs", OrderTab.values());
		model.addAttribute("tab", current);
		model.addAttribute("counts", orderFlowService.myOrderCounts(userId));
		model.addAttribute("orders", orderFlowService.myOrders(userId, current));
		return "orders/list";
	}

	@PostMapping("/{id}/reorder")
	public String reorder(@PathVariable Integer id, Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			ReorderRes res = cartService.reorder(userId, id);
			if (res.addedQty() > 0)
				ra.addFlashAttribute("success", "Đã thêm " + res.addedQty() + " ly từ đơn #" + id
						+ " vào giỏ. Giá được tính theo giá hiện tại.");
			else
				ra.addFlashAttribute("error", "Không thêm được món nào từ đơn #" + id + ".");
			if (!res.notes().isEmpty())
				ra.addFlashAttribute("reorderNotes", res.notes());
			return "redirect:/cart";
		} catch (ResourceNotFoundException | BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders";
		}
	}

	@GetMapping("/{id}/tracking")
	public String tracking(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			model.addAttribute("order", orderFlowService.myOrder(userId, id));
			return "orders/tracking";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders";
		}
	}

	@GetMapping("/{id}/status")
	@ResponseBody
	public java.util.Map<String, String> status(@PathVariable Integer id, Principal principal, HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		var o = orderFlowService.myOrder(userId, id);
		return java.util.Map.of("status", o.status(), "statusLabel", o.statusLabel(),
				"message", o.cancelReason() == null ? "" : "Lý do: " + o.cancelReason());
	}

	@GetMapping("/{id}/review")
	public String reviewPage(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			model.addAttribute("page", reviewService.reviewPage(userId, id));
			model.addAttribute("productTags", ReviewService.PRODUCT_TAGS);
			model.addAttribute("storeTags", ReviewService.STORE_TAGS);
			return "orders/review";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders";
		}
	}

	@PostMapping("/{id}/review")
	public String submitReview(@PathVariable Integer id, MultipartHttpServletRequest request, Principal principal,
			HttpSession session, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		List<ReviewItemReq> items = new ArrayList<>();
		for (String name : request.getParameterMap().keySet()) {
			if (!name.startsWith("rating_")) continue;
			Integer detailId = toInt(name.substring(7));
			Integer rating = toInt(request.getParameter(name));
			if (detailId == null || rating == null) continue;
			items.add(new ReviewItemReq(detailId, rating, values(request.getParameterValues("tags_" + detailId)),
					request.getParameter("content_" + detailId), request.getFiles("media_" + detailId)));
		}
		try {
			reviewService.submit(userId, id, new OrderReviewReq(items, toInt(request.getParameter("storeRating")),
					values(request.getParameterValues("storeTags")), request.getParameter("storeComment"),
					request.getFiles("storeMedia")));
			ra.addFlashAttribute("success", "Cảm ơn bạn đã đánh giá! Góp ý của bạn giúp quán phục vụ tốt hơn.");
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/orders/" + id + "/review";
	}

	private static List<String> values(String[] arr) {
		return arr == null ? List.of() : List.of(arr);
	}

	private static Integer toInt(String s) {
		try {
			return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@GetMapping("/{id}")
	public String detail(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			model.addAttribute("order", orderFlowService.myOrder(userId, id));
			model.addAttribute("cancelReasons", CANCEL_REASONS);
			model.addAttribute("reviewItems", reviewService.purchasedItems(userId, null).stream()
					.filter(i -> i.orderId().equals(id)).toList());
			long pending = reviewService.pendingItems(userId, null).stream().filter(i -> i.orderId().equals(id)).count();
			model.addAttribute("pendingReview", pending);
			model.addAttribute("canReview", pending > 0);
			return "orders/detail";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders";
		}
	}

	@PostMapping("/{id}/cancel")
	public String cancel(@PathVariable Integer id, @RequestParam(required = false) String reason,
			@RequestParam(required = false) String otherReason, Principal principal, HttpSession session,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		String finalReason = reason;
		if (OTHER.equals(reason)) {
			if (otherReason == null || otherReason.isBlank()) {
				ra.addFlashAttribute("error", "Bạn chọn \"Lý do khác\", vui lòng ghi rõ lý do");
				return "redirect:/orders/" + id;
			}
			finalReason = "Khác: " + otherReason.trim();
		}
		try {
			orderFlowService.cancelByCustomer(userId, id, finalReason);
			ra.addFlashAttribute("success", "Đã huỷ đơn #" + id + ". Mã giảm giá (nếu có) đã được hoàn lại.");
		} catch (BusinessException | ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/orders/" + id;
	}

	@GetMapping("/{id}/issue")
	public String issueForm(@PathVariable Integer id, Principal principal, HttpSession session, Model model,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			var order = orderFlowService.myOrder(userId, id);
			if (!order.canReportIssue()) {
				ra.addFlashAttribute("error", order.issue() != null ? "Bạn đã báo vấn đề cho đơn này rồi"
						: "Chỉ báo vấn đề được trong " + vn.utetra.service.OrderIssueService.WINDOW_HOURS
								+ " giờ sau khi nhận món");
				return "redirect:/orders/" + id;
			}
			model.addAttribute("order", order);
			model.addAttribute("reasons", vn.utetra.entity.enums.IssueReason.values());
			return "orders/issue";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders";
		}
	}

	@PostMapping("/{id}/issue")
	public String issueSubmit(@PathVariable Integer id, @RequestParam(required = false) String reason,
			@RequestParam(required = false) String description,
			@RequestParam(value = "images", required = false) List<org.springframework.web.multipart.MultipartFile> images,
			Principal principal, HttpSession session, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			vn.utetra.entity.enums.IssueReason r = reason == null || reason.isBlank() ? null
					: vn.utetra.entity.enums.IssueReason.valueOf(reason);
			orderIssueService.submit(userId, id, r, description, images);
			ra.addFlashAttribute("success", "Đã gửi báo cáo cho quán. Quán sẽ phản hồi sớm, bạn theo dõi ở chuông thông báo nhé."
					+ " Đơn đang được giải quyết nên đánh giá của đơn này (nếu có) đã được gỡ.");
			return "redirect:/orders/" + id;
		} catch (BusinessException | ResourceNotFoundException | IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders/" + id + "/issue";
		}
	}
}