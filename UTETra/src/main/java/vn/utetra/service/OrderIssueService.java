package vn.utetra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.utetra.dto.response.OrderIssueRes;
import vn.utetra.entity.Order;
import vn.utetra.entity.OrderIssue;
import vn.utetra.entity.enums.IssueReason;
import vn.utetra.entity.enums.IssueStatus;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.PaymentStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.OrderIssueRepository;
import vn.utetra.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderIssueService {

	public static final int WINDOW_HOURS = 2;
	public static final int MAX_FILES = ReviewService.MAX_FILES;
	public static final String ADMIN_HOTLINE = "1900 1234";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

	private final OrderIssueRepository issueRepository;
	private final OrderRepository orderRepository;
	private final CloudinaryService cloudinaryService;
	private final NotificationService notificationService;
	private final RefundService refundService;

	public OrderIssueService(OrderIssueRepository issueRepository, OrderRepository orderRepository,
			CloudinaryService cloudinaryService, NotificationService notificationService, RefundService refundService) {
		this.issueRepository = issueRepository;
		this.orderRepository = orderRepository;
		this.cloudinaryService = cloudinaryService;
		this.notificationService = notificationService;
		this.refundService = refundService;
	}

	public static LocalDateTime deadline(Order o) {
		return o.getDeliveredAt() == null ? null : o.getDeliveredAt().plusHours(WINDOW_HOURS);
	}

	public boolean canReport(Order o) {
		LocalDateTime dl = deadline(o);
		return o.getStatus() == OrderStatus.DELIVERED && dl != null && LocalDateTime.now().isBefore(dl)
				&& !issueRepository.existsByOrderId(o.getId());
	}

	@Transactional
	public OrderIssue submit(Integer userId, Integer orderId, IssueReason reason, String description,
			List<MultipartFile> files) {
		Order o = orderRepository.findById(orderId).filter(x -> x.getUser().getId().equals(userId))
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng #" + orderId));
		if (o.getStatus() != OrderStatus.DELIVERED) {
			throw new BusinessException("Chỉ báo vấn đề được với đơn đã nhận món");
		}
		if (issueRepository.existsByOrderId(orderId)) {
			throw new BusinessException("Bạn đã báo vấn đề cho đơn #" + orderId + " rồi");
		}
		if (!LocalDateTime.now().isBefore(deadline(o))) {
			throw new BusinessException("Đã quá " + WINDOW_HOURS + " giờ kể từ lúc nhận món, không báo vấn đề được nữa");
		}
		if (reason == null) throw new BusinessException("Vui lòng chọn vấn đề bạn gặp");
		String desc = description == null || description.isBlank() ? null : description.trim();
		if (desc != null && desc.length() > 500) throw new BusinessException("Mô tả tối đa 500 ký tự");
		if (reason == IssueReason.OTHER && desc == null) throw new BusinessException("Vui lòng mô tả vấn đề bạn gặp");

		List<MultipartFile> media = files == null ? List.of()
				: files.stream().filter(f -> f != null && !f.isEmpty()).toList();
		if (media.isEmpty()) throw new BusinessException("Vui lòng gửi ít nhất 1 ảnh hoặc video món nhận được để quán kiểm tra");
		if (media.size() > MAX_FILES) throw new BusinessException("Tối đa " + MAX_FILES + " ảnh / video");
		for (MultipartFile f : media) {
			String type = ReviewService.mediaType(f);
			if (type == null) {
				throw new BusinessException("File \"" + f.getOriginalFilename() + "\" không phải ảnh hoặc video");
			}
			long max = "VIDEO".equals(type) ? ReviewService.MAX_VIDEO_BYTES : ReviewService.MAX_IMAGE_BYTES;
			if (f.getSize() > max) {
				throw new BusinessException("\"" + f.getOriginalFilename() + "\" quá " + max / 1024 / 1024 + "MB");
			}
		}
		List<String> urls = new ArrayList<>();
		try {
			for (MultipartFile f : media) urls.add(cloudinaryService.uploadImage(f, "order-issues"));
		} catch (RuntimeException e) {
			urls.forEach(cloudinaryService::deleteImage);
			throw new BusinessException("Không tải được ảnh/video lên, vui lòng thử lại");
		}

		OrderIssue i = new OrderIssue();
		i.setOrder(o);
		i.setUser(o.getUser());
		i.setReason(reason);
		i.setDescription(desc);
		i.setImages(String.join("|", urls));
		issueRepository.save(i);

		notificationService.create(o.getBranch().getOwner(), o, "ORDER", "Khách báo vấn đề với đơn #" + orderId,
				reason.getLabel() + (desc == null ? "" : ": " + desc), "/manager/orders/" + orderId);
		return i;
	}

	@Transactional
	public void resolve(Integer orderId, String action, BigDecimal amount, String note) {
		OrderIssue i = issueRepository.findByOrderId(orderId)
				.orElseThrow(() -> new ResourceNotFoundException("Đơn #" + orderId + " chưa có báo cáo vấn đề"));
		if (i.getStatus() != IssueStatus.PENDING) throw new BusinessException("Báo cáo này đã được xử lý");
		Order o = i.getOrder();
		String cleanNote = note == null || note.isBlank() ? null : note.trim();
		if (cleanNote != null && cleanNote.length() > 255) throw new BusinessException("Ghi chú tối đa 255 ký tự");
		String title;
		String message;

		switch (action == null ? "" : action.trim().toUpperCase()) {
			case "REFUND" -> {
				BigDecimal max = o.getTotalAmount();
				if (amount == null || amount.signum() <= 0) throw new BusinessException("Nhập số tiền hoàn");
				if (amount.compareTo(max) > 0) {
					throw new BusinessException("Số tiền hoàn tối đa " + money(max) + "đ (số tiền khách đã trả)");
				}
				i.setStatus(IssueStatus.REFUNDED);
				i.setRefundAmount(amount);
				o.setRefundAmount(amount);
				boolean online = o.getPaymentMethod() != PaymentMethod.COD && o.getPaymentStatus() == PaymentStatus.PAID;
				if (online) {
					refundService.refundDelivered(o, amount, "Khách báo vấn đề: " + i.getReason().getLabel());
				}
				title = "Quán đã đồng ý hoàn " + money(amount) + "đ cho đơn #" + orderId;
				message = online
						? "Tiền được hoàn qua " + (o.getPaymentMethod() == PaymentMethod.MOMO ? "ví MoMo" : "VNPAY")
								+ ". " + RefundService.arrivalNote(o.getPaymentMethod().name())
						: "Quán sẽ liên hệ để hoàn tiền mặt / chuyển khoản cho bạn"
								+ (cleanNote == null ? "" : ". " + cleanNote);
			}
			case "REMAKE" -> {
				i.setStatus(IssueStatus.REMADE);
				title = "Quán sẽ làm lại món cho đơn #" + orderId;
				message = cleanNote != null ? cleanNote
						: "Quán sẽ liên hệ với bạn để giao lại / mời bạn ghé lấy lại món";
			}
			case "REJECT" -> {
				if (cleanNote == null) throw new BusinessException("Vui lòng ghi lý do từ chối để khách hiểu");
				i.setStatus(IssueStatus.REJECTED);
				title = "Quán từ chối hoàn tiền cho đơn #" + orderId;
				message = "Lý do: " + cleanNote;
			}
			default -> throw new BusinessException("Cách xử lý không hợp lệ");
		}
		message += " (Đã được giải quyết. Khiếu nại gọi Hotline Admin: " + ADMIN_HOTLINE + ")";
		i.setStoreNote(cleanNote);
		i.setResolvedAt(LocalDateTime.now());
		issueRepository.save(i);
		orderRepository.save(o);
		notificationService.create(o.getUser(), o, "REFUND", title, message, "/orders/" + orderId);
	}

	public OrderIssueRes forOrder(Integer orderId) {
		return issueRepository.findByOrderId(orderId).map(OrderIssueService::toRes).orElse(null);
	}

	public static OrderIssueRes toRes(OrderIssue i) {
		String badge = switch (i.getStatus()) {
			case PENDING -> "text-bg-warning";
			case REFUNDED -> "text-bg-info";
			case REMADE -> "text-bg-success";
			case REJECTED -> "text-bg-secondary";
		};
		List<String> imgs = i.getImages() == null || i.getImages().isBlank() ? List.of() : List.of(i.getImages().split("\\|"));

		boolean resolved = i.getStatus() != IssueStatus.PENDING;
		String statusLabel = resolved
				? i.getStatus().getLabel() + " · Đã được giải quyết, khiếu nại gọi Hotline Admin: " + ADMIN_HOTLINE
				: i.getStatus().getLabel();

		String storeNote = i.getStoreNote();
		if (resolved) {
			String hotlineText = "Đã được giải quyết. Khiếu nại gọi Hotline Admin: " + ADMIN_HOTLINE;
			storeNote = (storeNote == null || storeNote.isBlank())
					? hotlineText
					: storeNote + " — " + hotlineText;
		}

		return new OrderIssueRes(i.getId(), i.getReason().name(), i.getReason().getLabel(), i.getDescription(), imgs,
				i.getStatus().name(), statusLabel, badge, i.getRefundAmount(), storeNote,
				i.getCreatedAt() == null ? null : i.getCreatedAt().format(TIME),
				i.getResolvedAt() == null ? null : i.getResolvedAt().format(TIME));
	}

	private static String money(BigDecimal v) {
		return String.format("%,d", v.longValue()).replace(',', '.');
	}
}