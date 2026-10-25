package vn.utetra.service;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.dto.ProductDto;
import vn.utetra.dto.request.ReplyReviewReq;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.ReviewReplyRepository;
import vn.utetra.repository.ReviewRepository;
import vn.utetra.repository.UserRepository;

@SpringBootTest
@Transactional
class Week4ServiceTest {

	static final int ADMIN = 1, MANAGER3 = 2, MANAGER1 = 3, MANAGER2 = 4, SHIPPER1 = 5, USER1 = 6;

	@Autowired ProductService productService;
	@Autowired AdminProductService adminProductService;
	@Autowired ProductRepository productRepository;
	@Autowired BranchReviewManagementService reviewManagementService;
	@Autowired ReviewRepository reviewRepository;
	@Autowired ReviewReplyRepository replyRepository;
	@Autowired NotificationService notificationService;
	@Autowired AdminUserService adminUserService;
	@Autowired BranchStockService branchStockService;
	@Autowired ReportService reportService;
	@Autowired OrderRepository orderRepository;
	@Autowired UserRepository userRepository;
	@Autowired jakarta.persistence.EntityManager em;
	@Autowired OrderIssueService orderIssueService;
	@org.springframework.test.context.bean.override.mockito.MockitoBean CloudinaryService cloudinaryService;
	@Autowired vn.utetra.repository.OrderReviewRepository orderReviewRepository;

	@BeforeEach
	void chuanHoaDuLieuMau() {
		em.createNativeQuery("UPDATE Branches SET open_time = '00:00', close_time = '00:00', status = 'APPROVED'").executeUpdate();
		em.createNativeQuery("DELETE FROM BranchToppings").executeUpdate();
		em.createNativeQuery("UPDATE Toppings SET is_available = 1").executeUpdate();
		em.createNativeQuery("UPDATE Products SET is_available = 1").executeUpdate();
		em.createNativeQuery("DELETE FROM BranchProducts WHERE NOT (branch_id = 2 AND product_id = 4)").executeUpdate();
		em.createNativeQuery("UPDATE Users SET is_active = 1").executeUpdate();
		em.createNativeQuery("UPDATE Users SET branch_id = 1 WHERE username = 'shipper1'").executeUpdate();
		em.createNativeQuery("UPDATE Orders SET shipper_id = NULL WHERE status IN ('PENDING','CONFIRMED','READY','PICKED_UP','DELIVERING')").executeUpdate();
		em.clear();
	}

	private User user(int id) {
		return userRepository.findById(id).orElseThrow();
	}

	private Integer toppingCategoryId() {
		return productService.getAllCategories().stream().filter(c -> "Topping".equals(c.getName()))
				.map(Category::getId).findFirst().orElseThrow();
	}

	private Product taoMon(String name, int price, boolean dangBan) throws Exception {
		ProductDto dto = new ProductDto();
		dto.setName(name);
		dto.setDescription("Món thử");
		dto.setBasePrice(BigDecimal.valueOf(price));
		dto.setCategoryId(1);
		dto.setIsAvailable(dangBan);
		return adminProductService.createProduct(dto);
	}

	@Test
	void sanPham_timKiem_khongPhanBietHoaThuong() {
		List<Product> thuong = productService.filterProducts("trà sữa", null, null);
		List<Product> hoa = productService.filterProducts("TRÀ SỮA", null, null);
		assertFalse(thuong.isEmpty(), "có món tên chứa 'trà sữa'");
		assertEquals(thuong.size(), hoa.size(), "chữ hoa / thường cho cùng kết quả");
	}

	@Test
	void sanPham_locDanhMuc_chiLayDungDanhMuc() {
		List<Product> list = productService.filterProducts(null, 1, null);
		assertFalse(list.isEmpty());
		assertTrue(list.stream().allMatch(p -> p.getCategory().getId() == 1));
	}

