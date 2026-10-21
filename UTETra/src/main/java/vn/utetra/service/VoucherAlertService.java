package vn.utetra.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Voucher;
import vn.utetra.repository.VoucherRepository;

/**
 * Báo Admin (chuông) khi voucher sắp hết lượt / đã hết lượt / sắp hết hạn.
 * - Gọi ngay sau mỗi lần có người dùng mã (VoucherServiceImpl.markUsed)
 * - Quét định kỳ mỗi giờ (mã sắp hết hạn trong 24 giờ)
 * Mỗi việc chỉ báo 1 lần nhờ ref_key (VOUCHER_LOW:id, VOUCHER_OUT:id, VOUCHER_EXP:id:ngày hết hạn).
 */
@Service
public class VoucherAlertService {

	/** Còn <= 10% lượt thì báo sắp hết */
	public static final int LOW_PERCENT = 10;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

	private final VoucherRepository voucherRepository;
	private final NotificationService notificationService;

	public VoucherAlertService(VoucherRepository voucherRepository, NotificationService notificationService) {
		this.voucherRepository = voucherRepository;
		this.notificationService = notificationService;
	}

	/** Kiểm tra lượt còn lại của 1 voucher (gọi sau khi có người dùng mã) */
	@Transactional
	public void checkRemaining(Integer voucherId) {
		Voucher v = voucherRepository.findById(voucherId).orElse(null);
		if (v == null || v.getQuantity() == null || v.getQuantity() <= 0) return;
		int used = v.getUsedCount() == null ? 0 : v.getUsedCount();
		int left = Math.max(0, v.getQuantity() - used);
		String where = v.getBranch() == null ? "toàn chuỗi" : v.getBranch().getName();
		if (left == 0) {
			notificationService.notifyAdmins("VOUCHER", "Voucher " + v.getCode() + " đã hết lượt",
					v.getName() + " · " + where + " · đã dùng " + used + "/" + v.getQuantity(), "/admin/vouchers",
					"VOUCHER_OUT:" + v.getId());
		} else if (left * 100 <= v.getQuantity() * LOW_PERCENT) {
			notificationService.notifyAdmins("VOUCHER", "Voucher " + v.getCode() + " sắp hết lượt",
					"Còn " + left + "/" + v.getQuantity() + " lượt · " + where, "/admin/vouchers",
					"VOUCHER_LOW:" + v.getId());
		}
	}

	/** Mỗi giờ: báo các mã đang bật sắp hết hạn trong 24 giờ tới (chạy lần đầu sau 1 phút) */
	@Scheduled(initialDelay = 60_000, fixedDelay = 3_600_000)
	@Transactional
	public int scanExpiring() {
		LocalDateTime now = LocalDateTime.now();
		int sent = 0;
		for (Voucher v : voucherRepository.findAll()) {
			if (!Boolean.TRUE.equals(v.getIsActive()) || v.getEndDate() == null) continue;
			if (v.getEndDate().isAfter(now) && !v.getEndDate().isAfter(now.plusHours(24))) {
				sent += notificationService.notifyAdmins("VOUCHER", "Voucher " + v.getCode() + " sắp hết hạn",
						"Hết hạn lúc " + v.getEndDate().format(DAY) + " · còn "
								+ Math.max(0, v.getQuantity() - (v.getUsedCount() == null ? 0 : v.getUsedCount())) + " lượt",
						"/admin/vouchers", "VOUCHER_EXP:" + v.getId() + ":" + v.getEndDate().toLocalDate());
			}
		}
		return sent;
	}
}
