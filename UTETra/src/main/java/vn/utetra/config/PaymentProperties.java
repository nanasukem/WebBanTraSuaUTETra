package vn.utetra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentProperties {

	@Value("${app.base-url:http://localhost:8080}")
	private String baseUrl;

	@Value("${payment.ipn-base-url:}")
	private String ipnBaseUrl;

	@Value("${payment.timeout-minutes:15}")
	private int timeoutMinutes;

	@Value("${vnpay.tmn-code:}")
	private String vnpTmnCode;

	@Value("${vnpay.hash-secret:}")
	private String vnpHashSecret;

	@Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
	private String vnpPayUrl;

	@Value("${vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}")
	private String vnpApiUrl;

	@Value("${momo.partner-code:MOMO}")
	private String momoPartnerCode;

	@Value("${momo.access-key:F8BBA842ECF85}")
	private String momoAccessKey;

	@Value("${momo.secret-key:K951B6PE1waDMi640xX08PD3vg6EkVlz}")
	private String momoSecretKey;

	@Value("${momo.endpoint:https://test-payment.momo.vn}")
	private String momoEndpoint;

	@Value("${momo.request-type:payWithMethod}")
	private String momoRequestType;

	public String baseUrl() { return trimSlash(baseUrl); }

	public String ipnBaseUrl() { return ipnBaseUrl == null || ipnBaseUrl.isBlank() ? baseUrl() : trimSlash(ipnBaseUrl); }

	public int timeoutMinutes() { return timeoutMinutes; }

	public boolean vnpayConfigured() { return notBlank(vnpTmnCode) && notBlank(vnpHashSecret); }

	public String vnpTmnCode() { return vnpTmnCode.trim(); }
	public String vnpHashSecret() { return vnpHashSecret.trim(); }
	public String vnpPayUrl() { return vnpPayUrl; }
	public String vnpApiUrl() { return vnpApiUrl; }

	public boolean momoConfigured() { return notBlank(momoPartnerCode) && notBlank(momoAccessKey) && notBlank(momoSecretKey); }

	public String momoPartnerCode() { return momoPartnerCode.trim(); }
	public String momoAccessKey() { return momoAccessKey.trim(); }
	public String momoSecretKey() { return momoSecretKey.trim(); }
	public String momoEndpoint() { return trimSlash(momoEndpoint); }
	public String momoRequestType() { return momoRequestType; }

	private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

	private static String trimSlash(String s) {
		String t = s == null ? "" : s.trim();
		return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
	}
}
