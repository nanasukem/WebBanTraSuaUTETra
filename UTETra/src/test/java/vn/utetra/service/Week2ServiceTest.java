package vn.utetra.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.AddressReq;
import vn.utetra.dto.request.BranchReq;
import vn.utetra.dto.request.CarrierReq;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.AddressRes;
import vn.utetra.dto.response.CartItemRes;
import vn.utetra.dto.response.CartRes;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.MemberTierInfo;
import vn.utetra.dto.response.NearbyBranchRes;
import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.OrderListRes;
import vn.utetra.dto.response.OrderSummaryRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.dto.response.ProductOptionsRes;
import vn.utetra.dto.response.ManagerBoardRes;
import vn.utetra.dto.response.ManagerCustomerDetailRes;
import vn.utetra.dto.response.ManagerCustomerRes;
import vn.utetra.dto.response.ManagerHistoryRes;
import vn.utetra.dto.response.ManagerOrderRes;
import vn.utetra.dto.response.ManagerTabRes;
import vn.utetra.dto.response.VoucherOptionRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Carrier;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.NotificationRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.VoucherRepository;
import vn.utetra.repository.VoucherUsageRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEST TUẦN 2 (gộp): nhà vận chuyển, giỏ hàng, đặt hàng, hạng thành viên + giới hạn voucher,
 * đến lấy / chi nhánh gần nhất, Checkout (mã của bạn, sổ địa chỉ), đơn hàng của tôi, huỷ đơn,
 * luồng trạng thái, Quầy đơn hàng của Manager + thông báo, đơn hàng của chi nhánh theo trạng thái.
 *
 * Mỗi test tự rollback, không làm bẩn DB. Chạy lại UTETra.sql trước khi chạy để dữ liệu mẫu đúng.
 *
 * Dữ liệu mẫu:
 * - user1 (id 6): giỏ 1 Bạc xỉu M = 34.000; địa chỉ #1 (mặc định, có ghi chú), #2; đơn #1 ĐÃ GIAO (FREESHIP10K), đơn #2 CHỜ XÁC NHẬN
 * - user2 (id 7): tài khoản mới; giỏ 2 Cà phê muối L + Kem cheese = 104.000; địa chỉ #3
 * - manager1 (id 3) chủ chi nhánh 1 Võ Văn Ngân; manager2 (id 4) chủ chi nhánh 2 Quận 1 (đang hết Matcha)
 * - Đối tác giao hàng (phí theo khoảng cách, tự chọn rẻ nhất): 1 = Shipper UTETra 15.000 cho 5 km đầu + 5.000/km,
 *   2 = Ahamove 18.000 + 3.000/km. Địa chỉ #1 -> chi nhánh 1: 3,7 km (15.000); #3 -> chi nhánh 2: 0 km (15.000);
 *   #3 -> chi nhánh 1: 11,3 km (Ahamove 18.000 + 7 x 3.000 = 39.000). Mỗi đơn chỉ dùng 1 mã giảm giá.
 */
@SpringBootTest
@Transactional
class Week2ServiceTest {

	@Autowired CarrierService carrierService;
	@Autowired CartService cartService;
	@Autowired OrderService orderService;
	@Autowired BranchService branchService;
	@Autowired MemberService memberService;
	@Autowired AddressService addressService;
	@Autowired OrderFlowService orderFlowService;
	@Autowired ManagerOrderService managerOrderService;
	@Autowired ManagerCustomerService managerCustomerService;
	@Autowired OrderRepository orderRepository;
	@Autowired VoucherRepository voucherRepository;
	@Autowired VoucherUsageRepository voucherUsageRepository;
	@Autowired ProductRepository productRepository;
	@Autowired NotificationRepository notificationRepository;
	@Autowired EntityManager em;

	private static final int USER1 = 6;
	private static final int USER2 = 7;
	private static final int MANAGER1 = 3;     // chủ chi nhánh 1
	private static final int MANAGER2 = 4;     // chủ chi nhánh 2
	private static final int DON_DA_GIAO = 1; // đơn mẫu của user1, chi nhánh 1, ĐÃ GIAO
	private static final int DON_CHO = 2;     // đơn mẫu của user1, chi nhánh 2, CHỜ XÁC NHẬN, 1 ly món #5

	// =====================================================================
	// HÀM PHỤ
	// =====================================================================

