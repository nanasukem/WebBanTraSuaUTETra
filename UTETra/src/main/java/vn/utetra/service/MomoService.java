package vn.utetra.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.utetra.config.PaymentProperties;
import vn.utetra.entity.Payment;
import vn.utetra.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MomoService {

	private static final Logger log = LoggerFactory.getLogger(MomoService.class);

	private final PaymentProperties props;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
	private final ObjectMapper json = new ObjectMapper();

	public MomoService(PaymentProperties props) {
		this.props = props;
	}

	public boolean isConfigured() {
		return props.momoConfigured();
	}

	public String createPayUrl(Payment payment, String orderInfo) {
		String requestId = payment.getTxnRef();
		String amount = String.valueOf(payment.getAmount().longValue());
		String redirectUrl = props.baseUrl() + "/payment/momo/return";
		String ipnUrl = props.ipnBaseUrl() + "/payment/momo/ipn";
		String extraData = "";
		String requestType = props.momoRequestType();

		String raw = "accessKey=" + props.momoAccessKey() + "&amount=" + amount + "&extraData=" + extraData
				+ "&ipnUrl=" + ipnUrl + "&orderId=" + payment.getTxnRef() + "&orderInfo=" + orderInfo
				+ "&partnerCode=" + props.momoPartnerCode() + "&redirectUrl=" + redirectUrl
				+ "&requestId=" + requestId + "&requestType=" + requestType;

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("partnerCode", props.momoPartnerCode());
		body.put("storeName", "UTETra");
		body.put("storeId", "UTETra");
		body.put("requestId", requestId);
		body.put("amount", payment.getAmount().longValue());
		body.put("orderId", payment.getTxnRef());
		body.put("orderInfo", orderInfo);
		body.put("redirectUrl", redirectUrl);
		body.put("ipnUrl", ipnUrl);
		body.put("lang", "vi");
		body.put("requestType", requestType);
		body.put("autoCapture", true);
		body.put("extraData", extraData);
		body.put("signature", hmacSha256(props.momoSecretKey(), raw));

		JsonNode res = post("/v2/gateway/api/create", body);
		if (res == null) throw new BusinessException("Không kết nối được MoMo, vui lòng thử lại sau ít phút");
		int code = res.path("resultCode").asInt(-1);
		String payUrl = text(res, "payUrl");
		if (code != 0 || payUrl == null) {
			throw new BusinessException("MoMo từ chối tạo giao dịch: " + text(res, "message") + " (mã " + code + ")");
		}
		return payUrl;
	}

	public GatewayResult readResult(Map<String, String> p) {
		String received = p.get("signature");
		if (received == null) return GatewayResult.invalid("Thiếu chữ ký MoMo");
		String raw = "accessKey=" + props.momoAccessKey() + "&amount=" + v(p, "amount") + "&extraData=" + v(p, "extraData")
				+ "&message=" + v(p, "message") + "&orderId=" + v(p, "orderId") + "&orderInfo=" + v(p, "orderInfo")
				+ "&orderType=" + v(p, "orderType") + "&partnerCode=" + v(p, "partnerCode") + "&payType=" + v(p, "payType")
				+ "&requestId=" + v(p, "requestId") + "&responseTime=" + v(p, "responseTime")
				+ "&resultCode=" + v(p, "resultCode") + "&transId=" + v(p, "transId");
		if (!hmacSha256(props.momoSecretKey(), raw).equalsIgnoreCase(received)) {
			log.warn("MoMo: sai chữ ký, orderId={}", p.get("orderId"));
			return GatewayResult.invalid("Sai chữ ký MoMo");
		}
		String code = v(p, "resultCode");
		return new GatewayResult(true, statusOf(code), p.get("orderId"), p.get("transId"), parseLong(p.get("amount")),
				p.get("payType"), code, resultMessage(code));
	}

	public GatewayResult query(Payment payment) {
		String requestId = UUID.randomUUID().toString();
		String raw = "accessKey=" + props.momoAccessKey() + "&orderId=" + payment.getTxnRef()
				+ "&partnerCode=" + props.momoPartnerCode() + "&requestId=" + requestId;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("partnerCode", props.momoPartnerCode());
		body.put("requestId", requestId);
		body.put("orderId", payment.getTxnRef());
		body.put("lang", "vi");
		body.put("signature", hmacSha256(props.momoSecretKey(), raw));

		JsonNode res = post("/v2/gateway/api/query", body);
		if (res == null) return new GatewayResult(false, "PENDING", payment.getTxnRef(), null, 0, null, null,
				"Không kết nối được MoMo");
		String code = String.valueOf(res.path("resultCode").asInt(-1));
		return new GatewayResult(true, statusOf(code), payment.getTxnRef(), text(res, "transId"),
				res.path("amount").asLong(0), text(res, "payType"), code, resultMessage(code));
	}

	public String lookup(Payment payment) {
		String requestId = UUID.randomUUID().toString();
		String raw = "accessKey=" + props.momoAccessKey() + "&orderId=" + payment.getTxnRef()
				+ "&partnerCode=" + props.momoPartnerCode() + "&requestId=" + requestId;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("partnerCode", props.momoPartnerCode());
		body.put("requestId", requestId);
		body.put("orderId", payment.getTxnRef());
		body.put("lang", "vi");
		body.put("signature", hmacSha256(props.momoSecretKey(), raw));
		JsonNode res = post("/v2/gateway/api/query", body);
		if (res == null) return "Không kết nối được MoMo";
		String code = String.valueOf(res.path("resultCode").asInt(-1));
		StringBuilder sb = new StringBuilder("MoMo xác nhận: " + resultMessage(code) + " · Số tiền "
				+ res.path("amount").asLong(0) + "đ · Mã GD MoMo " + text(res, "transId") + " · Hình thức " + text(res, "payType"));
		JsonNode refunds = res.get("refundTrans");
		if (refunds != null && refunds.isArray() && !refunds.isEmpty()) {
			sb.append(" · Đã hoàn ").append(refunds.size()).append(" lần:");
			for (JsonNode r : refunds) {
				sb.append(" [").append(r.path("amount").asLong(0)).append("đ, mã ").append(text(r, "transId"))
						.append(", ").append(r.path("resultCode").asInt(-1) == 0 ? "thành công" : "mã " + r.path("resultCode").asInt(-1))
						.append("]");
			}
		}
		return sb.toString();
	}

	public RefundResult refund(Payment payment) {
		String refundOrderId = payment.getRefundRef();
		String requestId = UUID.randomUUID().toString();
		String amount = String.valueOf(payment.refundValue().longValue());
		String description = "Hoan tien don UTETra " + payment.getOrder().getId();
		String transId = payment.getTransactionCode() == null ? "" : payment.getTransactionCode();
		String raw = "accessKey=" + props.momoAccessKey() + "&amount=" + amount + "&description=" + description
				+ "&orderId=" + refundOrderId + "&partnerCode=" + props.momoPartnerCode() + "&requestId=" + requestId
				+ "&transId=" + transId;

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("partnerCode", props.momoPartnerCode());
		body.put("orderId", refundOrderId);
		body.put("requestId", requestId);
		body.put("amount", payment.refundValue().longValue());
		body.put("transId", parseLong(transId));
		body.put("lang", "vi");
		body.put("description", description);
		body.put("signature", hmacSha256(props.momoSecretKey(), raw));

		JsonNode res = post("/v2/gateway/api/refund", body);
		if (res == null) return new RefundResult(false, null, null, "Không kết nối được MoMo");
		int code = res.path("resultCode").asInt(-1);
		return new RefundResult(code == 0, String.valueOf(code), text(res, "transId"),
				code == 0 ? "MoMo đã hoàn tiền" : "MoMo từ chối hoàn tiền: " + text(res, "message") + " (mã " + code + ")");
	}

	private static String statusOf(String code) {
		return switch (code == null ? "" : code) {
			case "0", "9000" -> "SUCCESS";
			case "1000", "7000", "7002" -> "PENDING";
			case "42" -> "NOT_FOUND";
			default -> "FAILED";
		};
	}

	public static String resultMessage(String code) {
		if (code == null) return "Không rõ kết quả";
		return switch (code) {
			case "0" -> "Giao dịch thành công";
			case "9000" -> "Giao dịch đã được xác nhận";
			case "1000" -> "Đang chờ bạn xác nhận thanh toán trên MoMo";
			case "7000", "7002" -> "Giao dịch đang được xử lý";
			case "1001" -> "Ví MoMo không đủ số dư";
			case "1002" -> "Giao dịch bị từ chối bởi ngân hàng / nhà phát hành";
			case "1003" -> "Giao dịch đã bị huỷ";
			case "1004" -> "Vượt hạn mức thanh toán";
			case "1005" -> "Link / mã QR thanh toán đã hết hạn";
			case "1006" -> "Bạn đã từ chối xác nhận thanh toán";
			case "1007" -> "Tài khoản MoMo đang bị tạm khoá";
			case "1017" -> "Giao dịch đã bị huỷ";
			case "1026" -> "Giao dịch bị hạn chế theo thể lệ khuyến mãi";
			case "42" -> "MoMo chưa ghi nhận giao dịch";
			case "99" -> "Lỗi không xác định từ MoMo";
			default -> "Giao dịch không thành công (mã " + code + ")";
		};
	}

	private JsonNode post(String path, Map<String, Object> body) {
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(props.momoEndpoint() + path))
					.timeout(Duration.ofSeconds(30))
					.header("Content-Type", "application/json; charset=UTF-8")
					.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			log.info("MoMo {} {} -> {}", path, body.get("orderId"), res.body());
			return json.readTree(res.body());
		} catch (Exception e) {
			log.warn("MoMo {} lỗi kết nối: {}", path, e.getMessage());
			return null;
		}
	}

	private static String v(Map<String, String> p, String key) {
		String s = p.get(key);
		return s == null ? "" : s;
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		return v == null || v.isNull() ? null : v.asString();
	}

	private static long parseLong(String s) {
		try {
			return s == null || s.isBlank() ? 0 : Long.parseLong(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static String hmacSha256(String key, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("Không tạo được chữ ký MoMo", e);
		}
	}
}
