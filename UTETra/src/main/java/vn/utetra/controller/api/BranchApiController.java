package vn.utetra.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.response.NearbyBranchRes;
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

	/**
	 * GET /api/branches/approved - danh sách chi nhánh đang hoạt động.
	 * Trả DTO (id, tên, địa chỉ, SĐT, tọa độ), KHÔNG trả entity Branch để không lộ thông tin chủ shop
	 * và không bị lỗi JSON với owner LAZY.
	 */
	@GetMapping("/approved")
	public List<NearbyBranchRes> getApprovedBranches() {
		return branchService.findNearby(null, null);
	}

	/** GET /api/branches/nearby?lat=10.85&lng=106.77 -> chi nhánh từ gần đến xa (không gửi lat/lng thì không sắp) */
	@GetMapping("/nearby")
	public List<NearbyBranchRes> nearby(@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng) {
		return branchService.findNearby(lat, lng);
	}

	/**
	 * POST /api/branches/{branchId}/toggle-product - bật/tắt món tại chi nhánh.
	 * Chỉ CHỦ chi nhánh (Vendor) hoặc ADMIN được gọi.
	 */
	@PostMapping("/{branchId}/toggle-product")
	public ResponseEntity<Map<String, Object>> toggleProduct(@PathVariable Integer branchId,
			@RequestParam Integer productId, @RequestParam boolean isAvailable, Authentication auth) {
		if (!canManageBranch(auth, branchId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("message", "Bạn không có quyền quản lý món của chi nhánh này"));
		}
		boolean result = vendorBranchService.toggleProductAvailability(branchId, productId, isAvailable);
		return ResponseEntity.ok(Map.of("branchId", branchId, "productId", productId, "isAvailable", result, "message",
				result ? "Món ăn đã được bật bán" : "Món ăn đã chuyển sang tạm hết hàng"));
	}

	/** ADMIN: mọi chi nhánh. VENDOR: chỉ chi nhánh mình làm chủ. Còn lại: không */
	private boolean canManageBranch(Authentication auth, Integer branchId) {
		if (auth == null || !auth.isAuthenticated()) return false;
		if (hasRole(auth, "ROLE_ADMIN")) return true;
		return hasRole(auth, "ROLE_VENDOR") && branchService.isOwner(branchId, auth.getName());
	}

	private boolean hasRole(Authentication auth, String role) {
		return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
	}
}