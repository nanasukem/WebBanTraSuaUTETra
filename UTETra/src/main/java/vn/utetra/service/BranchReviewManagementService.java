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
	private BranchRepository branchRepo;

	public Branch getManagedBranch(User manager) {
		if (manager == null)
			return null;
		return branchRepo.findAll().stream()
				.filter(b -> b.getOwner() != null && b.getOwner().getId().equals(manager.getId())).findFirst()
				.orElse(null);
	}

	// 1. User & Guest: Xem đánh giá QUÁN công khai của 1 chi nhánh
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

	// 2. Admin: Xem tất cả đánh giá của tất cả chi nhánh (hỗ trợ lọc theo branchId,
	// type, rating)
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

	// 3. Manager: Xem đánh giá chi nhánh do mình phụ trách (hỗ trợ lọc theo type,
	// rating)
	@Transactional(readOnly = true)
	public List<BranchReviewRes> getReviewsForManager(User manager, String type, Integer rating) {
		Branch myBranch = getManagedBranch(manager);
		if (myBranch == null) {
			return Collections.emptyList();
		}
		return getAllReviewsForAdmin(myBranch.getId(), type, rating);
	}

	// 4. Tính điểm sao trung bình và tổng số lượng đánh giá QUÁN của 1 chi nhánh
	@Transactional(readOnly = true)
	public Map<String, Object> getBranchReviewSummary(Integer branchId) {
		// Chỉ lấy đánh giá quán (OrderReview) để tính điểm trung bình cho chi nhánh
		List<BranchReviewRes> storeReviews = getPublicReviewsByBranch(branchId);
		double avgRating = storeReviews.stream().mapToInt(BranchReviewRes::getRating).average().orElse(0.0);

		Map<String, Object> summary = new HashMap<>();
		summary.put("avgRating", Math.round(avgRating * 10.0) / 10.0); // Đã fix cú pháp * 10.0
		summary.put("totalReviews", storeReviews.size());
		return summary;
	}

	// 5. Phản hồi đánh giá
	@Transactional
	public void replyToReview(ReplyReviewReq req, User currentUser) {
		if (req.getContent() == null || req.getContent().trim().isEmpty()) {
			throw new BusinessException("Nội dung phản hồi không được để trống!");
		}

		String roleName = currentUser.getRole().getName();
		boolean isAdmin = "ADMIN".equalsIgnoreCase(roleName) || "ROLE_ADMIN".equalsIgnoreCase(roleName);

		if (req.getOrderReviewId() != null) {
			OrderReview or = orderReviewRepo.findById(req.getOrderReviewId())
					.orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá quán!"));

			Branch reviewBranch = (or.getOrder() != null) ? or.getOrder().getBranch() : null;
			validateManagerPermission(isAdmin, currentUser, reviewBranch);

			ReviewReply reply = ReviewReply.builder().orderReviewId(or.getId()).repliedBy(currentUser)
					.replyContent(req.getContent().trim()).build();
			replyRepo.save(reply);

		} else if (req.getReviewId() != null) {
			Review r = reviewRepo.findById(req.getReviewId())
					.orElseThrow(() -> new BusinessException("Không tìm thấy đánh giá món!"));

			Order o = (r.getOrderDetail() != null) ? r.getOrderDetail().getOrder() : null;
			Branch reviewBranch = (o != null) ? o.getBranch() : null;
			validateManagerPermission(isAdmin, currentUser, reviewBranch);

			ReviewReply reply = ReviewReply.builder().reviewId(r.getId()).repliedBy(currentUser)
					.replyContent(req.getContent().trim()).build();
			replyRepo.save(reply);
		} else {
			throw new BusinessException("Thiếu thông tin đánh giá cần phản hồi!");
		}
	}

	private void validateManagerPermission(boolean isAdmin, User currentUser, Branch reviewBranch) {
		if (!isAdmin) {
			Branch managerBranch = getManagedBranch(currentUser);
			if (managerBranch == null || reviewBranch == null || !managerBranch.getId().equals(reviewBranch.getId())) {
				throw new BusinessException("Bạn chỉ được phép phản hồi đánh giá thuộc chi nhánh do mình quản lý!");
			}
		}
	}

	private BranchReviewRes convertOrderReviewToRes(OrderReview or, Branch b) {
		List<ReviewReply> replies = replyRepo.findByOrderReviewIdOrderByCreatedAtAsc(or.getId());
		return BranchReviewRes.builder().id(or.getId()).orderId(or.getOrder() != null ? or.getOrder().getId() : null)
				.branchId(b != null ? b.getId() : null).branchName(b != null ? b.getName() : "UTETra")
				.customerName(or.getUser() != null && or.getUser().getFullName() != null ? or.getUser().getFullName()
						: "Khách hàng")
				.rating((int) or.getStoreRating()).comment(or.getComment()).tags(or.getTags()).type("STORE")
				.productName(null).createdAt(or.getCreatedAt()).replies(mapReplies(replies)).build();
	}

	private BranchReviewRes convertProductReviewToRes(Review r, Order o, Branch b) {
		List<ReviewReply> replies = replyRepo.findByReviewIdOrderByCreatedAtAsc(r.getId());
		return BranchReviewRes.builder().id(r.getId()).orderId(o != null ? o.getId() : null)
				.branchId(b != null ? b.getId() : null).branchName(b != null ? b.getName() : "UTETra")
				.customerName(r.getUser() != null && r.getUser().getFullName() != null ? r.getUser().getFullName()
						: "Khách hàng")
				.rating((int) r.getRating()).comment(r.getContent()).tags(r.getTags()).type("PRODUCT")
				.productName(r.getProduct() != null ? r.getProduct().getName() : null).createdAt(r.getCreatedAt())
				.replies(mapReplies(replies)).build();
	}

	private List<ReplyDto> mapReplies(List<ReviewReply> replies) {
		return replies.stream()
				.map(rep -> ReplyDto.builder().responderName(rep.getRepliedBy().getFullName())
						.responderRole(rep.getRepliedBy().getRole().getName()).content(rep.getReplyContent())
						.createdAt(rep.getCreatedAt()).build())
				.collect(Collectors.toList());
	}
}