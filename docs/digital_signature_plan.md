# Kế Hoạch Triển Khai Module Ký Số (Digital Signature) Qua Video Meeting

Vì bạn hiện tại **chưa có Chứng thư số (Digital Certificate)**, quá trình phát triển và kiểm thử sẽ gặp khó khăn nếu muốn làm thật ngay. Tuy nhiên, chúng ta hoàn toàn có thể xây dựng luồng hệ thống hoàn chỉnh bằng các giải pháp thay thế.

Dưới đây là kế hoạch chi tiết để triển khai tính năng ký số trong quá trình "đối soát qua meeting", bao gồm cách giải quyết vấn đề thiếu chứng thư số và lộ trình thực hiện.

---

## Phần 1: Giải pháp khi KHÔNG có Chứng thư số thật

Trong giai đoạn phát triển (Dev/Test), chúng ta sẽ sử dụng các phương pháp giả lập để hệ thống vẫn chạy đúng chuẩn PKI (Public Key Infrastructure):

1. **Self-signed Certificate (Chứng thư số tự ký):**
   - **Cách làm:** Sử dụng Java (`keytool` hoặc thư viện `BouncyCastle`) hoặc `OpenSSL` để tự sinh ra một cặp khóa (Private/Public Key) và tự cấp phát chứng thư số (File `.p12` hoặc `.jks`).
   - **Ưu điểm:** Hoàn toàn miễn phí, lưu trữ ngay trên Backend, code ký số hoạt động y hệt như dùng chứng thư thật. Khi lên Production chỉ cần thay file `.p12` thật vào là xong.
   - **Nhược điểm:** Khi mở file PDF bằng Adobe Acrobat, nó sẽ báo "Signature is valid but the identity of the signer is unknown" (Vì là cert tự tạo, không thuộc tổ chức CA uy tín).

2. **Dùng Sandbox của các nhà cung cấp VNPT-CA, Viettel-CA, FPT-CA:**
   - **Cách làm:** Đăng ký tài khoản Developer/Sandbox tại các dịch vụ Remote Signing (Ký số từ xa) của các nhà mạng. Họ sẽ cấp cho bạn một API và Chứng thư số test.
   - **Ưu điểm:** Trải nghiệm luồng tích hợp y như thật.

3. **Phân loại chữ ký:**
   - **Người dân (Citizen):** Đa phần người dân không có USB Token hoặc chữ ký số. Giải pháp là dùng **Chữ ký điện tử (Electronic Signature)**: Người dân vẽ chữ ký trên màn hình (Signature Pad) hoặc ký bằng mã OTP qua điện thoại.
   - **Công chứng viên (Notary):** Bắt buộc dùng **Chữ ký số (Digital Signature)** bằng Chứng thư số (hiện tại sẽ dùng file tự ký `.p12`).

---

## Phần 2: Quy trình nghiệp vụ trong Video Meeting

Luồng Ký số sẽ diễn ra đồng thời trong lúc gọi Video Call (`VideoRoomPage`):

1. **Bước 1: Đối soát danh tính:**
   - CCV (Công chứng viên) và Người dân vào phòng Meeting. CCV yêu cầu người dân đưa CCCD lên camera để đối chiếu.
2. **Bước 2: Review Tài liệu:**
   - CCV chia sẻ file tài liệu (PDF) lên màn hình chung hoặc mở giao diện "Trình chiếu tài liệu" để cả hai bên cùng đọc.
3. **Bước 3: Người dân ký (Chữ ký điện tử):**
   - Người dân bấm nút "Ký tên". Một popup hiện ra cho phép họ **vẽ chữ ký** (dùng `react-signature-canvas`).
   - Hình ảnh chữ ký và Tọa độ ký được gửi xuống Backend.
   - Backend đính (stamp) hình ảnh này vào file PDF gốc.
4. **Bước 4: CCV ký (Chữ ký số):**
   - CCV bấm nút "Ký số và Đóng dấu".
   - Backend sử dụng file Chứng thư số giả lập (`.p12`) để thực hiện ký thuật toán RSA/SHA-256 lên file PDF (dùng thư viện `iText` hoặc `Apache PDFBox`).
   - Trạng thái yêu cầu chuyển thành `COMPLETED`.
5. **Bước 5: Hoàn tất:**
   - File PDF cuối cùng sẽ có cả hình ảnh chữ ký của người dân và **Chữ ký số hợp lệ** của CCV. Cả 2 bên đều có thể tải xuống file này.

---

## Phần 3: Lộ trình triển khai (Implementation Plan)

### Giai đoạn 1: Chuẩn bị Backend & Sinh Mock Certificate (1-2 Ngày)
- Dùng OpenSSL hoặc Java sinh ra một file `notary-test.p12` chứa Private Key & Public Key.
- Cấu hình file này vào thư mục `src/main/resources/certs/` của Spring Boot.
- Viết Service đọc file `.p12` và ký PDF sử dụng thư viện `iText7` hoặc `PDFBox`.
  - **Input:** File PDF đầu vào, Tọa độ ký, Mật khẩu file `.p12`.
  - **Output:** File PDF đã được ký số (Digital Signature embedded).

### Giai đoạn 2: UI/UX Frontend cho Video Meeting (2-3 Ngày)
- Tích hợp thư viện xem PDF vào `VideoRoomPage.tsx` (có thể dùng `@react-pdf-viewer/core`).
- Tạo Component `SignaturePadModal` cho phép Người dân dùng chuột/cảm ứng để vẽ chữ ký.
- Tạo nút "Ký số" cho CCV. Nút này gọi API xuống backend để trigger hàm Ký số bằng file `.p12`.

### Giai đoạn 3: Ghép nối API & Lưu trữ (1-2 Ngày)
- Cập nhật entity `Signature.java` (đã có sẵn) để lưu trữ `signatureValue` và `certSerial`.
- Xây dựng API:
  - `POST /api/video-meeting/{meetingId}/citizen-sign` -> Upload ảnh vẽ chữ ký & chèn vào PDF.
  - `POST /api/video-meeting/{meetingId}/notary-sign` -> Thực hiện ký số bằng chứng thư, khóa file PDF không cho sửa đổi.
- Cập nhật realtime UI bằng WebRTC/WebSocket để khi một người ký xong, màn hình người kia tự động load lại file PDF mới nhất.

### Giai đoạn 4: Mở rộng lên Production (Tương lai)
Khi dự án được triển khai thật và CCV có USB Token:
- Bỏ file `.p12` trên Backend.
- Viết một Client App (chạy ngầm trên máy tính CCV) để đọc USB Token qua cổng USB.
- Frontend của CCV sẽ gọi qua localhost/WebSocket đến Client App đó để lấy chữ ký.
- Hoặc nâng cấp sử dụng dịch vụ Ký số từ xa (Remote Signing) của VNPT/Viettel thông qua API của họ.
