package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.utetra.entity.ReviewReply;
import java.util.List;

@Repository
public interface ReviewReplyRepository extends JpaRepository<ReviewReply, Integer> {
	List<ReviewReply> findByOrderReviewIdOrderByCreatedAtAsc(Integer orderReviewId);

	List<ReviewReply> findByReviewIdOrderByCreatedAtAsc(Integer reviewId);
}