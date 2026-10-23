package vn.utetra.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.config.PaymentProperties;
import vn.utetra.dto.request.CartAddReq;
import vn.utetra.dto.request.CheckoutReq;
import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.entity.Order;
import vn.utetra.entity.Payment;
import vn.utetra.entity.enums.CancelledBy;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.PaymentRepository;
import vn.utetra.repository.VoucherRepository;

/**
 * TEST TUẦN 3 (B): thanh toán VNPAY / MoMo sandbox, đơn Chờ thanh toán, quá hạn tự huỷ, hoàn tiền,
 * nhãn Đã thanh toán / Chưa thanh toán.
 *
 * Không gọi mạng tới VNPAY / MoMo: chữ ký được kiểm bằng dữ liệu tự ký; luồng thành công dùng chế độ demo của VNPAY
 * (ép vnpay.tmn-code rỗng trong môi trường test để 4 hàm demo luôn chạy xanh kể cả khi .env đã điền mã thật).
 */
@SpringBootTest
@TestPropertySource(properties = { "vnpay.tmn-code=", "vnpay.hash-secret=" })
@Transactional
class PaymentServiceTest {

	static final int USER1 = 6;
	static final int MANAGER1 = 3; // chủ chi nhánh 1

	@Autowired OrderService orderService;
	@Autowired OrderFlowService orderFlowService;
	@Autowired ManagerOrderService managerOrderService;
	@Autowired PaymentService paymentService;
	@Autowired CartService cartService;
	@Autowired OrderRepository orderRepository;
	@Autowired PaymentRepository paymentRepository;
	@Autowired VoucherRepository voucherRepository;
	@Autowired EntityManager em;

	/** Giờ mở cửa mẫu 7:00 - 22:00: test chạy giờ nào cũng được -> cho chi nhánh mở cả ngày (rollback sau mỗi test) */
	@org.junit.jupiter.api.BeforeEach
	void moCuaCaNgay() {
		em.createNativeQuery("UPDATE Branches SET open_time = '00:00', close_time = '00:00'").executeUpdate();
		em.clear();
	}

	// ======================= ĐẶT ĐƠN TRẢ TRƯỚC =======================

	@Test
	void datMomo_choThanhToan_quanChuaThay_gioDaXoa() {
		Order o = datDon(PaymentMethod.MOMO, null);
		assertEquals(OrderStatus.AWAITING_PAYMENT, o.getStatus());
		assertEquals(PaymentStatus.UNPAID, o.getPaymentStatus());
		assertTrue(cartService.getCart(USER1).items().isEmpty(), "đặt xong thì giỏ trống (món nằm trong đơn)");
		em.flush();
		em.clear();

		assertFalse(trenQuay(o.getId()), "chưa trả tiền thì quán chưa thấy đơn");
		assertTrue(orderFlowService.myOrders(USER1, OrderTab.PENDING).stream().anyMatch(x -> x.id().equals(o.getId())));

		OrderDetailRes d = orderFlowService.myOrder(USER1, o.getId());
		assertEquals("Chờ thanh toán", d.statusLabel());
		assertTrue(d.payment().awaiting());
		assertTrue(d.payment().payDeadlineMillis() > System.currentTimeMillis());
		assertTrue(d.canCancel());
	}

	@Test
	void vnpay_thanhToanXong_donVaoQuan_daThanhToan() {
		assumeTrue(paymentService.vnpayDemo(), "đã cấu hình VNPAY thật thì bỏ qua (test dùng chế độ demo)");
		Order o = datDon(PaymentMethod.VNPAY, null);
		String url = paymentService.startPayment(USER1, o.getId(), null, "127.0.0.1");
		assertEquals("/payment/" + o.getId() + "/demo", url);

		PaymentService.Outcome r = paymentService.demoResult(USER1, o.getId(), true);
		assertEquals("SUCCESS", r.status());
		em.flush();
		em.clear();

		Order sau = orderRepository.findById(o.getId()).orElseThrow();
		assertEquals(OrderStatus.PENDING, sau.getStatus(), "trả xong -> Chờ xác nhận");
		assertEquals(PaymentStatus.PAID, sau.getPaymentStatus());
		assertTrue(trenQuay(o.getId()), "trả xong quán mới thấy đơn");

		OrderDetailRes d = orderFlowService.myOrder(USER1, o.getId());
		assertEquals("Đã thanh toán", d.payment().badge());
		assertTrue(d.payment().prepaid());
		assertNotNull(d.payment().transactionCode());
		assertTrue(managerOrderService.orderCard(MANAGER1, o.getId()).prepaid(), "quán biết không phải thu tiền");
	}

