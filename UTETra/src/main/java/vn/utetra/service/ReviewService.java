package vn.utetra.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import vn.utetra.dto.response.ReviewRes;
import vn.utetra.dto.response.ReviewableItemRes;
import vn.utetra.entity.OrderDetail;
import vn.utetra.entity.Review;
import vn.utetra.entity.ReviewMedia;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderDetailRepository;
import vn.utetra.repository.ReviewMediaRepository;
import vn.utetra.repository.ReviewRepository;

/**
 * Đánh giá + bình luận sản phẩm ĐÃ MUA:
 * - Chỉ ly thuộc đơn ĐÃ GIAO của chính khách, mỗi ly đánh giá 1 lần (Reviews.order_detail_id UNIQUE)
 * - Số sao 1..5, nội dung tối thiểu 50 ký tự (tối đa 2000)
 * - Đính kèm tối đa 5 ảnh/video, tải lên Cloudinary (thư mục utetra/reviews)
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

	public static final int MIN_CONTENT = 50;
	public static final int MAX_CONTENT = 2000;
	public static final int MAX_FILES = 5;
	public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;   // 5MB
	public static final long MAX_VIDEO_BYTES = 30L * 1024 * 1024;  // 30MB

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final Set<String> VIDEO_EXT = Set.of("mp4", "mov", "webm", "m4v", "avi", "mkv");
	private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp");

	private final ReviewRepository reviewRepository;
	private final ReviewMediaRepository reviewMediaRepository;
	private final OrderDetailRepository orderDetailRepository;
	private final CloudinaryService cloudinaryService;

	public ReviewService(ReviewRepository reviewRepository, ReviewMediaRepository reviewMediaRepository,
			OrderDetailRepository orderDetailRepository, CloudinaryService cloudinaryService) {
		this.reviewRepository = reviewRepository;
		this.reviewMediaRepository = reviewMediaRepository;
		this.orderDetailRepository = orderDetailRepository;
		this.cloudinaryService = cloudinaryService;
	}

	// ================= XEM =================

	/** Đánh giá của 1 món, mới nhất lên đầu (kèm ảnh/video) */
	public List<ReviewRes> listByProduct(Integer productId) {
		List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
		if (reviews.isEmpty()) {
			return List.of();
		}
		Map<Integer, List<ReviewRes.Media>> media = reviewMediaRepository
				.findByReviewIdInOrderByIdAsc(reviews.stream().map(Review::getId).toList()).stream()
				.collect(Collectors.groupingBy(m -> m.getReview().getId(),
						Collectors.mapping(m -> new ReviewRes.Media(m.getCloudinaryId(), m.getMediaType()),
								Collectors.toList())));
		return reviews.stream().map(r -> toRes(r, media.getOrDefault(r.getId(), List.of()))).toList();
	}

	/**
	 * Các ly khách đã mua + đã nhận (đơn Đã giao). productId = null: mọi món.
	 * reviewed = true: ly đó đã đánh giá rồi.
	 */
	public List<ReviewableItemRes> purchasedItems(Integer userId, Integer productId) {
		if (userId == null) {
			return List.of();
		}
		List<OrderDetail> details = orderDetailRepository.findPurchased(userId, productId, OrderStatus.DELIVERED);
		if (details.isEmpty()) {
			return List.of();
		}
		Set<Integer> reviewed = reviewRepository.findByOrderDetailIdIn(details.stream().map(OrderDetail::getId).toList())
				.stream().map(r -> r.getOrderDetail().getId()).collect(Collectors.toSet());
		return details.stream().map(d -> new ReviewableItemRes(d.getId(), d.getOrder().getId(), d.getProduct().getId(),
				d.getProduct().getName(), d.getProduct().getImageCloudinaryId(), info(d),
				time(d.getOrder().getDeliveredAt()), reviewed.contains(d.getId()))).toList();
	}

	/** Chỉ các ly CHƯA đánh giá (để hiện form) */
	public List<ReviewableItemRes> pendingItems(Integer userId, Integer productId) {
		return purchasedItems(userId, productId).stream().filter(i -> !i.reviewed()).toList();
	}

	// ================= VIẾT ĐÁNH GIÁ =================

	/**
	 * Khách đánh giá 1 ly đã mua.
	 * Kiểm tra hết (sao, nội dung, quyền, trạng thái đơn, file) TRƯỚC khi tải file lên Cloudinary.
	 */
	@Transactional
	public ReviewRes create(Integer userId, Integer orderDetailId, Integer rating, String content,
			List<MultipartFile> files) {
		if (rating == null || rating < 1 || rating > 5) {
			throw new BusinessException("Vui lòng chọn số sao từ 1 đến 5");
		}
		String text = content == null ? "" : content.trim();
		if (text.length() < MIN_CONTENT) {
			throw new BusinessException("Nội dung đánh giá tối thiểu " + MIN_CONTENT + " ký tự (bạn mới viết "
					+ text.length() + " ký tự)");
		}
		if (text.length() > MAX_CONTENT) {
			throw new BusinessException("Nội dung đánh giá tối đa " + MAX_CONTENT + " ký tự");
		}

		OrderDetail od = orderDetailId == null ? null : orderDetailRepository.findById(orderDetailId).orElse(null);
		if (od == null || !od.getOrder().getUser().getId().equals(userId)) {
			throw new ResourceNotFoundException("Không tìm thấy món bạn đã mua");
		}
		if (od.getOrder().getStatus() != OrderStatus.DELIVERED) {
			throw new BusinessException("Chỉ đánh giá được món trong đơn đã giao");
		}
		if (reviewRepository.existsByOrderDetailId(od.getId())) {
			throw new BusinessException("Bạn đã đánh giá ly này rồi");
		}

		List<MultipartFile> uploads = validFiles(files);

		Review r = new Review();
		r.setUser(od.getOrder().getUser());
		r.setProduct(od.getProduct());
		r.setOrderDetail(od);
		r.setRating(rating);
		r.setContent(text);
		r.setCreatedAt(LocalDateTime.now());
		r = reviewRepository.saveAndFlush(r);

		// Tải ảnh/video. Lỗi giữa chừng -> xoá các file đã tải, huỷ cả đánh giá (rollback)
		List<String> uploaded = new ArrayList<>();
		List<ReviewRes.Media> media = new ArrayList<>();
		try {
			for (MultipartFile f : uploads) {
				String type = mediaType(f);
				String url = cloudinaryService.uploadImage(f, "reviews");
				if (url == null) {
					continue;
				}
				uploaded.add(url);
				ReviewMedia m = new ReviewMedia();
				m.setReview(r);
				m.setCloudinaryId(url);
				m.setMediaType(type);
				reviewMediaRepository.save(m);
				media.add(new ReviewRes.Media(url, type));
			}
		} catch (RuntimeException e) {
			uploaded.forEach(cloudinaryService::deleteImage);
			throw new BusinessException("Không tải được ảnh/video lên, vui lòng thử lại");
		}
		return toRes(r, media);
	}

	// ================= HÀM PHỤ =================

	/** Bỏ file rỗng, kiểm tra số lượng, loại (ảnh/video) và dung lượng */
	private List<MultipartFile> validFiles(List<MultipartFile> files) {
		List<MultipartFile> list = files == null ? List.of()
				: files.stream().filter(f -> f != null && !f.isEmpty()).toList();
		if (list.size() > MAX_FILES) {
			throw new BusinessException("Chỉ đính kèm tối đa " + MAX_FILES + " ảnh/video");
		}
		for (MultipartFile f : list) {
			String type = mediaType(f);
			if (type == null) {
				throw new BusinessException("File \"" + f.getOriginalFilename() + "\" không phải ảnh hoặc video");
			}
			long max = "VIDEO".equals(type) ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
			if (f.getSize() > max) {
				throw new BusinessException("File \"" + f.getOriginalFilename() + "\" quá lớn (ảnh tối đa 5MB, video tối đa 30MB)");
			}
		}
		return list;
	}

	/** IMAGE / VIDEO theo loại file (hoặc đuôi file); không phải ảnh/video -> null */
	static String mediaType(MultipartFile f) {
		String ct = f.getContentType() == null ? "" : f.getContentType().toLowerCase();
		if (ct.startsWith("image/")) return "IMAGE";
		if (ct.startsWith("video/")) return "VIDEO";
		String name = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase();
		String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
		if (IMAGE_EXT.contains(ext)) return "IMAGE";
		if (VIDEO_EXT.contains(ext)) return "VIDEO";
		return null;
	}

	private ReviewRes toRes(Review r, List<ReviewRes.Media> media) {
		User u = r.getUser();
		String name = u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
		return new ReviewRes(r.getId(), name, u.getAvatarCloudinaryId(), r.getRating(), r.getContent(),
				time(r.getCreatedAt()), r.getOrderDetail() == null ? "" : info(r.getOrderDetail()), media);
	}

	/** "Size L · 70% đường · 50% đá" */
	static String info(OrderDetail d) {
		return "Size " + d.getSize() + " · " + d.getSugarLevel() + "% đường · " + d.getIceLevel() + "% đá";
	}

	private static String time(LocalDateTime t) {
		return t == null ? "" : t.format(TIME);
	}
}
