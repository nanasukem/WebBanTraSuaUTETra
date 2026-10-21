package vn.utetra.dto.response;

/** 1 ô danh mục trên trang chủ User / trang danh mục: tên + số món đang bán */
public record CategoryCardRes(Integer id, String name, String imageCloudinaryId, long productCount) {
}
