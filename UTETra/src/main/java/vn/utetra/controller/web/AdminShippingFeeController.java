package vn.utetra.controller.web;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.exception.BusinessException;
import vn.utetra.service.ShippingFeeService;

/** Admin cài đặt phí giao hàng của chuỗi (Shipper UTETra) - thay cho trang "đối tác giao hàng" cũ */
@Controller
@RequestMapping("/admin/shipping-fee")
public class AdminShippingFeeController {

	private final ShippingFeeService shippingFeeService;

	public AdminShippingFeeController(ShippingFeeService shippingFeeService) {
		this.shippingFeeService = shippingFeeService;
	}

	@GetMapping
	public String index(Model model) {
		model.addAttribute("baseKm", ShippingFeeService.BASE_KM);
		model.addAttribute("baseFee", shippingFeeService.baseFee());
		model.addAttribute("perKmFee", shippingFeeService.perKmFee());
		// Ví dụ để Admin dễ hình dung
		model.addAttribute("examples", java.util.List.of(2, 3, 5, 8, 12).stream()
				.map(km -> new Object[] { km, shippingFeeService.feeFor(BigDecimal.valueOf(km)) }).toList());
		return "admin/shipping-fee";
	}

	@PostMapping
	public String update(@RequestParam(required = false) BigDecimal baseFee,
			@RequestParam(required = false) BigDecimal perKmFee, RedirectAttributes ra) {
		try {
			shippingFeeService.update(baseFee, perKmFee);
			ra.addFlashAttribute("successMessage", "Đã lưu phí giao hàng (áp dụng cho đơn đặt từ bây giờ)");
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/shipping-fee";
	}
}
