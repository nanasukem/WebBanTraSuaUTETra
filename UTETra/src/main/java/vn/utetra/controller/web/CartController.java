package vn.utetra.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Trang giỏ hàng. Tuần 1: dữ liệu giỏ nằm ở localStorage, cart.js tự vẽ ra
 * trang.
 */
@Controller
public class CartController {

	@GetMapping("/cart")
	public String cart() {
		return "cart/index";
	}
}