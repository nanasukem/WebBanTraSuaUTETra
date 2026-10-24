package vn.utetra.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import vn.utetra.dto.request.OrderReviewReq;
import vn.utetra.dto.request.ReviewItemReq;
import vn.utetra.dto.response.OrderReviewPageRes;
import vn.utetra.dto.response.ReviewRes;
import vn.utetra.dto.response.ReviewSubmitRes;
import vn.utetra.dto.response.ReviewableItemRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.OrderDetail;
import vn.utetra.entity.OrderReview;
import vn.utetra.entity.OrderReviewMedia;
import vn.utetra.entity.Review;
import vn.utetra.entity.ReviewMedia;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.DeliveryType;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderDetailRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.OrderReviewRepository;
import vn.utetra.repository.ReviewMediaRepository;
import vn.utetra.repository.ReviewRepository;
import vn.utetra.service.impl.OrderFlowServiceImpl;

@Service
@Transactional(readOnly = true)
public class ReviewService {

	public static final int MAX_CONTENT = 2000;
	public static final int MAX_FILES = 5;
	public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
	public static final long MAX_VIDEO_BYTES = 30L * 1024 * 1024;
	public static final int REVIEW_DAYS = OrderFlowServiceImpl.REVIEW_DAYS;

	public static final List<String> PRODUCT_TAGS = List.of("Đúng vị, rất ngon", "Độ ngọt vừa phải", "Topping nhiều",
			"Hơi ngọt", "Hơi nhạt", "Sẽ mua lại");
	public static final List<String> STORE_TAGS = List.of("Nhân viên thân thiện", "Đóng gói cẩn thận", "Làm món nhanh",
			"Quán sạch sẽ", "Giao đúng giờ");

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Set<String> VIDEO_EXT = Set.of("mp4", "mov", "webm", "m4v", "avi", "mkv");
	private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp");

	private final ReviewRepository reviewRepository;
	private final ReviewMediaRepository reviewMediaRepository;
	@org.springframework.beans.factory.annotation.Autowired
	private vn.utetra.repository.OrderReviewMediaRepository orderReviewMediaRepository;
	private final OrderDetailRepository orderDetailRepository;
	private final OrderRepository orderRepository;
	private final OrderReviewRepository orderReviewRepository;
	private final CloudinaryService cloudinaryService;
	private final OrderIssueService orderIssueService;
	private final NotificationService notificationService;

	public ReviewService(ReviewRepository reviewRepository, ReviewMediaRepository reviewMediaRepository,
			OrderDetailRepository orderDetailRepository, OrderRepository orderRepository,
			OrderReviewRepository orderReviewRepository, CloudinaryService cloudinaryService,
			OrderIssueService orderIssueService, NotificationService notificationService) {
		this.reviewRepository = reviewRepository;
		this.reviewMediaRepository = reviewMediaRepository;
		this.orderDetailRepository = orderDetailRepository;
		this.orderRepository = orderRepository;
		this.orderReviewRepository = orderReviewRepository;
		this.cloudinaryService = cloudinaryService;
		this.orderIssueService = orderIssueService;
		this.notificationService = notificationService;
	}

	public List<ReviewRes> listByProduct(Integer productId) {
		List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
		if (reviews.isEmpty()) {
			return List.of();
		}
		Map<Integer, List<ReviewRes.Media>> media = mediaOf(reviews.stream().map(Review::getId).toList());
		return reviews.stream().map(r -> toRes(r, media.getOrDefault(r.getId(), List.of()))).toList();
	}

	public List<ReviewableItemRes> purchasedItems(Integer userId, Integer productId) {
		if (userId == null) {
			return List.of();
		}
		List<OrderDetail> details = orderDetailRepository.findPurchased(userId, productId, OrderStatus.DELIVERED);
		if (details.isEmpty()) {
			return List.of();
		}
		Set<Integer> reviewed = reviewedIds(details.stream().map(OrderDetail::getId).toList());
		return details.stream().map(d -> new ReviewableItemRes(d.getId(), d.getOrder().getId(), d.getProduct().getId(),
				d.getProduct().getName(), d.getProduct().getImageCloudinaryId(), info(d),
				time(d.getOrder().getDeliveredAt()), reviewed.contains(d.getId()))).toList();
	}

