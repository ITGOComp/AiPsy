package com.example.aipsy;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class EmailSender {

    private static final String FROM_EMAIL   = Secrets.FROM_EMAIL;
    private static final String APP_PASSWORD = Secrets.APP_PASSWORD;
    private static final String TO_EMAIL     = Secrets.TO_EMAIL;

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public static void send(String subject, String htmlBody, Callback callback) {
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host",            "smtp.yandex.ru");
                props.put("mail.smtp.port",            "465");
                props.put("mail.smtp.auth",            "true");
                props.put("mail.smtp.ssl.enable",      "true");
                props.put("mail.smtp.ssl.protocols",   "TLSv1.2");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(FROM_EMAIL, APP_PASSWORD);
                    }
                });

                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(FROM_EMAIL, "ИИ-Психолог"));
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL));
                msg.setSubject(subject, "UTF-8");

                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(htmlBody, "text/html; charset=utf-8");

                MimeMultipart multipart = new MimeMultipart("alternative");
                multipart.addBodyPart(htmlPart);
                msg.setContent(multipart);

                Transport.send(msg);
                callback.onSuccess();

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
