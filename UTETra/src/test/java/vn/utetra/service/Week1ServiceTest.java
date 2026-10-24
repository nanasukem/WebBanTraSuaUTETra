package vn.utetra.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.BranchReq;
import vn.utetra.dto.request.VoucherReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.DiscountType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.VoucherApplyTo;
import vn.utetra.exception.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class Week1ServiceTest {

	@Autowired
	BranchService branchService;
	@Autowired
	VoucherService voucherService;
	@Autowired
	EntityManager em;

	@BeforeEach
	void chuanHoaDuLieuMau() {
		em.createNativeQuery("UPDATE Users SET carrier_id = 1 WHERE carrier_id IS NOT NULL AND carrier_id <> 1").executeUpdate();
		em.createNativeQuery("UPDATE Orders SET carrier_id = 1 WHERE delivery_type = 'DELIVERY'").executeUpdate();
		em.createNativeQuery("DELETE FROM Carriers WHERE id <> 1 AND name = N'Ahamove'").executeUpdate();
		em.createNativeQuery("UPDATE Carriers SET name = N'Shipper UTETra', fee = 15000, per_km_fee = 10000, is_active = 1 WHERE id = 1").executeUpdate();
		em.createNativeQuery("DELETE FROM VoucherUsages WHERE voucher_id IN (SELECT id FROM Vouchers WHERE apply_to = 'SHIPPING')").executeUpdate();
		em.createNativeQuery("DELETE FROM Vouchers WHERE apply_to = 'SHIPPING'").executeUpdate();
		em.createNativeQuery("UPDATE Vouchers SET per_user_limit = 2, used_count = 1 WHERE code = 'VVN15'").executeUpdate();
		em.createNativeQuery("UPDATE Orders SET product_voucher_id = (SELECT id FROM Vouchers WHERE code = 'VVN15'), "
				+ "items_total = 148000, product_discount = 15000, shipping_fee = 15000, total_amount = 148000 WHERE id = 1").executeUpdate();
		em.createNativeQuery("INSERT INTO VoucherUsages (voucher_id, user_id, order_id) "
				+ "SELECT v.id, 6, 1 FROM Vouchers v WHERE v.code = 'VVN15' "
				+ "AND NOT EXISTS (SELECT 1 FROM VoucherUsages u WHERE u.voucher_id = v.id AND u.order_id = 1)").executeUpdate();
		em.clear();
	}

	private static BigDecimal vnd(long n) {
		return BigDecimal.valueOf(n);
	}

	private static void assertMoney(long expected, BigDecimal actual) {
		assertEquals(0, vnd(expected).compareTo(actual), "Mong đợi " + expected + " nhưng là " + actual);
	}

	@Test
	void entity_docDonHangMau_dungDuLieu() {
		Order o = em.find(Order.class, 1);
		assertNotNull(o, "Không đọc được đơn #1");
		assertEquals(OrderStatus.DELIVERED, o.getStatus());
		assertEquals(PaymentMethod.VNPAY, o.getPaymentMethod());
		assertMoney(148000, o.getTotalAmount());
		assertMoney(148000, o.getSalesAmount());
		assertEquals(2, o.getDetails().size(), "Đơn #1 phải có 2 dòng chi tiết");
		int soTopping = o.getDetails().stream().mapToInt(d -> d.getToppings().size()).sum();
		assertEquals(2, soTopping, "Đơn #1 phải có 2 topping");
		assertNotNull(o.getBranch().getName());
		assertNotNull(o.getProductVoucher(), "Đơn #1 có dùng voucher giảm tiền món VVN15");
	}

	@Test
	void branch_themHopLe_tuDongDuyet() {
		BranchReq req = new BranchReq();
		req.setName("UTETra Test JUnit");
		req.setAddress("123 Đường Test");
		req.setPhone("0912345678");
		req.setOwnerId(2);
		Branch b = branchService.create(req);
		assertNotNull(b.getId());
		assertEquals(BranchStatus.APPROVED, b.getStatus());
	}

	@Test
	void branch_managerDaCoChiNhanh_baoLoi() {
		BranchReq req = new BranchReq();
		req.setName("UTETra Chi Nhánh Thứ Hai");
		req.setAddress("abc");
		req.setOwnerId(3);
		BusinessException ex = assertThrows(BusinessException.class, () -> branchService.create(req));
		assertTrue(ex.getMessage().contains("chỉ quản lý 1 chi nhánh"));

		BranchReq sua = branchService.toReq(branchService.findById(2));
		sua.setOwnerId(3);
		assertThrows(BusinessException.class, () -> branchService.update(2, sua));

		BranchReq giuNguyen = branchService.toReq(branchService.findById(1));
		assertEquals(3, branchService.update(1, giuNguyen).getOwner().getId());
	}

	@Test
	void branch_trungTen_baoLoi() {
		BranchReq req = new BranchReq();
		req.setName("utetra quận 1");
		req.setAddress("abc");
		req.setOwnerId(2);
		assertThrows(BusinessException.class, () -> branchService.create(req));
	}

	@Test
	void branch_chuShopKhongPhaiManager_baoLoi() {
		BranchReq req = new BranchReq();
		req.setName("Shop của user thường");
		req.setAddress("abc");
		req.setOwnerId(6);
		assertThrows(BusinessException.class, () -> branchService.create(req));
	}

	@Test
	void branch_doiTrangThai_dungLuong() {
		branchService.changeStatus(2, BranchStatus.SUSPENDED);
		assertEquals(BranchStatus.SUSPENDED, branchService.findById(2).getStatus());
		branchService.changeStatus(2, BranchStatus.APPROVED);
		assertEquals(BranchStatus.APPROVED, branchService.findById(2).getStatus());
		branchService.changeStatus(2, BranchStatus.APPROVED);
		assertEquals("Tạm ngưng", BranchStatus.SUSPENDED.getLabel());
	}

	@Test
	void branch_timKiem() {
		assertEquals(1, branchService.search("quận 1", null).size());
		assertEquals(2, branchService.search(null, BranchStatus.APPROVED).size());
		assertEquals(0, branchService.search(null, BranchStatus.SUSPENDED).size());
	}

	@Test
	void voucher_giamSoTien_cuaShop() {
		assertMoney(15000, voucherService.calculateDiscount("VVN15", 1, vnd(100000)));
	}

	@Test
	void voucher_giamSoTien_tienMonNhoHonMucGiam_khongGiamQua() {
		VoucherReq req = voucherReq("JUNITCAP1");
		req.setDiscountValue(vnd(20000));
		req.setMinOrderValue(vnd(0));
		voucherService.create(req);
		assertMoney(12000, voucherService.calculateDiscount("JUNITCAP1", 1, vnd(12000)));
	}

	@Test
	void voucher_chuaDuDonToiThieu_baoLoi() {
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("UTETRA20", 1, vnd(40000)));
	}

	@Test
	void voucher_phanTram_vaTranGiam() {
		assertMoney(20000, voucherService.calculateDiscount("UTETRA20", 2, vnd(100000)));
		assertMoney(30000, voucherService.calculateDiscount("utetra20", 2, vnd(500000)));
	}

	@Test
	void voucher_cuaShop_chiDungDungShop() {
		assertMoney(15000, voucherService.calculateDiscount("VVN15", 1, vnd(60000)));
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("VVN15", 2, vnd(60000)));
	}

	@Test
	void voucher_khongTonTai_baoLoi() {
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("KHONGCO", 1, vnd(100000)));
	}

	@Test
	void voucher_danhSachDungDuoc_theoShop() {
		List<String> shop1 = voucherService.findUsable(1).stream().map(Voucher::getCode).toList();
		List<String> shop2 = voucherService.findUsable(2).stream().map(Voucher::getCode).toList();
		assertTrue(shop1.containsAll(List.of("UTETRA20", "VVN15")));
		assertFalse(shop2.contains("VVN15"), "Chi nhánh 2 không được thấy voucher riêng của chi nhánh 1");
	}

	@Test
	void voucher_tatVaDanhSachKhongConHien() {
		Voucher v = voucherService.findAppVouchers().stream().filter(x -> x.getCode().equals("UTETRA20")).findFirst()
				.orElseThrow();
		voucherService.toggleActive(v.getId());
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("UTETRA20", 1, vnd(100000)));
	}

	@Test
	void branch_capNhat_doiTen() {
		BranchReq req = branchService.toReq(branchService.findById(2));
		req.setName("UTETra Đổi Tên JUnit");
		branchService.update(2, req);
		assertEquals("UTETra Đổi Tên JUnit", branchService.findById(2).getName());
	}

	@Test
	void branch_findApproved_anShopBiTamNgung() {
		assertTrue(branchService.findApproved().stream().allMatch(b -> b.getStatus() == BranchStatus.APPROVED));
		branchService.changeStatus(2, BranchStatus.SUSPENDED);
		assertTrue(branchService.findApproved().stream().noneMatch(b -> b.getId().equals(2)),
				"Shop bị tạm ngưng không được hiện cho khách");
	}

	@Test
	void branch_findManagers_chiLayManager() {
		List<Integer> ids = branchService.findManagers().stream().map(User::getId).toList();
		assertTrue(ids.contains(3), "manager1 (id 3) phải có trong danh sách");
		assertTrue(ids.contains(2), "manager3 (id 2, chưa có chi nhánh) cũng là Manager");
		assertFalse(ids.contains(6), "user1 (id 6) không phải manager");
	}

	private VoucherReq voucherReq(String code) {
		VoucherReq r = new VoucherReq();
		r.setCode(code);
		r.setName("Voucher test JUnit");
		r.setBranchId(null);
		r.setApplyTo(VoucherApplyTo.PRODUCT);
		r.setDiscountType(DiscountType.AMOUNT);
		r.setDiscountValue(vnd(10000));
		r.setMinOrderValue(vnd(0));
		r.setQuantity(5);
		r.setStartDate(LocalDateTime.now().minusDays(1));
		r.setEndDate(LocalDateTime.now().plusDays(7));
		return r;
	}

	@Test
	void voucher_taoMoi_dungDuocNgay() {
		Voucher v = voucherService.create(voucherReq("JUNITNEW1"));
		assertNotNull(v.getId());
		assertEquals(0, v.getUsedCount());
		assertEquals(VoucherApplyTo.PRODUCT, v.getApplyTo());
		assertTrue(voucherService.findAppVouchers().stream().anyMatch(x -> x.getId().equals(v.getId())));
		assertMoney(10000, voucherService.calculateDiscount("JUNITNEW1", 1, vnd(100000)));
	}

	@Test
	void voucher_trungMa_baoLoi() {
		assertThrows(BusinessException.class, () -> voucherService.create(voucherReq("UTETRA20")));
	}

	@Test
	void voucher_cuaShop_khongLanVaoVoucherApp() {
		VoucherReq req = voucherReq("JUNITSHOP1");
		req.setBranchId(1);
		Voucher v = voucherService.create(req);
		assertTrue(voucherService.findByBranch(1).stream().anyMatch(x -> x.getId().equals(v.getId())));
		assertTrue(voucherService.findAppVouchers().stream().noneMatch(x -> x.getId().equals(v.getId())));
	}

	@Test
	void voucher_hetHan_baoLoi() {
		Voucher v = voucherService.create(voucherReq("JUNITEXP1"));
		v.setEndDate(LocalDateTime.now().minusMinutes(1));
		em.flush();
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("JUNITEXP1", 1, vnd(100000)));
	}

	@Test
	void voucher_markUsed_tangLuot_vaHetLuotThiBaoLoi() {
		VoucherReq req = voucherReq("JUNITQTY1");
		req.setQuantity(1);
		Voucher v = voucherService.create(req);
		voucherService.markUsed(v.getId());
		em.flush();
		em.clear();
		assertEquals(1, voucherService.findById(v.getId()).getUsedCount());
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("JUNITQTY1", 1, vnd(100000)));
	}

	@Test
	void voucher_xoa_chuaAiDung_thiXoaHan() {
		Voucher v = voucherService.create(voucherReq("JUNITDEL1"));
		voucherService.delete(v.getId());
		em.flush();
		assertThrows(RuntimeException.class, () -> voucherService.findById(v.getId()));
	}

	@Test
	void voucher_xoa_daCoNguoiDung_thiChiTat() {
		Voucher v = voucherService.create(voucherReq("JUNITDEL2"));
		voucherService.markUsed(v.getId());
		voucherService.delete(v.getId());
		em.flush();
		em.clear();
		Voucher sauXoa = voucherService.findById(v.getId());
		assertFalse(sauXoa.getIsActive(), "Voucher đã dùng chỉ được tắt, không xoá");
	}
}