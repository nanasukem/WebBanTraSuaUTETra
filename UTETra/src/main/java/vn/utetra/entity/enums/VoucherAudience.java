package vn.utetra.entity.enums;

/** Đối tượng được dùng voucher */
public enum VoucherAudience {
    ALL,      // mọi tài khoản
    NEW_USER, // chỉ tài khoản mới: chưa có đơn nào (không tính đơn đã huỷ)
    MIN_TIER  // từ hạng thành viên min_tier trở lên
}