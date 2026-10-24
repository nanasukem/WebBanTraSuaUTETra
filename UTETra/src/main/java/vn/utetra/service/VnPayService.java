package vn.utetra.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.utetra.config.PaymentProperties;
import vn.utetra.entity.Payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class VnPayService {

	private static final Logger log = LoggerFactory.getLogger(VnPayService.class);
	private static final String VERSION = "2.1.0";
	public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
	public static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final PaymentProperties props;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final ObjectMapper json = new ObjectMapper();

	public VnPayService(PaymentProperties props) {
		this.props = props;
	}

	public boolean isConfigured() {
		return props.vnpayConfigured();
	}

	public String createPaymentUrl(Payment payment, String orderInfo, String clientIp, LocalDateTime expireAt,
			String bankCode) {
		Map<String, String> p = new TreeMap<>();
		p.put("vnp_Version", VERSION);
		p.put("vnp_Command", "pay");
		p.put("vnp_TmnCode", props.vnpTmnCode());
		p.put("vnp_Amount", String.valueOf(payment.getAmount().longValue() * 100));
		p.put("vnp_CurrCode", "VND");
		p.put("vnp_TxnRef", payment.getTxnRef());
		p.put("vnp_OrderInfo", orderInfo);
		p.put("vnp_OrderType", "other");
		p.put("vnp_Locale", "vn");
		p.put("vnp_ReturnUrl", props.baseUrl() + "/payment/vnpay/return");
		p.put("vnp_IpAddr", clientIp);
		p.put("vnp_CreateDate", payment.getGatewayDate());
		p.put("vnp_ExpireDate", expireAt.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN).format(VNP_TIME));
		if (bankCode != null && !bankCode.isBlank()) p.put("vnp_BankCode", bankCode);

		StringBuilder hashData = new StringBuilder();
		StringBuilder query = new StringBuilder();
		for (Map.Entry<String, String> e : p.entrySet()) {
			if (e.getValue() == null || e.getValue().isEmpty()) continue;
			if (!hashData.isEmpty()) {
				hashData.append('&');
				query.append('&');
			}
			hashData.append(e.getKey()).append('=').append(enc(e.getValue()));
			query.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
		}
		String secureHash = hmacSha512(props.vnpHashSecret(), hashData.toString());
		return props.vnpPayUrl() + "?" + query + "&vnp_SecureHash=" + secureHash;
	}

	public GatewayResult readResult(Map<String, String> params) {
		String received = params.get("vnp_SecureHash");
		if (received == null || !isConfigured()) return GatewayResult.invalid("Thiếu chữ ký VNPAY");

		Map<String, String> fields = new TreeMap<>();
		params.forEach((k, v) -> {
			if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")
					&& v != null && !v.isEmpty()) {
				fields.put(enc(k), enc(v));
			}
		});
		StringBuilder hashData = new StringBuilder();
		fields.forEach((k, v) -> {
			if (!hashData.isEmpty()) hashData.append('&');
			hashData.append(k).append('=').append(v);
		});
		boolean verified = hmacSha512(props.vnpHashSecret(), hashData.toString()).equalsIgnoreCase(received);
		if (!verified) {
			log.warn("VNPAY: sai chữ ký, vnp_TxnRef={}", params.get("vnp_TxnRef"));
			return GatewayResult.invalid("Sai chữ ký VNPAY");
		}

		String responseCode = params.get("vnp_ResponseCode");
		String transStatus = params.get("vnp_TransactionStatus");
		String status = "00".equals(responseCode) && "00".equals(transStatus) ? "SUCCESS" : "FAILED";
		return new GatewayResult(true, status, params.get("vnp_TxnRef"), params.get("vnp_TransactionNo"),
				parseLong(params.get("vnp_Amount")) / 100, params.get("vnp_BankCode"), responseCode,
				responseMessage(responseCode));
	}

	public GatewayResult query(Payment payment) {
		String requestId = requestId();
		String createDate = LocalDateTime.now(VN).format(VNP_TIME);
		String ip = "127.0.0.1";
		String orderInfo = "Truy van giao dich " + payment.getTxnRef();
		String data = String.join("|", requestId, VERSION, "querydr", props.vnpTmnCode(), payment.getTxnRef(),
				payment.getGatewayDate(), createDate, ip, orderInfo);

		Map<String, String> body = new LinkedHashMap<>();
		body.put("vnp_RequestId", requestId);
		body.put("vnp_Version", VERSION);
		body.put("vnp_Command", "querydr");
		body.put("vnp_TmnCode", props.vnpTmnCode());
		body.put("vnp_TxnRef", payment.getTxnRef());
		body.put("vnp_OrderInfo", orderInfo);
		body.put("vnp_TransactionDate", payment.getGatewayDate());
		body.put("vnp_CreateDate", createDate);
		body.put("vnp_IpAddr", ip);
		body.put("vnp_SecureHash", hmacSha512(props.vnpHashSecret(), data));

		JsonNode res = post(body);
		if (res == null) return new GatewayResult(false, "PENDING", payment.getTxnRef(), null, 0, null, null,
				"Không kết nối được VNPAY");
		String code = text(res, "vnp_ResponseCode");
		if ("91".equals(code)) {
			return new GatewayResult(true, "NOT_FOUND", payment.getTxnRef(), null, 0, null, code,
					"Khách chưa thanh toán");
		}
		if (!"00".equals(code)) {
			return new GatewayResult(false, "PENDING", payment.getTxnRef(), null, 0, null, code, text(res, "vnp_Message"));
		}
		checkResponseHash(res, true);
		String ts = text(res, "vnp_TransactionStatus");
		String status = switch (ts == null ? "" : ts) {
			case "00" -> "SUCCESS";
			case "01" -> "PENDING";
			default -> "FAILED";
		};
		return new GatewayResult(true, status, payment.getTxnRef(), text(res, "vnp_TransactionNo"),
				parseLong(text(res, "vnp_Amount")) / 100, text(res, "vnp_BankCode"), ts, responseMessage(ts));
	}

	public String lookup(Payment payment) {
		if (!isConfigured()) return "VNPAY đang chạy chế độ demo, không tra cứu được";
		String requestId = requestId();
		String createDate = LocalDateTime.now(VN).format(VNP_TIME);
		String orderInfo = "Tra cuu giao dich " + payment.getTxnRef();
		String data = String.join("|", requestId, VERSION, "querydr", props.vnpTmnCode(), payment.getTxnRef(),
				payment.getGatewayDate(), createDate, "127.0.0.1", orderInfo);
		Map<String, String> body = new LinkedHashMap<>();
		body.put("vnp_RequestId", requestId);
		body.put("vnp_Version", VERSION);
		body.put("vnp_Command", "querydr");
		body.put("vnp_TmnCode", props.vnpTmnCode());
		body.put("vnp_TxnRef", payment.getTxnRef());
		body.put("vnp_OrderInfo", orderInfo);
		body.put("vnp_TransactionDate", payment.getGatewayDate());
		body.put("vnp_CreateDate", createDate);
		body.put("vnp_IpAddr", "127.0.0.1");
		body.put("vnp_SecureHash", hmacSha512(props.vnpHashSecret(), data));
		JsonNode res = post(body);
		if (res == null) return "Không kết nối được VNPAY";
		String code = text(res, "vnp_ResponseCode");
		if ("94".equals(code)) return "VNPAY chặn tra cứu liên tục cùng 1 giao dịch (mã 94), vui lòng thử lại sau vài phút";
		if ("91".equals(code)) return "VNPAY không tìm thấy giao dịch này (mã 91)";
		if (!"00".equals(code)) return "VNPAY trả lời mã " + code + ": " + text(res, "vnp_Message");
		String ts = text(res, "vnp_TransactionStatus");
		String type = text(res, "vnp_TransactionType");
		String status = switch (ts == null ? "" : ts) {
			case "00" -> "Thanh toán thành công";
			case "01" -> "Chưa hoàn tất";
			case "02" -> "Giao dịch lỗi";
			case "04" -> "Giao dịch đảo";
			case "05" -> "VNPAY đang xử lý hoàn tiền";
			case "06" -> "VNPAY đã gửi yêu cầu hoàn tiền sang ngân hàng";
			case "07" -> "Nghi ngờ gian lận";
			case "09" -> "Hoàn tiền bị từ chối";
			default -> "Trạng thái " + ts;
		};
		String typeLabel = "02".equals(type) ? " (hoàn toàn phần)" : "03".equals(type) ? " (hoàn một phần)" : "";
		return "VNPAY xác nhận: " + status + typeLabel + " · Số tiền " + parseLong(text(res, "vnp_Amount")) / 100
				+ "đ · Mã GD VNPAY " + text(res, "vnp_TransactionNo") + " · Ngân hàng " + text(res, "vnp_BankCode")
				+ " · Thời gian " + text(res, "vnp_PayDate");
	}

	public RefundResult refund(Payment payment, String createdBy) {
		String requestId = requestId();
		String createDate = LocalDateTime.now(VN).format(VNP_TIME);
		String ip = "127.0.0.1";
		String amount = String.valueOf(payment.refundValue().longValue() * 100);
		String type = payment.refundValue().compareTo(payment.getAmount()) < 0 ? "03" : "02";
		String transNo = payment.getTransactionCode() == null ? "" : payment.getTransactionCode();
		String orderInfo = "Hoan tien don UTETra " + payment.getOrder().getId();
		String data = String.join("|", requestId, VERSION, "refund", props.vnpTmnCode(), type, payment.getTxnRef(),
				amount, transNo, payment.getGatewayDate(), createdBy, createDate, ip, orderInfo);

		Map<String, String> body = new LinkedHashMap<>();
		body.put("vnp_RequestId", requestId);
		body.put("vnp_Version", VERSION);
		body.put("vnp_Command", "refund");
		body.put("vnp_TmnCode", props.vnpTmnCode());
		body.put("vnp_TransactionType", type);
		body.put("vnp_TxnRef", payment.getTxnRef());
		body.put("vnp_Amount", amount);
		body.put("vnp_OrderInfo", orderInfo);
		body.put("vnp_TransactionNo", transNo);
		body.put("vnp_TransactionDate", payment.getGatewayDate());
		body.put("vnp_CreateBy", createdBy);
		body.put("vnp_CreateDate", createDate);
		body.put("vnp_IpAddr", ip);
		body.put("vnp_SecureHash", hmacSha512(props.vnpHashSecret(), data));

		JsonNode res = post(body);
		if (res == null) return new RefundResult(false, null, null, "Không kết nối được VNPAY");
		String code = text(res, "vnp_ResponseCode");
		checkResponseHash(res, false);
		boolean ok = "00".equals(code) || "94".equals(code);
		return new RefundResult(ok, code, text(res, "vnp_TransactionNo"),
				ok ? "VNPAY đã nhận lệnh hoàn tiền" : refundMessage(code, text(res, "vnp_Message")));
	}

	public static String responseMessage(String code) {
		if (code == null) return "Không rõ kết quả";
		return switch (code) {
			case "00" -> "Giao dịch thành công";
			case "07" -> "Trừ tiền thành công nhưng giao dịch bị nghi ngờ, VNPAY đang kiểm tra";
			case "09" -> "Thẻ/Tài khoản chưa đăng ký Internet Banking";
			case "10" -> "Xác thực thông tin thẻ/tài khoản sai quá 3 lần";
			case "11" -> "Đã hết thời gian chờ thanh toán";
			case "12" -> "Thẻ/Tài khoản bị khoá";
			case "13" -> "Nhập sai mã OTP";
			case "24" -> "Bạn đã huỷ giao dịch";
			case "51" -> "Tài khoản không đủ số dư";
			case "65" -> "Tài khoản đã vượt hạn mức giao dịch trong ngày";
			case "75" -> "Ngân hàng thanh toán đang bảo trì";
			case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định";
			case "01" -> "Giao dịch chưa hoàn tất";
			case "02" -> "Giao dịch bị lỗi";
			default -> "Giao dịch không thành công (mã " + code + ")";
		};
	}

	private static String refundMessage(String code, String vnpMessage) {
		if (code == null) return "Không rõ kết quả hoàn tiền";
		return switch (code) {
			case "02" -> "Sai mã website (TmnCode)";
			case "03" -> "Dữ liệu gửi sang sai định dạng";
			case "91" -> "VNPAY không tìm thấy giao dịch cần hoàn";
			case "95" -> "Giao dịch gốc không thành công, VNPAY từ chối hoàn";
			case "97" -> "Sai chữ ký";
			default -> "VNPAY từ chối hoàn tiền (mã " + code + (vnpMessage == null ? "" : ": " + vnpMessage) + ")";
		};
	}

	private JsonNode post(Map<String, String> body) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(props.vnpApiUrl()))
					.timeout(Duration.ofSeconds(30))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
			log.info("VNPAY {} {} -> {}", body.get("vnp_Command"), body.get("vnp_TxnRef"), res.body());
			return json.readTree(res.body());
		} catch (Exception e) {
			log.warn("VNPAY {} lỗi kết nối: {}", body.get("vnp_Command"), e.getMessage());
			return null;
		}
	}

	private void checkResponseHash(JsonNode res, boolean query) {
		List<String> keys = new ArrayList<>(List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
				"vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
				"vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo"));
		if (query) keys.addAll(List.of("vnp_PromotionCode", "vnp_PromotionAmount"));
		StringJoiner data = new StringJoiner("|");
		keys.forEach(k -> data.add(Optional.ofNullable(text(res, k)).orElse("")));
		String expected = hmacSha512(props.vnpHashSecret(), data.toString());
		if (!expected.equalsIgnoreCase(Optional.ofNullable(text(res, "vnp_SecureHash")).orElse(""))) {
			log.warn("VNPAY: chữ ký phản hồi {} không khớp (vẫn dùng vì đi qua HTTPS)", text(res, "vnp_Command"));
		}
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		return v == null || v.isNull() ? null : v.asString();
	}

	private static String requestId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
	}

	private static long parseLong(String s) {
		try {
			return s == null ? 0 : Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.US_ASCII);
	}

	public static String hmacSha512(String key, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA512");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
			return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("Không tạo được chữ ký VNPAY", e);
		}
	}
}
