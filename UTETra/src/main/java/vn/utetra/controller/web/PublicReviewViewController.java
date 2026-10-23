package vn.utetra.controller.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import vn.utetra.dto.response.BranchReviewRes;
import vn.utetra.entity.Branch;
import vn.utetra.repository.BranchRepository;
import vn.utetra.service.BranchReviewManagementService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
public class PublicReviewViewController {

	@Autowired
	private BranchReviewManagementService reviewService;

	@Autowired
	private BranchRepository branchRepository;

	@GetMapping("/branches/{id}/reviews-public")
	public String viewBranchReviewsPublic(@PathVariable Integer id, Model model) {
		Branch branch = branchRepository.findById(id).orElse(null);
		if (branch == null) {
			return "redirect:/branches";
		}

		// CHỈ LẤY ĐÁNH GIÁ QUÁN CHO CHI NHÁNH NÀY
		List<BranchReviewRes> reviews = reviewService.getPublicReviewsByBranch(id);
		Map<String, Object> summary = reviewService.getBranchReviewSummary(id);

		model.addAttribute("branch", branch);
		model.addAttribute("reviews", reviews);
		model.addAttribute("avgRating", summary.get("avgRating"));
		model.addAttribute("totalReviews", summary.get("totalReviews"));
		model.addAttribute("now", LocalDateTime.now());

		return "branches/public-reviews";
	}
}