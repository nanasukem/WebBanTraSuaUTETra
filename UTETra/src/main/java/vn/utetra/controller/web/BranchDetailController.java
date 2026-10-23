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

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Controller
public class BranchDetailController {

	@Autowired
	private BranchReviewManagementService reviewService;

	@Autowired
	private BranchRepository branchRepository;

	/** Link cũ /branches/{id}/reviews-public -> chuyển về trang chủ chi nhánh */
	@GetMapping("/branches/{id:\\d+}/reviews-public")
	public String oldReviewsLink(@PathVariable Integer id) {
		return "redirect:/branches/" + id;
	}

	/**
	 * TRANG CHỦ CHI NHÁNH phía khách: /branches/{id}
	 * Ảnh bìa, mô tả (Manager cập nhật), địa chỉ, SĐT, giờ mở cửa (Admin quản lý), chỉ đường + đánh giá quán.
	 * Chi nhánh không tồn tại hoặc đang tạm ngưng -> về danh sách chi nhánh.
	 */
	@GetMapping("/branches/{id:\\d+}")
	public String branchDetail(@PathVariable Integer id, Model model) {
		Branch branch = branchRepository.findById(id).orElse(null);
		if (branch == null || branch.getStatus() != vn.utetra.entity.enums.BranchStatus.APPROVED) {
			return "redirect:/branches";
		}

		// CHỈ LẤY ĐÁNH GIÁ QUÁN CHO CHI NHÁNH NÀY
		List<BranchReviewRes> reviews = reviewService.getPublicReviewsByBranch(id);
		Map<String, Object> summary = reviewService.getBranchReviewSummary(id);

		model.addAttribute("branch", branch);
		model.addAttribute("reviews", reviews);
		model.addAttribute("avgRating", summary.get("avgRating"));
		model.addAttribute("totalReviews", summary.get("totalReviews"));
		model.addAttribute("now", LocalTime.now()); // Đang mở cửa / Đã đóng cửa

		return "branches/detail";
	}
}