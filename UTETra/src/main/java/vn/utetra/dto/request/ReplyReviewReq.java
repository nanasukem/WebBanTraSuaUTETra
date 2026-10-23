package vn.utetra.dto.request;

import lombok.Data;

@Data
public class ReplyReviewReq {
	private Integer orderReviewId;
	private Integer reviewId;
	private String content;
}