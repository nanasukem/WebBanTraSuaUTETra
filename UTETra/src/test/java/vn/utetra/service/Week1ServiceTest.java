package vn.utetra.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.BranchReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Order;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.BranchStatus;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.exception.BusinessException;

import java.math.BigDecimal;
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
}