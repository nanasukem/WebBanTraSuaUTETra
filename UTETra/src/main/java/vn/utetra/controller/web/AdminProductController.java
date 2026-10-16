package vn.utetra.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.utetra.dto.ProductFormDto;
import vn.utetra.service.AdminProductService;

@Controller
@RequestMapping("/admin")
public class AdminProductController {

	private final AdminProductService adminProductService;

	public AdminProductController(AdminProductService adminProductService) {
		this.adminProductService = adminProductService;
	}

	@GetMapping("/products")
	public String listProducts(Model model) {
		model.addAttribute("products", adminProductService.getAllProducts());
		model.addAttribute("categories", adminProductService.getAllCategories());
		return "admin/products/index";
	}

	@PostMapping("/products/save")
	public String saveProduct(@ModelAttribute ProductFormDto form) {
		adminProductService.saveProduct(form);
		return "redirect:/admin/products";
	}

	@GetMapping("/products/delete/{id}")
	public String deleteProduct(@PathVariable Integer id) {
		adminProductService.deleteProduct(id);
		return "redirect:/admin/products";
	}

	@GetMapping("/categories")
	public String listCategories(Model model) {
		model.addAttribute("categories", adminProductService.getAllCategories());
		return "admin/categories/index";
	}

	@PostMapping("/categories/save")
	public String saveCategory(@RequestParam String name, @RequestParam MultipartFile imageFile) {
		adminProductService.saveCategory(name, imageFile);
		return "redirect:/admin/categories";
	}
}