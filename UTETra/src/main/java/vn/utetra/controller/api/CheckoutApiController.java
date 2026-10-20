package vn.utetra.controller.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CheckoutInitRes;
import vn.utetra.dto.response.CheckoutPageRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.VoucherRepository;
import vn.utetra.service.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** API cho checkout.js */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutApiController {

	private final OrderService orderService;
	private final AddressService addressService;
	private final CarrierService carrierService;
	private final MemberService memberService;
	private final CurrentUserService currentUserService;
	private final VoucherRepository voucherRepository;

	public CheckoutApiController(OrderService orderService, AddressService addressService,
			CarrierService carrierService, MemberService memberService, CurrentUserService currentUserService,
			VoucherRepository voucherRepository) {
		this.orderService = orderService;
		this.addressService = addressService;
		this.carrierService = carrierService;
		this.memberService = memberService;
		this.currentUserService = currentUserService;
		this.voucherRepository = voucherRepository;
	}

	/** Dữ liệu ban đầu: người nhận, sổ địa chỉ, nhà vận chuyển, hạng thành viên */
	@GetMapping("/init")
	public CheckoutInitRes init(Principal principal, HttpSession session) {
		User u = currentUserService.requireUser(principal, session);
		List<CheckoutInitRes.CarrierOption> carriers = carrierService.findActive().stream()
				.map(c -> new CheckoutInitRes.CarrierOption(c.getId(), c.getName(), c.getFee())).toList();
		return new CheckoutInitRes(u.getFullName(), u.getPhone(), addressService.list(u.getId()), carriers,
				memberService.getTierInfo(u.getId()), memberService.isNewCustomer(u.getId()));
	}

	/** Tính lại tiền + danh sách mã mỗi khi khách đổi lựa chọn (không lưu gì) */
	@PostMapping("/preview")
	public CheckoutPageRes preview(@RequestBody CheckoutReq req, Principal principal, HttpSession session) {
		Integer userId = currentUserService.requireUserId(principal, session);
		return new CheckoutPageRes(orderService.preview(userId, req), orderService.voucherOptions(userId, req));
	}

	/** Khách tự gõ mã: cho biết mã giảm món hay giảm ship để đặt vào đúng ô */
	@GetMapping("/voucher-type")
	public Map<String, String> voucherType(@RequestParam String code) {
		return voucherRepository.findByCodeIgnoreCase(code.trim())
				.map(v -> Map.of("code", v.getCode(), "applyTo", v.getApplyTo().name()))
				.orElseThrow(() -> new BusinessException("Mã " + code.trim().toUpperCase() + " không tồn tại"));
	}

	/** Đặt hàng */
	@PostMapping
	public Map<String, Object> placeOrder(@Valid @RequestBody CheckoutReq req, Principal principal,
			HttpSession session) {
		Order o = orderService.placeOrder(currentUserService.requireUserId(principal, session), req);
		return Map.of("orderId", o.getId(), "message", "Đặt hàng thành công");
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

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException e) {
		String msg = e.getBindingResult().getFieldErrors().isEmpty() ? "Dữ liệu không hợp lệ"
				: e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
		return ResponseEntity.badRequest().body(Map.of("message", msg));
	}
}