package vn.utetra.controller.web;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import vn.utetra.entity.Branch;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.repository.BranchRepository;
import vn.utetra.service.BranchReviewManagementService;

/**
 * Trang "Chi nhánh" trên header (ai cũng xem được): danh sách chi nhánh ĐANG HOẠT ĐỘNG
 * của chuỗi UTETra - tên, số sao đánh giá, địa chỉ, số điện thoại, chỉ đường Google Maps, sắp xếp theo khoảng cách (JS).
 */
@Controller
public class BranchPageController {

	private final BranchRepository branchRepository;
	private final BranchReviewManagementService branchReviewManagementService;

	public BranchPageController(BranchRepository branchRepository,
			BranchReviewManagementService branchReviewManagementService) {
		this.branchRepository = branchRepository;
		this.branchReviewManagementService = branchReviewManagementService;
	}

	@GetMapping("/branches")
	public String branches(Model model) {
		List<Branch> branches = branchRepository.findByStatus(BranchStatus.APPROVED).stream()
				.sorted(Comparator.comparing(Branch::getId)).toList();

		Map<Integer, Map<String, Object>> branchReviewSummaries = new HashMap<>();
		for (Branch b : branches) {
			branchReviewSummaries.put(b.getId(), branchReviewManagementService.getBranchReviewSummary(b.getId()));
		}

		model.addAttribute("branches", branches);
		model.addAttribute("branchReviewSummaries", branchReviewSummaries);
		model.addAttribute("now", java.time.LocalTime.now()); // để hiện Đang mở cửa / Đã đóng cửa
		return "branches";
	}
}

