package vn.utetra.controller.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.utetra.dto.request.VendorStatusReq;
import vn.utetra.dto.response.VendorBoardRes;
import vn.utetra.dto.response.VendorBranchRes;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.VendorOrderService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** API cho trang Quầy đơn hàng (SecurityConfig: /api/vendor/** chỉ ROLE_VENDOR) */
@RestController
@RequestMapping("/api/vendor/orders")
public class VendorOrderApiController {

	private final VendorOrderService vendorOrderService;
	private final CurrentUserService currentUserService;

	public VendorOrderApiController(VendorOrderService vendorOrderService, CurrentUserService currentUserService) {
		this.vendorOrderService = vendorOrderService;
		this.currentUserService = currentUserService;
	}

	@GetMapping("/branches")
	public List<VendorBranchRes> branches(Principal principal, HttpSession session) {
		return vendorOrderService.myBranches(currentUserService.requireUserId(principal, session));
	}

	@GetMapping
	public VendorBoardRes board(@RequestParam(required = false) Integer branchId, Principal principal,
			HttpSession session) {
		return vendorOrderService.board(currentUserService.requireUserId(principal, session), branchId);
	}

	@PostMapping("/{orderId}/status")
	public Map<String, String> changeStatus(@PathVariable Integer orderId, @RequestBody VendorStatusReq req,
			Principal principal, HttpSession session) {
		if (req == null || req.status() == null) throw new BusinessException("Thiếu trạng thái mới");
		vendorOrderService.changeStatus(currentUserService.requireUserId(principal, session), orderId, req.status(),
				req.reason());
		return Map.of("message", "Đã cập nhật đơn #" + orderId);
	}

	// ================= LỖI -> {"message": "..."} =================

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, String>> business(BusinessException e) {
		return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, String>> notFound(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
		return ResponseEntity.status(e.getStatusCode())
				.body(Map.of("message", e.getReason() == null ? "Vui lòng đăng nhập" : e.getReason()));
	}
}