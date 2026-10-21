package vn.utetra.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import vn.utetra.dto.response.HighlightPageRes;
import vn.utetra.dto.response.ManagerVoucherRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.dto.response.ProductCardRes;
import vn.utetra.dto.response.ProductDetailRes;
import vn.utetra.dto.response.ReviewRes;
import vn.utetra.dto.response.ReviewableItemRes;
import vn.utetra.entity.User;
import vn.utetra.entity.Voucher;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.UserRepository;

/**
 * TEST TUẦN 3 (User): 20 sản phẩm nhất có phân trang, chi tiết món, thích sản phẩm, sản phẩm đã xem,
 * đánh giá + bình luận sản phẩm đã mua, cập nhật thông tin cá nhân, đổi mật khẩu,
 * khuyến mãi của chi nhánh (Manager chỉ xem, Admin tạo).
 *
 * Mỗi test tự rollback. Chạy lại UTETra.sql trước khi chạy để dữ liệu mẫu đúng.
 * Không test phần tải file thật lên Cloudinary (chỉ test các trường hợp file bị từ chối TRƯỚC khi tải).
 *
 * Dữ liệu mẫu dùng ở đây:
 * - user1 (id 6, mật khẩu 123456): thích món 1, 7; đã xem món 1, 2, 5
 *   đơn #1 ĐÃ GIAO: ly #1 = món 1 (ĐÃ đánh giá), ly #2 = món 2 (CHƯA đánh giá)
 *   đơn #2 CHỜ XÁC NHẬN: ly #3 = món 5
 * - user2 (id 7)
 * - 40 món, mỗi món có size S (+0), M (+5.000), L (+10.000). Món 1 = Trà sữa trân châu đường đen 35.000, danh mục Trà sữa
 */
@SpringBootTest
@Transactional
class Week3ServiceTest {

	static final int USER1 = 6;
	static final int USER2 = 7;
	static final String CONTENT_OK = "Trà đào cam sả thơm mùi sả, đào giòn, vị chua ngọt vừa phải, uống rất đã khát. Sẽ mua lại!";

	@Autowired ProductService productService;
	@Autowired FavoriteService favoriteService;
	@Autowired ViewedProductService viewedProductService;
	@Autowired ReviewService reviewService;
	@Autowired UserService userService;
	@Autowired UserRepository userRepository;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired VoucherService voucherService;

	// ======================= 20 SẢN PHẨM NHẤT (PHÂN TRANG) =======================

	@Test
	void top20_banChay_phanTrang8MonMotTrang() {
		HighlightPageRes p0 = productService.topProducts("sold", 0, 8);
		assertEquals(20, p0.total());
		assertEquals(3, p0.totalPages());
		assertEquals(8, p0.items().size());
		assertEquals(0, p0.offset());
		for (int i = 1; i < p0.items().size(); i++) {
			assertTrue(p0.items().get(i - 1).soldCount() >= p0.items().get(i).soldCount(), "phải xếp bán nhiều -> ít");
		}
		HighlightPageRes p2 = productService.topProducts("sold", 2, 8);
		assertEquals(4, p2.items().size(), "trang cuối còn 4 món (hạng 17..20)");
		assertEquals(16, p2.offset());
		assertTrue(p0.items().get(7).soldCount() >= productService.topProducts("sold", 1, 8).items().get(0).soldCount());
	}

	@Test
	void top20_trangVuotQua_veTrangCuoi_loaiSai_veMoiNhat() {
		assertEquals(2, productService.topProducts("sold", 99, 8).page());
		assertEquals(0, productService.topProducts("sold", -5, 8).page());
		assertEquals("newest", productService.topProducts("abc", 0, 8).type());
		assertEquals(20, productService.top20("newest").size());
	}

