package vn.utetra.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.dto.CategoryFormDto;
import vn.utetra.dto.ProductDto;
import vn.utetra.service.AdminProductService;

@Controller
@RequestMapping("/admin")
public class AdminProductController {

	private final AdminProductService adminProductService;

	public AdminProductController(AdminProductService adminProductService) {
		this.adminProductService = adminProductService;
	}

	// ==================== QUAN LY SAN PHAM ====================

	@GetMapping("/products")
	public String listProducts(Model model) {
		model.addAttribute("products", adminProductService.getAllProducts());

		model.addAttribute("categories", adminProductService.getAllCategories());

		model.addAttribute("productDTO", new ProductDto());

		return "admin/products/menu";
	}

	@PostMapping("/products/save")
	public String saveProduct(@ModelAttribute("productDTO") ProductDto form, RedirectAttributes redirectAttributes) {

		try {
			if (form.getId() == null) {
				adminProductService.createProduct(form);

				redirectAttributes.addFlashAttribute("successMsg", "Thêm sản phẩm mới thành công!");
			} else {
				adminProductService.updateProduct(form.getId(), form);

				redirectAttributes.addFlashAttribute("successMsg", "Cập nhật sản phẩm thành công!");
			}
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMsg", "Có lỗi xảy ra: " + e.getMessage());
		}

		return "redirect:/admin/products";
	}

	@PostMapping("/products/delete/{id}")
	public String deleteProduct(@PathVariable Integer id, RedirectAttributes redirectAttributes) {

		try {
			adminProductService.deleteProduct(id);

			redirectAttributes.addFlashAttribute("successMsg", "Đã xóa sản phẩm thành công!");

		} catch (Exception e) {
			e.printStackTrace();

			redirectAttributes.addFlashAttribute("errorMsg", "Lỗi khi xóa sản phẩm: " + e.getMessage());
		}

		return "redirect:/admin/products";
	}

	// ==================== QUAN LY DANH MUC ====================

	@GetMapping("/categories")
	public String listCategories(Model model) {
		model.addAttribute("categories", adminProductService.getAllCategories());

		model.addAttribute("categoryDTO", new CategoryFormDto());

		return "categories/admin/index";
	}

	@PostMapping("/categories/save")
	public String saveCategory(@ModelAttribute("categoryDTO") CategoryFormDto categoryDTO,
			RedirectAttributes redirectAttributes) {

		try {
			adminProductService.saveCategory(categoryDTO);

			redirectAttributes.addFlashAttribute("successMsg", "Lưu danh mục thành công!");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMsg", "Lỗi khi lưu danh mục: " + e.getMessage());
		}

		return "redirect:/admin/categories";
	}

	@PostMapping("/categories/delete/{id}")
	public String deleteCategory(@PathVariable Integer id, RedirectAttributes redirectAttributes) {

		try {
			adminProductService.deleteCategory(id);

			redirectAttributes.addFlashAttribute("successMsg", "Đã xóa danh mục thành công!");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMsg", "Không thể xóa danh mục đang có sản phẩm!");
		}

		return "redirect:/admin/categories";
	}
}