package vn.utetra.controller.web;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.dto.request.VoucherReq;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.DiscountType;
import vn.utetra.entity.enums.VoucherApplyTo;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.service.VoucherService;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/admin/vouchers")
public class AdminVoucherController {

	private final VoucherService voucherService;
	private final BranchRepository branchRepository;

	public AdminVoucherController(VoucherService voucherService, BranchRepository branchRepository) {
		this.voucherService = voucherService;
		this.branchRepository = branchRepository;
	}

	@GetMapping
	public String index(Model model) {
		model.addAttribute("vouchers", voucherService.findAllVouchers());
		model.addAttribute("branches", branchRepository.findAll());
		return "admin/vouchers/index";
	}

	@GetMapping("/new")
	public String createForm(Model model) {
		VoucherReq req = new VoucherReq();
		req.setApplyTo(VoucherApplyTo.PRODUCT);
		req.setMinOrderValue(java.math.BigDecimal.ZERO);
		req.setQuantity(100);
		req.setStartDate(LocalDateTime.now().withSecond(0).withNano(0));
		req.setEndDate(LocalDateTime.now().plusDays(7).withSecond(0).withNano(0));

		model.addAttribute("voucherReq", req);
		model.addAttribute("voucherId", null);
		addFormOptions(model);

		return "admin/vouchers/form";
	}

	@PostMapping
	public String create(@Valid @ModelAttribute("voucherReq") VoucherReq req, BindingResult result, Model model,
			RedirectAttributes redirectAttributes) {

		req.setApplyTo(VoucherApplyTo.PRODUCT);

		if (result.hasErrors()) {
			model.addAttribute("voucherId", null);
			addFormOptions(model);
			return "admin/vouchers/form";
		}

		try {
			voucherService.create(req);

			redirectAttributes.addFlashAttribute("successMessage", "Tạo voucher thành công!");
			return "redirect:/admin/vouchers";

		} catch (BusinessException ex) {
			result.reject("voucher.error", ex.getMessage());
			model.addAttribute("voucherId", null);
			addFormOptions(model);
			return "admin/vouchers/form";
		}
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Integer id, Model model) {

		Voucher voucher = voucherService.findById(id);

		model.addAttribute("voucherReq", voucherService.toReq(voucher));
		model.addAttribute("voucherId", id);
		addFormOptions(model);

		return "admin/vouchers/form";
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Integer id, @Valid @ModelAttribute("voucherReq") VoucherReq req,
			BindingResult result, Model model, RedirectAttributes redirectAttributes) {

		voucherService.findById(id);
		req.setApplyTo(VoucherApplyTo.PRODUCT);

		if (result.hasErrors()) {
			model.addAttribute("voucherId", id);
			addFormOptions(model);
			return "admin/vouchers/form";
		}

		try {
			voucherService.update(id, req);

			redirectAttributes.addFlashAttribute("successMessage", "Cập nhật voucher thành công!");
			return "redirect:/admin/vouchers";

		} catch (BusinessException ex) {
			result.reject("voucher.error", ex.getMessage());
			model.addAttribute("voucherId", id);
			addFormOptions(model);
			return "admin/vouchers/form";
		}
	}

	@PostMapping("/{id}/toggle")
	public String toggle(@PathVariable Integer id, RedirectAttributes redirectAttributes) {

		voucherService.toggleActive(id);

		redirectAttributes.addFlashAttribute("successMessage", "Đã thay đổi trạng thái voucher.");
		return "redirect:/admin/vouchers";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Integer id, RedirectAttributes redirectAttributes) {

		Voucher voucher = voucherService.findById(id);

		boolean wasUsed = voucher.getUsedCount() != null && voucher.getUsedCount() > 0;

		voucherService.delete(id);

		redirectAttributes.addFlashAttribute("successMessage",
				wasUsed ? "Voucher đã được sử dụng nên chỉ được tắt." : "Xóa voucher thành công.");

		return "redirect:/admin/vouchers";
	}

	private void addFormOptions(Model model) {
		model.addAttribute("discountTypes", DiscountType.values());
		model.addAttribute("applyToOptions", List.of(VoucherApplyTo.PRODUCT));
		model.addAttribute("branches", branchRepository.findAll());
	}
}
