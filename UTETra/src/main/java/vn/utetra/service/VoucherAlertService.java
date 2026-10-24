package vn.utetra.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.Voucher;
import vn.utetra.repository.VoucherRepository;

@Service
public class VoucherAlertService {

	public static final int LOW_PERCENT = 10;
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

	private final VoucherRepository voucherRepository;
	private final NotificationService notificationService;

	public VoucherAlertService(VoucherRepository voucherRepository, NotificationService notificationService) {
		this.voucherRepository = voucherRepository;
		this.notificationService = notificationService;
	}

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
