package vn.utetra.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.response.NearbyBranchRes;
import vn.utetra.service.BranchService;
import vn.utetra.service.ManagerBranchService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
public class BranchApiController {

	private final BranchService branchService;
	private final ManagerBranchService managerBranchService;

	public BranchApiController(BranchService branchService, ManagerBranchService managerBranchService) {
		this.branchService = branchService;
		this.managerBranchService = managerBranchService;
	}

	@GetMapping("/approved")
	public List<NearbyBranchRes> getApprovedBranches() {
		return branchService.findNearby(null, null);
	}

	@GetMapping("/nearby")
	public List<NearbyBranchRes> nearby(@RequestParam(required = false) Double lat,
			@RequestParam(required = false) Double lng) {
		return branchService.findNearby(lat, lng);
	}

	@PostMapping("/{branchId}/toggle-product")
	public ResponseEntity<Map<String, Object>> toggleProduct(@PathVariable Integer branchId,
			@RequestParam Integer productId, @RequestParam boolean isAvailable, Authentication auth) {
		if (!canManageBranch(auth, branchId)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("message", "Bạn không có quyền quản lý món của chi nhánh này"));
		}
		boolean result = managerBranchService.toggleProductAvailability(branchId, productId, isAvailable);
		return ResponseEntity.ok(Map.of("branchId", branchId, "productId", productId, "isAvailable", result, "message",
				result ? "Món ăn đã được bật bán" : "Món ăn đã chuyển sang tạm hết hàng"));
	}

	private boolean canManageBranch(Authentication auth, Integer branchId) {
		if (auth == null || !auth.isAuthenticated()) return false;
		if (hasRole(auth, "ROLE_ADMIN")) return true;
		return hasRole(auth, "ROLE_MANAGER") && branchService.isOwner(branchId, auth.getName());
	}

	private boolean hasRole(Authentication auth, String role) {
		return auth.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
	}
}