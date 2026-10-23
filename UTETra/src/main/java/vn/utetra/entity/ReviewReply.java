package vn.utetra.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ReviewReplies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewReply {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "order_review_id")
	private Integer orderReviewId;

	@Column(name = "review_id")
	private Integer reviewId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "replied_by_user_id", nullable = false)
	private User repliedBy;

	@Column(name = "reply_content", length = 2000, nullable = false)
	private String replyContent;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void prePersist() {
		if (this.createdAt == null) {
			this.createdAt = LocalDateTime.now();
		}
	}
}