	@Test
	void sanPham_sapXepGia_tangVaGiam() {
		List<Product> tang = productService.filterProducts(null, null, "price_asc");
		for (int i = 1; i < tang.size(); i++) {
			assertTrue(tang.get(i - 1).getBasePrice().compareTo(tang.get(i).getBasePrice()) <= 0, "giá tăng dần");
		}
		List<Product> giam = productService.filterProducts(null, null, "price_desc");
		for (int i = 1; i < giam.size(); i++) {
			assertTrue(giam.get(i - 1).getBasePrice().compareTo(giam.get(i).getBasePrice()) >= 0, "giá giảm dần");
		}
	}

	@Test
	void sanPham_adminThem_hienTrongThucDon_ngungBanThiAn() throws Exception {
		Product dangBan = taoMon("Trà thử nghiệm JUnit", 39000, true);
		Product ngungBan = taoMon("Trà ngừng bán JUnit", 41000, false);
		assertNotNull(dangBan.getId());
		List<Product> ketQua = productService.filterProducts("JUnit", null, null);
		assertTrue(ketQua.stream().anyMatch(p -> p.getId().equals(dangBan.getId())), "món đang bán hiện ở thực đơn");
		assertTrue(ketQua.stream().noneMatch(p -> p.getId().equals(ngungBan.getId())), "món ngừng bán không hiện");
	}

	@Test
	void sanPham_adminSua_doiTenVaGia() throws Exception {
		Product p = taoMon("Cà phê JUnit", 30000, true);
		ProductDto dto = new ProductDto();
		dto.setName("Cà phê JUnit sửa");
		dto.setBasePrice(BigDecimal.valueOf(32000));
		dto.setCategoryId(3);
		adminProductService.updateProduct(p.getId(), dto);
		em.flush();
		em.clear();
		Product sau = adminProductService.getProductById(p.getId());
		assertEquals("Cà phê JUnit sửa", sau.getName());
		assertEquals(0, sau.getBasePrice().compareTo(BigDecimal.valueOf(32000)));
		assertEquals(3, sau.getCategory().getId());
	}

	@Test
	void sanPham_adminXoa_monChuaBan_xoaDuoc() throws Exception {
		Product p = taoMon("Món xoá JUnit", 25000, true);
		adminProductService.deleteProduct(p.getId());
		em.flush();
		assertTrue(productRepository.findById(p.getId()).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> adminProductService.getProductById(p.getId()));
	}

	@Test
	void sanPham_loi_themVaoDanhMucTopping_suaToppingBanRieng() {
		ProductDto dto = new ProductDto();
		dto.setName("Không được thêm");
		dto.setBasePrice(BigDecimal.valueOf(10000));
		dto.setCategoryId(toppingCategoryId());
		assertThrows(IllegalArgumentException.class, () -> adminProductService.createProduct(dto),
				"topping thêm ở trang Quản lý topping");
		Product toppingBanRieng = productRepository.findByToppingIsNotNull().get(0);
		ProductDto sua = new ProductDto();
		sua.setName("Đổi tên");
		sua.setBasePrice(BigDecimal.ONE);
		sua.setCategoryId(1);
		assertThrows(IllegalArgumentException.class, () -> adminProductService.updateProduct(toppingBanRieng.getId(), sua));
	}

	@Test
	void sanPham_khongTonTai_baoLoi() {
		assertThrows(IllegalArgumentException.class, () -> adminProductService.getProductById(999999));
	}

	@Test
	void danhGia_managerPhanHoi_luuPhanHoi_khachDuocBao() {
		int truoc = replyRepository.findByReviewIdOrderByCreatedAtAsc(1).size();
		long chuaDoc = notificationService.unreadCount(USER1);
		ReplyReviewReq req = new ReplyReviewReq();
		req.setReviewId(1);
		req.setContent("Cảm ơn bạn đã ủng hộ quán!");
		reviewManagementService.replyToReview(req, user(MANAGER1));
		assertEquals(truoc + 1, replyRepository.findByReviewIdOrderByCreatedAtAsc(1).size());
		assertEquals(chuaDoc + 1, notificationService.unreadCount(USER1), "khách nhận thông báo quán đã phản hồi");
	}

