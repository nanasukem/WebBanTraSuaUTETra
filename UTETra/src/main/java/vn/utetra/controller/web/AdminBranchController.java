package vn.utetra.controller.web;

import vn.utetra.dto.request.BranchReq;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.service.BranchService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Quản lý chi nhánh cho ADMIN / MANAGER */
@Controller
@RequestMapping("/admin/branches")
public class AdminBranchController {

	private final BranchService branchService;

	public AdminBranchController(BranchService branchService) {
		this.branchService = branchService;
	}

	// Danh sách + tìm kiếm + lọc trạng thái
	@GetMapping
	public String list(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) BranchStatus status, Model model) {
		model.addAttribute("branches", branchService.search(keyword, status));
		model.addAttribute("keyword", keyword);
		model.addAttribute("status", status);
		model.addAttribute("statuses", BranchStatus.values());
		return "admin/branches/index";
	}

	// Form thêm
	@GetMapping("/create")
	public String createForm(Model model) {
		model.addAttribute("branchReq", new BranchReq());
		return formView(model, null);
	}

	@PostMapping("/create")
	public String create(@Valid @ModelAttribute("branchReq") BranchReq req, BindingResult result, Model model,
			RedirectAttributes ra) {
		if (result.hasErrors())
			return formView(model, null);
		try {
			branchService.create(req);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return formView(model, null);
		}
		ra.addFlashAttribute("success", "Đã thêm chi nhánh " + req.getName());
		return "redirect:/admin/branches";
	}

	// Form sửa
	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Integer id, Model model) {
		model.addAttribute("branchReq", branchService.toReq(branchService.findById(id)));
		return formView(model, id);
	}

	@PostMapping("/{id}/edit")
	public String update(@PathVariable Integer id, @Valid @ModelAttribute("branchReq") BranchReq req,
			BindingResult result, Model model, RedirectAttributes ra) {
		if (result.hasErrors())
			return formView(model, id);
		try {
			branchService.update(id, req);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return formView(model, id);
		}
		ra.addFlashAttribute("success", "Đã cập nhật chi nhánh " + req.getName());
		return "redirect:/admin/branches";
	}

	// Duyệt / từ chối / tạm ngưng / mở lại
	@PostMapping("/{id}/status")
	public String changeStatus(@PathVariable Integer id, @RequestParam BranchStatus value, RedirectAttributes ra) {
		try {
			branchService.changeStatus(id, value);
			ra.addFlashAttribute("success", "Đã chuyển trạng thái chi nhánh #" + id + " sang " + value);
		} catch (BusinessException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/branches";
	}

	private String formView(Model model, Integer id) {
		model.addAttribute("branchId", id);
		model.addAttribute("vendors", branchService.findVendors());
		return "admin/branches/form";
	}
}