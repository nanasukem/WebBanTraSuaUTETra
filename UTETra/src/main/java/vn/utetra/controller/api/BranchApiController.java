package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.utetra.entity.Branch;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.service.BranchService;
import vn.utetra.service.VendorBranchService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
public class BranchApiController {

	private final BranchService branchService;
	private final VendorBranchService vendorBranchService;

	public BranchApiController(BranchService branchService, VendorBranchService vendorBranchService) {
		this.branchService = branchService;
		this.vendorBranchService = vendorBranchService;
	}

	// GET /api/branches/approved (Danh sách chi nhánh đang hoạt động)
	@GetMapping("/approved")
	public ResponseEntity<List<Branch>> getApprovedBranches() {
		return ResponseEntity.ok(branchService.findApproved());
	}

	// POST /api/branches/{branchId}/toggle-product (Bật/Tắt món theo chi nhánh qua
	// API)
	@PostMapping("/{branchId}/toggle-product")
	public ResponseEntity<Map<String, Object>> toggleProduct(@PathVariable Integer branchId,
			@RequestParam Integer productId, @RequestParam boolean isAvailable) {
		boolean result = vendorBranchService.toggleProductAvailability(branchId, productId, isAvailable);
		return ResponseEntity.ok(Map.of("branchId", branchId, "productId", productId, "isAvailable", result, "message",
				result ? "Món ăn đã được bật bán" : "Món ăn đã chuyển sang tạm hết hàng"));
	}
}