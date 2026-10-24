package vn.utetra.controller.web;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import vn.utetra.entity.Branch;
import vn.utetra.service.BranchReviewManagementService;

@Controller
public class BranchPageController {

	private final vn.utetra.service.BranchService branchService;
	private final BranchReviewManagementService branchReviewManagementService;

	public BranchPageController(vn.utetra.service.BranchService branchService,
			BranchReviewManagementService branchReviewManagementService) {
		this.branchService = branchService;
		this.branchReviewManagementService = branchReviewManagementService;
	}

	@GetMapping("/branches")
	public String branches(Model model) {
		List<Branch> branches = branchService.findApproved().stream()
				.sorted(Comparator.comparing(Branch::getId)).toList();

		Map<Integer, Map<String, Object>> branchReviewSummaries = new HashMap<>();
		for (Branch b : branches) {
			branchReviewSummaries.put(b.getId(), branchReviewManagementService.getBranchReviewSummary(b.getId()));
		}

		model.addAttribute("branches", branches);
		model.addAttribute("branchReviewSummaries", branchReviewSummaries);
		model.addAttribute("now", java.time.LocalTime.now());
		return "branches";
	}
}