	@Test
	void top20_danhGiaCao_chiMonCoDanhGia_diemGiamDan() {
		List<ProductCardRes> list = productService.top20("rating");
		assertFalse(list.isEmpty());
		assertTrue(list.size() <= 20);
		for (int i = 0; i < list.size(); i++) {
			assertTrue(list.get(i).reviewCount() > 0);
			if (i > 0) assertTrue(list.get(i - 1).avgRating() >= list.get(i).avgRating());
		}
	}

	@Test
	void top20_yeuThich_chiMonCoLuotThich_giamDan() {
		List<ProductCardRes> list = productService.top20("favorite");
		assertFalse(list.isEmpty());
		for (int i = 0; i < list.size(); i++) {
			assertTrue(list.get(i).favoriteCount() > 0);
			if (i > 0) assertTrue(list.get(i - 1).favoriteCount() >= list.get(i).favoriteCount());
		}
	}

	// ======================= CHI TIẾT MÓN =======================

	@Test
	void chiTietMon_giaTheoSize_soSao() {
		ProductDetailRes d = productService.getDetail(1);
		assertEquals("Trà sữa trân châu đường đen", d.name());
		assertEquals("Trà sữa", d.categoryName());
		assertEquals(List.of("S", "M", "L"), d.sizes().stream().map(ProductDetailRes.SizeOption::size).toList());
		assertEquals(0, d.sizes().get(0).price().compareTo(new java.math.BigDecimal("35000")));
		assertEquals(0, d.sizes().get(2).price().compareTo(new java.math.BigDecimal("45000")));
		assertEquals(5, d.starCounts().size());
		assertEquals(d.reviewCount(), d.starCounts().stream().mapToLong(Long::longValue).sum());
		assertTrue(d.fullStars() >= 0 && d.fullStars() <= 5);
	}

	@Test
	void chiTietMon_khongTonTai_baoLoi_monCungDanhMuc() {
		assertThrows(ResourceNotFoundException.class, () -> productService.getDetail(99999));
		List<ProductCardRes> related = productService.related(1, 8);
		assertFalse(related.isEmpty());
		assertTrue(related.size() <= 8);
		assertTrue(related.stream().noneMatch(c -> c.id() == 1), "không lặp lại món đang xem");
		assertTrue(related.stream().allMatch(c -> "Trà sữa".equals(c.categoryName())));
	}

	// ======================= THÍCH SẢN PHẨM =======================

	@Test
	void thich_bamLan2_boThich() {
		assertTrue(favoriteService.isFavorite(USER1, 1));
		long before = favoriteService.countOfProduct(1);
		assertFalse(favoriteService.toggle(USER1, 1), "đang thích -> bấm = bỏ thích");
		assertFalse(favoriteService.isFavorite(USER1, 1));
		assertEquals(before - 1, favoriteService.countOfProduct(1));
		assertTrue(favoriteService.toggle(USER1, 1), "bấm lại = thích lại");
		assertEquals(before, favoriteService.countOfProduct(1));
	}

	@Test
	void thich_monMoi_lenDauDanhSach() {
		long before = favoriteService.count(USER1);
		assertTrue(favoriteService.toggle(USER1, 3));
		List<ProductCardRes> list = favoriteService.list(USER1);
		assertEquals(before + 1, list.size());
		assertEquals(3, list.get(0).id(), "thích sau cùng lên đầu");
		assertFalse(favoriteService.isFavorite(null, 3), "chưa đăng nhập = chưa thích");
	}

	@Test
	void thich_monKhongTonTai_baoLoi() {
		assertThrows(ResourceNotFoundException.class, () -> favoriteService.toggle(USER1, 99999));
	}

	// ======================= SẢN PHẨM ĐÃ XEM =======================

	@Test
	void daXem_monMoi_lenDau_xemLaiKhongTrung() {
		long before = viewedProductService.count(USER1);
		viewedProductService.record(USER1, 10);
		assertEquals(before + 1, viewedProductService.count(USER1));
		assertEquals(10, viewedProductService.recent(USER1, 40).get(0).id());

		viewedProductService.record(USER1, 1); // món đã xem rồi -> chỉ cập nhật thời gian
		assertEquals(before + 1, viewedProductService.count(USER1));
		assertEquals(1, viewedProductService.recent(USER1, 40).get(0).id());
		assertEquals(2, viewedProductService.recent(USER1, 2).size());
	}

