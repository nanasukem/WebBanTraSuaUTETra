package vn.utetra.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.utetra.dto.response.BranchReviewRes;
import vn.utetra.entity.Branch;
import vn.utetra.service.BranchReviewManagementService;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Controller
public class BranchDetailController {

	@Autowired
	private BranchReviewManagementService reviewService;

	@Autowired
	private vn.utetra.service.BranchService branchService;

	@GetMapping("/branches/{id:\\d+}/reviews-public")
	public String oldReviewsLink(@PathVariable Integer id) {
		return "redirect:/branches/" + id;
	}

	@GetMapping("/branches/{id:\\d+}")
	public String branchDetail(@PathVariable Integer id, Model model) {
		Branch branch = branchService.findOptional(id).orElse(null);
		if (branch == null || branch.getStatus() != vn.utetra.entity.enums.BranchStatus.APPROVED) {
			return "redirect:/branches";
		}

		List<BranchReviewRes> reviews = reviewService.getPublicReviewsByBranch(id);
		Map<String, Object> summary = reviewService.getBranchReviewSummary(id);

		model.addAttribute("branch", branch);
		model.addAttribute("reviews", reviews);
		model.addAttribute("avgRating", summary.get("avgRating"));
		model.addAttribute("totalReviews", summary.get("totalReviews"));
		model.addAttribute("now", LocalTime.now());

		return "branches/detail";
	}
}