	public List<ReviewableItemRes> pendingItems(Integer userId, Integer productId) {
		if (userId == null) return List.of();
		List<OrderDetail> details = orderDetailRepository.findPurchased(userId, productId, OrderStatus.DELIVERED);
		Set<Integer> reviewed = reviewedIds(details.stream().map(OrderDetail::getId).toList());
		return details.stream().filter(d -> !reviewed.contains(d.getId()) && inWindow(d.getOrder())
				&& orderIssueService.forOrder(d.getOrder().getId()) == null)
				.map(d -> new ReviewableItemRes(d.getId(), d.getOrder().getId(), d.getProduct().getId(),
						d.getProduct().getName(), d.getProduct().getImageCloudinaryId(), info(d),
						time(d.getOrder().getDeliveredAt()), false))
				.toList();
	}

	public int pendingOrderCount(Integer userId) {
		return (int) pendingItems(userId, null).stream().map(ReviewableItemRes::orderId).distinct().count();
	}

	public OrderReviewPageRes reviewPage(Integer userId, Integer orderId) {
		Order o = ownOrder(userId, orderId);
		List<OrderDetail> details = o.getDetails();
		List<Review> reviews = reviewRepository.findByOrderDetailIdIn(details.stream().map(OrderDetail::getId).toList());
		Map<Integer, Review> byDetail = reviews.stream()
				.collect(Collectors.toMap(r -> r.getOrderDetail().getId(), Function.identity(), (a, b) -> a));
		Map<Integer, List<ReviewRes.Media>> media = mediaOf(reviews.stream().map(Review::getId).toList());

		List<OrderReviewPageRes.Item> items = details.stream().map(d -> {
			Review r = byDetail.get(d.getId());
			return new OrderReviewPageRes.Item(d.getId(), d.getProduct().getId(), d.getProduct().getName(),
					d.getProduct().getImageCloudinaryId(), info(d), d.getQuantity(), r != null,
					r == null ? null : r.getRating(), r == null ? List.of() : splitTags(r.getTags()),
					r == null ? null : r.getContent(),
					r == null ? List.of() : media.getOrDefault(r.getId(), List.of()));
		}).toList();

		int pending = (int) items.stream().filter(i -> !i.reviewed()).count();
		OrderReview or = orderReviewRepository.findByOrderId(orderId).orElse(null);
		boolean can = o.getStatus() == OrderStatus.DELIVERED && inWindow(o)
				&& orderIssueService.forOrder(orderId) == null;
		return new OrderReviewPageRes(o.getId(), isPickup(o), o.getBranch().getName(), o.getBranch().getId(),
				o.getBranch().getImageCloudinaryId(), time(o.getDeliveredAt()),
				o.getDeliveredAt() == null ? null : o.getDeliveredAt().plusDays(REVIEW_DAYS).format(DAY), can, items,
				pending, or != null, or == null ? null : or.getStoreRating(),
				or == null ? List.of() : splitTags(or.getTags()), or == null ? null : or.getComment(),
				or == null ? List.of() : storeMediaOf(List.of(or.getId())).getOrDefault(or.getId(), List.of()));
	}

