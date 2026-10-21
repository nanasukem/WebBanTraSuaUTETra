package vn.utetra.dto.response;

import java.util.List;

/**
 * 1 trang của khối "20 món nhất" trên trang chủ User (mới nhất, bán chạy, đánh giá cao, yêu thích nhất).
 * type: newest | sold | rating | favorite. page bắt đầu từ 0; offset = vị trí món đầu trang (để đánh số hạng #1, #2...).
 */
public record HighlightPageRes(String type, List<ProductCardRes> items, int page, int totalPages, int total,
		int offset) {
}
