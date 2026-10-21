
package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.OrderService;

import java.security.Principal;

@Controller
public class CheckoutController {

	private final OrderService orderService;
	private final CurrentUserService currentUserService;

	public CheckoutController(OrderService orderService, CurrentUserService currentUserService) {

		this.orderService = orderService;
		this.currentUserService = currentUserService;
	}

	@GetMapping("/checkout")
	public String checkout(Principal principal, HttpSession session, Model model) {

		model.addAttribute("currentUser", currentUserService.requireUser(principal, session));

		return "cart/checkout";
	}

	@GetMapping("/checkout/success/{id}")
	public String success(@PathVariable Integer id, Principal principal, HttpSession session, Model model) {

		var user = currentUserService.requireUser(principal, session);
		model.addAttribute("currentUser", user);

		try {
			model.addAttribute("order", orderService.getOrderSummary(user.getId(), id));
		} catch (ResourceNotFoundException e) {
			return "redirect:/menu";
		}

		return "cart/checkout-success";
	}
}