	@Test
	void danhGia_loi_managerChiNhanhKhac_admin_noiDungTrong() {
		ReplyReviewReq req = new ReplyReviewReq();
		req.setReviewId(1);
		req.setContent("Phản hồi");
		assertThrows(BusinessException.class, () -> reviewManagementService.replyToReview(req, user(MANAGER2)),
				"đánh giá của chi nhánh 1, manager chi nhánh 2 không phản hồi được");
		assertThrows(BusinessException.class, () -> reviewManagementService.replyToReview(req, user(ADMIN)),
				"Admin chỉ xem / xoá, không phản hồi");
		ReplyReviewReq trong = new ReplyReviewReq();
		trong.setReviewId(1);
		trong.setContent("   ");
		assertThrows(BusinessException.class, () -> reviewManagementService.replyToReview(trong, user(MANAGER1)));
	}

	@Test
	void danhGia_adminXoaViPham_xoaLuonPhanHoi() {
		assertTrue(reviewRepository.findById(1).isPresent());
		assertFalse(replyRepository.findByReviewIdOrderByCreatedAtAsc(1).isEmpty(), "dữ liệu mẫu có 1 phản hồi");
		reviewManagementService.deleteReview("PRODUCT", 1);
		em.flush();
		assertTrue(reviewRepository.findById(1).isEmpty());
		assertTrue(replyRepository.findByReviewIdOrderByCreatedAtAsc(1).isEmpty());
		assertThrows(BusinessException.class, () -> reviewManagementService.deleteReview("KHAC", 1));
	}

	private static org.springframework.mock.web.MockMultipartFile file(String name, String type) {
		return new org.springframework.mock.web.MockMultipartFile("images", name, type, new byte[] { 1, 2, 3 });
	}

	@Test
	void baoVanDe_batBuocAnhHoacVideo_sai_loai_soLuong_baoLoi() {
		em.createNativeQuery("DELETE FROM OrderIssues WHERE order_id = 1").executeUpdate();
		em.createNativeQuery("UPDATE Orders SET status = 'DELIVERED', delivered_at = SYSDATETIME() WHERE id = 1").executeUpdate();
		em.clear();
		vn.utetra.entity.enums.IssueReason reason = vn.utetra.entity.enums.IssueReason.SPILLED;
		BusinessException khongCo = assertThrows(BusinessException.class,
				() -> orderIssueService.submit(USER1, 1, reason, "Ly bị đổ", List.of()));
		assertTrue(khongCo.getMessage().contains("ảnh hoặc video"), "bắt buộc ít nhất 1 ảnh hoặc video");
		BusinessException saiLoai = assertThrows(BusinessException.class, () -> orderIssueService.submit(USER1, 1,
				reason, "Ly bị đổ", List.of(file("hoadon.pdf", "application/pdf"))));
		assertTrue(saiLoai.getMessage().contains("không phải ảnh hoặc video"));
		List<org.springframework.web.multipart.MultipartFile> sauFile = new java.util.ArrayList<>();
		for (int i = 0; i < OrderIssueService.MAX_FILES + 1; i++) sauFile.add(file("a" + i + ".jpg", "image/jpeg"));
		BusinessException nhieu = assertThrows(BusinessException.class,
				() -> orderIssueService.submit(USER1, 1, reason, "Ly bị đổ", sauFile));
		assertTrue(nhieu.getMessage().contains("Tối đa"));
		org.springframework.mock.web.MockMultipartFile videoLon = new org.springframework.mock.web.MockMultipartFile(
				"images", "clip.mp4", "video/mp4", new byte[(int) ReviewService.MAX_VIDEO_BYTES + 1]);
		assertThrows(BusinessException.class, () -> orderIssueService.submit(USER1, 1, reason, "Ly bị đổ", List.of(videoLon)),
				"video quá 30MB");
	}

