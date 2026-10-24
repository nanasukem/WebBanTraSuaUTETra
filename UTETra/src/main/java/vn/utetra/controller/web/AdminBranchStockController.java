package vn.utetra.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.exception.BusinessException;
import vn.utetra.service.BranchStockService;

@Controller
@RequestMapping("/admin/branches")
public class AdminBranchStockController {

	private final BranchStockService branchStockService;

	public AdminBranchStockController(BranchStockService branchStockService) {
		this.branchStockService = branchStockService;
	}

	@GetMapping("/{id}/stock")
	public String stock(@PathVariable Integer id, Model model, RedirectAttributes ra) {
		try {
			BranchStockService.BranchStock s = branchStockService.stock(id);
			model.addAttribute("branch", s.branch());
			model.addAttribute("products", s.products());
			model.addAttribute("toppings", s.toppings());
			model.addAttribute("outCount", s.outCount());
			return "admin/branches/stock";
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/branches";
		}
	}

	@PostMapping("/{id}/stock/products/{productId}/toggle")
	public String toggleProduct(@PathVariable Integer id, @PathVariable Integer productId, RedirectAttributes ra) {
		try {
			BranchStockService.Toggled t = branchStockService.toggleProduct(id, productId, true);
			ra.addFlashAttribute("successMessage", (t.available() ? "Đã mở bán lại " : "Đã báo hết ") + t.name()
					+ " tại " + branchStockService.branch(id).getName());
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/branches/" + id + "/stock";
	}

	@PostMapping("/{id}/stock/toppings/{toppingId}/toggle")
	public String toggleTopping(@PathVariable Integer id, @PathVariable Integer toppingId, RedirectAttributes ra) {
		try {
			BranchStockService.Toggled t = branchStockService.toggleTopping(id, toppingId, true);
			ra.addFlashAttribute("successMessage", (t.available() ? "Đã mở bán lại topping " : "Đã báo hết topping ")
					+ t.name() + " tại " + branchStockService.branch(id).getName());
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/branches/" + id + "/stock";
	}
}
