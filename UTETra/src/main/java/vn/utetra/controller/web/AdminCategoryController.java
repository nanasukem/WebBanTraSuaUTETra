
package vn.utetra.controller.web;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.dto.CategoryFormDto;
import vn.utetra.entity.Category;
import vn.utetra.service.AdminProductService;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

	private final AdminProductService adminProductService;

	@GetMapping
	public String index(@RequestParam(required = false) String keyword, Model model) {

		List<Category> categories = adminProductService.getAllCategories();

		if (keyword != null && !keyword.isBlank()) {
			String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);

			categories = categories.stream()
					.filter(category -> category.getName() != null
							&& category.getName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
					.collect(Collectors.toList());
		}

		model.addAttribute("categories", categories);
		model.addAttribute("keyword", keyword == null ? "" : keyword);

		return "admin/categories/index";
	}

	@GetMapping("/create")
	public String createForm(Model model) {

		model.addAttribute("form", new CategoryFormDto());
		model.addAttribute("isEdit", false);

		return "admin/categories/form";
	}

	@PostMapping("/create")
	public String create(@ModelAttribute("form") CategoryFormDto form, Model model,
			RedirectAttributes redirectAttributes) {

		try {
			form.setId(null);

			adminProductService.saveCategory(form);

			redirectAttributes.addFlashAttribute("successMessage", "Thêm danh mục thành công.");

			return "redirect:/admin/categories";

		} catch (IllegalArgumentException | IllegalStateException e) {

			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("isEdit", false);

			return "admin/categories/form";

		} catch (IOException e) {

			model.addAttribute("errorMessage", "Không thể tải ảnh danh mục lên Cloudinary.");

			model.addAttribute("isEdit", false);

			return "admin/categories/form";
		}
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Integer id, Model model) {

		Category category = adminProductService.getCategoryById(id);

		CategoryFormDto form = new CategoryFormDto();
		form.setId(category.getId());
		form.setName(category.getName());
		form.setExistingImageUrl(category.getImageCloudinaryId());

		model.addAttribute("form", form);
		model.addAttribute("isEdit", true);

		return "admin/categories/form";
	}

	@PostMapping("/{id}/edit")
	public String update(@PathVariable Integer id, @ModelAttribute("form") CategoryFormDto form, Model model,
			RedirectAttributes redirectAttributes) {

		try {
			form.setId(id);

			adminProductService.saveCategory(form);

			redirectAttributes.addFlashAttribute("successMessage", "Cập nhật danh mục thành công.");

			return "redirect:/admin/categories";

		} catch (IllegalArgumentException | IllegalStateException e) {

			model.addAttribute("errorMessage", e.getMessage());
			model.addAttribute("isEdit", true);
			restoreExistingImage(form, id);

			return "admin/categories/form";

		} catch (IOException e) {

			model.addAttribute("errorMessage", "Không thể tải ảnh danh mục lên Cloudinary.");

			model.addAttribute("isEdit", true);
			restoreExistingImage(form, id);

			return "admin/categories/form";
		}
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Integer id, RedirectAttributes redirectAttributes) {

		try {
			adminProductService.deleteCategory(id);

			redirectAttributes.addFlashAttribute("successMessage", "Đã xóa danh mục thành công.");

		} catch (Exception e) {

			redirectAttributes.addFlashAttribute("errorMessage",
					"Không thể xóa danh mục. " + "Hãy kiểm tra xem danh mục có sản phẩm " + "đang sử dụng hay không.");
		}

		return "redirect:/admin/categories";
	}

	private void restoreExistingImage(CategoryFormDto form, Integer id) {

		try {
			Category category = adminProductService.getCategoryById(id);

			form.setExistingImageUrl(category.getImageCloudinaryId());

		} catch (Exception ignored) {
		}
	}
}
