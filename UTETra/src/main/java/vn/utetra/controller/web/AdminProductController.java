package vn.utetra.controller.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;
import vn.utetra.dto.ProductDto;
import vn.utetra.dto.ProductFormDto;
import vn.utetra.entity.Product;
import vn.utetra.service.AdminProductService;

@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

	private final AdminProductService adminProductService;
	private final vn.utetra.service.BranchStockService branchStockService;

	@GetMapping
	public String index(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer categoryId, @RequestParam(required = false) String status,
			Model model) {

		List<Product> all = adminProductService.getAllProducts();
		java.util.Set<Integer> toppingCategoryIds = all.stream().filter(Product::isToppingItem)
				.filter(p -> p.getCategory() != null).map(p -> p.getCategory().getId())
				.collect(java.util.stream.Collectors.toSet());
		List<Product> products = all.stream().filter(p -> !p.isToppingItem()).toList();

		if (keyword != null && !keyword.isBlank()) {
			String search = keyword.trim().toLowerCase();

			products = products.stream().filter(p -> p.getName() != null && p.getName().toLowerCase().contains(search))
					.toList();
		}

		if (categoryId != null) {
			products = products.stream()
					.filter(p -> p.getCategory() != null && categoryId.equals(p.getCategory().getId())).toList();
		}

		if ("available".equals(status)) {
			products = products.stream().filter(p -> Boolean.TRUE.equals(p.getIsAvailable())).toList();
		} else if ("unavailable".equals(status)) {
			products = products.stream().filter(p -> !Boolean.TRUE.equals(p.getIsAvailable())).toList();
		}

		model.addAttribute("products", products);
		model.addAttribute("categories", adminProductService.getAllCategories().stream()
				.filter(c -> !toppingCategoryIds.contains(c.getId())).toList());
		model.addAttribute("keyword", keyword);

		model.addAttribute("outAt", branchStockService.unavailableBranchNamesByProduct());
		model.addAttribute("categoryId", categoryId);
		model.addAttribute("status", status);

		return "admin/products/index";
	}

	@GetMapping("/create")
	public String createForm(Model model) {
		model.addAttribute("form", new ProductFormDto());
		model.addAttribute("categories", adminProductService.getAllCategories());
		model.addAttribute("isEdit", false);

		return "admin/products/form";
	}

	@PostMapping("/create")
	public String create(@ModelAttribute("form") ProductFormDto form, Model model,
			RedirectAttributes redirectAttributes) {

		try {
			ProductDto dto = toProductDto(form);
			dto.setIsAvailable(true);

			adminProductService.createProduct(dto);

			redirectAttributes.addFlashAttribute("successMessage", "Thêm sản phẩm thành công.");

			return "redirect:/admin/products";

		} catch (Exception e) {
			model.addAttribute("errorMessage", getErrorMessage(e, "Không thể thêm sản phẩm."));
			model.addAttribute("form", form);
			model.addAttribute("categories", adminProductService.getAllCategories());
			model.addAttribute("isEdit", false);

			return "admin/products/form";
		}
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Integer id, Model model, RedirectAttributes redirectAttributes) {

		try {
			Product product = adminProductService.getProductById(id);

			ProductFormDto form = new ProductFormDto();
			form.setId(product.getId());
			form.setName(product.getName());
			form.setDescription(product.getDescription());
			form.setBasePrice(product.getBasePrice());

			if (product.getCategory() != null) {
				form.setCategoryId(product.getCategory().getId());
			}

			model.addAttribute("form", form);
			model.addAttribute("product", product);
			model.addAttribute("categories", adminProductService.getAllCategories());
			model.addAttribute("isEdit", true);

			return "admin/products/form";

		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage", getErrorMessage(e, "Không tìm thấy sản phẩm."));

			return "redirect:/admin/products";
		}
	}

	@PostMapping("/{id}/edit")
	public String update(@PathVariable Integer id, @ModelAttribute("form") ProductFormDto form, Model model,
			RedirectAttributes redirectAttributes) {

		try {
			ProductDto dto = toProductDto(form);
			dto.setId(id);

			dto.setIsAvailable(null);

			adminProductService.updateProduct(id, dto);

			redirectAttributes.addFlashAttribute("successMessage", "Cập nhật sản phẩm thành công.");

			return "redirect:/admin/products";

		} catch (Exception e) {
			model.addAttribute("errorMessage", getErrorMessage(e, "Không thể cập nhật sản phẩm."));
			model.addAttribute("form", form);
			model.addAttribute("product", safeGetProduct(id));
			model.addAttribute("categories", adminProductService.getAllCategories());
			model.addAttribute("isEdit", true);

			return "admin/products/form";
		}
	}

	@PostMapping("/{id}/availability")
	public String toggleAvailability(@PathVariable Integer id, RedirectAttributes redirectAttributes) {

		try {
			Product product = adminProductService.getProductById(id);

			ProductDto dto = ProductDto.builder().id(product.getId()).name(product.getName())
					.description(product.getDescription()).basePrice(product.getBasePrice())
					.categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
					.isAvailable(!Boolean.TRUE.equals(product.getIsAvailable())).build();

			adminProductService.updateProduct(id, dto);

			redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật trạng thái sản phẩm.");

		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage",
					getErrorMessage(e, "Không thể đổi trạng thái sản phẩm."));
		}

		return "redirect:/admin/products";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Integer id, RedirectAttributes redirectAttributes) {

		try {
			adminProductService.deleteProduct(id);

			redirectAttributes.addFlashAttribute("successMessage", "Xóa sản phẩm thành công.");

		} catch (Exception e) {
			redirectAttributes.addFlashAttribute("errorMessage",
					getErrorMessage(e, "Không thể xóa sản phẩm. " + "Sản phẩm có thể đang được sử dụng."));
		}

		return "redirect:/admin/products";
	}

	private ProductDto toProductDto(ProductFormDto form) {
		if (form.getName() == null || form.getName().isBlank()) {
			throw new IllegalArgumentException("Tên sản phẩm không được để trống.");
		}

		if (form.getName().trim().length() > 150) {
			throw new IllegalArgumentException("Tên sản phẩm không được vượt quá 150 ký tự.");
		}

		if (form.getBasePrice() == null || form.getBasePrice().signum() <= 0) {
			throw new IllegalArgumentException("Giá sản phẩm phải lớn hơn 0.");
		}

		if (form.getCategoryId() == null) {
			throw new IllegalArgumentException("Vui lòng chọn danh mục sản phẩm.");
		}

		return ProductDto.builder().id(form.getId()).name(form.getName().trim()).description(form.getDescription())
				.basePrice(form.getBasePrice()).categoryId(form.getCategoryId()).imageFile(form.getImageFile()).build();
	}

	private Product safeGetProduct(Integer id) {
		try {
			return adminProductService.getProductById(id);
		} catch (Exception e) {
			return null;
		}
	}

	private String getErrorMessage(Exception e, String fallback) {
		if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
			return e.getMessage();
		}

		org.slf4j.LoggerFactory.getLogger(AdminProductController.class).error("Lỗi quản lý sản phẩm", e);
		return fallback;
	}
}