	@Test
	void daXem_monKhongTonTai_boQua_xoaLichSu() {
		long before = viewedProductService.count(USER1);
		viewedProductService.record(USER1, 99999);
		viewedProductService.record(null, 1);
		assertEquals(before, viewedProductService.count(USER1));
		assertEquals(before, viewedProductService.clear(USER1));
		assertEquals(0, viewedProductService.count(USER1));
		assertTrue(viewedProductService.recent(USER1, 40).isEmpty());
	}

	// ======================= ĐÁNH GIÁ + BÌNH LUẬN =======================

	@Test
	void danhGia_lyDaMua_chiDonDaGiao() {
		List<ReviewableItemRes> mon2 = reviewService.purchasedItems(USER1, 2);
		assertEquals(1, mon2.size());
		assertEquals(2, mon2.get(0).orderDetailId());
		assertFalse(mon2.get(0).reviewed());

		List<ReviewableItemRes> mon1 = reviewService.purchasedItems(USER1, 1);
		assertTrue(mon1.get(0).reviewed(), "ly #1 đã đánh giá trong dữ liệu mẫu");
		assertTrue(reviewService.pendingItems(USER1, 1).isEmpty());

		assertTrue(reviewService.purchasedItems(USER1, 5).isEmpty(), "đơn #2 chưa giao -> chưa đánh giá được");
		assertTrue(reviewService.purchasedItems(USER2, 2).isEmpty());
		assertTrue(reviewService.purchasedItems(null, 2).isEmpty());
	}

	@Test
	void danhGia_thanhCong_hienTrenTrangMon() {
		int before = reviewService.listByProduct(2).size();
		ReviewRes r = reviewService.create(USER1, 2, 4, "  " + CONTENT_OK + "  ", List.of());
		assertEquals(4, r.rating());
		assertEquals(CONTENT_OK, r.content(), "bỏ khoảng trắng đầu/cuối");
		assertEquals("Size M · 50% đường · 100% đá", r.purchasedInfo());
		assertTrue(r.media().isEmpty());

		List<ReviewRes> list = reviewService.listByProduct(2);
		assertEquals(before + 1, list.size());
		assertEquals(r.id(), list.get(0).id(), "mới nhất lên đầu");
		assertTrue(reviewService.purchasedItems(USER1, 2).get(0).reviewed());
		assertEquals(1, productService.getDetail(2).reviewCount() - (before));
	}

