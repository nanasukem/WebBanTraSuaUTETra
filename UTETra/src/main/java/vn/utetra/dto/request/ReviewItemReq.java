package vn.utetra.dto.request;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public record ReviewItemReq(Integer orderDetailId, Integer rating, List<String> tags, String content,
		List<MultipartFile> media) {
}
