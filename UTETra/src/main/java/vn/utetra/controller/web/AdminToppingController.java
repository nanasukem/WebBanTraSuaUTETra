package vn.utetra.controller.web;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.entity.Topping;
import vn.utetra.exception.BusinessException;
import vn.utetra.service.ToppingService;

@Controller
@RequestMapping("/admin/toppings")
public class AdminToppingController {

	private final ToppingService toppingService;

	public AdminToppingController(ToppingService toppingService) {
		this.toppingService = toppingService;
	}

	@GetMapping
	public String index(@RequestParam(required = false) Integer edit, Model model, RedirectAttributes ra) {
		model.addAttribute("rows", toppingService.list());
		if (edit != null) {
			try {
				Topping t = toppingService.get(edit);
				model.addAttribute("editing", t);
				model.addAttribute("editingRow", toppingService.list().stream()
						.filter(r -> r.topping().getId().equals(edit)).findFirst().orElse(null));
			} catch (BusinessException e) {
				ra.addFlashAttribute("errorMessage", e.getMessage());
				return "redirect:/admin/toppings";
			}
		}
		return "admin/toppings/index";
	}

	@PostMapping
	public String create(@RequestParam String name, @RequestParam(required = false) BigDecimal price,
			@RequestParam(required = false) String description,
			@RequestParam(name = "image", required = false) MultipartFile image, RedirectAttributes ra) {
		try {
			Topping t = toppingService.create(name, price, description, image);
			ra.addFlashAttribute("successMessage",
					"Đã thêm topping \"" + t.getName() + "\" (khách thêm vào ly và mua riêng ở danh mục Topping được ngay)");
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/toppings";
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Integer id, @RequestParam String name,
			@RequestParam(required = false) BigDecimal price, @RequestParam(required = false) String description,
			@RequestParam(name = "image", required = false) MultipartFile image, RedirectAttributes ra) {
		try {
			Topping t = toppingService.update(id, name, price, description, image);
			ra.addFlashAttribute("successMessage", "Đã cập nhật topping \"" + t.getName() + "\"");
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
			return "redirect:/admin/toppings?edit=" + id;
		}
		return "redirect:/admin/toppings";
	}

	@PostMapping("/{id}/toggle")
	public String toggle(@PathVariable Integer id, RedirectAttributes ra) {
		try {
			Topping t = toppingService.toggle(id);
			ra.addFlashAttribute("successMessage", "Topping \"" + t.getName() + "\" "
					+ (Boolean.TRUE.equals(t.getIsAvailable()) ? "đã mở bán lại" : "đã ngưng bán toàn chuỗi"));
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/toppings";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Integer id, RedirectAttributes ra) {
		try {
			toppingService.delete(id);
			ra.addFlashAttribute("successMessage", "Đã xoá topping #" + id);
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/toppings";
	}
}