	@Test
	void danhGia_noiDungDuoi50KyTu_baoLoi() {
		String s49 = "a".repeat(49);
		BusinessException e = assertThrows(BusinessException.class,
				() -> reviewService.create(USER1, 2, 5, "     " + s49 + "     ", List.of()));
		assertTrue(e.getMessage().contains("50"));
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 5, null, List.of()));
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 5, "a".repeat(2001), List.of()));
		assertNotNull(reviewService.create(USER1, 2, 5, "b".repeat(50), List.of()), "đúng 50 ký tự là được");
	}

	@Test
	void danhGia_soSaoSai_baoLoi() {
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 0, CONTENT_OK, List.of()));
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 6, CONTENT_OK, List.of()));
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, null, CONTENT_OK, List.of()));
	}

	@Test
	void danhGia_lyDaDanhGia_donChuaGiao_lyNguoiKhac_baoLoi() {
		BusinessException again = assertThrows(BusinessException.class,
				() -> reviewService.create(USER1, 1, 5, CONTENT_OK, List.of()));
		assertTrue(again.getMessage().contains("đã đánh giá"));

		BusinessException pending = assertThrows(BusinessException.class,
				() -> reviewService.create(USER1, 3, 5, CONTENT_OK, List.of()));
		assertTrue(pending.getMessage().contains("đã giao"));

		assertThrows(ResourceNotFoundException.class, () -> reviewService.create(USER2, 2, 5, CONTENT_OK, List.of()));
		assertThrows(ResourceNotFoundException.class, () -> reviewService.create(USER1, 99999, 5, CONTENT_OK, List.of()));

		// Đánh giá lần 2 cùng 1 ly
		reviewService.create(USER1, 2, 5, CONTENT_OK, List.of());
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 5, CONTENT_OK, List.of()));
	}

	@Test
	void danhGia_file_khongPhaiAnhVideo_quaNhieu_quaLon_baoLoi() {
		MultipartFile pdf = new MockMultipartFile("media", "hoa-don.pdf", "application/pdf", new byte[10]);
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 5, CONTENT_OK, List.of(pdf)));

		List<MultipartFile> six = new ArrayList<>();
		for (int i = 0; i < 6; i++) six.add(new MockMultipartFile("media", "a" + i + ".jpg", "image/jpeg", new byte[10]));
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 5, CONTENT_OK, six));

		MultipartFile bigImg = new MockMultipartFile("media", "to.jpg", "image/jpeg", new byte[6 * 1024 * 1024]);
		assertThrows(BusinessException.class, () -> reviewService.create(USER1, 2, 5, CONTENT_OK, List.of(bigImg)));

		// Bị từ chối thì chưa lưu gì: ly #2 vẫn chưa đánh giá
		assertFalse(reviewService.purchasedItems(USER1, 2).get(0).reviewed());
	}

	@Test
	void danhGia_nhanDangLoaiFile() {
		assertEquals("IMAGE", ReviewService.mediaType(new MockMultipartFile("m", "a.png", "image/png", new byte[1])));
		assertEquals("VIDEO", ReviewService.mediaType(new MockMultipartFile("m", "a.mp4", "video/mp4", new byte[1])));
		assertEquals("VIDEO", ReviewService.mediaType(new MockMultipartFile("m", "clip.MOV", null, new byte[1])));
		assertNull(ReviewService.mediaType(new MockMultipartFile("m", "a.txt", "text/plain", new byte[1])));
	}

	// ======================= THÔNG TIN CÁ NHÂN + ĐỔI MẬT KHẨU =======================

	@Test
	void capNhatThongTin_chuanHoaTenVaSoDienThoai() {
		User u = userService.updateProfile(USER1, "  Trần   Thị  Khách Mới ", "091 234 5678", null);
		assertEquals("Trần Thị Khách Mới", u.getFullName());
		assertEquals("0912345678", u.getPhone());
		assertEquals("user1@gmail.com", u.getEmail(), "email không đổi");

		assertNull(userService.updateProfile(USER1, "Khách", "", null).getPhone(), "bỏ trống số điện thoại = xoá");
	}

	@Test
	void capNhatThongTin_duLieuSai_baoLoi() {
		assertThrows(BusinessException.class, () -> userService.updateProfile(USER1, "   ", "0912345678", null));
		assertThrows(BusinessException.class, () -> userService.updateProfile(USER1, "A".repeat(101), null, null));
		assertThrows(BusinessException.class, () -> userService.updateProfile(USER1, "Khách", "12345", null));
		assertThrows(BusinessException.class, () -> userService.updateProfile(USER1, "Khách", "1912345678", null));
		MultipartFile notImage = new MockMultipartFile("avatar", "a.txt", "text/plain", new byte[5]);
		assertThrows(BusinessException.class, () -> userService.updateProfile(USER1, "Khách", null, notImage));
		assertEquals("Trần Thị Khách", userService.getProfile(USER1).getFullName(), "lỗi thì không đổi gì");
	}

	@Test
	void doiMatKhau_thanhCong() {
		userService.changePassword(USER1, "123456", "matkhaumoi", "matkhaumoi");
		User u = userRepository.findById(USER1).orElseThrow();
		assertTrue(passwordEncoder.matches("matkhaumoi", u.getPassword()));
		assertFalse(passwordEncoder.matches("123456", u.getPassword()));
	}

	@Test
	void doiMatKhau_saiMatKhauCu_ngan_khongKhop_trungCu_baoLoi() {
		BusinessException wrong = assertThrows(BusinessException.class,
				() -> userService.changePassword(USER1, "sai-roi", "matkhaumoi", "matkhaumoi"));
		assertTrue(wrong.getMessage().contains("hiện tại"));
		assertThrows(BusinessException.class, () -> userService.changePassword(USER1, "123456", "12345", "12345"));
		assertThrows(BusinessException.class, () -> userService.changePassword(USER1, "123456", "matkhaumoi", "matkhaukhac"));
		assertThrows(BusinessException.class, () -> userService.changePassword(USER1, "123456", "123456", "123456"));
		assertThrows(BusinessException.class, () -> userService.changePassword(USER1, null, "matkhaumoi", "matkhaumoi"));
		assertTrue(passwordEncoder.matches("123456", userRepository.findById(USER1).orElseThrow().getPassword()));
	}

	// ======================= KHUYẾN MÃI: MANAGER CHỈ XEM =======================

	@Test
	void khuyenMai_managerThayMaToanChuoiVaMaRiengChiNhanhMinh() {
		List<ManagerVoucherRes> cn1 = voucherService.branchView(1);
		List<ManagerVoucherRes> cn2 = voucherService.branchView(2);
		ManagerVoucherRes vvn15 = cn1.stream().filter(v -> v.code().equals("VVN15")).findFirst().orElseThrow();
		assertFalse(vvn15.chainWide(), "VVN15 là mã riêng chi nhánh Võ Văn Ngân");
		assertTrue(cn2.stream().noneMatch(v -> v.code().equals("VVN15")), "chi nhánh khác không thấy mã riêng");
		assertTrue(cn1.stream().anyMatch(v -> v.code().equals("UTETRA20") && v.chainWide()));
		assertTrue(cn2.stream().anyMatch(v -> v.code().equals("UTETRA20") && v.chainWide()));

		// Đơn #1 (chi nhánh 1) dùng FREESHIP10K giảm 10.000đ phí ship
		ManagerVoucherRes fs = cn1.stream().filter(v -> v.code().equals("FREESHIP10K")).findFirst().orElseThrow();
		assertTrue(fs.usedAtBranch() >= 1);
		assertTrue(fs.discountAtBranch().compareTo(new java.math.BigDecimal("10000")) >= 0);
		assertEquals("Giảm phí ship", fs.applyToLabel());
	}

	@Test
	void khuyenMai_trangThai_tatThiXuongCuoi() {
		Voucher v = voucherService.findAllVouchers().stream().filter(x -> x.getCode().equals("UTETRA20")).findFirst()
				.orElseThrow();
		voucherService.toggleActive(v.getId());
		List<ManagerVoucherRes> list = voucherService.branchView(1);
		ManagerVoucherRes off = list.stream().filter(x -> x.code().equals("UTETRA20")).findFirst().orElseThrow();
		assertEquals("OFF", off.status());
		assertEquals("Đã tắt", off.statusLabel());
		assertEquals("RUNNING", list.get(0).status(), "mã đang chạy xếp trước");
		assertTrue(list.indexOf(off) > list.stream().filter(x -> x.status().equals("RUNNING")).count() - 1);
	}

	// ======================= NHÃN TRẠNG THÁI THEO ĐỀ =======================

	@Test
	void tabDonHang_dungChuCuaDe() {
		assertEquals("Đơn hàng mới", OrderTab.PENDING.getLabel());
		assertEquals("Trả hàng - hoàn tiền", OrderTab.RETURNED.getLabel());
		assertEquals(6, OrderTab.values().length);
	}
}