	@Test
	void donDaTraTruoc_khachHuy_tuDongHoanTien() {
		assumeTrue(paymentService.vnpayDemo());
		Order o = datDon(PaymentMethod.VNPAY, null);
		paymentService.startPayment(USER1, o.getId(), null, "127.0.0.1");
		paymentService.demoResult(USER1, o.getId(), true);
		em.flush();
		em.clear();

		orderFlowService.cancelByCustomer(USER1, o.getId(), "Đặt nhầm");
		em.flush();
		em.clear();

		Order sau = orderRepository.findById(o.getId()).orElseThrow();
		assertEquals(OrderStatus.CANCELLED, sau.getStatus());
		assertEquals(PaymentStatus.REFUND_PENDING, sau.getPaymentStatus());
		assertEquals("REFUND_PENDING", paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(o.getId()).getStatus());

		OrderDetailRes d = orderFlowService.myOrder(USER1, o.getId());
		assertNotNull(d.payment().refund(), "chi tiết đơn hiện mục hoàn tiền");
		assertEquals("Đang xử lý hoàn tiền", d.payment().refund().statusLabel());
		assertEquals(0, sau.getTotalAmount().compareTo(d.payment().refund().amount()));
	}

	@Test
	void thanhToanThatBai_vanGiuDon_thanhToanLaiDuoc() {
		assumeTrue(paymentService.vnpayDemo());
		Order o = datDon(PaymentMethod.VNPAY, null);
		paymentService.startPayment(USER1, o.getId(), null, "127.0.0.1");
		assertEquals("FAILED", paymentService.demoResult(USER1, o.getId(), false).status());
		assertEquals(OrderStatus.AWAITING_PAYMENT, orderRepository.findById(o.getId()).orElseThrow().getStatus());

		paymentService.startPayment(USER1, o.getId(), null, "127.0.0.1");
		assertEquals(2, paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).size(), "mỗi lần thử là 1 giao dịch mới");
		assertEquals("SUCCESS", paymentService.demoResult(USER1, o.getId(), true).status());
	}

	@Test
	void quaHan_heThongTuHuy_traLuotMaGiamGia() {
		int truoc = voucherRepository.findByCodeIgnoreCase("MOMO15K").orElseThrow().getUsedCount();
		Order o = datDon(PaymentMethod.MOMO, "MOMO15K");
		em.flush();
		assertEquals(truoc + 1, voucherRepository.findByCodeIgnoreCase("MOMO15K").orElseThrow().getUsedCount(),
				"đặt đơn là giữ chỗ mã giảm giá");

		em.createNativeQuery("UPDATE Orders SET created_at = DATEADD(MINUTE, -30, SYSDATETIME()) WHERE id = :id")
				.setParameter("id", o.getId()).executeUpdate();
		em.clear();
		paymentService.expireOrder(o.getId());
		em.flush();
		em.clear();

		Order sau = orderRepository.findById(o.getId()).orElseThrow();
		assertEquals(OrderStatus.CANCELLED, sau.getStatus());
		assertEquals(CancelledBy.SYSTEM, sau.getCancelledBy());
		assertTrue(sau.getCancelReason().contains("chưa thanh toán"));
		assertEquals(truoc, voucherRepository.findByCodeIgnoreCase("MOMO15K").orElseThrow().getUsedCount(), "trả lại lượt mã");
	}

	@Test
	void chuaHetHan_khongHuy() {
		Order o = datDon(PaymentMethod.MOMO, null);
		paymentService.expireOrder(o.getId());
		assertEquals(OrderStatus.AWAITING_PAYMENT, orderRepository.findById(o.getId()).orElseThrow().getStatus());
	}

	@Test
	void doiSangCod_donVaoQuanNgay_maChiDanhChoMomoThiKhongDoiDuoc() {
		Order o = datDon(PaymentMethod.VNPAY, null);
		paymentService.switchToCod(USER1, o.getId());
		em.flush();
		em.clear();
		Order sau = orderRepository.findById(o.getId()).orElseThrow();
		assertEquals(OrderStatus.PENDING, sau.getStatus());
		assertEquals(PaymentMethod.COD, sau.getPaymentMethod());
		assertEquals("Chưa thanh toán", orderFlowService.myOrder(USER1, o.getId()).payment().badge(),
				"COD: hiện Chưa thanh toán, trả khi nhận");

		Order momo = datDon(PaymentMethod.MOMO, "MOMO15K");
		BusinessException e = assertThrows(BusinessException.class, () -> paymentService.switchToCod(USER1, momo.getId()));
		assertTrue(e.getMessage().contains("MOMO15K"));
	}

	@Test
	void traTreSauKhiDonDaHuy_tuHoanKhoanDo() {
		assumeTrue(paymentService.vnpayDemo());
		Order o = datDon(PaymentMethod.VNPAY, null);
		paymentService.startPayment(USER1, o.getId(), null, "127.0.0.1");
		orderFlowService.cancelByCustomer(USER1, o.getId(), "Đổi ý");
		em.flush();
		em.clear();

		paymentService.demoResult(USER1, o.getId(), true); // khách vẫn trả ở trang cổng đã mở từ trước
		em.flush();
		em.clear();
		assertEquals(PaymentStatus.REFUND_PENDING, orderRepository.findById(o.getId()).orElseThrow().getPaymentStatus());
		assertEquals("REFUND_PENDING", paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(o.getId()).getStatus());
	}

	@Test
	void khongThanhToanLaiDonDaTraHoacDonNguoiKhac() {
		Order o = datDon(PaymentMethod.MOMO, null);
		assertThrows(RuntimeException.class, () -> paymentService.startPayment(7, o.getId(), null, "127.0.0.1"));
		paymentService.switchToCod(USER1, o.getId());
		assertThrows(BusinessException.class, () -> paymentService.startPayment(USER1, o.getId(), null, "127.0.0.1"));
	}

	@Test
	void cod_chuaThanhToan_giaoXongThiDaThanhToan() {
		Order o = datDon(PaymentMethod.COD, null);
		assertEquals(OrderStatus.PENDING, o.getStatus());
		em.flush();
		em.clear();
		assertEquals("Chưa thanh toán", orderFlowService.myOrder(USER1, o.getId()).payment().badge());
		assertTrue(managerOrderService.orderCard(MANAGER1, o.getId()).paymentLabel().contains("thu"),
				"quán thấy phải thu tiền mặt");
	}

	// ======================= BÁO VẤN ĐỀ VỚI ĐƠN (thay Trả hàng) =======================

	@Autowired OrderIssueService orderIssueService;
	@Autowired vn.utetra.repository.OrderIssueRepository orderIssueRepository;

	@Test
	void baoVanDe_phaiCoAnh_vaChiTrong2Gio() {
		Order o = daNhan(PaymentMethod.COD);
		OrderDetailRes d = orderFlowService.myOrder(USER1, o.getId());
		assertTrue(d.canReportIssue(), "vừa nhận món thì báo được");

		BusinessException e = assertThrows(BusinessException.class, () -> orderIssueService.submit(USER1, o.getId(),
				vn.utetra.entity.enums.IssueReason.SPILLED, "Ly bị đổ", List.of()));
		assertTrue(e.getMessage().contains("ảnh"));

		em.createNativeQuery("UPDATE Orders SET delivered_at = DATEADD(HOUR, -3, SYSDATETIME()) WHERE id = :id")
				.setParameter("id", o.getId()).executeUpdate();
		em.clear();
		BusinessException het = assertThrows(BusinessException.class, () -> orderIssueService.submit(USER1, o.getId(),
				vn.utetra.entity.enums.IssueReason.SPILLED, "Ly bị đổ", List.of()));
		assertTrue(het.getMessage().contains("2 giờ"));
		assertFalse(orderFlowService.myOrder(USER1, o.getId()).canReportIssue());
	}

	@Test
	void baoVanDe_donCOD_quanHoanTien_truDoanhThu() {
		Order o = daNhan(PaymentMethod.COD);
		taoBaoCao(o);
		BigDecimal truoc = orderRepository.findById(o.getId()).orElseThrow().getSalesAmount();

		orderIssueService.resolve(o.getId(), "REFUND", new BigDecimal("20000"), "Quán chuyển khoản lại cho bạn");
		em.flush();
		em.clear();

		Order sau = orderRepository.findById(o.getId()).orElseThrow();
		assertEquals(0, new BigDecimal("20000").compareTo(sau.getRefundAmount()));
		assertEquals(0, truoc.subtract(new BigDecimal("20000")).compareTo(sau.getSalesAmount()), "doanh thu trừ tiền hoàn");
		OrderDetailRes d = orderFlowService.myOrder(USER1, o.getId());
		assertEquals("REFUNDED", d.issue().status());
		assertFalse(d.canReportIssue(), "đã báo thì không báo lại");
		assertThrows(BusinessException.class, () -> orderIssueService.resolve(o.getId(), "REJECT", null, "x"),
				"đã xử lý thì không xử lý lại");
	}

	@Test
	void baoVanDe_donTraTruoc_hoanMotPhanQuaCong() {
		Order o = daNhan(PaymentMethod.MOMO);
		taoBaoCao(o);
		orderIssueService.resolve(o.getId(), "REFUND", new BigDecimal("10000"), null);
		em.flush();
		em.clear();
		Payment p = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(o.getId());
		assertEquals("REFUND_PENDING", p.getStatus(), "gọi MoMo hoàn sau khi lưu xong");
		assertEquals(0, new BigDecimal("10000").compareTo(p.refundValue()), "hoàn 1 phần");
		assertEquals(PaymentStatus.PAID, orderRepository.findById(o.getId()).orElseThrow().getPaymentStatus(),
				"hoàn 1 phần thì đơn vẫn là đã thanh toán");
	}

	@Test
	void baoVanDe_tuChoiPhaiGhiLyDo_lamLaiMon_vaTabCuaQuan() {
		Order o = daNhan(PaymentMethod.COD);
		taoBaoCao(o);
		assertThrows(BusinessException.class, () -> orderIssueService.resolve(o.getId(), "REJECT", null, " "));
		assertThrows(BusinessException.class,
				() -> orderIssueService.resolve(o.getId(), "REFUND", new BigDecimal("999999999"), null), "quá số tiền khách trả");
		assertTrue(managerOrderService.history(MANAGER1, 1, "ISSUE", 0).orders().stream()
				.anyMatch(x -> x.id().equals(o.getId())), "tab Khách báo vấn đề của quán");
		orderIssueService.resolve(o.getId(), "REMAKE", null, null);
		em.flush();
		em.clear();
		assertEquals("REMADE", orderFlowService.myOrder(USER1, o.getId()).issue().status());
	}

	@Test
	void baoVanDe_dangGiaiQuyet_xuLyXongMoiVeHoanThanh() {
		Order o = daNhan(PaymentMethod.COD);
		taoBaoCao(o);
		assertTrue(coDon(OrderTab.ISSUE, o), "chờ quán xử lý -> tab Đang giải quyết");
		assertFalse(coDon(OrderTab.DELIVERED, o), "chưa tính Hoàn thành");
		assertFalse(managerOrderService.history(MANAGER1, 1, "DELIVERED", 0).orders().stream()
				.anyMatch(x -> x.id().equals(o.getId())), "quán: chưa vào tab Hoàn thành");
		assertTrue(managerOrderService.orderCard(MANAGER1, o.getId()).issuePending());

		orderIssueService.resolve(o.getId(), "REJECT", null, "Ảnh không thấy dị vật");
		em.flush();
		em.clear();
		assertFalse(coDon(OrderTab.ISSUE, o));
		assertTrue(coDon(OrderTab.DELIVERED, o), "xử lý xong mới về Hoàn thành");
		assertTrue(orderFlowService.myOrder(USER1, o.getId()).issue().statusLabel().startsWith("Quán từ chối hoàn tiền"));
		assertEquals(0, orderRepository.findById(o.getId()).orElseThrow().getRefundAmount().signum(), "từ chối thì không hoàn");
	}

	private boolean coDon(OrderTab tab, Order o) {
		return orderFlowService.myOrders(USER1, tab).stream().anyMatch(x -> x.id().equals(o.getId()));
	}

	/** Đơn đến lấy đã nhận món; MoMo thì giả lập đã trả qua MoMo (có giao dịch thành công) */
	private Order daNhan(PaymentMethod pm) {
		Order o = datDon(pm == PaymentMethod.COD ? PaymentMethod.COD : PaymentMethod.MOMO, null);
		if (pm != PaymentMethod.COD) {
			Payment p = new Payment();
			p.setOrder(o);
			p.setMethod("MOMO");
			p.setTxnRef("UTE" + o.getId() + "TEST");
			p.setTransactionCode("4088878653");
			p.setAmount(o.getTotalAmount());
			p.setStatus("SUCCESS");
			paymentRepository.save(p);
			o.setPaymentStatus(PaymentStatus.PAID);
			o.setStatus(OrderStatus.PENDING);
			orderRepository.save(o);
		}
		orderFlowService.updateStatus(o.getId(), OrderStatus.CONFIRMED, null);
		orderFlowService.updateStatus(o.getId(), OrderStatus.READY, null);
		orderFlowService.updateStatus(o.getId(), OrderStatus.DELIVERED, null);
		em.flush();
		em.clear();
		return orderRepository.findById(o.getId()).orElseThrow();
	}

	/** Tạo sẵn báo cáo (bỏ qua bước tải ảnh lên Cloudinary) */
	private void taoBaoCao(Order o) {
		vn.utetra.entity.OrderIssue i = new vn.utetra.entity.OrderIssue();
		i.setOrder(orderRepository.getReferenceById(o.getId()));
		i.setUser(o.getUser());
		i.setReason(vn.utetra.entity.enums.IssueReason.WRONG_ITEM);
		i.setDescription("Giao sai món");
		i.setImages("https://res.cloudinary.com/demo/image/upload/sample.jpg");
		orderIssueRepository.save(i);
		em.flush();
		em.clear();
	}

	// ======================= CHỮ KÝ CỔNG THANH TOÁN =======================

	@Test
	void vnpay_chuKyDung_suaSoTienThiSai() {
		PaymentProperties props = props();
		VnPayService vnpay = new VnPayService(props);
		Payment p = new Payment();
		p.setTxnRef("UTE99T1");
		p.setAmount(new BigDecimal("50000"));
		p.setGatewayDate("20261022100000");
		String url = vnpay.createPaymentUrl(p, "Thanh toan don hang UTETra 99", "127.0.0.1",
				java.time.LocalDateTime.now().plusMinutes(15), null);
		assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
		assertTrue(url.contains("vnp_Amount=5000000"), "VNPAY tính tiền x100");
		assertTrue(url.contains("vnp_SecureHash="));

		// VNPAY trả về (tự ký bằng cùng secret như VNPAY làm)
		Map<String, String> ret = new TreeMap<>();
		ret.put("vnp_Amount", "5000000");
		ret.put("vnp_BankCode", "NCB");
		ret.put("vnp_OrderInfo", "Thanh toan don hang UTETra 99");
		ret.put("vnp_PayDate", "20261022100500");
		ret.put("vnp_ResponseCode", "00");
		ret.put("vnp_TmnCode", "TESTCODE");
		ret.put("vnp_TransactionNo", "14600000");
		ret.put("vnp_TransactionStatus", "00");
		ret.put("vnp_TxnRef", "UTE99T1");
		ret.put("vnp_SecureHash", kyVnpay(ret, "SECRETSECRETSECRET"));

		GatewayResult ok = vnpay.readResult(ret);
		assertTrue(ok.verified());
		assertTrue(ok.success());
		assertEquals(50000, ok.amount());
		assertEquals("14600000", ok.transactionNo());

		Map<String, String> sua = new HashMap<>(ret);
		sua.put("vnp_Amount", "100");
		assertFalse(vnpay.readResult(sua).verified(), "sửa số tiền trên trình duyệt -> sai chữ ký");

		Map<String, String> huy = new TreeMap<>(ret);
		huy.remove("vnp_SecureHash");
		huy.put("vnp_ResponseCode", "24");
		huy.put("vnp_TransactionStatus", "02");
		huy.put("vnp_SecureHash", kyVnpay(huy, "SECRETSECRETSECRET"));
		GatewayResult cancel = vnpay.readResult(huy);
		assertTrue(cancel.verified());
		assertFalse(cancel.success());
		assertEquals("Bạn đã huỷ giao dịch", cancel.message());
	}

	@Test
	void momo_chuKyDung_suaKetQuaThiSai() {
		MomoService momo = new MomoService(props());
		Map<String, String> p = new HashMap<>(Map.of("partnerCode", "MOMO", "orderId", "UTE99T1", "requestId", "UTE99T1",
				"amount", "50000", "orderInfo", "Thanh toan don hang UTETra 99", "orderType", "momo_wallet",
				"transId", "4088878653", "resultCode", "0", "message", "Thành công.", "payType", "qr"));
		p.put("responseTime", "1729570000000");
		p.put("extraData", "");
		String raw = "accessKey=F8BBA842ECF85&amount=50000&extraData=&message=Thành công.&orderId=UTE99T1"
				+ "&orderInfo=Thanh toan don hang UTETra 99&orderType=momo_wallet&partnerCode=MOMO&payType=qr"
				+ "&requestId=UTE99T1&responseTime=1729570000000&resultCode=0&transId=4088878653";
		p.put("signature", MomoService.hmacSha256("K951B6PE1waDMi640xX08PD3vg6EkVlz", raw));

		GatewayResult ok = momo.readResult(p);
		assertTrue(ok.verified());
		assertTrue(ok.success());
		assertEquals("4088878653", ok.transactionNo());

		p.put("resultCode", "1006");
		assertFalse(momo.readResult(p).verified(), "đổi kết quả -> sai chữ ký");
	}

	// ======================= HÀM PHỤ =======================

	private Order datDon(PaymentMethod pm, String voucher) {
		cartService.clear(USER1);
		CartAddReq c = new CartAddReq();
		c.setProductId(3);
		c.setSize("M");
		c.setQuantity(3);
		cartService.addItem(USER1, c);
		CheckoutReq r = new CheckoutReq();
		r.setDeliveryType(DeliveryType.PICKUP);
		r.setBranchId(1);
		r.setReceiverName("Trần Thị Khách");
		r.setReceiverPhone("0900000006");
		r.setPaymentMethod(pm);
		r.setProductVoucherCode(voucher);
		return orderService.placeOrder(USER1, r);
	}

	private boolean trenQuay(Integer orderId) {
		var board = managerOrderService.board(MANAGER1, 1);
		return List.of(board.newOrders(), board.making(), board.shipping()).stream().flatMap(List::stream)
				.anyMatch(x -> x.id().equals(orderId));
	}

	private static PaymentProperties props() {
		PaymentProperties p = new PaymentProperties();
		ReflectionTestUtils.setField(p, "baseUrl", "http://localhost:8080");
		ReflectionTestUtils.setField(p, "ipnBaseUrl", "");
		ReflectionTestUtils.setField(p, "timeoutMinutes", 15);
		ReflectionTestUtils.setField(p, "vnpTmnCode", "TESTCODE");
		ReflectionTestUtils.setField(p, "vnpHashSecret", "SECRETSECRETSECRET");
		ReflectionTestUtils.setField(p, "vnpPayUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
		ReflectionTestUtils.setField(p, "vnpApiUrl", "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
		ReflectionTestUtils.setField(p, "momoPartnerCode", "MOMO");
		ReflectionTestUtils.setField(p, "momoAccessKey", "F8BBA842ECF85");
		ReflectionTestUtils.setField(p, "momoSecretKey", "K951B6PE1waDMi640xX08PD3vg6EkVlz");
		ReflectionTestUtils.setField(p, "momoEndpoint", "https://test-payment.momo.vn");
		ReflectionTestUtils.setField(p, "momoRequestType", "payWithMethod");
		return p;
	}

	/** Ký giống VNPAY: xếp theo tên, giá trị mã hoá URL, HMAC-SHA512 */
	private static String kyVnpay(Map<String, String> params, String secret) {
		StringBuilder sb = new StringBuilder();
		new TreeMap<>(params).forEach((k, v) -> {
			if (k.equals("vnp_SecureHash")) return;
			if (!sb.isEmpty()) sb.append('&');
			sb.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.US_ASCII));
		});
		return VnPayService.hmacSha512(secret, sb.toString());
	}
}