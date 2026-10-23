package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.utetra.dto.request.ReplyReviewReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.User;
import vn.utetra.service.BranchReviewManagementService;
import vn.utetra.service.CurrentUserService;

import java.security.Principal;

@Controller
@RequestMapping("/manager/reviews")
public class ManagerReviewController {

	@Autowired
	private BranchReviewManagementService reviewService;

	@Autowired
	private CurrentUserService currentUserService;

	@GetMapping
	public String listBranchReviews(@RequestParam(required = false) String type,
			@RequestParam(required = false) Integer rating, Principal principal, HttpSession session, Model model) {
		User manager = currentUserService.requireUser(principal, session);
		Branch branch = reviewService.getManagedBranch(manager);
		model.addAttribute("branch", branch);
		model.addAttribute("selectedType", type);
		model.addAttribute("selectedRating", rating);
		model.addAttribute("reviews", reviewService.getReviewsForManager(manager, type, rating));
		return "manager/reviews/index";
	}

	@PostMapping("/reply")
	public String replyBranchReview(@ModelAttribute ReplyReviewReq req, Principal principal, HttpSession session,
			RedirectAttributes ra) {
		try {
			User manager = currentUserService.requireUser(principal, session);
			reviewService.replyToReview(req, manager);
			ra.addFlashAttribute("successMessage", "Phản hồi đánh giá thành công!");
		} catch (Exception e) {
			ra.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/manager/reviews";
	}
}