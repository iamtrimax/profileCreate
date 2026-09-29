# Giai đoạn 4: analytics, bảo mật và vận hành

## Chạy ứng dụng

Khởi động lại backend để Flyway áp dụng V8. Không cần xóa database. Lượt xem cũ vẫn nằm trong `daily_visits`; tổng và biểu đồ cộng cả sự kiện mới. Chạy từ thư mục `linkhub`:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

## Xác minh email và quên mật khẩu

Dashboard có mục **Bảo mật tài khoản → Gửi email xác minh**. Trang đăng nhập có **Quên mật khẩu?**. Xác minh có hạn 24 giờ; reset có hạn 30 phút. Người dùng phải bấm xác nhận trên trang, GET không sử dụng token. Token ngẫu nhiên 256 bit, database lưu SHA-256, sử dụng một lần dưới khóa account trong transaction; yêu cầu lại vô hiệu hóa liên kết cũ. Đổi mật khẩu tăng phiên bản bảo mật, HTTP cũ bị từ chối và SSE đóng ở nhịp kiểm tra tiếp theo. Chưa bắt buộc xác minh để dùng profile/booking.

Email đi qua outbox của giai đoạn 3. Cần `MAIL_ENABLED=true` và cấu hình SMTP. SMTP sink chỉ giữ email để xem thử, không chuyển thư ra hộp thư thật. `APP_BASE_URL` phải là URL mà người nhận truy cập được; production dùng HTTPS và `COOKIE_SECURE=true`.

Nội dung email chứa token được mã hóa AES-256-GCM khi lưu trong outbox. Đặt `TOKEN_MAIL_KEY` là Base64 của 32 byte ngẫu nhiên, giữ cố định giữa các lần restart và dùng chung cho mọi instance. Lưu trong secret manager, không commit. Có thể tạo trong PowerShell:

```powershell
$keyBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$env:TOKEN_MAIL_KEY = [Convert]::ToBase64String($keyBytes)
$rng.Dispose()
```

Nếu không đặt, ứng dụng dùng khóa tạm cho phát triển; restart sẽ không giải mã được email bảo mật còn chờ. Khi đó yêu cầu liên kết mới. Sao lưu khóa riêng khỏi database. Không đổi khóa khi còn email bảo mật chờ gửi. Hệ thống có giới hạn yêu cầu theo IP/email, CSRF và thông báo chung cho email không tồn tại.

## Analytics và quyền riêng tư

`analytics_events` là luồng sự kiện append-only trong PostgreSQL, chưa dùng Kafka. Ghi VIEW/CLICK không lấy khóa ghi account; dashboard truy vấn sự kiện và cộng lượt xem cũ. Theo dõi click trên các link mạng xã hội, không tính preview/QR/API đọc là lượt xem.

- Lọc User-Agent bot/crawler/spider/headless/curl/wget/preview: mang tính gần đúng, không phải chống gian lận.
- Tôn trọng `DNT: 1` và `Sec-GPC: 1`: không ghi sự kiện hay tạo cookie analytics.
- Cookie `LH_VISITOR` ngẫu nhiên, HttpOnly, SameSite=Lax, hạn 24 giờ. Database chỉ lưu hash theo profile/ngày/visitor, không lưu IP hay User-Agent. Không nhận diện một người qua thiết bị hoặc trình duyệt khác.
- “Tổng khách duy nhất từng ngày” là tổng số trình duyệt khác nhau từng ngày, không phải số người duy nhất cả tháng. Chỉ áp dụng cho dữ liệu mới.
- Referrer chỉ giữ hostname, loại path/query và nguồn cùng host. Có thể trống hoặc bị giả mạo bởi client.
- Sau 90 ngày, job xóa hash visitor và hostname; vẫn giữ lượt xem/click và tiêu đề link để thống kê. API chi tiết chỉ cho 1–90 ngày. Bản backup có vòng đời riêng, đề xuất 30 ngày.

## Health, metric và log

**Giao diện giám sát:** mở `/ops/metrics` (hoặc `/assets/metrics.html`), nhập `OPS_TOKEN` rồi bấm **Kết nối**. Trang hiển thị sức khỏe hệ thống, bộ nhớ JVM/đồ thị 30 mẫu, CPU, uptime, HTTP, kết nối database, hàng đợi email và danh sách metric có bộ lọc nhãn. Tự cập nhật mỗi 15 giây, có thể tắt hoặc làm mới thủ công. Token chỉ nằm trong bộ nhớ trang, không lưu localStorage/sessionStorage và không đưa vào URL; tải lại/ngắt kết nối sẽ xóa token. Đây là giao diện vận hành, tài khoản profile thông thường không tự có quyền xem metric. Số liệu chưa có hiển thị `—`; bộ đếm email cập nhật trên server mỗi 30 giây. Đồ thị chỉ ghi từ lúc kết nối, chưa thay thế hệ thống lưu lịch sử Prometheus/Grafana.