	@Test
	void baoVanDe_goDanhGiaCuaDon_baoAdminVaManager_khongDanhGiaLaiDuoc() {
		org.mockito.Mockito.when(cloudinaryService.uploadImage(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.anyString())).thenReturn("https://res.cloudinary.com/x/image/upload/a.jpg");
		em.createNativeQuery("DELETE FROM OrderIssues WHERE order_id = 1").executeUpdate();
		em.createNativeQuery("UPDATE Orders SET status = 'DELIVERED', delivered_at = SYSDATETIME() WHERE id = 1").executeUpdate();
		em.clear();
		assertFalse(reviewRepository.findByOrderDetailIdIn(List.of(1, 2)).isEmpty());
		assertTrue(orderReviewRepository.findByOrderId(1).isPresent());
		long admin = notificationService.unreadCount(ADMIN);
		long manager = notificationService.unreadCount(MANAGER1);

		orderIssueService.submit(USER1, 1, vn.utetra.entity.enums.IssueReason.SPILLED, "Ly bị đổ",
				List.of(file("a.jpg", "image/jpeg")));
		em.flush();
		em.clear();

		assertTrue(reviewRepository.findByOrderDetailIdIn(List.of(1, 2)).isEmpty(), "gỡ đánh giá món của đơn");
		assertTrue(orderReviewRepository.findByOrderId(1).isEmpty(), "gỡ đánh giá quán của đơn");
		assertEquals(admin + 2, notificationService.unreadCount(ADMIN), "Admin nhận báo vấn đề + báo đã gỡ đánh giá");
		assertEquals(manager + 2, notificationService.unreadCount(MANAGER1), "Manager nhận báo vấn đề + báo đã gỡ đánh giá");
		assertTrue(orderIssueService.forOrder(1).removedReviews() >= 2, "ghi lại số đánh giá đã gỡ");

		orderIssueService.resolve(1, "REFUND", BigDecimal.valueOf(30000), "Đã chuyển khoản");
		em.flush();
		assertEquals(admin + 3, notificationService.unreadCount(ADMIN), "chuông Admin reo khi quán hoàn tiền");
		assertEquals(0, orderRepository.findById(1).orElseThrow().getRefundAmount().compareTo(BigDecimal.valueOf(30000)));
	}

	@Test
	void taiKhoan_tabNhanVien_khongCoKhach_tabKhach_chiCoKhach() {
		AdminUserService.SearchResult staff = adminUserService.search("", "STAFF", "ALL", "ALL", "");
		assertTrue(staff.users().stream().noneMatch(u -> "USER".equals(u.getRole().getName())));
		assertTrue(staff.users().stream().anyMatch(u -> "ADMIN".equals(u.getRole().getName())), "Admin nằm ở tab Nhân viên");
		AdminUserService.SearchResult khach = adminUserService.search("", "USER", "ALL", "ALL", "");
		assertTrue(khach.users().stream().allMatch(u -> "USER".equals(u.getRole().getName())));
		assertEquals(staff.staffCount() + staff.customerCount(), staff.users().size() + khach.users().size());
	}

	@Test
	void taiKhoan_locChiNhanh_vaChuaCoChiNhanh() {
		List<User> cn1 = adminUserService.search("", "STAFF", "ALL", "ALL", "1").users();
		assertTrue(cn1.stream().anyMatch(u -> u.getId() == MANAGER1), "manager1 quản lý chi nhánh 1");
		assertTrue(cn1.stream().anyMatch(u -> u.getId() == SHIPPER1), "shipper1 làm ở chi nhánh 1");
		assertTrue(cn1.stream().noneMatch(u -> u.getId() == MANAGER2));
		AdminUserService.SearchResult none = adminUserService.search("", "STAFF", "ALL", "ALL", "NONE");
		assertTrue(none.users().stream().anyMatch(u -> u.getId() == MANAGER3), "manager3 chưa có chi nhánh");
		assertTrue(none.users().stream().noneMatch(u -> u.getId() == ADMIN), "Admin làm toàn chuỗi, không tính");
		assertEquals("Toàn chuỗi", adminUserService.search("admin", "STAFF", "ALL", "ALL", "").branchLabels().get(ADMIN));
	}

