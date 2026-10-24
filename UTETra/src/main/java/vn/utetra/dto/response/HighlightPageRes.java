package vn.utetra.dto.response;

import java.util.List;

public record HighlightPageRes(String type, List<ProductCardRes> items, int page, int totalPages, int total,
		int offset) {
}
