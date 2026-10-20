package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CartItemRes;
import vn.utetra.dto.response.CartRes;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.OrderSummaryRes;
import vn.utetra.dto.response.VoucherOptionRes;
import vn.utetra.entity.*;
import vn.utetra.entity.enums.*;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.*;
import vn.utetra.service.CartService;
import vn.utetra.service.MemberService;
import vn.utetra.service.OrderNotifier;
import vn.utetra.service.OrderService;
import vn.utetra.service.VoucherService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

	/** SĐT Việt Nam: 10 số, bắt đầu bằng 0 */
	private static final String PHONE_REGEX = "0\\d{9}";
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

	private final CartService cartService;
	private final VoucherService voucherService;
	private final MemberService memberService;
	private final OrderRepository orderRepository;
	private final BranchRepository branchRepository;
	private final BranchProductRepository branchProductRepository;
	private final AddressRepository addressRepository;
	private final CarrierRepository carrierRepository;
	private final VoucherRepository voucherRepository;
	private final ProductRepository productRepository;
	private final ToppingRepository toppingRepository;
	private final UserRepository userRepository;
	private final OrderNotifier orderNotifier; // bước 7: báo quán có đơn mới

	public OrderServiceImpl(CartService cartService, VoucherService voucherService, MemberService memberService,
			OrderRepository orderRepository, BranchRepository branchRepository,
			BranchProductRepository branchProductRepository, AddressRepository addressRepository,
			CarrierRepository carrierRepository, VoucherRepository voucherRepository,
			ProductRepository productRepository, ToppingRepository toppingRepository, UserRepository userRepository,
			OrderNotifier orderNotifier) {
		this.cartService = cartService;
		this.voucherService = voucherService;
		this.memberService = memberService;
		this.orderRepository = orderRepository;
		this.branchRepository = branchRepository;
		this.branchProductRepository = branchProductRepository;
		this.addressRepository = addressRepository;
		this.carrierRepository = carrierRepository;
		this.voucherRepository = voucherRepository;
		this.productRepository = productRepository;
		this.toppingRepository = toppingRepository;
		this.userRepository = userRepository;
		this.orderNotifier = orderNotifier;
	}

	/** Kết quả tính toán, dùng chung cho preview và placeOrder */
	private static class Calc {
		CartRes cart;
		Branch branch;
		DeliveryType deliveryType;
		Address address; // chỉ khi giao tận nơi
		Carrier carrier; // chỉ khi giao tận nơi
		String receiverName; // từ địa chỉ (giao) hoặc khách nhập (đến lấy)
		String receiverPhone;
		Voucher productVoucher;
		Voucher shippingVoucher;
		BigDecimal itemsTotal = BigDecimal.ZERO;
		BigDecimal productDiscount = BigDecimal.ZERO;
		BigDecimal memberDiscount = BigDecimal.ZERO;
		String memberTierName;
		BigDecimal shippingFee = BigDecimal.ZERO;
		BigDecimal shippingDiscount = BigDecimal.ZERO;
		BigDecimal totalAmount = BigDecimal.ZERO;
		List<String> unavailable = new ArrayList<>();
		String productVoucherError;
		String shippingVoucherError;
		List<String> errors = new ArrayList<>();
	}

	// ================= XEM TRƯỚC (không lưu) =================
	@Override
	public CheckoutPreviewRes preview(Integer userId, CheckoutReq req) {
		Calc c = calculate(userId, req, false);
		boolean canOrder = c.errors.isEmpty() && c.productVoucherError == null && c.shippingVoucherError == null;
		return new CheckoutPreviewRes(c.cart.items(), c.cart.totalQty(), c.itemsTotal, c.productDiscount,
				c.memberDiscount, c.memberTierName, c.shippingFee, c.shippingDiscount, c.totalAmount, c.unavailable,
				c.productVoucherError, c.shippingVoucherError, c.errors, canOrder);
	}

	// ================= Bước 5: KHUNG "MÃ CỦA BẠN" =================
	@Override
	public List<VoucherOptionRes> voucherOptions(Integer userId, CheckoutReq req) {
		Calc c = calculate(userId, req, false); // để biết tiền món, phí ship, chi nhánh, hình thức nhận
		Integer branchId = c.branch == null ? null : c.branch.getId();
		boolean newCustomer = memberService.isNewCustomer(userId);

		List<VoucherOptionRes> result = new ArrayList<>();
		for (Voucher v : voucherService.findUsable(branchId)) {
			// Giống app thật: tài khoản cũ không thấy ưu đãi "bạn mới"
			if (v.getAudience() == VoucherAudience.NEW_USER && !newCustomer) continue;

			String reason = null;
			BigDecimal discount = BigDecimal.ZERO;
			if (v.getApplyTo() == VoucherApplyTo.SHIPPING && c.deliveryType == DeliveryType.PICKUP) {
				reason = "Chỉ dùng cho đơn giao tận nơi";
			} else if (c.itemsTotal.compareTo(v.getMinOrderValue()) < 0) {
				reason = "Mua thêm " + money(v.getMinOrderValue().subtract(c.itemsTotal)) + " để dùng mã này";
			} else {
				try {
					discount = voucherService.calculateDiscount(v.getCode(), branchId, userId, req.getPaymentMethod(),
							c.itemsTotal, c.shippingFee);
				} catch (BusinessException e) {
					reason = e.getMessage();
				}
			}
			result.add(new VoucherOptionRes(v.getCode(), v.getName(), v.getApplyTo().name(),
					v.getDiscountType().name(), v.getDiscountValue(), v.getMaxDiscount(), v.getMinOrderValue(),
					v.getEndDate().format(DATE), tags(v), reason == null, reason, discount));
		}
		// Dùng được lên trước, giảm nhiều lên trước
		result.sort(Comparator.comparing(VoucherOptionRes::usable).reversed()
				.thenComparing(VoucherOptionRes::discount, Comparator.reverseOrder()));
		return result;
	}

	private List<String> tags(Voucher v) {
		List<String> tags = new ArrayList<>();
		if (v.getBranch() != null) tags.add("Riêng " + v.getBranch().getName());
		if (v.getAudience() == VoucherAudience.NEW_USER) tags.add("Bạn mới");
		if (v.getAudience() == VoucherAudience.MIN_TIER && v.getMinTier() != null)
			tags.add("Hạng " + v.getMinTier().getName() + "+");
		if (v.getPaymentMethod() != null) tags.add("Chỉ thanh toán " + paymentLabel(v.getPaymentMethod()));
		if (v.getPerUserLimit() != null) tags.add("Mỗi người " + v.getPerUserLimit() + " lần");
		return tags;
	}

	// ================= Bước 5: TÓM TẮT ĐƠN =================
	@Override
	public OrderSummaryRes getOrderSummary(Integer userId, Integer orderId) {
		Order o = orderRepository.findById(orderId)
				.filter(x -> x.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
		int qty = o.getDetails().stream().mapToInt(d -> d.getQuantity()).sum();
		return new OrderSummaryRes(o.getId(), o.getBranch().getName(), o.getBranch().getAddress(),
				o.getBranch().getPhone(), o.getDeliveryType(), o.getReceiverName(), o.getReceiverPhone(),
				o.getDeliveryAddress(), o.getCarrier() == null ? null : o.getCarrier().getName(), o.getTotalAmount(),
				o.getPaymentMethod(), o.getStatus(), qty, o.getCreatedAt().format(DATE_TIME));
	}

	// ================= ĐẶT HÀNG =================
	@Override
	@Transactional
	public Order placeOrder(Integer userId, CheckoutReq req) {
		Calc c = calculate(userId, req, true); // strict: có lỗi là dừng ngay (gồm cả phương thức thanh toán)
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng #" + userId));

		// 1. Đơn hàng
		Order o = new Order();
		o.setUser(user);
		o.setBranch(c.branch);
		o.setDeliveryType(c.deliveryType);
		o.setCarrier(c.carrier); // null khi đến lấy
		o.setProductVoucher(c.productVoucher);
		o.setShippingVoucher(c.shippingVoucher);
		o.setReceiverName(c.receiverName);
		o.setReceiverPhone(c.receiverPhone);
		o.setDeliveryAddress(c.address == null ? null : c.address.getAddressLine()); // null khi đến lấy
		o.setDeliveryNote(c.address == null ? null : c.address.getNote()); // đến lấy thì không có
		o.setNote(req.getNote() == null || req.getNote().isBlank() ? null : req.getNote().trim());
		o.setItemsTotal(c.itemsTotal);
		o.setProductDiscount(c.productDiscount);
		o.setMemberDiscount(c.memberDiscount);
		o.setMemberTierName(c.memberTierName);
		o.setShippingFee(c.shippingFee);
		o.setShippingDiscount(c.shippingDiscount);
		o.setTotalAmount(c.totalAmount);
		o.setCommissionAmount(commission(c));
		o.setStatus(OrderStatus.PENDING);
		o.setPaymentMethod(req.getPaymentMethod());
		o.setPaymentStatus(PaymentStatus.UNPAID);

		// 2. Chi tiết đơn: chốt giá từng ly + giá từng topping lúc đặt
		for (CartItemRes line : c.cart.items()) {
			OrderDetail d = new OrderDetail();
			d.setProduct(productRepository.getReferenceById(line.productId()));
			d.setSize(line.size());
			d.setSugarLevel(line.sugarLevel());
			d.setIceLevel(line.iceLevel());
			d.setQuantity(line.quantity());
			d.setUnitPrice(line.unitPrice());
			d.setSubtotal(line.lineTotal());
			for (CartItemRes.ToppingRes t : line.toppings()) {
				OrderDetailTopping odt = new OrderDetailTopping();
				odt.setTopping(toppingRepository.getReferenceById(t.id()));
				odt.setPrice(t.price());
				d.addTopping(odt);
			}
			o.addDetail(d);
		}

		// 3. Lưu (cascade lưu luôn OrderDetail + OrderDetailTopping)
		Order saved = orderRepository.save(o);

		// 4. Báo quán có đơn mới: lưu thông báo + chuông/popup (WebSocket đẩy sau khi lưu đơn xong)
		orderNotifier.onNewOrder(saved);

		// 5. Xoá giỏ hàng
		cartService.clear(userId);

		// 6. Ghi lịch sử dùng mã + tăng lượt - làm SAU CÙNG (UPDATE có clearAutomatically).
		if (c.productVoucher != null) voucherService.markUsed(c.productVoucher.getId(), userId, saved.getId());
		if (c.shippingVoucher != null) voucherService.markUsed(c.shippingVoucher.getId(), userId, saved.getId());

		return saved;
	}

	// ================= TÍNH TOÁN DÙNG CHUNG =================
	/** strict = true: gặp lỗi là ném BusinessException; false: ghi lỗi lại để trang Checkout hiện */
	private Calc calculate(Integer userId, CheckoutReq req, boolean strict) {
		Calc c = new Calc();

		// Giỏ hàng (giá đã được CartService tính lại từ database)
		c.cart = cartService.getCart(userId);
		if (c.cart.items().isEmpty()) fail(c, strict, "Giỏ hàng đang trống");
		c.itemsTotal = c.cart.subtotal();

		// 1. Chi nhánh phải đang hoạt động
		if (req.getBranchId() == null) {
			fail(c, strict, "Vui lòng chọn chi nhánh");
		} else {
			c.branch = branchRepository.findById(req.getBranchId()).orElse(null);
			if (c.branch == null) fail(c, strict, "Chi nhánh không tồn tại");
			else if (c.branch.getStatus() != BranchStatus.APPROVED)
				fail(c, strict, "Chi nhánh " + c.branch.getName() + " hiện không nhận đơn");
		}

		// 2. Từng món phải còn bán (toàn chuỗi + tại chi nhánh đã chọn) và topping còn bán
		Set<Integer> hetOChiNhanh = c.branch == null ? Set.of()
				: new HashSet<>(branchProductRepository.findUnavailableProductIds(c.branch.getId()));
		for (CartItemRes line : c.cart.items()) {
			boolean conBan = productRepository.findById(line.productId())
					.map(p -> Boolean.TRUE.equals(p.getIsAvailable())).orElse(false)
					&& !hetOChiNhanh.contains(line.productId())
					&& line.toppings().stream().allMatch(t -> toppingRepository.findById(t.id())
							.map(tp -> Boolean.TRUE.equals(tp.getIsAvailable())).orElse(false));
			if (!conBan && !c.unavailable.contains(line.productName())) c.unavailable.add(line.productName());
		}
		if (!c.unavailable.isEmpty()) {
			String taiDau = c.branch == null ? "" : " tại " + c.branch.getName();
			fail(c, strict, "Các món sau đã hết" + taiDau + ": " + String.join(", ", c.unavailable)
					+ ". Vui lòng xoá khỏi giỏ hoặc chọn chi nhánh khác");
		}

		// 3. Hình thức nhận hàng
		c.deliveryType = req.getDeliveryType() == null ? DeliveryType.DELIVERY : req.getDeliveryType();
		if (c.deliveryType == DeliveryType.DELIVERY) {
			// 3a. Giao tận nơi: địa chỉ phải thuộc đúng người đặt
			if (req.getAddressId() == null) {
				fail(c, strict, "Vui lòng chọn địa chỉ nhận hàng");
			} else {
				c.address = addressRepository.findById(req.getAddressId())
						.filter(a -> a.getUser().getId().equals(userId)).orElse(null);
				if (c.address == null) {
					fail(c, strict, "Địa chỉ nhận hàng không hợp lệ");
				} else {
					c.receiverName = c.address.getReceiverName();
					c.receiverPhone = c.address.getReceiverPhone();
				}
			}
			// 3b. Nhà vận chuyển đang hoạt động -> phí ship
			if (req.getCarrierId() == null) {
				fail(c, strict, "Vui lòng chọn nhà vận chuyển");
			} else {
				c.carrier = carrierRepository.findById(req.getCarrierId())
						.filter(cr -> Boolean.TRUE.equals(cr.getIsActive())).orElse(null);
				if (c.carrier == null) fail(c, strict, "Nhà vận chuyển không khả dụng");
				else c.shippingFee = c.carrier.getFee();
			}
		} else {
			// 3c. Đến lấy: không địa chỉ, không phí ship; cần tên + SĐT để quán gọi khi xong
			c.receiverName = trimToNull(req.getReceiverName());
			c.receiverPhone = trimToNull(req.getReceiverPhone());
			if (c.receiverName == null) fail(c, strict, "Vui lòng nhập tên người đến lấy");
			if (c.receiverPhone == null || !c.receiverPhone.matches(PHONE_REGEX))
				fail(c, strict, "Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0)");
			c.shippingFee = BigDecimal.ZERO;
		}

		// 4. Phương thức thanh toán (tuần 3 mở VNPAY/MOMO thì bỏ dòng chặn)
		if (req.getPaymentMethod() == null) {
			fail(c, strict, "Vui lòng chọn phương thức thanh toán");
		} else if (req.getPaymentMethod() != PaymentMethod.COD) {
			fail(c, strict, "Thanh toán " + req.getPaymentMethod() + " sẽ có ở bản sau, hiện chỉ hỗ trợ COD");
		}

		// 5. Voucher: đúng ô (PRODUCT / SHIPPING) + theo người dùng + theo phương thức thanh toán
		Integer branchId = c.branch == null ? null : c.branch.getId();
		if (notBlank(req.getProductVoucherCode())) {
			try {
				c.productVoucher = findVoucher(req.getProductVoucherCode(), VoucherApplyTo.PRODUCT, "giảm tiền món");
				c.productDiscount = voucherService.calculateDiscount(c.productVoucher.getCode(), branchId, userId,
						req.getPaymentMethod(), c.itemsTotal, c.shippingFee);
			} catch (BusinessException e) {
				if (strict) throw e;
				c.productVoucher = null;
				c.productDiscount = BigDecimal.ZERO;
				c.productVoucherError = e.getMessage();
			}
		}
		if (notBlank(req.getShippingVoucherCode())) {
			try {
				if (c.deliveryType == DeliveryType.PICKUP)
					throw new BusinessException("Đơn đến lấy không có phí ship nên không dùng được mã giảm ship");
				c.shippingVoucher = findVoucher(req.getShippingVoucherCode(), VoucherApplyTo.SHIPPING, "giảm phí ship");
				c.shippingDiscount = voucherService.calculateDiscount(c.shippingVoucher.getCode(), branchId, userId,
						req.getPaymentMethod(), c.itemsTotal, c.shippingFee);
			} catch (BusinessException e) {
				if (strict) throw e;
				c.shippingVoucher = null;
				c.shippingDiscount = BigDecimal.ZERO;
				c.shippingVoucherError = e.getMessage();
			}
		}

		// 6. Giảm theo hạng thành viên, tính trên (tiền món - giảm mã món)
		MemberTier tier = memberService.getCurrentTier(userId);
		c.memberTierName = tier.getName();
		c.memberDiscount = memberService.calculateDiscount(tier, c.itemsTotal.subtract(c.productDiscount));

		// 7. Tổng = tiền món - giảm mã món - giảm theo hạng + phí ship - giảm ship
		c.totalAmount = c.itemsTotal.subtract(c.productDiscount).subtract(c.memberDiscount).add(c.shippingFee)
				.subtract(c.shippingDiscount);
		return c;
	}

	/** Hoa hồng app = (tiền món - giảm mã món - giảm theo hạng) x % chiết khấu của chi nhánh */
	private BigDecimal commission(Calc c) {
		BigDecimal rate = c.branch.getCommissionRate() == null ? BigDecimal.ZERO : c.branch.getCommissionRate();
		return c.itemsTotal.subtract(c.productDiscount).subtract(c.memberDiscount).multiply(rate)
				.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
	}

	private Voucher findVoucher(String code, VoucherApplyTo slot, String slotName) {
		String clean = code.trim().toUpperCase();
		Voucher v = voucherRepository.findByCodeIgnoreCase(clean)
				.orElseThrow(() -> new BusinessException("Mã " + clean + " không tồn tại"));
		if (v.getApplyTo() != slot)
			throw new BusinessException("Mã " + v.getCode() + " không phải mã " + slotName);
		return v;
	}

	private void fail(Calc c, boolean strict, String message) {
		if (strict) throw new BusinessException(message);
		c.errors.add(message);
	}

	private boolean notBlank(String s) {
		return s != null && !s.isBlank();
	}

	private String trimToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	/** 46000 -> "46.000đ" */
	private static String money(BigDecimal v) {
		return String.format(Locale.US, "%,d", v.longValue()).replace(',', '.') + "đ";
	}

	private static String paymentLabel(PaymentMethod m) {
		return switch (m) {
			case COD -> "COD";
			case VNPAY -> "VNPAY";
			case MOMO -> "MoMo";
		};
	}
}