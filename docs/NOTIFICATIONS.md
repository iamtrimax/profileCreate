# Giai đoạn 3 — Thông báo và email

Khởi động lại Spring Boot để Flyway áp dụng `V6__notifications_and_mail_outbox.sql`. Dashboard có nút **Thông báo**, số chưa đọc, bộ lọc chưa đọc, phân trang và thao tác đánh dấu đã đọc. Hộp thư và lịch hẹn tự cập nhật khi nhận sự kiện mới; không tải lại các form đang sửa.

## Sự kiện và người nhận

| Sự kiện | Thông báo dashboard | Email |
|---|---|---|
| Khách gửi liên hệ | Chủ profile | Chủ profile |
| Khách đặt lịch | Chủ profile | Chủ profile và khách |
| Xác nhận/hủy/hoàn tất lịch | Chủ profile | Chủ profile và khách |
| Nhắc lịch đã xác nhận trước 24 giờ / 1 giờ | Chủ profile | Khách hàng |

## Nhắc lịch hẹn

Migration V7 thêm `booking_reminders`. Khởi động lại ứng dụng để áp dụng. Khối **Lịch hẹn sắp tới** hiển thị ngay dưới lời chào trên dashboard, trước thống kê và các form quản lý. Khối này luôn hiển thị, không cần mở chuông thông báo: tối đa 5 lịch gần nhất đang diễn ra hoặc bắt đầu trong 24 giờ tới, số lịch còn lại và số lịch chờ xác nhận. Giờ hẹn theo timezone đã lưu của booking; thời gian còn lại tính từ thời gian server. Lịch trong 1 giờ hoặc đang diễn ra có màu nổi bật. Tự cập nhật mỗi 30 giây, khi thay đổi trạng thái lịch và khi nhận thông báo SSE.

Worker nhắc lịch chạy mỗi phút, độc lập với việc chủ profile có mở dashboard hay không:

- Chỉ nhắc booking `CONFIRMED`; không nhắc `PENDING`, `CANCELLED`, `COMPLETED` hoặc lịch đã bắt đầu.
- Còn trên 1 giờ và tối đa 24 giờ: tạo nhắc mốc 24 giờ. Còn tối đa 1 giờ: tạo nhắc mốc 1 giờ.
- Xác nhận muộn trong vòng 1 giờ chỉ tạo nhắc mốc 1 giờ, không gửi dồn cả hai mốc.
- Khóa booking trong giao dịch và khóa duy nhất `(booking_id, stage)` ngăn tạo nhắc trùng khi nhiều instance chạy. Notification và email outbox được ghi cùng giao dịch.
- Trước khi gửi mỗi email, worker kiểm tra lại trạng thái và hạn nhắc. Nhắc mốc 24 giờ hết hạn khi bước vào 1 giờ cuối; nhắc mốc 1 giờ hết hạn lúc bắt đầu. Email lỗi thời hoặc lịch đã hủy được kết thúc ở trạng thái `FAILED`, lý do `REMINDER_CANCELLED_OR_EXPIRED`, không thử gửi lại. Thư đã được SMTP nhận thì không thể thu hồi nếu hủy lịch sau đó.

API `GET /api/me/bookings/upcoming` yêu cầu đăng nhập, chỉ trả lịch của người đang đăng nhập. Đặt `BOOKING_REMINDERS_ENABLED=false` để tắt tạo nhắc tự động (khối lịch sắp tới vẫn hoạt động). Email nhắc dùng cùng SMTP/outbox với giai đoạn 3, cần `MAIL_ENABLED=true`; nếu đang dùng Mailpit, thư chỉ xuất hiện trong Mailpit. Không cần bật riêng một SMTP khác cho nhắc lịch.

Email dùng nội dung text UTF-8; dữ liệu khách không được render thành HTML. Mỗi sự kiện có `event_key` duy nhất, mỗi người nhận chỉ có một email trong outbox của sự kiện đó. Gửi lặp lại thao tác đổi sang cùng trạng thái booking không tạo thêm sự kiện. Hai yêu cầu liên hệ mới vẫn là hai sự kiện riêng.

## SMTP

Mặc định `MAIL_ENABLED=false`: vẫn lưu email vào outbox, chưa gửi thư. Khi bật, worker xử lý cả những email đang chờ từ trước. Các cấu hình dùng biến môi trường:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `MAIL_ENABLED` | `false` | Bật worker gửi email |
| `MAIL_FROM` | `no-reply@localhost` | Địa chỉ gửi; dùng địa chỉ được nhà cung cấp SMTP cho phép |
| `SMTP_HOST` | `localhost` | Máy chủ SMTP |
| `SMTP_PORT` | `1025` | Cổng; thường 587 khi dùng STARTTLS |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | rỗng | Tài khoản SMTP |
| `SMTP_AUTH` | `false` | Bật xác thực SMTP |
| `SMTP_SSL` | `false` | TLS trực tiếp, ví dụ cổng 465 |
| `SMTP_STARTTLS` | `false` | Bật và yêu cầu STARTTLS |

Ví dụ cho SMTP có xác thực: đặt `MAIL_ENABLED=true`, `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_PORT=587`, cùng host, tài khoản, mật khẩu và địa chỉ gửi của bạn. Không đưa mật khẩu vào mã nguồn. Timeout kết nối/đọc/ghi SMTP đều là 5 giây. Nhà cung cấp dùng TLS trực tiếp cổng 465 thì đặt `SMTP_SSL=true`, `SMTP_STARTTLS=false`, `SMTP_PORT=465`.

