package vn.utetra.controller.api;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.utetra.dto.request.ManagerStatusReq;
import vn.utetra.dto.response.ManagerBoardRes;
import vn.utetra.dto.response.ManagerBranchRes;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.CurrentUserService;
import vn.utetra.service.ManagerOrderService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager/orders")
public class ManagerOrderApiController {

	private final ManagerOrderService managerOrderService;
	private final CurrentUserService currentUserService;

	public ManagerOrderApiController(ManagerOrderService managerOrderService, CurrentUserService currentUserService) {
		this.managerOrderService = managerOrderService;
		this.currentUserService = currentUserService;
	}

	@GetMapping("/branches")
	public List<ManagerBranchRes> branches(Principal principal, HttpSession session) {
		return managerOrderService.myBranches(currentUserService.requireUserId(principal, session));
	}

	@GetMapping
	public ManagerBoardRes board(@RequestParam(required = false) Integer branchId, Principal principal,
			HttpSession session) {
		return managerOrderService.board(currentUserService.requireUserId(principal, session), branchId);
	}

	@PostMapping("/{orderId}/status")
	public Map<String, String> changeStatus(@PathVariable Integer orderId, @RequestBody ManagerStatusReq req,
			Principal principal, HttpSession session, jakarta.servlet.http.HttpServletRequest request) {
		if (req == null || req.status() == null) throw new BusinessException("Thiếu trạng thái mới");
		managerOrderService.changeStatus(currentUserService.requireUserId(principal, session), orderId, req.status(),
				req.reason());
		String text = vn.utetra.entity.enums.OrderStatus.actionText(req.status(), orderId, req.reason());
		request.setAttribute("activityLog", text);
		return Map.of("message", text);
	}

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