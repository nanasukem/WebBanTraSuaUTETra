package vn.utetra.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

	@Autowired(required = false)
	private JavaMailSender mailSender;

	public void sendOtpEmail(String toEmail, String otpCode) {
		if (mailSender == null) {
			System.out.println(">>> [DEV MODE] Mã OTP gửi tới " + toEmail + " là: " + otpCode);
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(toEmail);
		message.setSubject("Mã xác thực OTP tài khoản UTETra");
		message.setText("Mã OTP kích hoạt tài khoản UTETra của bạn là: " + otpCode + "\nMã có hiệu lực trong 5 phút.");
		mailSender.send(message);
	}
}