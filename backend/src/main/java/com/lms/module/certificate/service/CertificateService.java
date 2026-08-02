package com.lms.module.certificate.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.lms.common.service.MinioStorageService;
import com.lms.module.certificate.entity.Certificate;
import com.lms.module.certificate.repository.CertificateRepository;
import com.lms.module.course.entity.Course;
import com.lms.module.course.repository.CourseRepository;
import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final JavaMailSender mailSender;
    private final MinioStorageService minioStorageService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Transactional
    public Certificate generateCertificate(UUID studentId, UUID courseId, String tenantId) {
        // Idempotency check
        if (certificateRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            return certificateRepository.findByStudentIdAndCourseId(studentId, courseId).orElseThrow();
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Course not found"));

        UUID verificationCode = UUID.randomUUID();
        // IMPORTANT: /public/verify/ must match CertificateController.verifyCertificate path
        String verificationUrl = baseUrl + "/public/verify/" + verificationCode;

        try {
            // 1. Generate QR Code bytes
            byte[] qrCodeBytes = generateQRCode(verificationUrl, 200, 200);

            // 2. Generate PDF bytes
            byte[] pdfBytes = generatePdf(student, course, verificationCode, qrCodeBytes);

            // 3. Upload to MinIO — returns durable object key (NOT a presigned URL)
            String pdfObjectKey = uploadToMinio(tenantId, studentId, courseId, "certificate.pdf",
                    pdfBytes, "application/pdf");
            String qrObjectKey = uploadToMinio(tenantId, studentId, courseId, "qrcode.png",
                    qrCodeBytes, "image/png");

            Certificate certificate = Certificate.builder()
                    .studentId(studentId)
                    .courseId(courseId)
                    .issuedAt(LocalDateTime.now())
                    .pdfObjectKey(pdfObjectKey)
                    .qrObjectKey(qrObjectKey)
                    // certificateUrl kept for legacy compatibility
                    .certificateUrl(pdfObjectKey)
                    .qrCodeUrl(qrObjectKey)
                    .verificationCode(verificationCode)
                    .valid(true)
                    .build();

            certificate = certificateRepository.save(certificate);

            // 4. Send email with certificate attached
            sendCertificateEmail(student, course, pdfBytes, verificationCode);

            log.info("Certificate generated for student {} in course {}", studentId, courseId);
            return certificate;

        } catch (Exception e) {
            log.error("Failed to generate certificate for student {} course {}: {}",
                    studentId, courseId, e.getMessage(), e);
            throw new RuntimeException("Certificate generation failed", e);
        }
    }

    /**
     * Uploads file to MinIO and returns the durable object key.
     * Falls back to base64 data URL if MinIO is unavailable (e.g. local dev without MinIO).
     */
    private String uploadToMinio(String tenantId, UUID studentId, UUID courseId,
                                  String filename, byte[] data, String contentType) {
        try {
            String objectKey = String.format("certificates/%s/%s/%s/%s",
                    tenantId, courseId, studentId, filename);
            minioStorageService.uploadBytes("lms-certificates", objectKey, data, contentType);
            // Return the durable object key, NOT a presigned URL
            return objectKey;
        } catch (Exception e) {
            log.warn("MinIO upload failed, using base64 fallback: {}", e.getMessage());
            // Return base64 data URL as fallback for local dev without MinIO
            String mimeType = contentType;
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(data);
        }
    }

    private byte[] generateQRCode(String content, int width, int height) throws WriterException, java.io.IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        return outputStream.toByteArray();
    }

    private byte[] generatePdf(User student, Course course, UUID verificationCode, byte[] qrCodeBytes)
            throws Exception {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4.rotate());

        try {
            document.setMargins(40, 60, 40, 60);

            // Title
            document.add(new Paragraph("Certificate of Completion")
                    .setFontSize(36)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(DeviceRgb.BLACK));

            // Decorative line
            document.add(new Paragraph("─────────────────────────────────────────────────────")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(12));

            document.add(new Paragraph("\nThis is to certify that\n")
                    .setFontSize(16).setTextAlignment(TextAlignment.CENTER));

            // Student name
            String studentName = student.getFirstName() + " " + student.getLastName();
            document.add(new Paragraph(studentName)
                    .setFontSize(28).setBold().setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new DeviceRgb(0, 102, 204)));

            document.add(new Paragraph("has successfully completed the course\n")
                    .setFontSize(16).setTextAlignment(TextAlignment.CENTER));

            // Course title
            document.add(new Paragraph(course.getTitle())
                    .setFontSize(22).setBold().setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(new DeviceRgb(51, 51, 51)));

            document.add(new Paragraph("\n"));

            // Date
            String issuedDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));
            document.add(new Paragraph("Issued on: " + issuedDate)
                    .setFontSize(14).setTextAlignment(TextAlignment.CENTER));

            // Verification code
            document.add(new Paragraph("Verification Code: " + verificationCode.toString())
                    .setFontSize(10).setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

            // QR Code — iText 8 uses HorizontalAlignment from layout.properties
            Image qrImage = new Image(ImageDataFactory.create(qrCodeBytes));
            qrImage.setWidth(100).setHeight(100);
            qrImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
            document.add(qrImage);

            document.add(new Paragraph("Scan to verify: " + verificationCode.toString().substring(0, 8) + "...")
                    .setFontSize(9).setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY));

        } finally {
            document.close();
        }

        return outputStream.toByteArray();
    }

    private void sendCertificateEmail(User student, Course course, byte[] pdfBytes, UUID verificationCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(mailFrom);
            helper.setTo(student.getEmail());
            helper.setSubject("🎓 Your Certificate for: " + course.getTitle());
            helper.setText(
                    "Congratulations " + student.getFirstName() + "!\n\n" +
                    "You have successfully completed \"" + course.getTitle() + "\".\n\n" +
                    "Your certificate is attached. You can verify it at:\n" +
                    baseUrl + "/verify/" + verificationCode + "\n\n" +
                    "Keep learning!\nThe LMS Team",
                    false
            );
            helper.addAttachment("certificate.pdf",
                    () -> new ByteArrayInputStream(pdfBytes));
            mailSender.send(message);
            log.info("Certificate email sent to {}", student.getEmail());
        } catch (Exception e) {
            log.error("Failed to send certificate email to {}: {}", student.getEmail(), e.getMessage());
        }
    }

    public Certificate verifyCertificate(UUID verificationCode) {
        return certificateRepository.findByVerificationCode(verificationCode)
                .orElseThrow(() -> new RuntimeException("Certificate not found or invalid"));
    }

    public List<Certificate> getStudentCertificates(UUID studentId) {
        return certificateRepository.findByStudentId(studentId);
    }
}
