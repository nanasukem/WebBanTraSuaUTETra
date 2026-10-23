package vn.utetra.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.utetra.dto.ReplyDto;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchReviewRes {
	private Integer id;
	private Integer orderId;
	private Integer branchId;
	private String branchName;
	private String customerName;
	private Integer rating;
	private String comment;
	private String tags;
	private String type; // "STORE" (Đánh giá quán) hoặc "PRODUCT" (Đánh giá món)
	private String productName;
	private LocalDateTime createdAt;
	private List<ReplyDto> replies;
}