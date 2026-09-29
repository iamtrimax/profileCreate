# Lộ trình LinkHub VN

## Giai đoạn 1 — MVP đã có trong mã nguồn

Thứ tự đọc/triển khai: cấu hình và `V1__initial_schema.sql` → domain → SecurityConfig/RedisSessionConfig → LinkHubService → ApiController → giao diện → MvpIntegrationTests.

Tiêu chí dùng thử: đăng ký hai tài khoản, xuất bản một profile, thêm link/dịch vụ, truy cập profile ở cửa sổ riêng tư, quét QR, gửi yêu cầu, mở hộp thư và kiểm tra lượt xem. Tài khoản thứ hai không được đọc hoặc thay đổi dữ liệu quản trị của tài khoản thứ nhất.

## Giai đoạn 2 — Booking và lịch rảnh đã có trong mã nguồn

Thêm availability rules (thứ trong tuần, khung giờ, timezone), ngày nghỉ/ngoại lệ, thời lượng dịch vụ, booking và trạng thái PENDING/CONFIRMED/CANCELLED/COMPLETED. Lưu thời điểm booking theo UTC, hiển thị theo timezone của chủ profile. Khách chọn dịch vụ và khung giờ rồi gửi yêu cầu; chủ profile xác nhận/hủy.

Chống đặt trùng ở PostgreSQL bằng `btree_gist` và exclusion trên `tstzrange(starts_at, ends_at, '[)')`, theo owner, cho PENDING/CONFIRMED. Redis chỉ dùng session/rate limit; slot đọc từ database. Có kiểm thử PostgreSQL thật cho đồng thời, ranh giới khung giờ, hủy/đặt lại và DST. API retry deadlock trong giao dịch mới, trả 409 khi lịch không còn trống. Xem README để chạy và cấu hình lịch.

## Giai đoạn 3 — Email và realtime đã có trong mã nguồn

Spring Mail/SMTP và outbox lưu cùng transaction với contact/booking; worker nhận việc bằng lease, retry/backoff và khóa sự kiện duy nhất. Chỉ gửi email sau commit. Dashboard dùng SSE, lưu trạng thái đã đọc và phân trang trong PostgreSQL. Mỗi instance đọc snapshot database mỗi 3 giây, nên reconnect và nhiều instance không phụ thuộc broker. Redis tiếp tục phục vụ session/rate limit; Pub/Sub có thể bổ sung sau như tín hiệu đánh thức để giảm polling, không phải nguồn dữ liệu thông báo. Xem `docs/NOTIFICATIONS.md` để cấu hình SMTP, giới hạn bảo đảm gửi và vận hành.

## Giai đoạn 4 — Analytics và vận hành đã có trong mã nguồn

Đã thêm event stream PostgreSQL cho VIEW/CLICK, lọc bot, khách duy nhất từng ngày, hostname nguồn truy cập và chính sách ẩn danh sau 90 ngày. Không khóa ghi profile khi ghi analytics. Có Testcontainers PostgreSQL/Redis, CI, Actuator/Prometheus bảo vệ bằng token vận hành, log correlation ID và script backup/restore sang database mới. Xác minh email và reset mật khẩu dùng token băm một lần có hạn, email outbox mã hóa và thu hồi phiên sau reset. Xem [docs/PHASE4.md](PHASE4.md).

## Giai đoạn 5 — Pro, domain và thanh toán

Thêm gói/entitlement và giới hạn theo gói, xác minh domain qua DNS, TLS, review gắn với booking đã hoàn thành. Tích hợp thanh toán qua nhà cung cấp, xác minh chữ ký webhook và idempotency, lưu trạng thái giao dịch độc lập với booking. Không coi redirect thành công từ trình duyệt là bằng chứng thanh toán.
