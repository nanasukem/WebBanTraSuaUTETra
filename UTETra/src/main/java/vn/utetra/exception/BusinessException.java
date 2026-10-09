package vn.utetra.exception;

/** Vi phạm quy tắc nghiệp vụ (trùng tên, voucher hết hạn...) -> hiện thông báo cho người dùng */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}