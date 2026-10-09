package vn.utetra.exception;

/** Không tìm thấy dữ liệu (id sai, đã bị xoá...) -> trang 404 */
public class ResourceNotFoundException extends RuntimeException {
	public ResourceNotFoundException(String message) {
		super(message);
	}
}