`GET /actuator/health` trả trạng thái chung, không lộ chi tiết hạ tầng. Có health/liveness và health/readiness. Health tổng kiểm tra database/Redis; SMTP không nằm trong health vì mail có thể tắt và dùng outbox.

Đặt `OPS_TOKEN` ngẫu nhiên ít nhất 32 ký tự. `GET /actuator/prometheus`, `/actuator/metrics` và `/actuator/metrics/{name}` yêu cầu `Authorization: Bearer <OPS_TOKEN>`; tài khoản người dùng thông thường không có quyền. Không cấu hình token thì không truy cập được metrics. Không công khai token trong JavaScript. Dùng HTTPS và hạn chế đường dẫn actuator tại reverse proxy khi triển khai.

Metric HTTP/JVM/connection pool do Actuator cung cấp; `linkhub_mail_jobs{status=...}` cập nhật mỗi 30 giây. Theo dõi FAILED tăng, PENDING kéo dài, 5xx và độ trễ HTTP. Mỗi request có response `X-Request-ID`, log có cùng requestId; header đầu vào chỉ chấp nhận 1–64 chữ/số/gạch ngang. Log không chứa body, query, email hay token. SSE có log thời gian mở kết nối, không phải thời gian sống toàn bộ stream.

## Kiểm thử và CI

```powershell
# Kiểm thử nhanh H2/API; PostgreSQL/Redis container được bỏ qua
.\mvnw.cmd -B test
# Đầy đủ; cần Docker hoạt động, có thể tải image ở lần đầu
.\mvnw.cmd -B verify '-Dcontainers=true'
```

Testcontainers dùng database/container riêng và tự dọn; các test PostgreSQL kiểm tra exclusion, cạnh tranh đặt lịch, outbox và nhắc lịch. Test HTTP qua server thật kiểm tra Redis session, rate limiter, health và bảo vệ metrics. Workflow `.github/workflows/ci.yml` chạy đầy đủ trên push/pull_request khi thư mục này là root repository.

Nếu IDE cũng biên dịch vào target, thêm `'-Dlinkhub.build-directory=target/phase4-validation'` để cô lập đầu ra Maven. Bộ kiểm thử bảo mật kiểm tra token sai mục đích/hết hạn/dùng lại, reset thu hồi phiên, CSRF, lọc bot/opt-out và nguồn truy cập.

## Sao lưu và kiểm tra khôi phục

Các script dành cho PostgreSQL trong `compose.yaml` (user/database `linkhub`). Backup dùng `pg_dump -Fc`, copy file nhị phân trực tiếp và in SHA-256, tránh làm hỏng dữ liệu qua chuyển mã PowerShell.

```powershell
.\scripts\backup-postgres.ps1
.\scripts\restore-postgres.ps1 -Backup .\backups\TEN_FILE.dump -Database linkhub_restore_check
```

Restore chỉ tạo database mới có tiền tố `linkhub_restore_`, từ chối tên đã tồn tại; không xóa hay ghi đè database đang chạy. `pg_restore` chạy một transaction rồi kiểm tra số account/booking. File dump là đầu vào tin cậy của quản trị viên: không restore dump không rõ nguồn gốc.

Đề xuất backup hàng ngày, giữ 30 ngày trên kho mã hóa ngoài máy chủ; thử restore định kỳ. Trước chuyển ứng dụng sang bản khôi phục, kiểm tra migration, số bản ghi, constraint exclusion, đăng nhập và booking bằng môi trường riêng với `MAIL_ENABLED=false`, `BOOKING_REMINDERS_ENABLED=false` và Redis riêng. Chỉ đổi `DB_URL` trong cửa sổ bảo trì sau khi đã kiểm tra. Script không tự chuyển database ứng dụng. Dump có dữ liệu khách hàng, không commit vào Git.

Tham khảo: [Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Testcontainers PostgreSQL](https://java.testcontainers.org/modules/databases/postgres/), [OWASP reset password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html).