Để thử hoàn toàn ở local, chạy một SMTP sink như Mailpit trên `127.0.0.1:1025`, giữ `SMTP_AUTH=false`, `SMTP_STARTTLS=false`, đặt `MAIL_FROM=no-reply@linkhub.local`, bật `MAIL_ENABLED=true`. SMTP sink phải giữ thư cục bộ, không chuyển tiếp ra ngoài. Bộ test tự động chỉ dùng bộ gửi giả; không cần SMTP và không gửi thư thật.

## Outbox và retry

Contact/booking, notification và mail outbox nằm trong cùng giao dịch database. Nếu giao dịch rollback, các bản ghi này đều rollback. Worker ở giao dịch riêng chỉ nhìn thấy dữ liệu đã commit. Lệnh SMTP không chạy trong giao dịch nghiệp vụ.

- Worker kiểm tra hàng đợi mỗi 5 giây, tối đa 10 email mỗi lượt.
- Nhận việc bằng conditional UPDATE và lease 120 giây, có token riêng; nhiều instance cùng cạnh tranh nhưng chỉ một instance nhận được lease hiện tại.
- Mỗi lần nhận việc tăng số lần thử. Lỗi được thử lại sau 30, 60, 120, 240… giây, tối đa 1 giờ giữa các lần.
- Sau 8 lần, chuyển `FAILED`. Lease hết hạn do process chết được thu hồi; token cũ không thể ghi đè kết quả worker mới.
- `last_error` chỉ lưu tên loại lỗi, không lưu nội dung phản hồi SMTP chứa thông tin nhạy cảm.

Theo dõi bằng truy vấn vận hành:

```sql
SELECT status, count(*) FROM mail_outbox GROUP BY status;
SELECT id, attempts, last_error, next_attempt_at
FROM mail_outbox WHERE status = 'FAILED' ORDER BY created_at;
```

Sau khi sửa cấu hình, người vận hành có thể chủ động đưa một email lỗi về hàng đợi:

```sql
UPDATE mail_outbox
SET status = 'PENDING', attempts = 0, next_attempt_at = current_timestamp,
    lease_token = NULL, lease_until = NULL, last_error = NULL
WHERE id = '<uuid-email-can-thu-lai>' AND status = 'FAILED';
```

**Bảo đảm gửi:** outbox chống tạo việc trùng và gửi theo cơ chế ít nhất một lần. SMTP không bảo đảm exactly-once: nếu SMTP đã nhận nhưng ứng dụng mất kết nối hoặc dừng trước khi ghi `SENT`, lần thử lại có thể gửi thêm một thư. `Message-ID` ổn định giúp nhận diện thư thử lại, không phải cam kết mọi nhà cung cấp sẽ loại trùng. Không đánh dấu `SENT` trước khi SMTP chấp nhận thư.

## SSE, reconnect và quyền truy cập

| API | Chức năng |
|---|---|
| GET `/api/me/notifications?page=0&unreadOnly=false` | 20 mục/trang, tổng mục và số chưa đọc |
| PATCH `/api/me/notifications/{id}/read` | Đánh dấu một mục đã đọc |
| PATCH `/api/me/notifications/read-all` | Đánh dấu toàn bộ đã đọc |
| GET `/api/me/notifications/stream` | SSE `notifications` và heartbeat |

Các API yêu cầu đăng nhập, lấy owner từ session; thao tác ghi yêu cầu CSRF. Không nhận owner do client gửi. Stream kiểm tra lại session mỗi lượt và đóng khi session mất hiệu lực, tự đóng sau 60 giây để client kết nối lại. Tối đa 5 stream/tài khoản và 1.000 stream/instance.

Server kiểm tra snapshot PostgreSQL mỗi 3 giây, gửi khi nội dung/số chưa đọc thay đổi; mỗi lần reconnect gửi lại snapshot mới nhất. Client vẫn tải trang/bộ lọc đang chọn qua REST; các thông báo chưa đọc cũ được giữ trong database, không bị mất khi offline. Không dùng cursor chỉ dựa trên ID tăng dần vì thứ tự commit có thể khác thứ tự cấp ID. Các instance cùng đọc database chung nên không cần Redis Pub/Sub để giữ tính đúng đắn; Redis giữ session và rate limit. Đây là polling database phía server kết hợp SSE phía trình duyệt, không phải WebSocket.

Reverse proxy cần tắt buffering cho stream (response có `X-Accel-Buffering: no`) và cho phép kết nối ít nhất 60 giây. Heartbeat mỗi 3 giây giữ kết nối. Nếu trình duyệt không hỗ trợ EventSource, dashboard chuyển sang tải định kỳ 15 giây. Khi mất session, client dừng stream và hiển thị yêu cầu đăng nhập lại.

## Kiểm thử

```powershell
.\mvnw.cmd test '-DskipTests=false' '-Dmaven.test.skip=false'
.\mvnw.cmd test '-DskipTests=false' '-Dmaven.test.skip=false' '-Dbooking.pg.url=jdbc:postgresql://localhost:5432/linkhub'
```

Bộ PostgreSQL tạo schema tạm riêng rồi xóa sau test. Các tình huống gồm rollback nghiệp vụ/outbox, sự kiện trùng, phân quyền, CSRF, đọc/lọc thông báo, snapshot SSE khi reconnect, phiên hết hạn, retry/backoff, lease hết hạn, ACK cũ, giới hạn lần thử, MIME UTF-8 và hai worker nhận cùng một email.

Tài liệu tham khảo: [Spring Boot Mail](https://docs.spring.io/spring-boot/reference/io/email.html), [Spring MVC SSE](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html).