	@Transactional
	public ReviewSubmitRes submit(Integer userId, Integer orderId, OrderReviewReq req) {
		Order o = ownOrder(userId, orderId);
		if (o.getStatus() != OrderStatus.DELIVERED) {
			throw new BusinessException("Chỉ đánh giá được đơn đã giao");
		}
		if (orderIssueService.forOrder(orderId) != null) {
			throw new BusinessException("Đơn hàng đã được giải quyết khiếu nại nên không đánh giá món");
		}
		if (!inWindow(o)) {
			throw new BusinessException("Đơn #" + orderId + " đã quá hạn đánh giá (" + REVIEW_DAYS + " ngày sau khi nhận)");
		}

		Map<Integer, OrderDetail> details = o.getDetails().stream()
				.collect(Collectors.toMap(OrderDetail::getId, Function.identity()));
		Set<Integer> reviewed = reviewedIds(new ArrayList<>(details.keySet()));

		List<ReviewItemReq> toSave = new ArrayList<>();
		Map<Integer, List<MultipartFile>> files = new HashMap<>();
		for (ReviewItemReq it : req.items() == null ? List.<ReviewItemReq>of() : req.items()) {
			if (it == null || it.rating() == null) continue;
			OrderDetail d = details.get(it.orderDetailId());
			if (d == null) throw new ResourceNotFoundException("Ly #" + it.orderDetailId() + " không thuộc đơn #" + orderId);
			if (reviewed.contains(d.getId())) {
				throw new BusinessException("Bạn đã đánh giá " + d.getProduct().getName() + " (" + info(d) + ") rồi");
			}
			if (it.rating() < 1 || it.rating() > 5) throw new BusinessException("Số sao phải từ 1 đến 5");
			String text = clean(it.content());
			if (text != null && text.length() > MAX_CONTENT) {
				throw new BusinessException("Bình luận tối đa " + MAX_CONTENT + " ký tự");
			}
			files.put(d.getId(), validFiles(it.media()));
			toSave.add(it);
		}

		Integer store = req.storeRating();
		boolean service = store != null && !orderReviewRepository.existsByOrderId(orderId);
		if (store != null && (store < 1 || store > 5)) throw new BusinessException("Đánh giá quán phải từ 1 đến 5 sao");
		String storeComment = clean(req.storeComment());
		if (storeComment != null && storeComment.length() > 1000) throw new BusinessException("Góp ý cho quán tối đa 1000 ký tự");
		List<MultipartFile> storeFiles = service ? validFiles(req.storeMedia()) : List.of();

		if (toSave.isEmpty() && !service) {
			throw new BusinessException("Vui lòng chấm sao ít nhất 1 món hoặc đánh giá quán");
		}

		List<String> uploaded = new ArrayList<>();
		try {
			for (ReviewItemReq it : toSave) {
				OrderDetail d = details.get(it.orderDetailId());
				List<MultipartFile> fs = files.get(d.getId());
				String text = clean(it.content());

				Review r = new Review();
				r.setUser(o.getUser());
				r.setProduct(d.getProduct());
				r.setOrderDetail(d);
				r.setRating(it.rating());
				r.setContent(text);
				r.setTags(joinTags(it.tags()));
				r.setCreatedAt(LocalDateTime.now());
				r = reviewRepository.save(r);

				for (MultipartFile f : fs) {
					String url = cloudinaryService.uploadImage(f, "reviews");
					if (url == null) continue;
					uploaded.add(url);
					ReviewMedia m = new ReviewMedia();
					m.setReview(r);
					m.setCloudinaryId(url);
					m.setMediaType(mediaType(f));
					reviewMediaRepository.save(m);
				}
			}

			if (service) {
				OrderReview or = new OrderReview();
				or.setOrder(o);
				or.setUser(o.getUser());
				or.setStoreRating(store);
				or.setComment(storeComment);
				or.setTags(joinTags(req.storeTags()));
				or = orderReviewRepository.save(or);
				for (MultipartFile f : storeFiles) {
					String url = cloudinaryService.uploadImage(f, "store-reviews");
					if (url == null) continue;
					uploaded.add(url);
					OrderReviewMedia m = new OrderReviewMedia();
					m.setOrderReviewId(or.getId());
					m.setCloudinaryId(url);
					m.setMediaType(mediaType(f));
					orderReviewMediaRepository.save(m);
				}
			}
		} catch (RuntimeException e) {
			uploaded.forEach(cloudinaryService::deleteImage);
			if (e instanceof BusinessException be) throw be;
			throw new BusinessException("Không tải được ảnh/video lên, vui lòng thử lại");
		}
		reviewRepository.flush();
		notifyManager(o, toSave, service ? store : null);
		return new ReviewSubmitRes(toSave.size(), service);
	}

	@Transactional
	public ReviewSubmitRes create(Integer userId, Integer orderDetailId, Integer rating, String content,
			List<MultipartFile> files) {
		OrderDetail d = orderDetailId == null ? null : orderDetailRepository.findById(orderDetailId).orElse(null);
		if (d == null || !d.getOrder().getUser().getId().equals(userId)) {
			throw new ResourceNotFoundException("Không tìm thấy món bạn đã mua");
		}
		if (rating == null) throw new BusinessException("Vui lòng chọn số sao từ 1 đến 5");
		return submit(userId, d.getOrder().getId(),
				new OrderReviewReq(List.of(new ReviewItemReq(orderDetailId, rating, List.of(), content, files)), null,
						List.of(), null));
	}

	static final int LOW_RATING = 2;

