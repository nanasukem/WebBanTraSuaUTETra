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

/**
 * "Báo vấn đề với đơn" - thay cho Trả hàng (đồ uống không trả lại được), giống app chuỗi trà sữa thật:
 * - Khách báo trong {@value #WINDOW_HOURS} giờ sau khi nhận món, chọn lý do, mô tả, kèm 1-3 ảnh. Mỗi đơn báo 1 lần.
 * - Manager của chi nhánh xử lý:
 *   + Hoàn tiền (một phần hoặc toàn bộ): đơn trả trước hoàn qua VNPAY/MoMo, đơn COD quán tự hoàn tiền mặt / chuyển khoản.
 *     Tiền hoàn bị trừ khỏi doanh thu của đơn.
 *   + Làm lại món (giao lại / khách ghé lấy lại)
 *   + Từ chối (bắt buộc ghi lý do)
 * - Báo chuông cho quán khi khách gửi, báo chuông cho khách khi quán xử lý.
 */
@Service
@Transactional(readOnly = true)
public class OrderIssueService {

	public static final int WINDOW_HOURS = 2;
	public static final int MAX_IMAGES = 3;
	private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
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

	// ================= KHÁCH =================

	/** Hạn báo vấn đề của đơn (null = đơn chưa giao) */
	public static LocalDateTime deadline(Order o) {
		return o.getDeliveredAt() == null ? null : o.getDeliveredAt().plusHours(WINDOW_HOURS);
	}

	/** Đơn còn báo vấn đề được không: đã giao, còn trong hạn, chưa báo */
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

		List<MultipartFile> images = files == null ? List.of()
				: files.stream().filter(f -> f != null && !f.isEmpty()).toList();
		if (images.isEmpty()) throw new BusinessException("Vui lòng chụp ít nhất 1 ảnh món để quán kiểm tra");
		if (images.size() > MAX_IMAGES) throw new BusinessException("Tối đa " + MAX_IMAGES + " ảnh");
		for (MultipartFile f : images) {
			if (!"IMAGE".equals(ReviewService.mediaType(f))) {
				throw new BusinessException("File \"" + f.getOriginalFilename() + "\" không phải ảnh");
			}
			if (f.getSize() > MAX_IMAGE_BYTES) {
				throw new BusinessException("Ảnh \"" + f.getOriginalFilename() + "\" quá 5MB");
			}
		}
		List<String> urls = new ArrayList<>();
		for (MultipartFile f : images) urls.add(cloudinaryService.uploadImage(f, "order-issues"));

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

	// ================= QUÁN XỬ LÝ =================

	/**
	 * action: REFUND (amount bắt buộc, tối đa tiền món khách trả) / REMAKE / REJECT (note bắt buộc).
	 * Không kiểm tra quyền (controller kiểm tra đơn thuộc chi nhánh của Manager).
	 */
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
				o.setRefundAmount(amount); // trừ khỏi doanh thu
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
		i.setStoreNote(cleanNote);
		i.setResolvedAt(LocalDateTime.now());
		issueRepository.save(i);
		orderRepository.save(o);
		notificationService.create(o.getUser(), o, "REFUND", title, message, "/orders/" + orderId);
	}

	// ================= ĐỌC =================

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
		return new OrderIssueRes(i.getId(), i.getReason().name(), i.getReason().getLabel(), i.getDescription(), imgs,
				i.getStatus().name(), i.getStatus().getLabel(), badge, i.getRefundAmount(), i.getStoreNote(),
				i.getCreatedAt() == null ? null : i.getCreatedAt().format(TIME),
				i.getResolvedAt() == null ? null : i.getResolvedAt().format(TIME));
	}

	private static String money(BigDecimal v) {
		return String.format("%,d", v.longValue()).replace(',', '.');
	}
}