	private static void assertMoney(long expected, BigDecimal actual) {
		assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), "Mong đợi " + expected + " nhưng là " + actual);
	}

	private CarrierReq carrierReq(String name, long fee) {
		CarrierReq r = new CarrierReq();
		r.setName(name);
		r.setFee(BigDecimal.valueOf(fee));
		r.setPerKmFee(BigDecimal.valueOf(4000));
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
		// Giờ mở cửa mẫu 7:00 - 22:00: test chạy giờ nào cũng được -> cho chi nhánh mở cả ngày (tự rollback)
		em.createNativeQuery("UPDATE Branches SET open_time = '00:00', close_time = '00:00'").executeUpdate();
		cartService.clear(USER1);
		cartService.addItem(USER1, cartReq(3, "M", 50, 70, 1));    // 1 Bạc xỉu M, 50% đường, 70% đá = 34.000
		cartService.clear(USER2);
		cartService.addItem(USER2, cartReq(7, "L", 70, 50, 2, 4)); // 2 Cà phê muối L + Kem cheese = 52.000/ly
	}

	/** Giao tận nơi, COD, có ghi chú cho quán */
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

	/** Giao tận nơi, chọn phương thức thanh toán */
	private CheckoutReq giaoTanNoi(int branchId, int addressId, int carrierId, String productCode, PaymentMethod pm) {
		CheckoutReq r = new CheckoutReq();
		r.setDeliveryType(DeliveryType.DELIVERY);
		r.setBranchId(branchId);
		r.setAddressId(addressId);
		r.setCarrierId(carrierId);
		r.setProductVoucherCode(productCode);
		r.setPaymentMethod(pm);
		return r;
	}

	/** user2 giao tận nơi ở chi nhánh 2, địa chỉ #3, Giao Hàng Nhanh, COD */
	private CheckoutReq giaoTanNoi(String productCode) {
		return giaoTanNoi(2, 3, 1, productCode, PaymentMethod.COD);
	}

	/** Giao tận nơi, COD, không mã */
	private CheckoutReq giao(int branchId, int addressId, int carrierId) {
		return giaoTanNoi(branchId, addressId, carrierId, null, PaymentMethod.COD);
	}

	/** Đến chi nhánh lấy */
	private CheckoutReq denLay(int branchId, String name, String phone, String shipCode) {
		CheckoutReq r = new CheckoutReq();
		r.setDeliveryType(DeliveryType.PICKUP);
		r.setBranchId(branchId);
		r.setReceiverName(name);
		r.setReceiverPhone(phone);
		r.setShippingVoucherCode(shipCode);
		r.setPaymentMethod(PaymentMethod.COD);
		return r;
	}

	/** user2 đến lấy ở chi nhánh branchId */
	private CheckoutReq denLay(int branchId) {
		return denLay(branchId, "Phạm Văn Mua", "0900000007", null);
	}

	/** user2 đến lấy ở chi nhánh 2 */
	private CheckoutReq denLay() {
		return denLay(2);
	}

	/** Tạo 1 đơn giả lập với trạng thái tuỳ ý (để test hạng thành viên) */
	private void taoDon(int userId, long tongTien, OrderStatus status, LocalDateTime ngayGiao) {
		Order o = new Order();
		o.setUser(em.getReference(User.class, userId));
		o.setBranch(em.getReference(Branch.class, 1));
		o.setReceiverName("JUnit");
		o.setReceiverPhone("0900000000");
		o.setDeliveryAddress("Đơn giả lập để test hạng");
		o.setItemsTotal(BigDecimal.valueOf(tongTien));
		o.setTotalAmount(BigDecimal.valueOf(tongTien));
		o.setPaymentMethod(PaymentMethod.COD);
		o.setStatus(status);
		o.setDeliveredAt(ngayGiao);
		em.persist(o);
		em.flush();
	}

	/** Tạo 1 đơn ĐÃ GIAO giả lập để đẩy chi tiêu (xét hạng) */
	private void taoDonDaGiao(int userId, long tongTien, LocalDateTime ngayGiao) {
		taoDon(userId, tongTien, OrderStatus.DELIVERED, ngayGiao);
	}

	private AddressReq diaChi(String line, boolean macDinh) {
		AddressReq r = new AddressReq();
		r.setReceiverName("Phạm Văn Mua");
		r.setReceiverPhone("0900000007");
		r.setAddressLine(line);
		r.setLatitude(new BigDecimal("10.800000"));
		r.setLongitude(new BigDecimal("106.700000"));
		r.setIsDefault(macDinh);
		return r;
	}

	private OrderStatus trangThai(int orderId) {
		em.flush();
		em.clear();
		return orderRepository.findById(orderId).orElseThrow().getStatus();
	}

	private NearbyBranchRes timTheoId(List<NearbyBranchRes> list, int id) {
		return list.stream().filter(b -> b.id() == id).findFirst().orElseThrow();
	}

	private Optional<VoucherOptionRes> tim(List<VoucherOptionRes> list, String code) {
		return list.stream().filter(v -> v.code().equals(code)).findFirst();
	}

	private static ManagerOrderRes tim(List<ManagerOrderRes> col, int orderId) {
		return col.stream().filter(o -> o.id() == orderId).findFirst().orElse(null);
	}

	private static boolean co(List<OrderListRes> list, int orderId) {
		return list.stream().anyMatch(o -> o.id() == orderId);
	}

	private static boolean co(ManagerHistoryRes h, int orderId) {
		return h.orders().stream().anyMatch(o -> o.id() == orderId);
	}

	private static long dem(ManagerHistoryRes h, String status) {
		return h.tabs().stream().filter(t -> t.status().equals(status)).mapToLong(ManagerTabRes::count).sum();
	}

	// =====================================================================
	// 1. NHÀ VẬN CHUYỂN
	// =====================================================================

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
		assertThrows(BusinessException.class, () -> carrierService.create(carrierReq("shipper utetra", 20000)));
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
		assertEquals(1, carrierService.search("ahamove").size()); // "Giao Hàng Tiết Kiệm" trong dữ liệu mẫu
		assertEquals(2, carrierService.search(null).size());
	}

	// =====================================================================
	// 2. GIỎ HÀNG (lưu database)
	// =====================================================================

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

	// =====================================================================
	// 3. ĐẶT HÀNG (tính tiền, áp mã, lưu đơn @Transactional)
	// =====================================================================

	@Test
	void order_datHangCOD_tinhTienVaLuuDung() {
		cartService.addItem(USER1, cartReq(1, "L", 70, 50, 1, 1, 2)); // giỏ: 34.000 + 58.000 = 92.000
		int maTruoc = voucherRepository.findByCodeIgnoreCase("UTETRA20").orElseThrow().getUsedCount();

		Order o = orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "UTETRA20", null));
		em.flush();
		em.clear();
		Order saved = orderRepository.findById(o.getId()).orElseThrow();

		assertMoney(92000, saved.getItemsTotal());
		assertMoney(18400, saved.getProductDiscount());  // UTETRA20: 20% x 92.000
		assertMoney(0, saved.getMemberDiscount());        // user1 hạng Thành viên (chi 153.000 < 300.000): không giảm
		assertEquals("Thành viên", saved.getMemberTierName());
		assertMoney(15000, saved.getShippingFee());       // 3,7 km: trong 5 km đầu, Shipper UTETra
		assertEquals("Shipper UTETra", saved.getCarrier().getName());
		assertMoney(0, saved.getShippingDiscount());
		assertMoney(88600, saved.getTotalAmount());       // 92.000 - 18.400 + 15.000
		assertMoney(73600, saved.getSalesAmount());       // doanh thu quán: không gồm phí ship
		assertEquals(OrderStatus.PENDING, saved.getStatus());
		assertEquals(PaymentMethod.COD, saved.getPaymentMethod());
		assertEquals("Trần Thị Khách", saved.getReceiverName());
		assertEquals("Ít ngọt giúp em", saved.getNote());
		assertEquals(2, saved.getDetails().size());
		assertEquals(2, saved.getDetails().stream().mapToInt(d -> d.getToppings().size()).sum());
		assertTrue(cartService.getCart(USER1).items().isEmpty(), "Đặt xong phải xoá giỏ");
		assertEquals(maTruoc + 1,
				voucherRepository.findByCodeIgnoreCase("UTETRA20").orElseThrow().getUsedCount(),
				"Đặt xong phải tăng lượt dùng voucher");
	}

	@Test
	void gioMoCua_ngoaiGioKhongDatDuoc() {
		// Đặt giờ mở cửa chi nhánh 1 là 1 tiếng sau -> 2 tiếng sau: lúc này đang đóng cửa
		java.time.LocalTime now = java.time.LocalTime.now();
		em.createNativeQuery("UPDATE Branches SET open_time = :o, close_time = :c WHERE id = 1")
				.setParameter("o", now.plusHours(1).withSecond(0).withNano(0).toString())
				.setParameter("c", now.plusHours(2).withSecond(0).withNano(0).toString()).executeUpdate();
		em.clear();
		CheckoutPreviewRes p = orderService.preview(USER1, checkoutReq(1, 1, 1, null, null));
		assertFalse(p.canOrder());
		assertTrue(p.errors().stream().anyMatch(x -> x.contains("đã đóng cửa")), String.valueOf(p.errors()));
		assertThrows(BusinessException.class, () -> orderService.placeOrder(USER1, checkoutReq(1, 1, 1, null, null)));
	}

	@Test
	void gioMoCua_tinhDungCaGioQuaDem() {
		Branch b = new Branch();
		b.setOpenTime(java.time.LocalTime.of(7, 0));
		b.setCloseTime(java.time.LocalTime.of(22, 0));
		assertTrue(b.isOpenAt(java.time.LocalTime.of(7, 0)));
		assertTrue(b.isOpenAt(java.time.LocalTime.of(21, 59)));
		assertFalse(b.isOpenAt(java.time.LocalTime.of(22, 0)));
		assertFalse(b.isOpenAt(java.time.LocalTime.of(6, 30)));
		assertEquals("7:00 - 22:00", b.getHoursLabel());
		assertEquals("Đã đóng cửa · mở lại lúc 7:00", b.openStatusAt(java.time.LocalTime.of(23, 0)));
		b.setOpenTime(java.time.LocalTime.of(18, 0)); // quán đêm 18:00 - 02:00
		b.setCloseTime(java.time.LocalTime.of(2, 0));
		assertTrue(b.isOpenAt(java.time.LocalTime.of(1, 0)));
		assertFalse(b.isOpenAt(java.time.LocalTime.of(12, 0)));
	}

	@Test
	void moiDonChiMotMaGiamGia() {
		cartService.addItem(USER1, cartReq(1, "L", 70, 50, 1, 1, 2)); // 92.000
		CheckoutPreviewRes p = orderService.preview(USER1, checkoutReq(1, 1, 1, "UTETRA20", "FREESHIP10K"));
		assertFalse(p.canOrder());
		assertNotNull(p.shippingVoucherError());
		assertTrue(p.shippingVoucherError().contains("1 mã"));
		BusinessException e = assertThrows(BusinessException.class,
				() -> orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "UTETRA20", "FREESHIP10K")));
		assertTrue(e.getMessage().contains("1 mã"));
		// Chỉ mã freeship thì được
		Order o = orderService.placeOrder(USER1, checkoutReq(1, 1, 1, null, "FREESHIP10K"));
		assertMoney(10000, o.getShippingDiscount());
		assertMoney(97000, o.getTotalAmount()); // 92.000 + 15.000 - 10.000
	}

	@Test
	void phiGiao_theoKhoangCach_tuChonDoiTacReNhat() {
		CheckoutPreviewRes gan = orderService.preview(USER2, checkoutReq(2, 3, 2, null, null)); // cùng chỗ chi nhánh 2
		assertEquals(0, BigDecimal.ZERO.compareTo(gan.deliveryDistanceKm()));
		assertEquals("Shipper UTETra", gan.carrierName());
		assertMoney(15000, gan.shippingFee());

		CheckoutPreviewRes xa = orderService.preview(USER2, checkoutReq(1, 3, 1, null, null)); // 11,3 km
		assertEquals(0, new BigDecimal("11.3").compareTo(xa.deliveryDistanceKm()));
		assertEquals("Ahamove", xa.carrierName(), "xa thì Ahamove rẻ hơn");
		assertMoney(39000, xa.shippingFee()); // 18.000 + 7 km x 3.000
	}

	@Test
	void phiGiao_quaXaHoacChuaGhimViTri_baoLoi() {
		AddressReq vungTau = diaChi("1 Thuỳ Vân, Vũng Tàu", false);
		vungTau.setLatitude(new BigDecimal("10.346000"));
		vungTau.setLongitude(new BigDecimal("107.084000"));
		AddressRes a = addressService.add(USER2, vungTau);
		CheckoutPreviewRes p = orderService.preview(USER2, checkoutReq(2, a.id(), 1, null, null));
		assertFalse(p.canOrder());
		assertTrue(p.errors().stream().anyMatch(x -> x.contains("15 km")), String.valueOf(p.errors()));

		AddressReq chuaGhim = diaChi("5 Lê Lợi, Quận 1", false);
		chuaGhim.setLatitude(null);
		chuaGhim.setLongitude(null);
		AddressRes b = addressService.add(USER2, chuaGhim);
		CheckoutPreviewRes q = orderService.preview(USER2, checkoutReq(2, b.id(), 1, null, null));
		assertFalse(q.canOrder());
		assertTrue(q.errors().stream().anyMatch(x -> x.contains("ghim vị trí")), String.valueOf(q.errors()));
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
		assertMoney(15000, p.shippingFee()); // khách không chọn hãng: hệ thống tính theo khoảng cách
		assertMoney(49000, p.totalAmount()); // 34.000 + 15.000, không giảm
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

	@Test
	void order_ghiChuDiaChi_chepVaoDon_vaBaoChoQuan() {
		Order o = orderService.placeOrder(USER1, checkoutReq(1, 1, 1, null, null)); // địa chỉ #1 có ghi chú
		em.flush();
		em.clear();
		Order saved = orderRepository.findById(o.getId()).orElseThrow();
		assertEquals("Toà B3, gọi trước khi tới", saved.getDeliveryNote(), "Ghi chú địa chỉ phải chép vào đơn");
		assertEquals(1, notificationRepository.countByUserIdAndOrderId(MANAGER1, o.getId()),
				"Chủ chi nhánh 1 phải có thông báo đơn mới");
	}

	// =====================================================================
	// 4. HẠNG THÀNH VIÊN + GIỚI HẠN VOUCHER (bước 4B - mở rộng)
	// =====================================================================

	@Test
	void hang_tinhTheo12ThangGanNhat() {
		assertTrue(memberService.isNewCustomer(USER2), "user2 chưa có đơn nào");
		MemberTierInfo info = memberService.getTierInfo(USER2);
		assertEquals("Thành viên", info.tierName());
		assertMoney(0, info.spentLast12Months());
		assertEquals("Bạc", info.nextTierName());
		assertMoney(300000, info.amountToNextTier());

		taoDonDaGiao(USER2, 350000, LocalDateTime.now().minusMonths(13)); // quá 12 tháng -> không tính
		assertEquals("Thành viên", memberService.getTierInfo(USER2).tierName());
		assertFalse(memberService.isNewCustomer(USER2), "Đã có đơn thì không còn là tài khoản mới");

		taoDonDaGiao(USER2, 400000, LocalDateTime.now().minusDays(3));
		info = memberService.getTierInfo(USER2);
		assertEquals("Bạc", info.tierName());
		assertMoney(400000, info.spentLast12Months());
		assertEquals("Vàng", info.nextTierName());
		assertMoney(600000, info.amountToNextTier());
	}

	@Test
	void hang_datHang_coGiamTheoHang() {
		taoDonDaGiao(USER2, 1_200_000, LocalDateTime.now().minusDays(1)); // -> Vàng: 5%, tối đa 20.000

		CheckoutPreviewRes p = orderService.preview(USER2, checkoutReq(1, 3, 1, null, null));
		assertEquals("Vàng", p.memberTierName());
		assertMoney(5200, p.memberDiscount()); // 5% x 104.000

		Order o = orderService.placeOrder(USER2, checkoutReq(1, 3, 1, null, null));
		em.flush();
		em.clear();
		Order saved = orderRepository.findById(o.getId()).orElseThrow();
		assertMoney(104000, saved.getItemsTotal());
		assertMoney(5200, saved.getMemberDiscount());
		assertEquals("Vàng", saved.getMemberTierName());
		assertMoney(137800, saved.getTotalAmount());     // 104.000 - 5.200 + 39.000 (11,3 km, Ahamove)
	}

	@Test
	void voucher_banMoi_chiDungChoDonDauTien() {
		Voucher chao = voucherRepository.findByCodeIgnoreCase("CHAOBANMOI").orElseThrow();
		Integer chaoId = chao.getId();
		int luotTruoc = chao.getUsedCount();

		Order o = orderService.placeOrder(USER2, checkoutReq(1, 3, 1, "CHAOBANMOI", null));
		assertMoney(25000, o.getProductDiscount());  // 30% x 104.000 = 31.200 -> trần 25.000
		assertMoney(0, o.getShippingDiscount());
		assertMoney(0, o.getMemberDiscount());       // hạng Thành viên: 0%
		assertMoney(118000, o.getTotalAmount());     // 104.000 - 25.000 + 39.000 ship

		em.flush();
		em.clear();
		assertEquals(1, voucherUsageRepository.countByVoucherIdAndUserId(chaoId, USER2), "Phải ghi lịch sử dùng mã");
		assertEquals(luotTruoc + 1, voucherRepository.findById(chaoId).orElseThrow().getUsedCount());

		// Đơn thứ 2: không dùng lại được nữa
		cartService.addItem(USER2, cartReq(7, "L", 70, 50, 2, 4));
		CheckoutPreviewRes p = orderService.preview(USER2, checkoutReq(1, 3, 1, "CHAOBANMOI", null));
		assertNotNull(p.productVoucherError());
		assertFalse(p.canOrder());
		assertThrows(BusinessException.class,
				() -> orderService.placeOrder(USER2, checkoutReq(1, 3, 1, "CHAOBANMOI", null)));
	}

	@Test
	void voucher_banMoi_taiKhoanCu_khongDung() {
		CheckoutPreviewRes p = orderService.preview(USER1, checkoutReq(1, 1, 1, "CHAOBANMOI", null));
		assertNotNull(p.productVoucherError());
		assertTrue(p.productVoucherError().contains("đơn hàng đầu tiên"), p.productVoucherError());
		assertFalse(p.canOrder());
		assertThrows(BusinessException.class,
				() -> orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "CHAOBANMOI", null)));
	}

	@Test
	void voucher_moiNguoiChiDung1Lan() {
		cartService.addItem(USER1, cartReq(1, "L", 70, 50, 1, 1, 2)); // giỏ 92.000 (>= 80.000)
		orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "UTETRA20", null)); // lần 1: được

		cartService.addItem(USER1, cartReq(3, "M", 50, 70, 1));
		cartService.addItem(USER1, cartReq(1, "L", 70, 50, 1, 1, 2)); // giỏ lại 92.000
		CheckoutPreviewRes p = orderService.preview(USER1, checkoutReq(1, 1, 1, "UTETRA20", null));
		assertNotNull(p.productVoucherError());
		assertTrue(p.productVoucherError().contains("đã dùng"), p.productVoucherError());
		assertThrows(BusinessException.class,
				() -> orderService.placeOrder(USER1, checkoutReq(1, 1, 1, "UTETRA20", null)));
	}

	@Test
	void voucher_theoHang_chuaDuHangThiBaoLoi() {
		cartService.addItem(USER1, cartReq(8, "L", 100, 100, 2)); // + 2 Cookie L 59.000 -> giỏ 152.000

		CheckoutPreviewRes p = orderService.preview(USER1, checkoutReq(1, 1, 1, "VANG30K", null));
		assertNotNull(p.productVoucherError());
		assertTrue(p.productVoucherError().contains("hạng Vàng"), p.productVoucherError());

		taoDonDaGiao(USER1, 1_000_000, LocalDateTime.now().minusDays(1)); // 153.000 + 1.000.000 -> Vàng
		p = orderService.preview(USER1, checkoutReq(1, 1, 1, "VANG30K", null));
		assertNull(p.productVoucherError());
		assertTrue(p.canOrder());
		assertMoney(30000, p.productDiscount());
		assertMoney(6100, p.memberDiscount());  // 5% x (152.000 - 30.000)
		assertMoney(130900, p.totalAmount());   // 152.000 - 30.000 - 6.100 + 15.000
	}

	@Test
	void hang_donDaHuy_khongTinhVaoHang_vanLaTaiKhoanMoi() {
		taoDon(USER2, 5_000_000, OrderStatus.CANCELLED, null); // đơn huỷ dù lớn cũng không tính
		MemberTierInfo info = memberService.getTierInfo(USER2);
		assertEquals("Thành viên", info.tierName());
		assertMoney(0, info.spentLast12Months());
		assertTrue(memberService.isNewCustomer(USER2), "Chỉ có đơn đã huỷ thì vẫn là tài khoản mới");
	}

	// =====================================================================
	// 5. ĐẾN LẤY TẠI CỬA HÀNG + VOUCHER THEO THANH TOÁN (bước 4C)
	// =====================================================================

	@Test
	void denLay_khongPhiShip_khongCanDiaChi() {
		Order o = orderService.placeOrder(USER2, denLay(1, "Phạm Văn Mua", "0900000007", null));
		em.flush();
		em.clear();
		Order saved = orderRepository.findById(o.getId()).orElseThrow();

		assertEquals(DeliveryType.PICKUP, saved.getDeliveryType());
		assertNull(saved.getCarrier(), "Đến lấy thì không có nhà vận chuyển");
		assertNull(saved.getDeliveryAddress(), "Đến lấy thì không có địa chỉ giao");
		assertEquals("Phạm Văn Mua", saved.getReceiverName());
		assertEquals("0900000007", saved.getReceiverPhone());
		assertMoney(0, saved.getShippingFee());
		assertMoney(104000, saved.getTotalAmount());     // chỉ tiền món
	}

	@Test
	void denLay_thieuSdtHoacTen_baoLoi() {
		CheckoutPreviewRes p = orderService.preview(USER2, denLay(1, "Phạm Văn Mua", "", null));
		assertFalse(p.canOrder());
		assertMoney(0, p.shippingFee());
		assertThrows(BusinessException.class,
				() -> orderService.placeOrder(USER2, denLay(1, "Phạm Văn Mua", "123", null))); // SĐT sai
		assertThrows(BusinessException.class,
				() -> orderService.placeOrder(USER2, denLay(1, "  ", "0900000007", null)));    // thiếu tên
	}

	@Test
	void denLay_khongDungDuocMaShip() {
		CheckoutPreviewRes p = orderService.preview(USER2, denLay(1, "Phạm Văn Mua", "0900000007", "FREESHIPMOI"));
		assertNotNull(p.shippingVoucherError());
		assertMoney(0, p.shippingDiscount());
		assertFalse(p.canOrder());
	}

	@Test
	void maMomo_chiApDungKhiThanhToanMomo() {
		cartService.addItem(USER1, cartReq(1, "L", 70, 50, 1, 1, 2)); // giỏ 92.000 (>= 70.000)

		CheckoutPreviewRes cod = orderService.preview(USER1, giaoTanNoi(1, 1, 1, "MOMO15K", PaymentMethod.COD));
		assertNotNull(cod.productVoucherError());
		assertTrue(cod.productVoucherError().contains("MoMo"), cod.productVoucherError());
		assertMoney(0, cod.productDiscount());

		CheckoutPreviewRes momo = orderService.preview(USER1, giaoTanNoi(1, 1, 1, "MOMO15K", PaymentMethod.MOMO));
		assertNull(momo.productVoucherError());
		assertMoney(15000, momo.productDiscount());
		assertMoney(92000, momo.totalAmount()); // 92.000 - 15.000 + 15.000 ship
	}

	/** Tuần 3: đặt bằng MoMo được, đơn nằm ở Chờ thanh toán (trả xong quán mới thấy) */
	@Test
	void thanhToanMomo_datDuoc_choThanhToan() {
		CheckoutPreviewRes p = orderService.preview(USER1, giaoTanNoi(1, 1, 1, null, PaymentMethod.MOMO));
		assertTrue(p.canOrder(), String.valueOf(p.errors()));
		Order o = orderService.placeOrder(USER1, giaoTanNoi(1, 1, 1, null, PaymentMethod.MOMO));
		assertEquals(OrderStatus.AWAITING_PAYMENT, o.getStatus());
		assertEquals(vn.utetra.entity.enums.PaymentStatus.UNPAID, o.getPaymentStatus());
	}

	// =====================================================================
	// 6. CHI NHÁNH GẦN NHẤT + QUYỀN CHỦ CHI NHÁNH (bước 4D)
	// =====================================================================

	@Test
	void ganNguyenHue_thiChiNhanhQuan1LenDau() {
		List<NearbyBranchRes> list = branchService.findNearby(10.7748, 106.7035);
		assertEquals(2, list.get(0).id(), "Chi nhánh Quận 1 phải gần nhất");
		assertTrue(list.get(0).distanceKm() < 1, "Thực tế: " + list.get(0).distanceKm());
		assertTrue(timTheoId(list, 1).distanceKm() > 5, "Võ Văn Ngân phải xa hơn 5 km");
	}

	@Test
	void oKtxKhuB_thiChiNhanhVoVanNganLenDau() {
		List<NearbyBranchRes> list = branchService.findNearby(10.8823, 106.7826);
		assertEquals(1, list.get(0).id(), "Chi nhánh Võ Văn Ngân phải gần nhất");
		assertTrue(list.get(0).distanceKm() < 6, "Thực tế: " + list.get(0).distanceKm());
	}

	@Test
	void khongCoViTri_vanTraDuDanhSach() {
		List<NearbyBranchRes> list = branchService.findNearby(null, null);
		assertTrue(list.size() >= 2);
		assertTrue(list.stream().allMatch(b -> b.distanceKm() == null), "Không có vị trí thì không có khoảng cách");
	}

	@Test
	void chiNhanhTamNgung_khongDuocGoiY() {
		branchService.changeStatus(2, BranchStatus.SUSPENDED);
		List<NearbyBranchRes> list = branchService.findNearby(10.7748, 106.7035);
		assertTrue(list.stream().noneMatch(b -> b.id() == 2), "Chi nhánh tạm ngưng không được gợi ý");
	}

	@Test
	void toaDo_thieuMot_baoLoi() {
		BranchReq req = new BranchReq();
		req.setName("UTETra Test Tọa Độ");
		req.setAddress("abc");
		req.setOwnerId(2); // manager3 chưa có chi nhánh -> lỗi chỉ do tọa độ
		req.setLatitude(new BigDecimal("10.8"));
		// thiếu longitude
		assertThrows(BusinessException.class, () -> branchService.create(req));
	}

	@Test
	void isOwner_chiDungChuChiNhanh() {
		assertTrue(branchService.isOwner(1, "manager1"), "manager1 là chủ chi nhánh 1");
		assertFalse(branchService.isOwner(1, "manager2"), "manager2 không phải chủ chi nhánh 1");
		assertFalse(branchService.isOwner(1, "user1"), "khách thường không phải chủ");
		assertFalse(branchService.isOwner(999, "manager1"), "chi nhánh không tồn tại");
	}

	// =====================================================================
	// 7. CHECKOUT: MÃ CỦA BẠN, SỔ ĐỊA CHỈ, TÓM TẮT ĐƠN (bước 5)
	// =====================================================================

	@Test
	void maCuaBan_taiKhoanMoi_dungDuocVaLyDoDung() {
		List<VoucherOptionRes> list = orderService.voucherOptions(USER2, giao(1, 3, 1));

		VoucherOptionRes chao = tim(list, "CHAOBANMOI").orElseThrow();
		assertTrue(chao.usable());
		assertMoney(25000, chao.discount()); // 30% x 104.000 = 31.200 -> trần 25.000

		VoucherOptionRes freeshipMoi = tim(list, "FREESHIPMOI").orElseThrow();
		assertTrue(freeshipMoi.usable());
		assertMoney(15000, freeshipMoi.discount());

		VoucherOptionRes vang = tim(list, "VANG30K").orElseThrow();
		assertFalse(vang.usable());
		assertTrue(vang.reason().contains("46.000"), vang.reason()); // đơn từ 150.000, mới 104.000

		VoucherOptionRes momo = tim(list, "MOMO15K").orElseThrow();
		assertFalse(momo.usable());
		assertTrue(momo.reason().contains("MoMo"), momo.reason());

		assertTrue(list.get(0).usable(), "Mã dùng được phải lên đầu");
		assertEquals("CHAOBANMOI", list.get(0).code(), "Mã giảm nhiều nhất lên đầu");
	}

	@Test
	void maCuaBan_taiKhoanCu_khongThayUuDaiBanMoi() {
		List<VoucherOptionRes> list = orderService.voucherOptions(USER1, giao(1, 1, 1));
		assertTrue(tim(list, "CHAOBANMOI").isEmpty());
		assertTrue(tim(list, "FREESHIPMOI").isEmpty());
	}

	@Test
	void maCuaBan_denLay_maShipKhongDungDuoc() {
		List<VoucherOptionRes> list = orderService.voucherOptions(USER2, denLay(1));
		VoucherOptionRes ship = tim(list, "FREESHIPMOI").orElseThrow();
		assertFalse(ship.usable());
		assertTrue(ship.reason().contains("giao tận nơi"), ship.reason());
	}

	@Test
	void maCuaBan_maRiengChiNhanh_chiHienDungChiNhanh() {
		assertTrue(tim(orderService.voucherOptions(USER2, giao(1, 3, 1)), "VVN15").isPresent());
		assertTrue(tim(orderService.voucherOptions(USER2, giao(2, 3, 1)), "VVN15").isEmpty());
	}

	@Test
	void diaChi_themMacDinh_boMacDinhCu() {
		int soDiaChiTruoc = addressService.list(USER2).size(); // không giả định có sẵn bao nhiêu địa chỉ

		AddressRes moi = addressService.add(USER2, diaChi("99 Lê Lợi, Quận 1", true));
		List<AddressRes> list = addressService.list(USER2);
		assertEquals(soDiaChiTruoc + 1, list.size());
		assertEquals(moi.id(), list.get(0).id(), "Địa chỉ mặc định lên đầu");
		assertEquals(1, list.stream().filter(AddressRes::isDefault).count(), "Chỉ 1 địa chỉ mặc định");

		AddressRes thuong = addressService.add(USER2, diaChi("1 Pasteur, Quận 1", false));
		assertFalse(thuong.isDefault());
	}

	@Test
	void diaChi_sdtSai_baoLoi() {
		AddressReq r = diaChi("abc", false);
		r.setReceiverPhone("123");
		assertThrows(BusinessException.class, () -> addressService.add(USER2, r));
	}

	@Test
	void diaChi_xoaMacDinh_chuyenSangDiaChiKhac() {
		addressService.delete(USER1, 1); // #1 đang là mặc định
		List<AddressRes> list = addressService.list(USER1);
		assertEquals(1, list.size());
		assertTrue(list.get(0).isDefault(), "Địa chỉ còn lại phải thành mặc định");
	}

	@Test
	void diaChi_nguoiKhac_khongXoaDuoc() {
		assertThrows(ResourceNotFoundException.class, () -> addressService.delete(USER1, 3)); // #3 của user2
	}

	@Test
	void tomTatDon_chiChuDonXemDuoc() {
		Order o = orderService.placeOrder(USER2, giao(1, 3, 1));
		em.flush();
		em.clear();

		OrderSummaryRes s = orderService.getOrderSummary(USER2, o.getId());
		assertEquals("UTETra Võ Văn Ngân", s.branchName());
		assertEquals(DeliveryType.DELIVERY, s.deliveryType());
		assertEquals("Ahamove", s.carrierName()); // 11,3 km: Ahamove rẻ hơn
		assertEquals(2, s.totalQty());
		assertMoney(143000, s.totalAmount()); // 104.000 + 39.000 ship

		assertThrows(ResourceNotFoundException.class, () -> orderService.getOrderSummary(USER1, o.getId()));
	}

	@Test
	void diaChi_sua_doiNoiDungVaToaDo() {
		AddressReq r = diaChi("12 Nguyễn Huệ (đã sửa), Quận 1", false);
		r.setLatitude(new BigDecimal("10.775000"));
		r.setLongitude(new BigDecimal("106.704000"));
		AddressRes sua = addressService.update(USER2, 3, r);
		assertEquals("12 Nguyễn Huệ (đã sửa), Quận 1", sua.addressLine());
		assertEquals(0, new BigDecimal("10.775000").compareTo(sua.latitude()));
		assertTrue(sua.isDefault(), "Địa chỉ đang mặc định thì vẫn giữ mặc định");
	}

	@Test
	void diaChi_suaThanhMacDinh_vaKhongSuaDuocCuaNguoiKhac() {
		addressService.update(USER1, 2, diaChi("1 Võ Văn Ngân, Thủ Đức", true)); // #2 thành mặc định
		List<AddressRes> list = addressService.list(USER1);
		assertEquals(2, list.get(0).id(), "Địa chỉ vừa đặt mặc định lên đầu");
		assertEquals(1, list.stream().filter(AddressRes::isDefault).count(), "Chỉ 1 địa chỉ mặc định");

		assertThrows(ResourceNotFoundException.class,
				() -> addressService.update(USER1, 3, diaChi("Sửa trộm địa chỉ user2", false))); // #3 của user2
	}

	@Test
	void diaChi_kieuShopee_ghepDiaChiVaLuuLoai() {
		AddressReq r = new AddressReq();
		r.setReceiverName("Phạm Văn Mua");
		r.setReceiverPhone("0900000007");
		r.setProvince("Thành phố Hồ Chí Minh");
		r.setWard("Phường An Lạc");
		r.setStreet("76 Đường số 1B");
		r.setAddressType("HOME");
		r.setLatitude(new BigDecimal("10.740000"));
		r.setLongitude(new BigDecimal("106.610000"));

		AddressRes s = addressService.add(USER2, r);
		assertEquals("76 Đường số 1B, Phường An Lạc, Thành phố Hồ Chí Minh", s.addressLine());
		assertEquals("Phường An Lạc", s.ward());
		assertEquals("HOME", s.addressType());
	}

	@Test
	void diaChi_kieuShopee_thieuPhuongHoacLoaiSai_baoLoi() {
		AddressReq r = new AddressReq();
		r.setReceiverName("Phạm Văn Mua");
		r.setReceiverPhone("0900000007");
		r.setProvince("Thành phố Hồ Chí Minh");
		r.setStreet("76 Đường số 1B"); // thiếu Phường/Xã
		assertThrows(BusinessException.class, () -> addressService.add(USER2, r));

		r.setWard("Phường An Lạc");
		r.setAddressType("SHOP"); // chỉ được HOME / OFFICE
		assertThrows(BusinessException.class, () -> addressService.add(USER2, r));
	}

	@Test
	void diaChi_ghiChu_luuVaTraVe() {
		AddressReq r = new AddressReq();
		r.setReceiverName("Test Ghi Chú");
		r.setReceiverPhone("0900000006");
		r.setProvince("Thành phố Hồ Chí Minh");
		r.setWard("Phường An Lạc");
		r.setStreet("76 Đường số 1B");
		r.setAddressType("HOME");
		r.setNote("  Cổng sau, gọi trước khi tới  ");

		AddressRes res = addressService.add(6, r);
		assertEquals("Cổng sau, gọi trước khi tới", res.note()); // đã cắt khoảng trắng

		r.setNote("x".repeat(201));
		assertThrows(BusinessException.class, () -> addressService.add(6, r)); // quá 200 ký tự
	}

	// =====================================================================
	// 8. ĐƠN HÀNG CỦA TÔI (6 tab), HUỶ ĐƠN, LUỒNG TRẠNG THÁI (bước 6)
	// =====================================================================

	@Test
	void tab_donNamDungTab_vaDemSoDon() {
		assertTrue(co(orderFlowService.myOrders(USER1, OrderTab.PENDING), DON_CHO));
		assertFalse(co(orderFlowService.myOrders(USER1, OrderTab.PENDING), DON_DA_GIAO));
		assertTrue(co(orderFlowService.myOrders(USER1, OrderTab.DELIVERED), DON_DA_GIAO));

		var counts = orderFlowService.myOrderCounts(USER1);
		assertEquals(6, counts.size(), "Đủ 6 tab: Chờ xác nhận, Đang chuẩn bị, Đang giao, Đang giải quyết, Hoàn thành, Đã huỷ");
		assertTrue(counts.get("PENDING") >= 1);
		assertTrue(counts.get("DELIVERED") >= 1);
	}

	@Test
	void tab_dangGiao_gomCaPickedUpVaDelivering() {
		int id = orderService.placeOrder(USER2, giaoTanNoi(null)).getId();
		orderFlowService.updateStatus(id, OrderStatus.CONFIRMED, null);
		orderFlowService.updateStatus(id, OrderStatus.READY, null);
		assertTrue(co(orderFlowService.myOrders(USER2, OrderTab.CONFIRMED), id), "Pha xong (chờ shipper) nằm ở tab Đã xác nhận");
		orderFlowService.updateStatus(id, OrderStatus.PICKED_UP, null);
		assertTrue(co(orderFlowService.myOrders(USER2, OrderTab.SHIPPING), id), "PICKED_UP nằm ở tab Đang giao");

		orderFlowService.updateStatus(id, OrderStatus.DELIVERING, null);
		assertTrue(co(orderFlowService.myOrders(USER2, OrderTab.SHIPPING), id), "DELIVERING nằm ở tab Đang giao");
		assertEquals(1L, orderFlowService.myOrderCounts(USER2).get("SHIPPING"));
	}

	@Test
	void tab_thamSoSai_veChoXacNhan() {
		assertEquals(OrderTab.SHIPPING, OrderTab.parse("shipping"));
		assertEquals(OrderTab.PENDING, OrderTab.parse("abc"));
		assertEquals(OrderTab.PENDING, OrderTab.parse(null));
	}

	@Test
	void chiTiet_dungGiaDaChot() {
		OrderDetailRes d = orderFlowService.myOrder(USER1, DON_DA_GIAO);
		assertEquals(2, d.items().size());
		assertEquals(3, d.totalQty());
		assertEquals(0, d.itemsTotal().compareTo(java.math.BigDecimal.valueOf(148000)));
		assertEquals(0, d.totalAmount().compareTo(java.math.BigDecimal.valueOf(153000)));
		assertEquals("FREESHIP10K", d.shippingVoucherCode());
		assertTrue(d.items().stream().anyMatch(i -> i.toppings().contains("Trân châu đen")));
		assertEquals(6, d.steps().size(), "Đơn mới, Đang pha chế, Pha xong, Shipper lấy hàng, Đang giao, Đã giao");
		assertTrue(d.steps().stream().allMatch(OrderDetailRes.Step::done), "Đã giao: mọi bước đều xong");
		assertFalse(d.canCancel());
	}

	@Test
	void chiTiet_khongXemDuocDonNguoiKhac() {
		assertThrows(ResourceNotFoundException.class, () -> orderFlowService.myOrder(USER2, DON_DA_GIAO));
		assertThrows(ResourceNotFoundException.class, () -> orderFlowService.myOrder(USER1, 999999));
	}

	@Test
	void huy_donChoXacNhan_thanhCong() {
		orderFlowService.cancelByCustomer(USER1, DON_CHO, "  Đặt nhầm, không còn nhu cầu  ");
		em.flush();
		em.clear();
		Order o = orderRepository.findById(DON_CHO).orElseThrow();
		assertEquals(OrderStatus.CANCELLED, o.getStatus());
		assertEquals("Đặt nhầm, không còn nhu cầu", o.getCancelReason());
		assertNotNull(o.getUpdatedAt());
		assertTrue(co(orderFlowService.myOrders(USER1, OrderTab.CANCELLED), DON_CHO));
		assertTrue(orderFlowService.myOrder(USER1, DON_CHO).steps().isEmpty(), "Đã huỷ thì không vẽ tiến trình");
	}

	@Test
	void huy_khongLyDo_baoLoi() {
		assertThrows(BusinessException.class, () -> orderFlowService.cancelByCustomer(USER1, DON_CHO, "   "));
		assertEquals(OrderStatus.PENDING, trangThai(DON_CHO));
	}

	@Test
	void huy_donNguoiKhac_khongDuoc() {
		assertThrows(ResourceNotFoundException.class,
				() -> orderFlowService.cancelByCustomer(USER2, DON_CHO, "Thử huỷ đơn người khác"));
		assertEquals(OrderStatus.PENDING, trangThai(DON_CHO));
	}

	@Test
	void huy_donDaGiaoHoacDaXacNhan_khongDuoc() {
		assertThrows(BusinessException.class, () -> orderFlowService.cancelByCustomer(USER1, DON_DA_GIAO, "Đổi ý"));

		orderFlowService.updateStatus(DON_CHO, OrderStatus.CONFIRMED, null);
		assertThrows(BusinessException.class, () -> orderFlowService.cancelByCustomer(USER1, DON_CHO, "Đổi ý"));
		assertEquals(OrderStatus.CONFIRMED, trangThai(DON_CHO));
	}

	@Test
	void huy_hoanLuotVoucher_vaDungLaiDuoc() {
		var voucher = voucherRepository.findByCodeIgnoreCase("UTETRA20").orElseThrow();
		int voucherId = voucher.getId();
		int usedTruoc = voucher.getUsedCount();

		int id = orderService.placeOrder(USER2, giaoTanNoi("UTETRA20")).getId(); // 104.000 >= 80.000
		em.flush();
		em.clear();
		assertEquals(usedTruoc + 1, voucherRepository.findById(voucherId).orElseThrow().getUsedCount());
		assertEquals(1, voucherUsageRepository.countByVoucherIdAndUserId(voucherId, USER2));

		orderFlowService.cancelByCustomer(USER2, id, "Muốn dùng mã giảm giá khác");
		em.flush();
		em.clear();
		assertEquals(usedTruoc, voucherRepository.findById(voucherId).orElseThrow().getUsedCount(), "Phải trả lại lượt");
		assertEquals(0, voucherUsageRepository.countByVoucherIdAndUserId(voucherId, USER2), "Phải xoá lịch sử dùng mã");

		// UTETRA20 mỗi người 1 lần: huỷ rồi thì dùng lại được
		cartService.addItem(USER2, cartReq(7, "L", 70, 50, 2, 4));
		assertNotNull(orderService.placeOrder(USER2, giaoTanNoi("UTETRA20")).getId());
	}

	@Test
	void giaoTanNoi_luongDayDu_tangDaBan_vaCodThanhDaThanhToan() {
		int daBanTruoc = productRepository.findById(5).orElseThrow().getSoldCount();

		orderFlowService.updateStatus(DON_CHO, OrderStatus.CONFIRMED, null);
		orderFlowService.updateStatus(DON_CHO, OrderStatus.READY, null);
		orderFlowService.updateStatus(DON_CHO, OrderStatus.PICKED_UP, null);
		orderFlowService.updateStatus(DON_CHO, OrderStatus.DELIVERING, null);
		orderFlowService.updateStatus(DON_CHO, OrderStatus.DELIVERED, null);
		em.flush();
		em.clear();

		Order o = orderRepository.findById(DON_CHO).orElseThrow();
		assertEquals(OrderStatus.DELIVERED, o.getStatus());
		assertNotNull(o.getDeliveredAt());
		assertEquals(PaymentStatus.PAID, o.getPaymentStatus(), "COD giao xong là đã thu tiền");
		assertEquals(daBanTruoc + 1, productRepository.findById(5).orElseThrow().getSoldCount(), "Đã bán +1 ly");
		assertTrue(co(orderFlowService.myOrders(USER1, OrderTab.DELIVERED), DON_CHO));
	}

	@Test
	void saiLuong_baoLoi() {
		assertThrows(BusinessException.class, () -> orderFlowService.updateStatus(DON_CHO, OrderStatus.DELIVERED, null));
		assertThrows(BusinessException.class, () -> orderFlowService.updateStatus(DON_CHO, OrderStatus.PICKED_UP, null));
		assertThrows(BusinessException.class, () -> orderFlowService.updateStatus(DON_CHO, OrderStatus.RETURNED, null));
		assertThrows(BusinessException.class, () -> orderFlowService.updateStatus(DON_CHO, null, null));
		assertThrows(ResourceNotFoundException.class, () -> orderFlowService.updateStatus(999999, OrderStatus.CONFIRMED, null));
		assertEquals(OrderStatus.PENDING, trangThai(DON_CHO), "Sai luồng thì không được đổi gì");
	}

	@Test
	void denLay_luongNgan_khongQuaShipper() {
		int id = orderService.placeOrder(USER2, denLay()).getId();
		orderFlowService.updateStatus(id, OrderStatus.CONFIRMED, null);
		assertEquals("Đang pha chế", orderFlowService.myOrder(USER2, id).statusLabel());
		assertEquals(4, orderFlowService.myOrder(USER2, id).steps().size(), "Đơn mới, Đang pha chế, Chờ đến lấy, Đã nhận món");
		assertThrows(BusinessException.class, () -> orderFlowService.updateStatus(id, OrderStatus.DELIVERED, null),
				"Chưa pha xong thì chưa giao được");

		orderFlowService.updateStatus(id, OrderStatus.READY, null);
		assertEquals("Đã xong, chờ khách đến lấy", orderFlowService.myOrder(USER2, id).statusLabel());
		assertThrows(BusinessException.class, () -> orderFlowService.updateStatus(id, OrderStatus.PICKED_UP, null),
				"Đơn đến lấy không có bước shipper");

		orderFlowService.updateStatus(id, OrderStatus.DELIVERED, null);
		assertEquals(OrderStatus.DELIVERED, trangThai(id));
		assertEquals("Đã nhận món", orderFlowService.myOrder(USER2, id).statusLabel());
	}

	@Test
	void huy_khongTangDaBan() {
		int daBanTruoc = productRepository.findById(5).orElseThrow().getSoldCount();
		orderFlowService.cancelByCustomer(USER1, DON_CHO, "Đặt nhầm, không còn nhu cầu");
		em.flush();
		em.clear();
		assertEquals(daBanTruoc, productRepository.findById(5).orElseThrow().getSoldCount(), "Huỷ thì không tăng đã bán");
	}

	// =====================================================================
	// 9. QUẦY ĐƠN HÀNG CỦA MANAGER + THÔNG BÁO (bước 7)
	// =====================================================================

	@Test
	void manager_chiThayChiNhanhCuaMinh() {
		assertEquals(1, managerOrderService.myBranches(MANAGER2).size());
		assertEquals(2, managerOrderService.resolveBranchId(MANAGER2, null), "Không chọn thì lấy chi nhánh đầu tiên");
		assertThrows(ResourceNotFoundException.class, () -> managerOrderService.resolveBranchId(MANAGER1, 2));
		assertThrows(ResourceNotFoundException.class, () -> managerOrderService.board(MANAGER1, 2));
		assertThrows(BusinessException.class, () -> managerOrderService.resolveBranchId(USER1, null),
				"Khách không được giao quản lý chi nhánh nào");
	}

	@Test
	void bang_donMoiNamCotDonMoi_coNutXacNhan() {
		ManagerBoardRes b = managerOrderService.board(MANAGER2, 2);
		ManagerOrderRes don = tim(b.newOrders(), DON_CHO);
		assertNotNull(don, "Đơn #2 PENDING phải ở cột Đơn mới");
		assertTrue(don.actions().stream().anyMatch(a -> a.to().equals("CONFIRMED")));
		assertTrue(don.canCancel());
		assertFalse(don.items().isEmpty());
		assertNull(tim(managerOrderService.board(MANAGER1, 1).newOrders(), DON_CHO), "Chi nhánh 1 không thấy đơn chi nhánh 2");
	}

	@Test
	void managerKhac_khongDoiDuocDon() {
		assertThrows(ResourceNotFoundException.class,
				() -> managerOrderService.changeStatus(MANAGER1, DON_CHO, OrderStatus.CONFIRMED, null));
		assertEquals(OrderStatus.PENDING, trangThai(DON_CHO));
	}

	@Test
	void xacNhan_luuThongBaoChoKhach_vaQuaCotDangPha() {
		long truoc = notificationRepository.countByUserIdAndOrderId(USER1, DON_CHO);
		managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.CONFIRMED, null);

		assertEquals(OrderStatus.CONFIRMED, trangThai(DON_CHO));
		assertEquals(truoc + 1, notificationRepository.countByUserIdAndOrderId(USER1, DON_CHO), "Khách phải có thông báo");
		ManagerOrderRes don = tim(managerOrderService.board(MANAGER2, 2).making(), DON_CHO);
		assertNotNull(don, "Nhận đơn phải sang cột Đang pha chế");
		assertTrue(don.actions().stream().anyMatch(a -> a.to().equals("READY")), "Đang pha: có nút Pha xong");
		assertTrue(don.canCancel(), "Đang pha mà hết nguyên liệu thì quán huỷ được");

		// Pha xong: đơn giao tận nơi chờ shipper, quán không còn nút tiếp, không huỷ được nữa
		managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.READY, null);
		ManagerOrderRes xong = tim(managerOrderService.board(MANAGER2, 2).shipping(), DON_CHO);
		assertNotNull(xong, "Pha xong phải sang cột Pha xong / đang giao");
		assertTrue(xong.actions().isEmpty());
		assertFalse(xong.canCancel());
	}

	@Test
	void donGiao_quanKhongTuLamBuocCuaShipper() {
		managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.CONFIRMED, null);
		assertThrows(BusinessException.class,
				() -> managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.PICKED_UP, null));
		assertThrows(BusinessException.class,
				() -> managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.DELIVERED, null));
		assertEquals(OrderStatus.CONFIRMED, trangThai(DON_CHO));
	}

	@Test
	void denLay_datDon_quanCoThongBao_xacNhanRoiKhachNhan() {
		int id = orderService.placeOrder(USER2, denLay()).getId();
		assertEquals(1, notificationRepository.countByUserIdAndOrderId(MANAGER2, id), "Chủ chi nhánh phải có thông báo đơn mới");

		managerOrderService.changeStatus(MANAGER2, id, OrderStatus.CONFIRMED, null);
		ManagerOrderRes don = tim(managerOrderService.board(MANAGER2, 2).making(), id);
		assertNotNull(don);
		assertTrue(don.actions().stream().anyMatch(a -> a.to().equals("READY")), "Đang pha: có nút Pha xong, báo khách đến lấy");

		managerOrderService.changeStatus(MANAGER2, id, OrderStatus.READY, null);
		ManagerOrderRes cho = tim(managerOrderService.board(MANAGER2, 2).shipping(), id);
		assertNotNull(cho, "Đã xong, chờ khách lấy");
		assertTrue(cho.actions().stream().anyMatch(a -> a.to().equals("DELIVERED")), "Đến lấy: có nút Khách đã nhận món");
		assertTrue(cho.canCancel(), "Khách không đến lấy thì quán huỷ được");

		managerOrderService.changeStatus(MANAGER2, id, OrderStatus.DELIVERED, null);
		assertEquals(OrderStatus.DELIVERED, trangThai(id));
		assertNotNull(tim(managerOrderService.board(MANAGER2, 2).recent(), id), "Xong rồi phải sang cột Gần đây");
	}

	@Test
	void quanHuy_batBuocLyDo_vaBaoKhach() {
		assertThrows(BusinessException.class,
				() -> managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.CANCELLED, "  "));
		long truoc = notificationRepository.countByUserIdAndOrderId(USER1, DON_CHO);

		managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.CANCELLED, "Quán hết nguyên liệu / hết món");
		assertEquals(OrderStatus.CANCELLED, trangThai(DON_CHO));
		assertEquals(truoc + 1, notificationRepository.countByUserIdAndOrderId(USER1, DON_CHO));
	}

	@Test
	void khachHuy_quanCoThongBao() {
		long truoc = notificationRepository.countByUserIdAndOrderId(MANAGER2, DON_CHO);
		orderFlowService.cancelByCustomer(USER1, DON_CHO, "Đặt nhầm, không còn nhu cầu");
		assertEquals(truoc + 1, notificationRepository.countByUserIdAndOrderId(MANAGER2, DON_CHO), "Quán phải biết khách huỷ");
	}

	@Test
	void quanHuy_cungHoanLuotVoucher() {
		Voucher v = voucherRepository.findByCodeIgnoreCase("UTETRA20").orElseThrow();
		int voucherId = v.getId();
		int usedTruoc = v.getUsedCount();

		int id = orderService.placeOrder(USER2, giaoTanNoi("UTETRA20")).getId(); // chi nhánh 2
		managerOrderService.changeStatus(MANAGER2, id, OrderStatus.CANCELLED, "Quán hết nguyên liệu / hết món");
		em.flush();
		em.clear();
		assertEquals(usedTruoc, voucherRepository.findById(voucherId).orElseThrow().getUsedCount(), "Quán huỷ cũng trả lượt");
		assertEquals(0, voucherUsageRepository.countByVoucherIdAndUserId(voucherId, USER2));
	}

	// =====================================================================
	// 10. ĐƠN HÀNG CỦA SHOP THEO 8 TRẠNG THÁI (bước 7b, thêm Pha xong)
	// =====================================================================

	@Test
	void tabDonCuaShop_dungThuTu() {
		ManagerHistoryRes h = managerOrderService.history(MANAGER2, null, null, 0);
		assertEquals(7, h.tabs().size());
		assertEquals("PENDING", h.tabs().get(0).status());
		assertEquals("Đơn mới", h.tabs().get(0).label());
		assertEquals("Đang chuẩn bị", h.tabs().get(1).label());
		assertEquals("READY", h.tabs().get(2).status());
		assertEquals("Pha xong / chờ lấy", h.tabs().get(2).label());
		assertEquals("SHIPPING", h.tabs().get(3).status());
		assertEquals("Đã giao shipper / Đang giao", h.tabs().get(3).label());
		assertEquals("Hoàn thành", h.tabs().get(4).label());
		assertEquals("Đã huỷ", h.tabs().get(5).label());
		assertEquals("ISSUE", h.tabs().get(6).status());
		assertEquals("Khách báo vấn đề", h.tabs().get(6).label());
		assertEquals("PENDING", h.status(), "Không chọn tab thì mở tab Đơn mới");
		assertTrue(co(h, DON_CHO));
		assertTrue(dem(h, "PENDING") >= 1);
	}

	@Test
	void daLayHangVaDangGiao_chungTabDangGiao() {
		orderFlowService.updateStatus(DON_CHO, OrderStatus.CONFIRMED, null);
		orderFlowService.updateStatus(DON_CHO, OrderStatus.READY, null);
		orderFlowService.updateStatus(DON_CHO, OrderStatus.PICKED_UP, null); // giả lập shipper lấy hàng
		em.flush();
		em.clear();

		assertTrue(co(managerOrderService.history(MANAGER2, 2, "SHIPPING", 0), DON_CHO), "Shipper đã lấy -> tab Đang giao");
		assertTrue(co(managerOrderService.history(MANAGER2, 2, "PICKED_UP", 0), DON_CHO), "link cũ vẫn mở đúng tab");
		assertFalse(co(managerOrderService.history(MANAGER2, 2, "READY", 0), DON_CHO));

		orderFlowService.updateStatus(DON_CHO, OrderStatus.DELIVERING, null);
		em.flush();
		em.clear();
		ManagerHistoryRes dangGiao = managerOrderService.history(MANAGER2, 2, "SHIPPING", 0);
		assertTrue(co(dangGiao, DON_CHO));
		assertEquals("SHIPPING", dangGiao.status());
		assertTrue(dem(dangGiao, "SHIPPING") >= 1);
	}

	@Test
	void huy_nam_tabDaHuy() {
		managerOrderService.changeStatus(MANAGER2, DON_CHO, OrderStatus.CANCELLED, "Ngoài giờ phục vụ");
		em.flush();
		em.clear();
		assertTrue(co(managerOrderService.history(MANAGER2, 2, "CANCELLED", 0), DON_CHO));
		assertFalse(co(managerOrderService.history(MANAGER2, 2, "PENDING", 0), DON_CHO));
	}

	@Test
	void khongXemDuocChiNhanhVaDonNguoiKhac() {
		assertThrows(ResourceNotFoundException.class, () -> managerOrderService.history(MANAGER1, 2, null, 0));
		assertThrows(ResourceNotFoundException.class, () -> managerOrderService.orderDetail(MANAGER1, DON_CHO));
		assertThrows(ResourceNotFoundException.class, () -> managerOrderService.orderCard(MANAGER2, DON_DA_GIAO));
	}

	@Test
	void chiTiet_vaNutXuLy() {
		var d = managerOrderService.orderDetail(MANAGER2, DON_CHO);
		assertEquals(DON_CHO, d.id());
		var card = managerOrderService.orderCard(MANAGER2, DON_CHO);
		assertTrue(card.canCancel());
		assertTrue(card.actions().stream().anyMatch(a -> a.to().equals("CONFIRMED")));
		assertFalse(managerOrderService.orderCard(MANAGER1, DON_DA_GIAO).canCancel(), "Đơn đã giao không huỷ được");
	}

	@Test
	void phanTrang_trangAmVeTrangDau() {
		ManagerHistoryRes h = managerOrderService.history(MANAGER1, 1, "DELIVERED", -5);
		assertEquals(0, h.page());
		assertTrue(co(h, DON_DA_GIAO));
		assertTrue(h.totalPages() >= 1);
	}

	// ================= (CHỨC NĂNG MỞ RỘNG) MANAGER KIỂM TRA KHÁCH HÀNG CỦA CHI NHÁNH =================

	@Test
	void khachHang_chiNhanh1_coUser1_soDonVaTongChiTieu() {
		List<ManagerCustomerRes> list = managerCustomerService.customers(MANAGER1, 1, null);
		ManagerCustomerRes c = list.stream().filter(x -> x.userId() == USER1).findFirst().orElseThrow();
		assertEquals(1, c.orderCount(), "Chi nhánh 1 chỉ có đơn #1 của user1");
		assertEquals(1, c.deliveredCount());
		assertEquals(0, c.cancelledCount());
		assertMoney(148000, c.totalSpent()); // tiền món 148.000, không tính 5.000 phí ship còn lại
		assertEquals("Thành viên", c.tierName());
		assertTrue(list.stream().noneMatch(x -> x.userId() == USER2), "user2 chưa đặt đơn ở chi nhánh 1");
	}

	@Test
	void khachHang_donChuaGiao_khongTinhVaoChiTieu() {
		// Chi nhánh 2: user1 có đơn #2 đang chờ xác nhận -> có trong danh sách nhưng chưa có chi tiêu
		ManagerCustomerRes c = managerCustomerService.customers(MANAGER2, 2, null).stream()
				.filter(x -> x.userId() == USER1).findFirst().orElseThrow();
		assertEquals(1, c.orderCount());
		assertEquals(0, c.deliveredCount());
		assertMoney(0, c.totalSpent());
	}

	@Test
	void khachHang_timTheoTenHoacSdt() {
		assertEquals(1, managerCustomerService.customers(MANAGER1, 1, "0900000006").size());
		assertEquals(1, managerCustomerService.customers(MANAGER1, 1, "trần thị").size(), "Tìm không phân biệt hoa thường");
		assertTrue(managerCustomerService.customers(MANAGER1, 1, "khong-co-ai").isEmpty());
	}

	@Test
	void khachHang_chiTiet_chiCoDonCuaChiNhanhMinh() {
		ManagerCustomerDetailRes d = managerCustomerService.customer(MANAGER2, 2, USER1);
		assertEquals(1, d.orders().size());
		assertEquals(DON_CHO, d.orders().get(0).id(), "Không lẫn đơn #1 của chi nhánh 1");
		assertEquals("PENDING", d.orders().get(0).status());
	}

	@Test
	void khachHang_khongXemDuocChiNhanhKhac_vaKhachChuaMua() {
		assertThrows(ResourceNotFoundException.class, () -> managerCustomerService.customers(MANAGER1, 2, null));
		assertThrows(ResourceNotFoundException.class, () -> managerCustomerService.customer(MANAGER1, 2, USER1));
		assertThrows(ResourceNotFoundException.class, () -> managerCustomerService.customer(MANAGER2, 2, USER2),
				"user2 chưa đặt đơn ở chi nhánh 2");
	}
}
