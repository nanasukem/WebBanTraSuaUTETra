package vn.utetra.controller.web;

import vn.utetra.dto.request.BranchReq;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.service.BranchService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Quản lý chi nhánh dành cho ADMIN.
 *
 * Chức năng: - Danh sách, tìm kiếm, lọc chi nhánh. - Xem chi tiết chi nhánh. -
 * Thêm chi nhánh. - Sửa thông tin chi nhánh. - Tạm ngưng hoặc mở lại chi nhánh.
 */
@Controller
@RequestMapping("/admin/branches")
public class AdminBranchController {

	private final BranchService branchService;

	public AdminBranchController(BranchService branchService) {
		this.branchService = branchService;
	}

	/**
	 * Danh sách chi nhánh, tìm kiếm và lọc trạng thái. URL: GET /admin/branches
	 */
	@GetMapping
	public String list(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) BranchStatus status, Model model) {

		model.addAttribute("branches", branchService.search(keyword, status));
		model.addAttribute("keyword", keyword);
		model.addAttribute("status", status);
		model.addAttribute("statuses", BranchStatus.values());

		return "admin/branches/index";
	}

	/**
	 * Xem chi tiết chi nhánh. URL: GET /admin/branches/{id}
	 *
	 * Trang chi tiết sử dụng đối tượng Branch để hiển thị: - Ảnh bìa Cloudinary do
	 * Manager cập nhật. - Mô tả chi nhánh do Manager cập nhật. - Thông tin cơ bản
	 * do Admin quản lý.
	 */
	@GetMapping("/{id}")
	public String detail(@PathVariable Integer id, Model model) {

		model.addAttribute("branch", branchService.findById(id));

		return "admin/branches/detail";
	}

	/**
	 * Hiển thị form thêm chi nhánh. URL: GET /admin/branches/create
	 */
	@GetMapping("/create")
	public String createForm(Model model) {
		model.addAttribute("branchReq", new BranchReq());

		return formView(model, null);
	}

	/**
	 * Xử lý thêm chi nhánh. URL: POST /admin/branches/create
	 */
	@PostMapping("/create")
	public String create(@Valid @ModelAttribute("branchReq") BranchReq req, BindingResult result, Model model,
			RedirectAttributes ra) {

		if (result.hasErrors()) {
			return formView(model, null);
		}

		try {
			branchService.create(req);

			ra.addFlashAttribute("success", "Đã thêm chi nhánh " + req.getName());

			return "redirect:/admin/branches";

		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());

			return formView(model, null);
		}
	}

	/**
	 * Hiển thị form sửa chi nhánh. URL: GET /admin/branches/{id}/edit
	 */
	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Integer id, Model model) {

		model.addAttribute("branchReq", branchService.toReq(branchService.findById(id)));

		return formView(model, id);
	}

	/**
	 * Xử lý cập nhật thông tin chi nhánh. URL: POST /admin/branches/{id}/edit
	 */
	@PostMapping("/{id}/edit")
	public String update(@PathVariable Integer id, @Valid @ModelAttribute("branchReq") BranchReq req,
			BindingResult result, Model model, RedirectAttributes ra) {

		if (result.hasErrors()) {
			return formView(model, id);
		}

		try {
			branchService.update(id, req);

			ra.addFlashAttribute("success", "Đã cập nhật chi nhánh " + req.getName());

			return "redirect:/admin/branches";

		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());

			return formView(model, id);
		}
	}

	/**
	 * Thay đổi trạng thái chi nhánh. URL: POST
	 * /admin/branches/{id}/status?value=APPROVED URL: POST
	 * /admin/branches/{id}/status?value=SUSPENDED
	 */
	@PostMapping("/{id}/status")
	public String changeStatus(@PathVariable Integer id, @RequestParam BranchStatus value, RedirectAttributes ra) {

		try {
			branchService.changeStatus(id, value);

			ra.addFlashAttribute("success", "Đã chuyển chi nhánh #" + id + " sang " + value.getLabel());

		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}

		return "redirect:/admin/branches";
	}

	/**
	 * Chuẩn bị dữ liệu dùng chung cho form thêm và sửa.
	 */
	private String formView(Model model, Integer id) {
		model.addAttribute("branchId", id);
		model.addAttribute("managers", branchService.findManagers());

		return "admin/branches/form";
	}
}