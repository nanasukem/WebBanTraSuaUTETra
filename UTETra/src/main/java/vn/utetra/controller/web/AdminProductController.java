package vn.utetra.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.request.ProductRequest;
import vn.utetra.service.AdminProductService;
import vn.utetra.repository.CategoryRepository;

@Controller
@RequestMapping("/admin/products")
public class AdminProductController {

	private final AdminProductService adminProductService;
	private final CategoryRepository categoryRepository;

	public AdminProductController(AdminProductService adminProductService, CategoryRepository categoryRepository) {
		this.adminProductService = adminProductService;
		this.categoryRepository = categoryRepository;
	}

	@GetMapping
	public String listProducts(Model model) {
		model.addAttribute("products", adminProductService.getAllProducts());
		return "admin/products/list";
	}

	@GetMapping("/create")
	public String showCreateForm(Model model) {
		model.addAttribute("productRequest", new ProductRequest());
		model.addAttribute("categories", categoryRepository.findAll());
		return "admin/products/form";
	}

	@PostMapping("/create")
	public String createProduct(@ModelAttribute ProductRequest productRequest, RedirectAttributes redirectAttributes) {
		try {
			adminProductService.createProduct(productRequest);
			redirectAttributes.addFlashAttribute("successMsg", "Thêm sản phẩm vào Menu thành công!");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
		}
		return "redirect:/admin/products";
	}

	@GetMapping("/edit/{id}")
	public String showEditForm(@PathVariable Integer id, Model model) {
		var product = adminProductService.getProductById(id);
		ProductRequest request = new ProductRequest();
		request.setName(product.getName());
		request.setBasePrice(product.getBasePrice());
		request.setDescription(product.getDescription());
		request.setCategoryId(product.getCategory().getId());
		request.setIsAvailable(product.getIsAvailable());

		model.addAttribute("productRequest", request);
		model.addAttribute("productId", id);
		model.addAttribute("currentImage", product.getImageCloudinaryId());
		model.addAttribute("categories", categoryRepository.findAll());
		return "admin/products/form";
	}

	@PostMapping("/edit/{id}")
	public String updateProduct(@PathVariable Integer id, @ModelAttribute ProductRequest productRequest,
			RedirectAttributes redirectAttributes) {
		try {
			adminProductService.updateProduct(id, productRequest);
			redirectAttributes.addFlashAttribute("successMsg", "Cập nhật sản phẩm thành công!");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
		}
		return "redirect:/admin/products";
	}

	@GetMapping("/delete/{id}")
	public String deleteProduct(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
		try {
			adminProductService.deleteProduct(id);
			redirectAttributes.addFlashAttribute("successMsg", "Xóa sản phẩm thành công!");
		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMsg", "Lỗi: " + e.getMessage());
		}
		return "redirect:/admin/products";
	}
}