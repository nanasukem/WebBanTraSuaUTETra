package vn.utetra.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.BranchReq;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.NearbyBranchRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test bước 4C + 4D: đến lấy, voucher theo thanh toán, chi nhánh gần nhất, quyền chủ chi nhánh.
 * Mỗi test tự rollback, không làm bẩn DB.
 */
@SpringBootTest
@Transactional
class Buoc4CDTest {

	@Autowired
	CartService cartService;
	@Autowired
	OrderService orderService;
	@Autowired
	BranchService branchService;
	@Autowired
	OrderRepository orderRepository;
	@Autowired
	EntityManager em;

	private static final int USER1 = 6; // địa chỉ #1
	private static final int USER2 = 7; // tài khoản mới, hạng Thành viên

	private static void assertMoney(long expected, BigDecimal actual) {
		assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual), "Mong đợi " + expected + " nhưng là " + actual);
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

	/** Giao tận nơi. carrierId 1 = Giao Hàng Nhanh 15.000 */
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

	private NearbyBranchRes timTheoId(List<NearbyBranchRes> list, int id) {
		return list.stream().filter(b -> b.id() == id).findFirst().orElseThrow();
	}

	/** Trước MỖI test: dựng lại giỏ mẫu cố định (cũng tự rollback) */
	@BeforeEach
	void datLaiGioMau() {
		cartService.clear(USER1);
		cartService.addItem(USER1, cartReq(3, "M", 50, 70, 1));    // 1 Bạc xỉu M = 34.000
		cartService.clear(USER2);
		cartService.addItem(USER2, cartReq(7, "L", 70, 50, 2, 4)); // 2 Cà phê muối L + Kem cheese = 104.000
	}

	// ================= 4C: ĐẾN LẤY TẠI CỬA HÀNG =================
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
		assertMoney(5200, saved.getCommissionAmount());  // 5% x 104.000
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

	// ================= 4C: VOUCHER THEO PHƯƠNG THỨC THANH TOÁN =================
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

	/** Tuần 3 tích hợp MoMo thì sửa test này thành đặt được */
	@Test
	void thanhToanMomo_tamThoiChuaDatDuoc() {
		CheckoutPreviewRes p = orderService.preview(USER1, giaoTanNoi(1, 1, 1, null, PaymentMethod.MOMO));
		assertFalse(p.canOrder());
		assertFalse(p.errors().isEmpty());
		assertThrows(BusinessException.class,
				() -> orderService.placeOrder(USER1, giaoTanNoi(1, 1, 1, null, PaymentMethod.MOMO)));
	}

	// ================= 4D: CHI NHÁNH GẦN NHẤT =================
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
		req.setOwnerId(3);
		req.setLatitude(new BigDecimal("10.8"));
		// thiếu longitude
		assertThrows(BusinessException.class, () -> branchService.create(req));
	}

	// ================= QUYỀN CHỦ CHI NHÁNH =================
	@Test
	void isOwner_chiDungChuChiNhanh() {
		assertTrue(branchService.isOwner(1, "vendor1"), "vendor1 là chủ chi nhánh 1");
		assertFalse(branchService.isOwner(1, "vendor2"), "vendor2 không phải chủ chi nhánh 1");
		assertFalse(branchService.isOwner(1, "user1"), "khách thường không phải chủ");
		assertFalse(branchService.isOwner(999, "vendor1"), "chi nhánh không tồn tại");
	}
}