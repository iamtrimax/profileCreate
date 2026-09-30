# Audit log và Ops Metrics

Khởi động lại backend để Flyway chạy `V9__audit_logs.sql`. Mở `/ops/metrics`, nhập `OPS_TOKEN` (ít nhất 32 ký tự, cấu hình trong `.env`), xem khối **Nhật ký hoạt động**. Token chỉ giữ trong bộ nhớ trang. Tài khoản người dùng thông thường không có quyền đọc audit log.

Giao diện có tổng số sự kiện, thành công, bị từ chối và thất bại theo bộ lọc; lọc thời gian, hành động, UUID người thực hiện, kết quả và Request ID. Phân trang 20 dòng bằng cursor; chỉ trang đầu tự cập nhật theo tùy chọn 15 giây của Metrics. Thời gian hiển thị theo thiết bị, lưu database theo UTC.

## Phạm vi ghi nhận

- Đăng ký, đăng nhập/đăng xuất, yêu cầu và xác nhận đặt lại mật khẩu/xác minh email.
- Cập nhật profile, link, dịch vụ, mục nội dung, lịch rảnh; gửi liên hệ/đặt lịch và đổi trạng thái.
- Yêu cầu bị từ chối khi đọc API audit (không ghi các lần đọc thành công để tránh tự sinh log khi polling).

Mỗi sự kiện có thời điểm, UUID người thực hiện nếu phiên hợp lệ, hành động, loại/ID đối tượng có sẵn trên đường dẫn, kết quả HTTP, Request ID và thời gian xử lý. Đăng ký, khôi phục mật khẩu và thao tác khách chưa đăng nhập được ghi ẩn danh. Khi tạo mới, ID đối tượng chưa có trên đường dẫn nên có thể trống; liên hệ/booking public dùng username chủ profile làm tham chiếu. Không đọc request/response body để lấy ID.

Đây là nhật ký kết quả **HTTP request**, không phải lịch sử thay đổi từng trường. Không ghi mật khẩu, token, email, nội dung khách gửi, cookie, IP hoặc User-Agent. Không ghi lượt xem/click analytics, thao tác đọc thông thường, đánh dấu thông báo đã đọc, công việc nền hay thay đổi database trực tiếp. Không có dữ liệu hồi tố trước khi triển khai.

Kết quả: HTTP dưới 400 = `SUCCESS`; 401/403/429 = `DENIED`; các lỗi khác = `FAILURE`. Lỗi kiểm tra dữ liệu hoặc xung đột lịch cũng được ghi nhận. Request ID dùng đối chiếu với log máy chủ, không phải bằng chứng định danh (client có thể cung cấp giá trị hợp lệ).

## API chỉ đọc

`GET /api/ops/audit-logs`, header `Authorization: Bearer <OPS_TOKEN>`, phản hồi `Cache-Control: no-store`.

Tham số tùy chọn: `from`, `to` (ISO Instant), `action`, `actorId` (UUID), `outcome`, `requestId`, `before` (cursor ID), `size` (1–100, mặc định 20). Mặc định 7 ngày gần nhất, tối đa 90 ngày mỗi truy vấn. Bộ lọc khớp chính xác; SQL sử dụng tham số bind.

Phản hồi có `items`, `nextCursor` (null nếu hết) và `summary` gồm `total`, `success`, `denied`, `failure`. Summary tính toàn bộ bộ lọc, không chỉ trang hiện tại. Không cung cấp API sửa/xóa audit. Cursor theo ID giảm dần; sự kiện đồng thời mới xuất hiện khi quay về trang đầu/làm mới, không phải bản chụp cố định.

## Lưu trữ và sự cố

Trong `.env`, đặt `AUDIT_RETENTION_DAYS=180` rồi khởi động lại nếu muốn đổi thời gian lưu (1–3650 ngày). Worker chạy mỗi giờ, bắt đầu sau 5 phút, xóa tối đa 5.000 dòng hết hạn mỗi lần; khi backlog lớn có thể tồn tại dòng quá hạn lâu hơn. Bao gồm bảng audit trong backup database hiện có.

Ghi audit trong giao dịch riêng sau khi request hoàn tất để giữ được sự kiện lỗi dù giao dịch nghiệp vụ rollback. Nếu không ghi được audit, phản hồi nghiệp vụ vẫn giữ nguyên; tăng metric `linkhub.audit.write.failures` (xem trong **Khám phá metric**) và ghi lỗi kèm Request ID. Có thể mất sự kiện khi database lỗi hoặc tiến trình dừng giữa nghiệp vụ và ghi audit; không có retry bền vững hay cam kết atomic cùng nghiệp vụ. Bảng không chống sửa đổi bởi quản trị viên database; đây không phải hệ thống lưu trữ chứng cứ bất biến.

Kiểm thử: `./mvnw.cmd test -Dtest=AuditIntegrationTests` kiểm tra phân quyền, CSRF, actor đăng nhập/đăng xuất, không lưu nội dung nhạy cảm, bộ lọc, phân trang, rollback, retention và lỗi lưu trữ.