	private void notifyManager(Order o, List<ReviewItemReq> items, Integer store) {
		User manager = o.getBranch() == null ? null : o.getBranch().getOwner();
		if (manager == null || (items.isEmpty() && store == null)) {
			return;
		}
		List<String> parts = new ArrayList<>();
		if (store != null) parts.add("quán " + store + "★");
		if (!items.isEmpty()) parts.add(items.size() + " món");

		int min = items.stream().mapToInt(ReviewItemReq::rating).min().orElse(5);
		if (store != null) min = Math.min(min, store);
		boolean low = min <= LOW_RATING;

		User u = o.getUser();
		String customer = u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
		String title = low ? "⚠ Đánh giá thấp " + min + "★ – đơn #" + o.getId()
				: "Đơn #" + o.getId() + " có đánh giá mới";
		String content = customer + " đã đánh giá " + String.join(", ", parts)
				+ (low ? ". Bạn nên phản hồi sớm cho khách." : ".");
		notificationService.create(manager, o, "REVIEW", title, content, "/manager/reviews");
	}

	private Order ownOrder(Integer userId, Integer orderId) {
		return orderRepository.findById(orderId).filter(o -> o.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
	}

	static boolean inWindow(Order o) {
		return o.getDeliveredAt() != null && !o.getDeliveredAt().plusDays(REVIEW_DAYS).isBefore(LocalDateTime.now());
	}

	private static boolean isPickup(Order o) {
		return o.getDeliveryType() == DeliveryType.PICKUP;
	}

	private Set<Integer> reviewedIds(List<Integer> detailIds) {
		if (detailIds.isEmpty()) return Set.of();
		return reviewRepository.findByOrderDetailIdIn(detailIds).stream().map(r -> r.getOrderDetail().getId())
				.collect(Collectors.toSet());
	}

	public Map<Integer, List<ReviewRes.Media>> storeMediaOf(List<Integer> orderReviewIds) {
		if (orderReviewIds.isEmpty()) return Map.of();
		return orderReviewMediaRepository.findByOrderReviewIdInOrderByIdAsc(orderReviewIds).stream()
				.collect(Collectors.groupingBy(OrderReviewMedia::getOrderReviewId,
						Collectors.mapping(m -> new ReviewRes.Media(m.getCloudinaryId(), m.getMediaType()),
								Collectors.toList())));
	}

	public Map<Integer, List<ReviewRes.Media>> productMediaOf(List<Integer> reviewIds) {
		return mediaOf(reviewIds);
	}

	private Map<Integer, List<ReviewRes.Media>> mediaOf(List<Integer> reviewIds) {
		if (reviewIds.isEmpty()) return Map.of();
		return reviewMediaRepository.findByReviewIdInOrderByIdAsc(reviewIds).stream()
				.collect(Collectors.groupingBy(m -> m.getReview().getId(),
						Collectors.mapping(m -> new ReviewRes.Media(m.getCloudinaryId(), m.getMediaType()),
								Collectors.toList())));
	}

	static String joinTags(List<String> tags) {
		if (tags == null) return null;
		List<String> list = tags.stream().filter(t -> t != null && !t.isBlank())
				.map(t -> t.trim().replace("|", " ")).map(t -> t.length() > 50 ? t.substring(0, 50) : t).distinct()
				.limit(6).toList();
		if (list.isEmpty()) return null;
		String s = String.join("|", list);
		return s.length() > 255 ? s.substring(0, 255) : s;
	}

	static List<String> splitTags(String s) {
		return s == null || s.isBlank() ? List.of() : List.of(s.split("\\|"));
	}

	private static String clean(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private List<MultipartFile> validFiles(List<MultipartFile> files) {
		List<MultipartFile> list = files == null ? List.of()
				: files.stream().filter(f -> f != null && !f.isEmpty()).toList();
		if (list.size() > MAX_FILES) {
			throw new BusinessException("Mỗi món chỉ đính kèm tối đa " + MAX_FILES + " ảnh/video");
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
				time(r.getCreatedAt()), r.getOrderDetail() == null ? "" : info(r.getOrderDetail()), splitTags(r.getTags()),
				media);
	}

	static String info(OrderDetail d) {
		if (d.getProduct() != null && d.getProduct().isToppingItem()) return "Topping mua riêng";
		return "Size " + d.getSize() + " · " + d.getSugarLevel() + "% đường · " + d.getIceLevel() + "% đá";
	}

	private static String time(LocalDateTime t) {
		return t == null ? "" : t.format(TIME);
	}
}