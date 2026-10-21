package vn.utetra.dto.response;

import java.util.List;

/** 1 nhóm trên trang Thực đơn: 1 danh mục + các món của danh mục đó */
public record MenuSectionRes(Integer categoryId, String categoryName, List<ProductCardRes> items) {
}
