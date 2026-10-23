package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.request.ReplyReviewReq;
import vn.utetra.entity.User;
import vn.utetra.repository.BranchRepository;
import vn.utetra.service.BranchReviewManagementService;
import vn.utetra.service.CurrentUserService;

import java.security.Principal;

@Controller
@RequestMapping("/admin/reviews")
public class AdminReviewController {

	@Autowired
	private BranchReviewManagementService reviewService;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private CurrentUserService currentUserService;

	@GetMapping
	public String listAllReviews(@RequestParam(required = false) Integer branchId,
			@RequestParam(required = false) String type, @RequestParam(required = false) Integer rating, Model model) {
		model.addAttribute("branches", branchRepository.findAll());
		model.addAttribute("selectedBranchId", branchId);
		model.addAttribute("selectedType", type);
		model.addAttribute("selectedRating", rating);
		model.addAttribute("reviews", reviewService.getAllReviewsForAdmin(branchId, type, rating));
		return "admin/reviews/index";
	}

	@PostMapping("/reply")
	public String replyReview(@ModelAttribute ReplyReviewReq req, Principal principal, HttpSession session,
			RedirectAttributes ra) {
		try {
			User admin = currentUserService.requireUser(principal, session);
			reviewService.replyToReview(req, admin);
			ra.addFlashAttribute("successMessage", "Phản hồi đánh giá thành công!");
		} catch (Exception e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/admin/reviews";
	}
}