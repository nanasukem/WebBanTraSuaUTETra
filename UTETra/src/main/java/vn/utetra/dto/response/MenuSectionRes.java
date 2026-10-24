package vn.utetra.dto.response;

import java.util.List;

public record MenuSectionRes(Integer categoryId, String categoryName, List<ProductCardRes> items) {
}
