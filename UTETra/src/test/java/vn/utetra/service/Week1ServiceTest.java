package vn.utetra.service;

import jakarta.persistence.EntityManager;
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

/**
 * Test tuần 1 - chạy trên database UTETra có dữ liệu mẫu.
 * 
 * @Transactional: mỗi test chạy xong tự rollback, không làm bẩn DB.
 */
@SpringBootTest
@Transactional
class Week1ServiceTest {

	@Autowired
	BranchService branchService;
	@Autowired
	VoucherService voucherService;
	@Autowired
	EntityManager em;

	private static BigDecimal vnd(long n) {
		return BigDecimal.valueOf(n);
	}

	private static void assertMoney(long expected, BigDecimal actual) {
		assertEquals(0, vnd(expected).compareTo(actual), "Mong đợi " + expected + " nhưng là " + actual);
	}

	// ================= ENTITY: đọc đơn hàng mẫu #1 =================
	@Test
	void entity_docDonHangMau_dungDuLieu() {
		Order o = em.find(Order.class, 1);
		assertNotNull(o, "Không đọc được đơn #1");
		assertEquals(OrderStatus.DELIVERED, o.getStatus());
		assertEquals(PaymentMethod.VNPAY, o.getPaymentMethod());
		assertMoney(153000, o.getTotalAmount());
		assertEquals(2, o.getDetails().size(), "Đơn #1 phải có 2 dòng chi tiết");
		int soTopping = o.getDetails().stream().mapToInt(d -> d.getToppings().size()).sum();
		assertEquals(2, soTopping, "Đơn #1 phải có 2 topping");
		assertNotNull(o.getBranch().getName());
		assertNotNull(o.getShippingVoucher(), "Đơn #1 có dùng voucher FREESHIP10K");
	}

	// ================= BRANCH SERVICE =================
	@Test
	void branch_themHopLe_tuDongDuyet() {
		BranchReq req = new BranchReq();
		req.setName("UTETra Test JUnit");
		req.setAddress("123 Đường Test");
		req.setPhone("0912345678");
		req.setOwnerId(3); // vendor1
		req.setCommissionRate(new BigDecimal("7.5"));
		Branch b = branchService.create(req);
		assertNotNull(b.getId());
		assertEquals(BranchStatus.APPROVED, b.getStatus());
	}

	@Test
	void branch_trungTen_baoLoi() {
		BranchReq req = new BranchReq();
		req.setName("utetra quận 1"); // trùng, khác hoa thường
		req.setAddress("abc");
		req.setOwnerId(3);
		assertThrows(BusinessException.class, () -> branchService.create(req));
	}

	@Test
	void branch_chuShopKhongPhaiVendor_baoLoi() {
		BranchReq req = new BranchReq();
		req.setName("Shop của user thường");
		req.setAddress("abc");
		req.setOwnerId(6); // user1 - role USER
		assertThrows(BusinessException.class, () -> branchService.create(req));
	}

	@Test
	void branch_doiTrangThai_dungLuong() {
		branchService.changeStatus(2, BranchStatus.SUSPENDED); // APPROVED -> SUSPENDED: được
		assertEquals(BranchStatus.SUSPENDED, branchService.findById(2).getStatus());
		branchService.changeStatus(2, BranchStatus.APPROVED); // mở lại: được
		assertThrows(BusinessException.class, // APPROVED -> REJECTED: không được
				() -> branchService.changeStatus(2, BranchStatus.REJECTED));
	}

	@Test
	void branch_timKiem() {
		assertEquals(1, branchService.search("quận 1", null).size());
		assertEquals(2, branchService.search(null, BranchStatus.APPROVED).size());
		assertEquals(0, branchService.search(null, BranchStatus.PENDING).size());
	}

	// ================= VOUCHER SERVICE =================
	@Test
	void voucher_freeship_giam10k() {
		assertMoney(10000, voucherService.calculateDiscount("FREESHIP10K", 1, vnd(100000), vnd(15000)));
	}

	@Test
	void voucher_freeship_phiShipNhoHon_khongGiamQua() {
		assertMoney(8000, voucherService.calculateDiscount("FREESHIP10K", 1, vnd(100000), vnd(8000)));
	}

	@Test
	void voucher_chuaDuDonToiThieu_baoLoi() {
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("FREESHIP10K", 1, vnd(40000), vnd(15000)));
	}

	@Test
	void voucher_phanTram_vaTranGiam() {
		assertMoney(20000, voucherService.calculateDiscount("UTETRA20", 2, vnd(100000), vnd(15000))); // 20%
		assertMoney(30000, voucherService.calculateDiscount("utetra20", 2, vnd(500000), vnd(15000))); // trần 30k, không
																										// phân biệt hoa
																										// thường
	}

	@Test
	void voucher_cuaShop_chiDungDungShop() {
		assertMoney(15000, voucherService.calculateDiscount("VVN15", 1, vnd(60000), vnd(15000)));
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("VVN15", 2, vnd(60000), vnd(15000)));
	}

	@Test
	void voucher_khongTonTai_baoLoi() {
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("KHONGCO", 1, vnd(100000), vnd(15000)));
	}

	@Test
	void voucher_danhSachDungDuoc_theoShop() {
		List<String> shop1 = voucherService.findUsable(1).stream().map(Voucher::getCode).toList();
		List<String> shop2 = voucherService.findUsable(2).stream().map(Voucher::getCode).toList();
		assertTrue(shop1.containsAll(List.of("FREESHIP10K", "UTETRA20", "VVN15")));
		assertFalse(shop2.contains("VVN15"), "Shop 2 không được thấy voucher của shop 1");
	}

	@Test
	void voucher_tatVaDanhSachKhongConHien() {
		Voucher v = voucherService.findAppVouchers().stream().filter(x -> x.getCode().equals("UTETRA20")).findFirst()
				.orElseThrow();
		voucherService.toggleActive(v.getId());
		assertThrows(BusinessException.class,
				() -> voucherService.calculateDiscount("UTETRA20", 1, vnd(100000), vnd(15000)));
	}

	// ================= BỔ SUNG: BRANCH =================
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
	void branch_findVendors_chiLayVendor() {
		List<Integer> ids = branchService.findVendors().stream().map(User::getId).toList();
		assertTrue(ids.contains(3), "vendor1 (id 3) phải có trong danh sách");
		assertFalse(ids.contains(6), "user1 (id 6) không phải vendor");
	}

	// ================= BỔ SUNG: VOUCHER =================
	private VoucherReq voucherReq(String code) {
		VoucherReq r = new VoucherReq();
		r.setCode(code);
		r.setName("Voucher test JUnit");
		r.setBranchId(null); // voucher của app
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
		assertTrue(voucherService.findAppVouchers().stream().anyMatch(x -> x.getId().equals(v.getId())));
		assertMoney(10000, voucherService.calculateDiscount("JUNITNEW1", 1, vnd(100000), vnd(15000)));
	}

	@Test
	void voucher_trungMa_baoLoi() {
		assertThrows(BusinessException.class, () -> voucherService.create(voucherReq("FREESHIP10K")));
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
				() -> voucherService.calculateDiscount("JUNITEXP1", 1, vnd(100000), vnd(15000)));
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
				() -> voucherService.calculateDiscount("JUNITQTY1", 1, vnd(100000), vnd(15000)));
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