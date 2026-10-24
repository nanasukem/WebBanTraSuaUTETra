package vn.utetra.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.entity.Payment;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.PaymentService;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/payment")
public class PaymentController {

	private final PaymentService paymentService;
	private final OrderFlowService orderFlowService;
	private final CurrentUserService currentUserService;

	public PaymentController(PaymentService paymentService, OrderFlowService orderFlowService,
			CurrentUserService currentUserService) {
		this.paymentService = paymentService;
		this.orderFlowService = orderFlowService;
		this.currentUserService = currentUserService;
	}

	@GetMapping("/{orderId}/pay")
	public String pay(@PathVariable Integer orderId, @RequestParam(required = false) String method,
			Principal principal, HttpSession session, HttpServletRequest request, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			return "redirect:" + paymentService.startPayment(userId, orderId, method, clientIp(request));
		} catch (BusinessException | IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/payment/" + orderId + "/result";
		} catch (ResourceNotFoundException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/orders";
		}
	}

	@PostMapping("/{orderId}/cod")
	public String switchToCod(@PathVariable Integer orderId, Principal principal, HttpSession session,
			RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		try {
			paymentService.switchToCod(userId, orderId);
			return "redirect:/checkout/success/" + orderId;
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/payment/" + orderId + "/result";
		}
	}

	@GetMapping("/vnpay/return")
	public String vnpayReturn(@RequestParam Map<String, String> params, Model model) {
		return afterReturn(paymentService.vnpayReturn(params), model);
	}

	@GetMapping("/vnpay/ipn")
	@ResponseBody
	public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
		try {
			return paymentService.vnpayIpn(params);
		} catch (Exception e) {
			return Map.of("RspCode", "99", "Message", "Unknown error");
		}
	}

	@GetMapping("/momo/return")
	public String momoReturn(@RequestParam Map<String, String> params, Model model) {
		return afterReturn(paymentService.momoReturn(params), model);
	}

	@PostMapping("/momo/ipn")
	public ResponseEntity<Void> momoIpn(@RequestBody Map<String, Object> body) {
		Map<String, String> params = new HashMap<>();
		body.forEach((k, v) -> params.put(k, v == null ? "" : String.valueOf(v)));
		paymentService.momoIpn(params);
		return ResponseEntity.noContent().build();
	}

	private String afterReturn(PaymentService.Outcome outcome, Model model) {
		if (outcome.orderId() == null) {
			model.addAttribute("invalidMessage", outcome.message());
			return "payment/result";
		}
		return "redirect:/payment/" + outcome.orderId() + "/result";
	}

	@GetMapping("/{orderId}/result")
	public String result(@PathVariable Integer orderId, Principal principal, HttpSession session, Model model) {
		Integer userId = currentUserService.requireUserId(principal, session);
		OrderDetailRes order;
		try {
			order = orderFlowService.myOrder(userId, orderId);
		} catch (ResourceNotFoundException e) {
			return "redirect:/orders";
		}
		List<Payment> history = paymentService.history(orderId);
		Payment last = history.isEmpty() ? null : history.get(0);

		String state;
		if ("PAID".equals(order.payment().status()) && !"CANCELLED".equals(order.status())) state = "SUCCESS";
		else if ("AWAITING_PAYMENT".equals(order.status()))
			state = last != null && "PENDING".equals(last.getStatus()) ? "PROCESSING" : "FAILED";
		else if ("CANCELLED".equals(order.status())) state = "CANCELLED";
		else state = "COD";

		model.addAttribute("order", order);
		model.addAttribute("state", state);
		model.addAttribute("last", last);
		model.addAttribute("lastMessage", last == null ? null : last.getMessage());
		model.addAttribute("vnpayDemo", paymentService.vnpayDemo());
		return "payment/result";
	}

	@GetMapping("/{orderId}/status")
	@ResponseBody
	public Map<String, Object> status(@PathVariable Integer orderId, @RequestParam(defaultValue = "false") boolean refresh,
			Principal principal, HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		return paymentService.status(userId, orderId, refresh);
	}

	@GetMapping("/{orderId}/demo")
	public String demo(@PathVariable Integer orderId, Principal principal, HttpSession session, Model model) {
		Integer userId = currentUserService.requireUserId(principal, session);
		if (!paymentService.vnpayDemo()) return "redirect:/payment/" + orderId + "/pay";
		try {
			model.addAttribute("order", orderFlowService.myOrder(userId, orderId));
		} catch (ResourceNotFoundException e) {
			return "redirect:/orders";
		}
		return "payment/demo";
	}

	@PostMapping("/{orderId}/demo")
	public String demoSubmit(@PathVariable Integer orderId, @RequestParam String result, Principal principal,
			HttpSession session, RedirectAttributes ra) {
		Integer userId = currentUserService.requireUserId(principal, session);
		if (!paymentService.vnpayDemo()) return "redirect:/payment/" + orderId + "/result";
		try {
			paymentService.demoResult(userId, orderId, "success".equals(result));
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/payment/" + orderId + "/result";
	}

	private static String clientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Forwarded-For");
		if (ip != null && !ip.isBlank()) ip = ip.split(",")[0].trim();
		else ip = request.getRemoteAddr();
		if (ip == null || ip.isBlank() || ip.contains(":")) return "127.0.0.1";
		return ip;
	}
}
