package vn.utetra.controller.web;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.utetra.exception.BusinessException;
import vn.utetra.service.ShippingFeeService;

@Controller
@RequestMapping("/admin/shipping-fee")
public class AdminShippingFeeController {

	private final ShippingFeeService shippingFeeService;
	private final vn.utetra.service.ReportService reportService;
	private final vn.utetra.service.BranchService branchService;

	public AdminShippingFeeController(ShippingFeeService shippingFeeService,
			vn.utetra.service.ReportService reportService, vn.utetra.service.BranchService branchService) {
		this.shippingFeeService = shippingFeeService;
		this.reportService = reportService;
		this.branchService = branchService;
	}

	@GetMapping
	public String index(@RequestParam(required = false) String period, @RequestParam(required = false) String month,
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
			@RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
			@RequestParam(required = false) Integer branchId, @RequestParam(defaultValue = "0") int page, Model model) {
		model.addAttribute("baseKm", ShippingFeeService.BASE_KM);
		model.addAttribute("baseFee", shippingFeeService.baseFee());
		model.addAttribute("perKmFee", shippingFeeService.perKmFee());
		model.addAttribute("examples", java.util.List.of(2, 3, 5, 8, 12).stream()
				.map(km -> new Object[] { km, shippingFeeService.feeFor(BigDecimal.valueOf(km)) }).toList());
		java.time.LocalDate today = java.time.LocalDate.now();
		vn.utetra.dto.DateRange range = vn.utetra.dto.DateRange.resolve(period, month, year, from, to, "MONTH");
		model.addAttribute("range", range);
		model.addAttribute("years", vn.utetra.dto.DateRange.recentYears(5));
		model.addAttribute("branchId", branchId);
		model.addAttribute("branches", branchService.findAll());
		model.addAttribute("todayStat", reportService.summary(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), branchId));
		model.addAttribute("rangeStat", reportService.summary(range.start(), range.end(), branchId));
		model.addAttribute("reportFrom", range.from() != null ? range.from() : java.time.LocalDate.of(2026, 1, 1));
		model.addAttribute("reportTo", range.to() != null && range.to().isBefore(today) ? range.to() : today);
		model.addAttribute("shipped", reportService.shippedBetween(range.start(), range.end(), branchId, page, 15));
		model.addAttribute("pageQuery", range.query() + (branchId != null ? "&branchId=" + branchId : ""));
		return "admin/shipping-fee";
	}

	@PostMapping
	public String update(@RequestParam(required = false) BigDecimal baseFee,
			@RequestParam(required = false) BigDecimal perKmFee, RedirectAttributes ra) {
		try {
			shippingFeeService.update(baseFee, perKmFee);
			ra.addFlashAttribute("successMessage", "Đã lưu phí giao hàng (áp dụng cho đơn đặt từ bây giờ)");
		} catch (BusinessException e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/shipping-fee";
	}
}