	@Test
	void taiKhoan_phanQuyen_managerDangQuanLyChiNhanh_khongDoiDuoc() {
		BusinessException e = assertThrows(BusinessException.class,
				() -> adminUserService.changeRole(MANAGER1, "USER", "admin"));
		assertTrue(e.getMessage().contains("giao chi nhánh"));
		assertTrue(adminUserService.changeRole(MANAGER3, "SHIPPER", "admin").contains("SHIPPER"),
				"manager chưa có chi nhánh thì đổi được");
		assertEquals("SHIPPER", user(MANAGER3).getRole().getName());
	}

	@Test
	void taiKhoan_khoaMoKhoa_vaKhongTuKhoaMinh() {
		adminUserService.toggleActive(USER1, "admin");
		assertFalse(user(USER1).getIsActive());
		adminUserService.toggleActive(USER1, "admin");
		assertTrue(user(USER1).getIsActive());
		assertThrows(BusinessException.class, () -> adminUserService.toggleActive(ADMIN, "admin"));
		assertThrows(BusinessException.class, () -> adminUserService.changeRole(ADMIN, "USER", "admin"));
	}

	@Test
	void taiKhoan_xoa_daCoDonThiChiKhoaDuoc() {
		BusinessException e = assertThrows(BusinessException.class, () -> adminUserService.delete(USER1, "admin"));
		assertTrue(e.getMessage().contains("KHOÁ"));
	}

	@Test
	void taiKhoan_taoNhanVien_loi_khongGuiEmail() {
		assertThrows(BusinessException.class, () -> adminUserService.createStaff("shipper_moi", "Shipper Mới",
				"moi@utetra.vn", "0911111111", "SHIPPER", null, "123456", "123456"), "shipper bắt buộc chọn chi nhánh");
		assertThrows(BusinessException.class, () -> adminUserService.createStaff("khach_moi", "Khách",
				"khach@utetra.vn", "0911111112", "USER", null, "123456", "123456"), "khách tự đăng ký trên web");
		assertThrows(BusinessException.class, () -> adminUserService.createStaff("admin", "Trùng",
				"trung@utetra.vn", "0911111113", "ADMIN", null, "123456", "123456"), "trùng tên đăng nhập");
		assertThrows(BusinessException.class, () -> adminUserService.createStaff("nv_moi", "NV",
				"nv@utetra.vn", "0911111114", "MANAGER", null, "123456", "654321"), "nhập lại mật khẩu không khớp");
		assertThrows(BusinessException.class, () -> adminUserService.createStaff("nv_moi2", "NV",
				"nv2@utetra.vn", null, "MANAGER", null, "123456", "123456"), "bắt buộc số điện thoại");
		assertThrows(BusinessException.class, () -> adminUserService.createStaff("nv_moi3", "NV",
				"nv3@utetra.vn", "0900000006", "MANAGER", null, "123456", "123456"), "trùng số điện thoại của user1");
	}

	@Test
	void taiKhoan_shipper_chuyenChiNhanh_vaKhoaKhiDangGiao() {
		adminUserService.changeShipperBranch(SHIPPER1, 2);
		assertEquals(2, user(SHIPPER1).getBranch().getId());
		assertThrows(BusinessException.class, () -> adminUserService.changeShipperBranch(SHIPPER1, 2), "đã ở chi nhánh 2");
		assertThrows(BusinessException.class, () -> adminUserService.changeShipperBranch(MANAGER1, 2), "chỉ shipper");

		em.createNativeQuery("UPDATE Orders SET status = 'DELIVERING', shipper_id = 5 WHERE id = 2").executeUpdate();
		em.clear();
		assertThrows(BusinessException.class, () -> adminUserService.toggleActive(SHIPPER1, "admin"));
		assertThrows(BusinessException.class, () -> adminUserService.changeShipperBranch(SHIPPER1, 1));
	}

