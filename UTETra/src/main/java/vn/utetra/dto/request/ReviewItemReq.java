package vn.utetra.dto.request;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/** Đánh giá 1 ly: số sao bắt buộc; gợi ý chọn nhanh, bình luận, ảnh/video không bắt buộc */
public record ReviewItemReq(Integer orderDetailId, Integer rating, List<String> tags, String content,
		List<MultipartFile> media) {
}
