package vn.utetra.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.AddressReq;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.AddressRes;
import vn.utetra.dto.response.OrderSummaryRes;
import vn.utetra.dto.response.VoucherOptionRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test bước 5: khung "Mã của bạn", sổ địa chỉ, tóm tắt đơn. Mỗi test tự
 * rollback
 */
@SpringBootTest
@Transactional
class Buoc5Test {

	@Autowired
	CartService cartService;
	@Autowired
	OrderService orderService;
	@Autowired
	AddressService addressService;
	@Autowired
	EntityManager em;

	private static final int USER1 = 6; // đã có đơn, địa chỉ #1 (mặc định), #2
	private static final int USER2 = 7; // tài khoản mới, địa chỉ #3

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

	private CheckoutReq giao(int branchId, int addressId, int carrierId) {
		CheckoutReq r = new CheckoutReq();
		r.setDeliveryType(DeliveryType.DELIVERY);
		r.setBranchId(branchId);
		r.setAddressId(addressId);
		r.setCarrierId(carrierId);
		r.setPaymentMethod(PaymentMethod.COD);
		return r;
	}

	private CheckoutReq denLay(int branchId) {
		CheckoutReq r = new CheckoutReq();
		r.setDeliveryType(DeliveryType.PICKUP);
		r.setBranchId(branchId);
		r.setReceiverName("Phạm Văn Mua");
		r.setReceiverPhone("0900000007");
		r.setPaymentMethod(PaymentMethod.COD);
		return r;
	}

	private Optional<VoucherOptionRes> tim(List<VoucherOptionRes> list, String code) {
		return list.stream().filter(v -> v.code().equals(code)).findFirst();
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

	@BeforeEach
	void datLaiGioMau() {
		cartService.clear(USER1);
		cartService.addItem(USER1, cartReq(3, "M", 50, 70, 1)); // 34.000
		cartService.clear(USER2);
		cartService.addItem(USER2, cartReq(7, "L", 70, 50, 2, 4)); // 104.000
	}

	// ================= KHUNG "MÃ CỦA BẠN" =================
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

	// ================= SỔ ĐỊA CHỈ =================
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

	// ================= TÓM TẮT ĐƠN =================
	@Test
	void tomTatDon_chiChuDonXemDuoc() {
		Order o = orderService.placeOrder(USER2, giao(1, 3, 1));
		em.flush();
		em.clear();

		OrderSummaryRes s = orderService.getOrderSummary(USER2, o.getId());
		assertEquals("UTETra Võ Văn Ngân", s.branchName());
		assertEquals(DeliveryType.DELIVERY, s.deliveryType());
		assertEquals("Giao Hàng Nhanh", s.carrierName());
		assertEquals(2, s.totalQty());
		assertMoney(119000, s.totalAmount()); // 104.000 + 15.000 ship

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
}