	private String trangThaiMon(int branchId, int productId) {
		return branchStockService.stock(branchId).products().stream().filter(r -> r.id() == productId)
				.findFirst().orElseThrow().status();
	}

	@Test
	void tonKho_managerBaoHet_adminThayHet_moLaiThiCon() {
		assertEquals("OK", trangThaiMon(1, 3));
		assertFalse(branchStockService.toggleProduct(1, 3, false).available(), "bấm lần 1 = báo hết");
		em.flush();
		assertEquals("OUT", trangThaiMon(1, 3));
		assertEquals("OK", trangThaiMon(2, 3), "chi nhánh khác không ảnh hưởng");
		assertTrue(branchStockService.toggleProduct(1, 3, true).available(), "Admin mở bán lại");
		em.flush();
		assertEquals("OK", trangThaiMon(1, 3));
	}

	@Test
	void tonKho_ngungBanToanChuoi_chiNhanhKhongMoDuoc() {
		em.createNativeQuery("UPDATE Products SET is_available = 0 WHERE id = 3").executeUpdate();
		em.clear();
		assertEquals("OFF", trangThaiMon(1, 3));
		assertThrows(BusinessException.class, () -> branchStockService.toggleProduct(1, 3, false));
		assertThrows(BusinessException.class, () -> branchStockService.toggleProduct(1, 3, true));
	}

	@Test
	void tonKho_chiNhanhTamNgung_managerKhongChinh_adminVanChinh() {
		em.createNativeQuery("UPDATE Branches SET status = 'SUSPENDED' WHERE id = 1").executeUpdate();
		em.clear();
		assertThrows(BusinessException.class, () -> branchStockService.toggleProduct(1, 3, false));
		assertFalse(branchStockService.toggleProduct(1, 3, true).available());
	}

	@Test
	void tonKho_toppingBaoHet() {
		assertFalse(branchStockService.toggleTopping(1, 1, false).available());
		em.flush();
		assertEquals("OUT", branchStockService.stock(1).toppings().stream().filter(r -> r.id() == 1)
				.findFirst().orElseThrow().status());
		assertTrue(branchStockService.unavailableToppingIds(1).contains(1));
		assertFalse(branchStockService.unavailableToppingIds(2).contains(1));
	}

	@Test
	void baoCao_theoNgay_nativeKhopTongJPQL_caChuoiVaChiNhanh() {
		LocalDate from = LocalDate.of(2026, 9, 1);
		LocalDate to = LocalDate.now();
		for (Integer branchId : new Integer[] { null, 1, 2 }) {
			ReportService.Point tong = ReportService.total(reportService.byDay(from, to, branchId));
			BigDecimal jpql = orderRepository.sumRevenueOfBranch(OrderStatus.DELIVERED, from.atStartOfDay(),
					to.plusDays(1).atStartOfDay(), branchId);
			long soDon = orderRepository.countDeliveredOfBranch(OrderStatus.DELIVERED, from.atStartOfDay(),
					to.plusDays(1).atStartOfDay(), branchId);
			assertEquals(0, jpql.compareTo(tong.revenue()), "doanh thu chi nhánh " + branchId);
			assertEquals(soDon, tong.orders());
		}
		assertEquals(7, reportService.byDay(LocalDate.now().minusDays(6), LocalDate.now(), null).size(),
				"đủ 7 ngày, ngày không bán = 0");
	}

