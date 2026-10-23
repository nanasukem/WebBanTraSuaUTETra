package vn.utetra.controller.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CheckoutInitRes;
import vn.utetra.dto.response.CheckoutPageRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.VoucherRepository;
import vn.utetra.service.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutApiController {

	private final OrderService orderService;
	private final AddressService addressService;
	private final MemberService memberService;
	private final vn.utetra.service.ShippingFeeService shippingFeeService;
	private final CurrentUserService currentUserService;
	private final VoucherRepository voucherRepository;
	private final OrderRepository orderRepository;
	private final CartService cartService;
	private final OrderFlowService orderFlowService;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.service.MenuBranchService menuBranchService;

	public CheckoutApiController(OrderService orderService, AddressService addressService,
			MemberService memberService, vn.utetra.service.ShippingFeeService shippingFeeService,
			CurrentUserService currentUserService,
			VoucherRepository voucherRepository, OrderRepository orderRepository, CartService cartService,
			OrderFlowService orderFlowService) {
		this.orderFlowService = orderFlowService;

		this.orderService = orderService;
		this.addressService = addressService;
		this.memberService = memberService;
		this.shippingFeeService = shippingFeeService;
		this.currentUserService = currentUserService;
		this.voucherRepository = voucherRepository;
		this.orderRepository = orderRepository;
		this.cartService = cartService;
	}

	// Khởi tạo trang checkout
	@GetMapping("/init")
	public CheckoutInitRes init(Principal principal, HttpSession session) {

		User user = currentUserService.requireUser(principal, session);

		return new CheckoutInitRes(user.getFullName(), user.getPhone(), addressService.list(user.getId()),
				memberService.getTierInfo(user.getId()), memberService.isNewCustomer(user.getId()),
				shippingFeeService.baseFee(), shippingFeeService.perKmFee(),
				// Khách tự chọn chi nhánh -> chọn sẵn; hệ thống tự chọn gần nhất -> trang thanh toán tự tính lại theo địa chỉ giao
				menuBranchService.auto(session) ? null : menuBranchService.currentId(session));
	}

	// Tính trước đơn hàng
	@PostMapping("/preview")
	public CheckoutPageRes preview(@RequestBody CheckoutReq req, Principal principal, HttpSession session) {

		Integer userId = currentUserService.requireUserId(principal, session);

		return new CheckoutPageRes(orderService.preview(userId, req), orderService.voucherOptions(userId, req));
	}

	// Kiểm tra loại voucher
	@GetMapping("/voucher-type")
	public Map<String, String> voucherType(@RequestParam String code) {

		return voucherRepository.findByCodeIgnoreCase(code.trim())
				.map(v -> Map.of("code", v.getCode(), "applyTo", v.getApplyTo().name()))
				.orElseThrow(() -> new BusinessException("Mã " + code.trim().toUpperCase() + " không tồn tại"));
	}

	// Đặt hàng
	@PostMapping
	public Map<String, Object> placeOrder(@Valid @RequestBody CheckoutReq req, Principal principal,
			HttpSession session) {

		Integer userId = currentUserService.requireUserId(principal, session);

		Order order = orderService.placeOrder(userId, req);
		PaymentMethod method = order.getPaymentMethod();

		Map<String, Object> result = new HashMap<>();
		result.put("orderId", order.getId());
		result.put("message", "Đặt hàng thành công");
		result.put("paymentMethod", method != null ? method.name() : "COD");

		// VNPAY / MoMo: /payment/{id}/pay tạo giao dịch rồi chuyển sang cổng; COD: trang đặt hàng thành công
		boolean online = method == PaymentMethod.VNPAY || method == PaymentMethod.MOMO;
		result.put("paymentUrl", online ? "/payment/" + order.getId() + "/pay" : "/checkout/success/" + order.getId());

		return result;
	}

	// "Quay lại sửa đơn" khi chưa thanh toán: huỷ đơn chờ thanh toán (trả lượt mã giảm giá) và cho món lại vào giỏ
	@PostMapping("/{orderId}/return-to-checkout")
	@Transactional
	public ResponseEntity<Map<String, Object>> returnToCheckout(@PathVariable Integer orderId, Principal principal,
			HttpSession session) {

		Integer userId = currentUserService.requireUserId(principal, session);

		Order order = orderRepository.findById(orderId)
				.filter(o -> o.getUser() != null && o.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));

		if (order.getStatus() != OrderStatus.AWAITING_PAYMENT || order.getPaymentStatus() != PaymentStatus.UNPAID) {
			throw new BusinessException(order.getPaymentStatus() == PaymentStatus.PAID
					? "Đơn hàng đã thanh toán thành công, không thể quay lại chỉnh sửa"
					: "Đơn hàng không còn chờ thanh toán");
		}

		orderFlowService.cancelByCustomer(userId, orderId, "Quay lại sửa đơn trước khi thanh toán");
		cartService.reorder(userId, orderId);

		return ResponseEntity
				.ok(Map.of("redirectUrl", "/checkout", "message", "Đã cho món lại vào giỏ, bạn có thể sửa đơn"));
	}

	// Xử lý lỗi nghiệp vụ
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

		String message = e.getBindingResult().getFieldErrors().isEmpty() ? "Dữ liệu không hợp lệ"
				: e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();

		return ResponseEntity.badRequest().body(Map.of("message", message != null ? message : "Dữ liệu không hợp lệ"));
	}
}
