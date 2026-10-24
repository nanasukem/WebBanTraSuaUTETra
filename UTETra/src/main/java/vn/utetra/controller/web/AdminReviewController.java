package vn.utetra.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.service.BranchReviewManagementService;

@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {

	@Autowired
	private BranchReviewManagementService reviewService;

	@Autowired
	private vn.utetra.service.BranchService branchService;

	@GetMapping
	public String listAllReviews(@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String type, @RequestParam(required = false) Integer rating, Model model) {
		model.addAttribute("branches", branchService.findAll());
		model.addAttribute("selectedBranchId", branchId);
		model.addAttribute("selectedType", type);
		model.addAttribute("selectedRating", rating);
		model.addAttribute("reviews", reviewService.getAllReviewsForAdmin(branchId, type, rating));
		return "admin/reviews/index";
	}

	@PostMapping("/{type}/{id}/delete")
	public String deleteReview(@PathVariable String type, @PathVariable Integer id,
			@RequestParam(required = false) Integer branchId, @RequestParam(required = false) String filterType,
			@RequestParam(required = false) Integer rating, RedirectAttributes ra) {
		try {
			reviewService.deleteReview(type, id);
			ra.addFlashAttribute("successMessage", "Đã xoá đánh giá #" + id + " vì vi phạm tiêu chuẩn cộng đồng.");
		} catch (Exception e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		if (branchId != null) ra.addAttribute("branchId", branchId);
		if (filterType != null && !filterType.isBlank()) ra.addAttribute("type", filterType);
		if (rating != null) ra.addAttribute("rating", rating);
		return "redirect:/admin/reviews";
	}
}