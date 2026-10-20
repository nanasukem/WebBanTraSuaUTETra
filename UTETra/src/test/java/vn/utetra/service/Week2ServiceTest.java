package vn.utetra.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.CarrierReq;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CartItemRes;
import vn.utetra.dto.response.CartRes;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.ProductOptionsRes;
import vn.utetra.entity.Carrier;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.VoucherRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Test tuần 2 - mỗi test tự rollback, không làm bẩn DB */
@SpringBootTest
@Transactional
class Week2ServiceTest {

	@Autowired
	CarrierService carrierService;

	@Autowired
	CartService cartService;

	@Autowired
	OrderService orderService;

	@Autowired
	BranchService branchService;

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	VoucherRepository voucherRepository;

	@Autowired
	EntityManager em;

	private static final int USER1 = 6; // giỏ mẫu: 1 Bạc xỉu M, 50% đường, 70% đá = 34.000; địa chỉ #1, #2
	private static final int USER2 = 7; // giỏ mẫu: 2 Cà phê muối L + Kem cheese = 52.000/ly; địa chỉ #3

	private static void assertMoney(long expected, BigDecimal actual) {
		assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), "Mong đợi " + expected + " nhưng là " + actual);
	}

	private CarrierReq carrierReq(String name, long fee) {
		CarrierReq r = new CarrierReq();
		r.setName(name);
		r.setFee(BigDecimal.valueOf(fee));
		return r;
	}

	private CartAddReq cartReq(int productId, String size, int sugar, int ice, int qty, Integer... toppingIds) {
		CartAddReq r = new CartAddReq();
		r.setProductId(productId);
		r.setSize(size);
		r.setSugarLevel(sugar);
		r.setIceLevel(ice);
		r.setQuantity(qty);
		r.setToppingIds(List.of(toppingIds));
		return r;
	}

	/**
	 * Trước MỖI test: dựng lại giỏ mẫu cố định.
	 * Lý do: thử trên web có thể đã làm giỏ thật thay đổi. Hàm này chạy trong transaction của test
	 * nên cũng tự rollback, không đụng tới giỏ thật.
	 */
	@BeforeEach
	void datLaiGioMau() {
		cartService.clear(USER1);
		cartService.addItem(USER1, cartReq(3, "M", 50, 70, 1));    // 1 Bạc xỉu M, 50% đường, 70% đá = 34.000
		cartService.clear(USER2);
		cartService.addItem(USER2, cartReq(7, "L", 70, 50, 2, 4)); // 2 Cà phê muối L + Kem cheese = 52.000/ly
	}

	/** carrierId: 1 = Giao Hàng Nhanh 15.000, 2 = Giao Hàng Tiết Kiệm 12.000 */
	private CheckoutReq checkoutReq(int branchId, int addressId, int carrierId, String productCode, String shipCode) {
		CheckoutReq r = new CheckoutReq();
		r.setBranchId(branchId);
		r.setAddressId(addressId);
		r.setCarrierId(carrierId);
		r.setProductVoucherCode(productCode);
		r.setShippingVoucherCode(shipCode);
		r.setPaymentMethod(PaymentMethod.COD);
		r.setNote("Ít ngọt giúp em");
		return r;
	}

	// ================= NHÀ VẬN CHUYỂN =================
	@Test
	void carrier_themMoi_dangHoatDong() {
		Carrier c = carrierService.create(carrierReq("  JUnit Express  ", 18000));
		assertNotNull(c.getId());
		assertEquals("JUnit Express", c.getName(), "Tên phải được cắt khoảng trắng");
		assertTrue(c.getIsActive());
		assertTrue(carrierService.findActive().stream().anyMatch(x -> x.getId().equals(c.getId())));
	}

	@Test
	void carrier_trungTen_baoLoi() {
		assertThrows(BusinessException.class, () -> carrierService.create(carrierReq("giao hàng nhanh", 20000)));
	}

	@Test
	void carrier_capNhatPhi() {
		Carrier c = carrierService.create(carrierReq("JUnit Update", 15000));
		carrierService.update(c.getId(), carrierReq("JUnit Update", 22000));
		assertEquals(0, BigDecimal.valueOf(22000).compareTo(carrierService.findById(c.getId()).getFee()));
	}

	@Test
	void carrier_tat_khongConHienOCheckout() {
		Carrier c = carrierService.create(carrierReq("JUnit Toggle", 15000));
		carrierService.toggleActive(c.getId());
		assertFalse(carrierService.findById(c.getId()).getIsActive());
		assertTrue(carrierService.findActive().stream().noneMatch(x -> x.getId().equals(c.getId())));
		carrierService.toggleActive(c.getId()); // bật lại
		assertTrue(carrierService.findById(c.getId()).getIsActive());
	}

	@Test
	void carrier_timKiem() {
		assertEquals(1, carrierService.search("tiết kiệm").size()); // "Giao Hàng Tiết Kiệm" trong dữ liệu mẫu
		assertEquals(2, carrierService.search(null).size());
	}

	// ================= GIỎ HÀNG =================
	@Test
	void cart_gioMau_tinhTienDung() {
		CartRes cart = cartService.getCart(USER2);
		assertEquals(1, cart.items().size());
		CartItemRes line = cart.items().get(0);
		assertMoney(52000, line.unitPrice());   // 32000 + size L 10000 + Kem cheese 10000
		assertMoney(104000, line.lineTotal());  // x 2 ly
		assertEquals(2, cart.totalQty());
		assertMoney(104000, cart.subtotal());
	}

	@Test
	void cart_themMonMoi_coTopping() {
		CartRes cart = cartService.addItem(USER1, cartReq(1, "L", 70, 50, 1, 1, 2)); // + Trân châu đen, Thạch phô mai
		assertEquals(2, cart.items().size());
		CartItemRes moi = cart.items().stream().filter(i -> i.productId() == 1).findFirst().orElseThrow();
		assertMoney(58000, moi.unitPrice()); // 35000 + 10000 + 5000 + 8000
		assertEquals(2, moi.toppings().size());
		assertMoney(92000, cart.subtotal()); // 34000 + 58000
	}

	@Test
	void cart_trungCauHinh_congDonSoLuong() {
		CartRes cart = cartService.addItem(USER1, cartReq(3, "m", 50, 70, 2)); // size viết thường vẫn nhận
		assertEquals(1, cart.items().size(), "Trùng cấu hình thì không tạo dòng mới");
		assertEquals(3, cart.items().get(0).quantity());
	}

	@Test
	void cart_khacTopping_thanhDongMoi() {
		CartRes cart = cartService.addItem(USER1, cartReq(3, "M", 50, 70, 1, 1));
		assertEquals(2, cart.items().size());
	}

	@Test
	void cart_suaSoLuong_vaKhongHopLe() {
		Integer id = cartService.getCart(USER1).items().get(0).id();
		assertEquals(5, cartService.updateQuantity(USER1, id, 5).items().get(0).quantity());
		assertThrows(BusinessException.class, () -> cartService.updateQuantity(USER1, id, 0));
		assertThrows(BusinessException.class, () -> cartService.updateQuantity(USER1, id, 51));
	}

	@Test
	void cart_xoaMonCoTopping() {
		Integer id = cartService.getCart(USER2).items().get(0).id();
		assertTrue(cartService.removeItem(USER2, id).items().isEmpty());
	}

	@Test
	void cart_khongDuocDungVaoGioNguoiKhac() {
		Integer idCuaUser2 = cartService.getCart(USER2).items().get(0).id();
		assertThrows(ResourceNotFoundException.class, () -> cartService.removeItem(USER1, idCuaUser2));
		assertThrows(ResourceNotFoundException.class, () -> cartService.updateQuantity(USER1, idCuaUser2, 3));
		assertEquals(1, cartService.getCart(USER2).items().size(), "Giỏ của user2 phải còn nguyên");
	}

	@Test
	void cart_duLieuKhongHopLe_baoLoi() {
		assertThrows(BusinessException.class, () -> cartService.addItem(USER1, cartReq(1, "X", 100, 100, 1)));      // size sai
		assertThrows(BusinessException.class, () -> cartService.addItem(USER1, cartReq(1, "M", 45, 100, 1)));       // % đường sai
		assertThrows(BusinessException.class, () -> cartService.addItem(USER1, cartReq(999, "M", 100, 100, 1)));    // món không có
		assertThrows(BusinessException.class, () -> cartService.addItem(USER1, cartReq(1, "M", 100, 100, 1, 999))); // topping không có
	}

	@Test
	void cart_tuyChonMon_choModal() {
		ProductOptionsRes opt = cartService.getOptions(1);
		assertEquals(List.of("S", "M", "L"), List.copyOf(opt.sizes().keySet()), "Size phải theo thứ tự S, M, L");
		assertMoney(5000, opt.sizes().get("M"));
		assertEquals(4, opt.toppings().size());
		assertThrows(BusinessException.class, () -> cartService.getOptions(999));
	}

	// ================= ĐẶT HÀNG (ORDER ENGINE) =================
	@Test
	void order_datHangCOD_tinhTienVaLuuDung() {
		cartService.addItem(USER1, cartReq(1, "L", 70, 50, 1, 1, 2)); // giỏ: 34.000 + 58.000 = 92.000
		int freeshipTruoc = voucherRepository.findByCodeIgnoreCase("FREESHIP10K").orElseThrow().getUsedCount();

		Order o = orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "UTETRA20", "FREESHIP10K"));
		em.flush();
		em.clear();
		Order saved = orderRepository.findById(o.getId()).orElseThrow();

		assertMoney(92000, saved.getItemsTotal());
		assertMoney(18400, saved.getProductDiscount());  // UTETRA20: 20% x 92.000
		assertMoney(0, saved.getMemberDiscount());        // user1 hạng Thành viên (chi 153.000 < 300.000): không giảm
		assertEquals("Thành viên", saved.getMemberTierName());
		assertMoney(15000, saved.getShippingFee());       // Giao Hàng Nhanh
		assertMoney(10000, saved.getShippingDiscount());  // FREESHIP10K
		assertMoney(78600, saved.getTotalAmount());       // 92.000 - 18.400 + 15.000 - 10.000
		assertMoney(3680, saved.getCommissionAmount());   // 5% x (92.000 - 18.400)
		assertEquals(OrderStatus.PENDING, saved.getStatus());
		assertEquals(PaymentMethod.COD, saved.getPaymentMethod());
		assertEquals("Trần Thị Khách", saved.getReceiverName());
		assertEquals("Ít ngọt giúp em", saved.getNote());
		assertEquals(2, saved.getDetails().size());
		assertEquals(2, saved.getDetails().stream().mapToInt(d -> d.getToppings().size()).sum());
		assertTrue(cartService.getCart(USER1).items().isEmpty(), "Đặt xong phải xoá giỏ");
		assertEquals(freeshipTruoc + 1,
				voucherRepository.findByCodeIgnoreCase("FREESHIP10K").orElseThrow().getUsedCount(),
				"Đặt xong phải tăng lượt dùng voucher");
	}

	@Test
	void order_monHetOChiNhanh_baoLoi_vaPreviewBaoTruoc() {
		cartService.addItem(USER1, cartReq(4, "M", 100, 100, 1)); // Matcha đá xay: chi nhánh 2 đang hết
		CheckoutPreviewRes p = orderService.preview(USER1, checkoutReq(2, 1, 1, null, null));
		assertFalse(p.canOrder());
		assertTrue(p.unavailableItems().contains("Matcha đá xay"));
		assertThrows(BusinessException.class, () -> orderService.placeOrder(USER1, checkoutReq(2, 1, 1, null, null)));
		assertEquals(2, cartService.getCart(USER1).items().size(), "Lỗi thì giỏ phải còn nguyên");
		// Cùng giỏ đó, đặt ở chi nhánh 1 (còn Matcha) thì được
		assertNotNull(orderService.placeOrder(USER1, checkoutReq(1, 1, 1, null, null)).getId());
	}

	@Test
	void order_maSaiO_vaDiaChiNguoiKhac_baoLoi() {
		// FREESHIP10K là mã giảm ship, không được nhập vào ô giảm tiền món
		assertThrows(BusinessException.class, () -> orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "FREESHIP10K", null)));
		// Địa chỉ #3 là của user2
		assertThrows(BusinessException.class, () -> orderService.placeOrder(USER1, checkoutReq(1, 3, 1, null, null)));
	}

	@Test
	void order_preview_maChuaDuDieuKien_khongLuuDon() {
		long soDonTruoc = orderRepository.count();
		CheckoutPreviewRes p = orderService.preview(USER1, checkoutReq(1, 1, 2, "UTETRA20", null)); // giỏ 34.000 < 80.000
		assertNotNull(p.productVoucherError(), "Phải báo mã chưa đủ đơn tối thiểu");
		assertFalse(p.canOrder());
		assertMoney(34000, p.itemsTotal());
		assertMoney(12000, p.shippingFee()); // Giao Hàng Tiết Kiệm
		assertMoney(46000, p.totalAmount()); // 34.000 + 12.000, không giảm
		assertEquals(soDonTruoc, orderRepository.count(), "Preview không được lưu đơn");
	}

	@Test
	void order_chiNhanhTamNgung_vaGioTrong_baoLoi() {
		branchService.changeStatus(2, BranchStatus.SUSPENDED);
		assertThrows(BusinessException.class, () -> orderService.placeOrder(USER1, checkoutReq(2, 1, 1, null, null)));
		cartService.clear(USER1);
		assertThrows(BusinessException.class, () -> orderService.placeOrder(USER1, checkoutReq(1, 1, 1, null, null)));
	}

	@Test
	void order_voucherCuaChiNhanh_chiDungDungChiNhanh() {
		cartService.addItem(USER1, cartReq(2, "M", 100, 100, 1)); // + Trà đào 45.000 -> giỏ 79.000 (>= 60.000)
		assertThrows(BusinessException.class, () -> orderService.placeOrder(USER1, checkoutReq(2, 1, 1, "VVN15", null)));
		Order o = orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "VVN15", null)); // VVN15 chỉ của chi nhánh 1
		assertMoney(15000, o.getProductDiscount());
	}
}