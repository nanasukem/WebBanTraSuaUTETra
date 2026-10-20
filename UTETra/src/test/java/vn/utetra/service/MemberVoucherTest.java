package vn.utetra.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.CheckoutPreviewRes;
import vn.utetra.dto.response.MemberTierInfo;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Order;
import vn.utetra.entity.User;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.VoucherRepository;
import vn.utetra.repository.VoucherUsageRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test bước 4B: hạng thành viên + giới hạn voucher. Mỗi test tự rollback.
 * Dữ liệu mẫu: user1 (6) đã có đơn #1 ĐÃ GIAO 153.000 -> hạng Thành viên, không còn là tài khoản mới;
 *              user2 (7) chưa có đơn nào -> tài khoản mới.
 */
@SpringBootTest
@Transactional
class MemberVoucherTest {

	@Autowired
	CartService cartService;
	@Autowired
	OrderService orderService;
	@Autowired
	MemberService memberService;
	@Autowired
	OrderRepository orderRepository;
	@Autowired
	VoucherRepository voucherRepository;
	@Autowired
	VoucherUsageRepository voucherUsageRepository;
	@Autowired
	EntityManager em;

	private static final int USER1 = 6; // địa chỉ #1
	private static final int USER2 = 7; // địa chỉ #3

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

	/** carrierId 1 = Giao Hàng Nhanh 15.000 */
	private CheckoutReq checkoutReq(int branchId, int addressId, int carrierId, String productCode, String shipCode) {
		CheckoutReq r = new CheckoutReq();
		r.setBranchId(branchId);
		r.setAddressId(addressId);
		r.setCarrierId(carrierId);
		r.setProductVoucherCode(productCode);
		r.setShippingVoucherCode(shipCode);
		r.setPaymentMethod(PaymentMethod.COD);
		return r;
	}

	/** Tạo 1 đơn ĐÃ GIAO giả lập để đẩy chi tiêu (xét hạng) */
	private void taoDonDaGiao(int userId, long tongTien, LocalDateTime ngayGiao) {
		Order o = new Order();
		o.setUser(em.getReference(User.class, userId));
		o.setBranch(em.getReference(Branch.class, 1));
		o.setReceiverName("JUnit");
		o.setReceiverPhone("0900000000");
		o.setDeliveryAddress("Đơn giả lập để test hạng");
		o.setItemsTotal(BigDecimal.valueOf(tongTien));
		o.setTotalAmount(BigDecimal.valueOf(tongTien));
		o.setPaymentMethod(PaymentMethod.COD);
		o.setStatus(OrderStatus.DELIVERED);
		o.setDeliveredAt(ngayGiao);
		em.persist(o);
		em.flush();
	}

	@BeforeEach
	void datLaiGioMau() {
		cartService.clear(USER1);
		cartService.addItem(USER1, cartReq(3, "M", 50, 70, 1));    // 1 Bạc xỉu M = 34.000
		cartService.clear(USER2);
		cartService.addItem(USER2, cartReq(7, "L", 70, 50, 2, 4)); // 2 Cà phê muối L + Kem cheese = 104.000
	}

	// ================= HẠNG THÀNH VIÊN =================
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
		assertMoney(113800, saved.getTotalAmount());     // 104.000 - 5.200 + 15.000
		assertMoney(4940, saved.getCommissionAmount());  // 5% x (104.000 - 5.200)
	}

	// ================= ƯU ĐÃI TÀI KHOẢN MỚI =================
	@Test
	void voucher_banMoi_chiDungChoDonDauTien() {
		Voucher chao = voucherRepository.findByCodeIgnoreCase("CHAOBANMOI").orElseThrow();
		Integer chaoId = chao.getId();
		int luotTruoc = chao.getUsedCount();

		Order o = orderService.placeOrder(USER2, checkoutReq(1, 3, 1, "CHAOBANMOI", "FREESHIPMOI"));
		assertMoney(25000, o.getProductDiscount());  // 30% x 104.000 = 31.200 -> trần 25.000
		assertMoney(15000, o.getShippingDiscount()); // miễn ship 15.000
		assertMoney(0, o.getMemberDiscount());       // hạng Thành viên: 0%
		assertMoney(79000, o.getTotalAmount());      // 104.000 - 25.000 + 15.000 - 15.000
		assertMoney(3950, o.getCommissionAmount());  // 5% x 79.000

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

	// ================= GIỚI HẠN VOUCHER =================
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
}