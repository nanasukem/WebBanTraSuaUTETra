package vn.utetra.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.ReplyDto;
import vn.utetra.dto.request.ReplyReviewReq;
import vn.utetra.dto.response.BranchReviewRes;
import vn.utetra.entity.*;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BranchReviewManagementService {

	@Autowired
	private OrderReviewRepository orderReviewRepo;

	@Autowired
	private ReviewRepository reviewRepo;

	@Autowired
	private ReviewReplyRepository replyRepo;

	@Autowired
	private ReviewMediaRepository mediaRepo;

	@Autowired
	private BranchRepository branchRepo;

	@Autowired
	private CloudinaryService cloudinaryService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private OrderReviewMediaRepository orderReviewMediaRepository;

	public Branch getManagedBranch(User manager) {
		if (manager == null)
			return null;
		return branchRepo.findAll().stream()
				.filter(b -> b.getOwner() != null && b.getOwner().getId().equals(manager.getId())).findFirst()
				.orElse(null);
	}

	@Transactional(readOnly = true)
	public List<BranchReviewRes> getPublicReviewsByBranch(Integer branchId) {
		if (branchId == null) {
			return Collections.emptyList();
		}
		List<BranchReviewRes> result = new ArrayList<>();
		for (OrderReview or : orderReviewRepo.findAll()) {
			Order o = or.getOrder();
			if (o != null && o.getBranch() != null && o.getBranch().getId().equals(branchId)) {
				result.add(convertOrderReviewToRes(or, o.getBranch()));
			}
		}
		result.sort(
				Comparator.comparing(BranchReviewRes::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
		return result;
	}

	@Transactional(readOnly = true)
	public List<BranchReviewRes> getAllReviewsForAdmin(Integer branchId, String type, Integer rating) {
		List<BranchReviewRes> result = new ArrayList<>();

		if (type == null || type.isBlank() || "STORE".equalsIgnoreCase(type)) {
			for (OrderReview or : orderReviewRepo.findAll()) {
				Order o = or.getOrder();
				Branch b = (o != null) ? o.getBranch() : null;
				boolean matchBranch = (branchId == null || (b != null && b.getId().equals(branchId)));
				boolean matchRating = (rating == null || or.getStoreRating() == rating);
				if (matchBranch && matchRating) {
					result.add(convertOrderReviewToRes(or, b));
				}
			}
		}

		if (type == null || type.isBlank() || "PRODUCT".equalsIgnoreCase(type)) {
			for (Review r : reviewRepo.findAll()) {
				Order o = (r.getOrderDetail() != null) ? r.getOrderDetail().getOrder() : null;
				Branch b = (o != null) ? o.getBranch() : null;
				boolean matchBranch = (branchId == null || (b != null && b.getId().equals(branchId)));
				boolean matchRating = (rating == null || r.getRating() == rating);
				if (matchBranch && matchRating) {
					result.add(convertProductReviewToRes(r, o, b));
				}
			}
		}

		result.sort(
				Comparator.comparing(BranchReviewRes::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
		return result;
	}

	@Transactional(readOnly = true)
	public List<BranchReviewRes> getReviewsForManager(User manager, String type, Integer rating) {
		Branch myBranch = getManagedBranch(manager);
		if (myBranch == null) {
			return Collections.emptyList();
		}
		return getAllReviewsForAdmin(myBranch.getId(), type, rating);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> getBranchReviewSummary(Integer branchId) {
		List<BranchReviewRes> storeReviews = getPublicReviewsByBranch(branchId);
		double avgRating = storeReviews.stream().mapToInt(BranchReviewRes::getRating).average().orElse(0.0);

		Map<String, Object> summary = new HashMap<>();
		summary.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
		summary.put("totalReviews", storeReviews.size());
		return summary;
	}

	@Transactional
	public void replyToReview(ReplyReviewReq req, User currentUser) {
		if (req.getContent() == null || req.getContent().trim().isEmpty()) {
			throw new BusinessException("Nội dung phản hồi không được để trống!");
		}

		String roleName = currentUser.getRole().getName();
		boolean isAdmin = "ADMIN".equalsIgnoreCase(roleName) || "ROLE_ADMIN".equalsIgnoreCase(roleName);
		if (isAdmin) {
			throw new BusinessException("Admin chỉ xem và xoá đánh giá vi phạm, việc phản hồi do quản lý chi nhánh đảm nhận!");
		}

		if (req.getOrderReviewId() != null) {
			OrderReview or = orderReviewRepo.findById(req.getOrderReviewId())
					.orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá quán!"));

			Branch reviewBranch = (or.getOrder() != null) ? or.getOrder().getBranch() : null;
			validateManagerPermission(currentUser, reviewBranch);

			ReviewReply reply = ReviewReply.builder().orderReviewId(or.getId()).repliedBy(currentUser)
					.replyContent(req.getContent().trim()).build();
			replyRepo.save(reply);

			notifyCustomer(or.getUser(), or.getOrder(), reviewBranch, "đánh giá quán", reply.getReplyContent(),
					reviewBranch != null ? "/branches/" + reviewBranch.getId() : "/branches");

		} else if (req.getReviewId() != null) {
			Review r = reviewRepo.findById(req.getReviewId())
					.orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá món!"));

			Order o = (r.getOrderDetail() != null) ? r.getOrderDetail().getOrder() : null;
			Branch reviewBranch = (o != null) ? o.getBranch() : null;
			validateManagerPermission(currentUser, reviewBranch);

			ReviewReply reply = ReviewReply.builder().reviewId(r.getId()).repliedBy(currentUser)
					.replyContent(req.getContent().trim()).build();
			replyRepo.save(reply);

			String what = "đánh giá món " + (r.getProduct() != null ? r.getProduct().getName() : "");
			notifyCustomer(r.getUser(), o, reviewBranch, what.trim(), reply.getReplyContent(),
					o != null ? "/orders/" + o.getId() + "/review" : "/orders");
		} else {
			throw new BusinessException("Thiếu thông tin đánh giá cần phản hồi!");
		}
	}

	@Transactional
	public void deleteReview(String type, Integer id) {
		if ("STORE".equalsIgnoreCase(type)) {
			OrderReview or = orderReviewRepo.findById(id)
					.orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá quán #" + id));
			replyRepo.deleteAll(replyRepo.findByOrderReviewIdOrderByCreatedAtAsc(id));
			List<OrderReviewMedia> storeMedia = orderReviewMediaRepository.findByOrderReviewIdOrderByIdAsc(id);
			storeMedia.forEach(m -> {
				try {
					cloudinaryService.deleteImage(m.getCloudinaryId());
				} catch (RuntimeException ignored) {
				}
			});
			orderReviewMediaRepository.deleteAll(storeMedia);
			orderReviewRepo.delete(or);
		} else if ("PRODUCT".equalsIgnoreCase(type)) {
			Review r = reviewRepo.findById(id)
					.orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá món #" + id));
			replyRepo.deleteAll(replyRepo.findByReviewIdOrderByCreatedAtAsc(id));
			List<ReviewMedia> media = mediaRepo.findByReviewIdInOrderByIdAsc(List.of(id));
			media.forEach(m -> {
				try {
					cloudinaryService.deleteImage(m.getCloudinaryId());
				} catch (RuntimeException ignored) {
				}
			});
			mediaRepo.deleteAll(media);
			reviewRepo.delete(r);
		} else {
			throw new BusinessException("Loại đánh giá không hợp lệ");
		}
	}

	private void notifyCustomer(User customer, Order order, Branch branch, String what, String replyContent,
			String link) {
		if (customer == null) {
			return;
		}
		String shop = branch != null ? branch.getName() : "UTETra";
		String quote = replyContent.length() > 150 ? replyContent.substring(0, 150) + "..." : replyContent;
		notificationService.create(customer, order, "REVIEW", shop + " đã phản hồi " + what + " của bạn",
				"\u201C" + quote + "\u201D", link);
	}

	private void validateManagerPermission(User currentUser, Branch reviewBranch) {
		Branch managerBranch = getManagedBranch(currentUser);
		if (managerBranch == null || reviewBranch == null || !managerBranch.getId().equals(reviewBranch.getId())) {
			throw new BusinessException("Bạn chỉ được phép phản hồi đánh giá thuộc chi nhánh do mình quản lý!");
		}
	}

	private BranchReviewRes convertOrderReviewToRes(OrderReview or, Branch b) {
		List<ReviewReply> replies = replyRepo.findByOrderReviewIdOrderByCreatedAtAsc(or.getId());
		return BranchReviewRes.builder().id(or.getId()).orderId(or.getOrder() != null ? or.getOrder().getId() : null)
				.branchId(b != null ? b.getId() : null).branchName(b != null ? b.getName() : "UTETra")
				.customerName(or.getUser() != null && or.getUser().getFullName() != null ? or.getUser().getFullName()
						: "Khách hàng")
				.rating((int) or.getStoreRating()).comment(or.getComment()).tags(or.getTags()).type("STORE")
				.productName(null).createdAt(or.getCreatedAt()).replies(mapReplies(replies))
				.media(reviewService.storeMediaOf(List.of(or.getId())).getOrDefault(or.getId(), List.of())).build();
	}

	private BranchReviewRes convertProductReviewToRes(Review r, Order o, Branch b) {
		List<ReviewReply> replies = replyRepo.findByReviewIdOrderByCreatedAtAsc(r.getId());
		return BranchReviewRes.builder().id(r.getId()).orderId(o != null ? o.getId() : null)
				.branchId(b != null ? b.getId() : null).branchName(b != null ? b.getName() : "UTETra")
				.customerName(r.getUser() != null && r.getUser().getFullName() != null ? r.getUser().getFullName()
						: "Khách hàng")
				.rating((int) r.getRating()).comment(r.getContent()).tags(r.getTags()).type("PRODUCT")
				.productName(r.getProduct() != null ? r.getProduct().getName() : null).createdAt(r.getCreatedAt())
				.replies(mapReplies(replies))
				.media(reviewService.productMediaOf(List.of(r.getId())).getOrDefault(r.getId(), List.of())).build();
	}

	private List<ReplyDto> mapReplies(List<ReviewReply> replies) {
		return replies.stream()
				.map(rep -> ReplyDto.builder().responderName(rep.getRepliedBy().getFullName())
						.responderRole(rep.getRepliedBy().getRole().getName()).content(rep.getReplyContent())
						.createdAt(rep.getCreatedAt()).build())
				.collect(Collectors.toList());
	}
}