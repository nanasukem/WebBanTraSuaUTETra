package vn.utetra.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.utetra.service.ProductService;

import java.math.BigDecimal;

@Controller
public class HomeController {

	@Autowired
	private ProductService productService;

	@GetMapping({ "/", "/menu" })
	public String viewMenu(Model model, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer categoryId, @RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice) {

		model.addAttribute("categories", productService.getAllCategories());
		model.addAttribute("products", productService.filterProducts(keyword, categoryId, minPrice, maxPrice));
		model.addAttribute("selectedCategory", categoryId);
		model.addAttribute("keyword", keyword);

		return "admin/products/menu";
	}
}