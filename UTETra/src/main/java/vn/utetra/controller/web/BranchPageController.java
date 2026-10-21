package vn.utetra.controller.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import vn.utetra.entity.Branch;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.repository.BranchRepository;

/**
 * Trang "Chi nhánh" trên header (ai cũng xem được): danh sách chi nhánh ĐANG HOẠT ĐỘNG
 * của chuỗi UTETra - tên, địa chỉ, số điện thoại, chỉ đường Google Maps, sắp xếp theo khoảng cách (JS).
 */
@Controller
public class BranchPageController {

	private final BranchRepository branchRepository;

	public BranchPageController(BranchRepository branchRepository) {
		this.branchRepository = branchRepository;
	}

	@GetMapping("/branches")
	public String branches(Model model) {
		List<Branch> branches = branchRepository.findByStatus(BranchStatus.APPROVED).stream()
				.sorted(Comparator.comparing(Branch::getId)).toList();
		model.addAttribute("branches", branches);
		return "branches";
	}
}