	@Test
	void baoCao_monBanChay_giamDanSoLy_vaDu12Thang() {
		List<ReportService.TopProduct> top = reportService.topProducts(LocalDate.of(2026, 1, 1), LocalDate.now(), null, 5);
		assertFalse(top.isEmpty());
		assertTrue(top.size() <= 5);
		for (int i = 1; i < top.size(); i++) {
			assertTrue(top.get(i - 1).quantity() >= top.get(i).quantity(), "nhiều ly lên trước");
		}
		List<ReportService.Point> months = reportService.byMonth(12, LocalDate.now(), null);
		assertEquals(12, months.size());
		BigDecimal tongThang = months.stream().map(ReportService.Point::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
		LocalDate dau = LocalDate.now().withDayOfMonth(1).minusMonths(11);
		BigDecimal jpql = orderRepository.sumRevenueOfBranch(OrderStatus.DELIVERED, dau.atStartOfDay(),
				LocalDate.now().plusDays(1).atStartOfDay(), null);
		assertEquals(0, jpql.compareTo(tongThang), "12 tháng cộng lại = tổng cùng kỳ");
	}

	@Autowired VoucherService voucherService;

	private vn.utetra.dto.request.VoucherReq tangRieng(String code, String khach) {
		vn.utetra.dto.request.VoucherReq r = new vn.utetra.dto.request.VoucherReq();
		r.setCode(code);
		r.setName("Tặng riêng JUnit");
		r.setApplyTo(vn.utetra.entity.enums.VoucherApplyTo.PRODUCT);
		r.setDiscountType(vn.utetra.entity.enums.DiscountType.AMOUNT);
		r.setDiscountValue(new BigDecimal("10000"));
		r.setMinOrderValue(BigDecimal.ZERO);
		r.setQuantity(5);
		r.setStartDate(java.time.LocalDateTime.now().minusDays(1));
		r.setEndDate(java.time.LocalDateTime.now().plusDays(7));
		r.setAudience(vn.utetra.entity.enums.VoucherAudience.SPECIFIC);
		r.setAssignedUsers(khach);
		return r;
	}

	@Test
	void voucherTangRieng_chiKhachDuocChiDinhDungDuoc() {
		User u1 = userRepository.findByUsername("user1").orElseThrow();
		User u2 = userRepository.findByUsername("user2").orElseThrow();
		voucherService.create(tangRieng("JUNITVIP1", "user1"));
		BigDecimal giam = voucherService.calculateDiscount("JUNITVIP1", 1, u1.getId(),
				vn.utetra.entity.enums.PaymentMethod.COD, new BigDecimal("50000"));
		assertEquals(0, new BigDecimal("10000").compareTo(giam));
		assertThrows(BusinessException.class, () -> voucherService.calculateDiscount("JUNITVIP1", 1, u2.getId(),
				vn.utetra.entity.enums.PaymentMethod.COD, new BigDecimal("50000")));
		assertThrows(BusinessException.class, () -> voucherService.create(tangRieng("JUNITVIP2", "")));
		assertThrows(BusinessException.class, () -> voucherService.create(tangRieng("JUNITVIP3", "khongcoainay")));
		assertThrows(BusinessException.class, () -> voucherService.create(tangRieng("JUNITVIP4", "admin")));
	}

	@Test
	void baoCao_tachBanHangVaPhiGiao_khopTungDon() {
		LocalDate from = LocalDate.of(2026, 1, 1);
		LocalDate to = LocalDate.now();
		List<vn.utetra.entity.Order> don = orderRepository.findDeliveredOfBranch(OrderStatus.DELIVERED,
				from.atStartOfDay(), to.plusDays(1).atStartOfDay(), null);
		BigDecimal ship = don.stream().map(vn.utetra.entity.Order::getShippingFee).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal ban = don.stream().map(vn.utetra.entity.Order::getGoodsRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
		ReportService.Point tong = reportService.summary(from.atStartOfDay(), to.plusDays(1).atStartOfDay(), null);
		ReportService.Point theoNgay = ReportService.total(reportService.byDay(from, to, null));
		assertEquals(0, ship.compareTo(tong.shipping()), "phí giao");
		assertEquals(0, ban.compareTo(tong.sales()), "bán hàng");
		assertEquals(0, tong.revenue().compareTo(tong.sales().add(tong.shipping())), "tổng thu = bán hàng + phí giao");
		assertEquals(0, tong.shipping().compareTo(theoNgay.shipping()));
		BigDecimal shipChiNhanh = reportService.byBranch(from, to).stream().map(ReportService.BranchRevenue::shipping)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		assertEquals(0, ship.compareTo(shipChiNhanh));
		List<vn.utetra.entity.Order> moiNhat = reportService.latestShipped(null, 10);
		assertTrue(moiNhat.size() <= 10);
		moiNhat.forEach(o -> assertTrue(o.getShippingFee().signum() > 0));
	}

	@Autowired AuthenticationService authenticationService;

	@Test
	void dangNhap_bangTenEmailHoacSoDienThoai() {
		User u1 = userRepository.findByUsername("user1").orElseThrow();
		assertEquals(u1.getId(), userRepository.findByLogin("user1").orElseThrow().getId());
		assertEquals(u1.getId(), userRepository.findByLogin("user1@gmail.com").orElseThrow().getId());
		assertEquals(u1.getId(), userRepository.findByLogin("USER1@GMAIL.COM").orElseThrow().getId());
		assertEquals(u1.getId(), userRepository.findByLogin("0900000006").orElseThrow().getId());
		assertEquals(u1.getId(), userRepository.findByLogin("+84 900 000 006").orElseThrow().getId());
		assertTrue(userRepository.findByLogin("0999999999").isEmpty());

		vn.utetra.dto.request.LoginRequest req = new vn.utetra.dto.request.LoginRequest();
		req.setUsername("0900000006");
		req.setPassword("123456");
		assertEquals(u1.getId(), authenticationService.authenticate(req).getId(), "đăng nhập bằng số điện thoại");
		req.setPassword("sai-mat-khau");
		assertThrows(RuntimeException.class, () -> authenticationService.authenticate(req));
	}

	@Test
	void dangKy_soDienThoaiBatBuocDungDinhDangKhongTrung() {
		java.util.function.Function<String, vn.utetra.dto.request.RegisterRequest> req = phone -> {
			vn.utetra.dto.request.RegisterRequest r = new vn.utetra.dto.request.RegisterRequest();
			r.setUsername("khachmoi_junit");
			r.setPassword("123456");
			r.setFullName("Khách Mới");
			r.setEmail("khachmoi_junit@gmail.com");
			r.setPhone(phone);
			return r;
		};
		RuntimeException e1 = assertThrows(RuntimeException.class, () -> authenticationService.signup(req.apply(null)));
		assertTrue(e1.getMessage().contains("số điện thoại"));
		assertThrows(RuntimeException.class, () -> authenticationService.signup(req.apply("12345")));
		RuntimeException e2 = assertThrows(RuntimeException.class, () -> authenticationService.signup(req.apply("0900000006")));
		assertTrue(e2.getMessage().contains("đã được dùng"));
		assertTrue(userRepository.findByUsername("khachmoi_junit").isEmpty(), "lỗi thì không tạo tài khoản");
	}

	@Autowired BranchService branchService;
	@Autowired vn.utetra.repository.BranchRepository branchRepository;

	@Test
	void xoaChiNhanh_daCoDonThiChan_chuaCoDonThiXoaDuoc() {
		BusinessException e = assertThrows(BusinessException.class, () -> branchService.delete(1));
		assertTrue(e.getMessage().contains("Tạm ngưng"));
		assertTrue(branchRepository.findById(1).isPresent(), "chi nhánh có đơn vẫn còn");

		vn.utetra.entity.Branch b = new vn.utetra.entity.Branch();
		b.setName("Chi nhánh tạo nhầm");
		b.setAddress("76 đường 1B");
		b.setStatus(vn.utetra.entity.enums.BranchStatus.APPROVED);
		b.setOwner(userRepository.findByUsername("manager3").orElseThrow());
		b = branchRepository.saveAndFlush(b);
		User s2 = userRepository.findByUsername("shipper2").orElseThrow();
		s2.setBranch(b);
		userRepository.saveAndFlush(s2);

		String msg = branchService.delete(b.getId());
		em.flush();
		em.clear();
		assertTrue(msg.contains("Chưa phân công"));
		assertTrue(branchRepository.findById(b.getId()).isEmpty());
		assertNull(userRepository.findByUsername("shipper2").orElseThrow().getBranch(), "shipper về chưa phân công");
	}
}
