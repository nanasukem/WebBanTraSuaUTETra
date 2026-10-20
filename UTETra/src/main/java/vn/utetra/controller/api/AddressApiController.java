package vn.utetra.controller.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.utetra.dto.request.AddressReq;
import vn.utetra.dto.response.AddressRes;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.AddressService;
import vn.utetra.service.CurrentUserService;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Sổ địa chỉ của user ĐANG ĐĂNG NHẬP. Không bao giờ tin userId do trình duyệt
 * gửi lên. Trả DTO (AddressRes), không trả entity Address.
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressApiController {

	private final AddressService addressService;
	private final CurrentUserService currentUserService;

	public AddressApiController(AddressService addressService, CurrentUserService currentUserService) {
		this.addressService = addressService;
		this.currentUserService = currentUserService;
	}

	// ================= ĐƯỜNG DẪN MỚI (Checkout dùng) =================

	/** GET /api/addresses - sổ địa chỉ của tôi */
	@GetMapping
	public List<AddressRes> list(Principal principal, HttpSession session) {
		return addressService.list(me(principal, session));
	}

	/** POST /api/addresses - thêm địa chỉ */
	@PostMapping
	public AddressRes add(@Valid @RequestBody AddressReq req, Principal principal, HttpSession session) {
		return addressService.add(me(principal, session), req);
	}

	/** PUT /api/addresses/{id} - sửa địa chỉ */
	@PutMapping("/{id}")
	public AddressRes update(@PathVariable Integer id, @Valid @RequestBody AddressReq req, Principal principal,
			HttpSession session) {
		return addressService.update(me(principal, session), id, req);
	}

	/** PUT /api/addresses/{id}/default - đặt mặc định */
	@PutMapping("/{id}/default")
	public List<AddressRes> setDefault(@PathVariable Integer id, Principal principal, HttpSession session) {
		Integer userId = me(principal, session);
		addressService.setDefault(userId, id);
		return addressService.list(userId);
	}

	/** DELETE /api/addresses/{id} - xoá */
	@DeleteMapping("/{id}")
	public List<AddressRes> delete(@PathVariable Integer id, Principal principal, HttpSession session) {
		Integer userId = me(principal, session);
		addressService.delete(userId, id);
		return addressService.list(userId);
	}

	// ================= ĐƯỜNG DẪN CŨ (giữ cho trang đang gọi, nhưng đã chặn xem/sửa
	// của người khác) =================

	@GetMapping("/user/{userId}")
	public List<AddressRes> getUserAddresses(@PathVariable Integer userId, Principal principal, HttpSession session) {
		return addressService.list(requireSelf(userId, principal, session));
	}

	@PostMapping("/user/{userId}")
	public AddressRes addAddress(@PathVariable Integer userId, @Valid @RequestBody AddressReq req, Principal principal,
			HttpSession session) {
		return addressService.add(requireSelf(userId, principal, session), req);
	}

	@PutMapping("/user/{userId}/default/{addressId}")
	public Map<String, String> setDefaultAddress(@PathVariable Integer userId, @PathVariable Integer addressId,
			Principal principal, HttpSession session) {
		addressService.setDefault(requireSelf(userId, principal, session), addressId);
		return Map.of("message", "Đã cập nhật địa chỉ mặc định thành công");
	}

	@DeleteMapping("/user/{userId}/{addressId}")
	public Map<String, String> deleteAddress(@PathVariable Integer userId, @PathVariable Integer addressId,
			Principal principal, HttpSession session) {
		addressService.delete(requireSelf(userId, principal, session), addressId);
		return Map.of("message", "Đã xóa địa chỉ thành công");
	}

	// ================= HÀM PHỤ =================

	private Integer me(Principal principal, HttpSession session) {
		return currentUserService.requireUserId(principal, session);
	}

	/** userId trên đường dẫn phải đúng là người đang đăng nhập, không thì 403 */
	private Integer requireSelf(Integer userId, Principal principal, HttpSession session) {
		Integer current = me(principal, session);
		if (!current.equals(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Bạn không có quyền xem/sửa địa chỉ của người khác");
		}
		return current;
	}

	// ===== Lỗi -> JSON {"message": "..."} =====
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, String>> handleBusiness(BusinessException e) {
		return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
		return ResponseEntity.status(e.getStatusCode())
				.body(Map.of("message", e.getReason() == null ? "Không có quyền" : e.getReason()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException e) {
		String msg = e.getBindingResult().getFieldErrors().isEmpty() ? "Dữ liệu không hợp lệ"
				: e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
		return ResponseEntity.badRequest().body(Map.of("message", msg));
	}
}