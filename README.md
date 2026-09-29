# LinkHub VN

MVP trang cá nhân cho freelancer: mỗi tài khoản có một đường dẫn `/{username}`, giới thiệu, link, dịch vụ và form nhận yêu cầu. Có giao diện tiếng Việt dùng trực tiếp trên điện thoại và máy tính.

## Những bước đã triển khai

1. **Hạ tầng:** giữ Spring Boot 4.1.1 / Java 21 của dự án; PostgreSQL, Redis, Docker Compose, Flyway và cấu hình qua biến môi trường.
2. **Tài khoản:** đăng ký, đăng nhập, đăng xuất; BCrypt; phiên đăng nhập Redis; CSRF; cookie HttpOnly; username/email không trùng và chuẩn hóa chữ thường.
3. **Profile:** chỉnh tên, giới thiệu, xuất bản/ẩn; chọn một trong 7 mẫu profile/landing page và xem trước trực tiếp; username cố định; profile chưa xuất bản trả 404.
4. **Link và dịch vụ:** thêm/sửa/xóa link HTTP(S), thứ tự link; dịch vụ có mô tả và giá VND; giới hạn 30 mục mỗi loại; chỉ chủ sở hữu được sửa.
5. **Yêu cầu liên hệ:** khách gửi tên/email/nội dung; hộp thư phân trang; trạng thái NEW/READ/ARCHIVED; Redis hạn chế spam.
6. **QR và thống kê:** QR PNG, tổng lượt xem và yêu cầu; sự kiện lượt xem/nhấp link ghi riêng không khóa profile, khách duy nhất từng ngày và nguồn truy cập, lọc bot và tôn trọng yêu cầu không theo dõi.
7. **Giao diện và kiểm thử:** HTML/CSS/JavaScript ES modules phục vụ cùng Spring Boot; kiểm thử HTTP qua MockMvc, JPA, Flyway và H2 PostgreSQL mode.

## Chạy trên máy

**Giai đoạn 4 đã bổ sung:** xác minh email, đặt lại mật khẩu, thu hồi phiên cũ, analytics nâng cấp, Actuator/Prometheus, correlation ID, Testcontainers PostgreSQL/Redis, CI và script backup/restore. Xem [cấu hình và hướng dẫn giai đoạn 4](docs/PHASE4.md), đặc biệt `TOKEN_MAIL_KEY`, SMTP và `OPS_TOKEN`.

Cần JDK 21 và Docker Desktop đang chạy Linux containers. Cổng 5432, 6379 và 8081 cần trống. Lần đầu Maven Wrapper tải Maven/dependency nên cần mạng.

```powershell
cd E:\Springtboot\linkhub
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Mở http://localhost:8081. Tạo tài khoản, đăng nhập, nhập giới thiệu, bật **Xuất bản profile công khai**, thêm link và dịch vụ. Mở `http://localhost:8081/<username>` trong cửa sổ riêng tư để thử gửi yêu cầu. Dashboard có nút **Làm mới** để tải hộp thư và thống kê.

```powershell
# Chạy test không cần Docker
.\mvnw.cmd test
# Build JAR
.\mvnw.cmd package
java -jar target/linkhub-0.0.1-SNAPSHOT.jar
# Dừng hạ tầng, giữ dữ liệu
docker compose stop
```

Linux/macOS dùng `./mvnw` thay cho `.\mvnw.cmd`.

## Cấu hình

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/linkhub` | PostgreSQL JDBC URL |
| `DB_USER` | `linkhub` | Tài khoản DB |
| `DB_PASSWORD` | `linkhub_dev` | Mật khẩu môi trường local |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | rỗng | Mật khẩu Redis nếu có |
| `REDIS_TIMEOUT` | `5s` | Thời gian tối đa chờ một lệnh Redis |
| `REDIS_CONNECT_TIMEOUT` | `3s` | Thời gian tối đa thiết lập kết nối Redis |
| `APP_BASE_URL` | `http://localhost:8081` | Domain gốc dùng tạo QR |
| `COOKIE_SECURE` | `false` | Đặt `true` khi chạy HTTPS |

Spring Boot và Docker Compose đọc file `.env` đặt cạnh `pom.xml`. Lần đầu sao chép `.env.example` thành `.env` nếu chưa có. Chạy backend với working directory `E:\Springtboot\linkhub` (IntelliJ: Run Configuration → Working directory). File được Spring đọc dưới dạng properties UTF-8: mỗi dòng `KEY=value`, không thêm `export` hoặc dấu nháy bao quanh giá trị; comment đặt trên dòng riêng bắt đầu bằng `#`. Với giá trị có ký tự backslash, phải escape theo cú pháp properties. File `.env` đã được Git bỏ qua và không được đóng gói vào JAR.

Đổi `APP_BASE_URL=https://domain-cua-ban.vn` trong `.env`, khởi động lại backend, rồi tải lại dashboard để tạo QR mới. File QR đã tải trước đó cần tải lại. Biến môi trường trong PowerShell/IDE và tham số dòng lệnh vẫn có độ ưu tiên cao hơn `.env`; xóa giá trị cũ ở đó nếu muốn dùng giá trị trong file. Thiếu `.env` thì ứng dụng dùng cấu hình mặc định. Cơ chế đọc file dùng [Spring Boot Config Data import](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.files.importing-extensionless).

Compose hiện chỉ bind DB/Redis vào loopback và phù hợp phát triển local. Khi đưa lên server: dùng secret riêng, TLS, `COOKIE_SECURE=true`, `APP_BASE_URL` đúng domain và bảo vệ mạng DB/Redis. Không bật profile `test` trên server.

Phiên Redis hết hạn sau 30 phút không hoạt động, cấu hình tại `RedisSessionConfig`. Redis cần hoạt động để đăng nhập và gửi yêu cầu. Bộ giới hạn dùng Lua INCR/EXPIRE nguyên tử: đăng ký 5 lần/giờ/IP, đăng nhập 20 lần/5 phút/IP, liên hệ 5 lần/10 phút/IP, lượt xem 60 lần/phút/IP. Đếm cả yêu cầu đăng nhập thành công. Dùng địa chỉ kết nối trực tiếp, không tin `X-Forwarded-For` do khách tự gửi; khi thêm reverse proxy cần cấu hình trusted proxy trước để tránh toàn bộ khách dùng chung hạn mức.

## Khi gặp Redis command timed out

Kiểm tra Redis trong thư mục dự án:

```powershell
docker compose ps
docker compose exec -T redis redis-cli PING
docker compose logs --tail 50 redis
```

`PING` cần trả `PONG`. Nếu Redis đang dừng, chạy `docker compose up -d redis`. Nếu Redis phản hồi nhưng thỉnh thoảng bị timeout, kiểm tra tài nguyên Docker Desktop và kết nối tới Redis. Timeout mặc định của lệnh là 5 giây, thời gian kết nối là 3 giây; có thể đặt `$env:REDIS_TIMEOUT='10s'` trước khi chạy ứng dụng để chẩn đoán mạng chậm. Tăng timeout không khắc phục Redis ngừng hoạt động.

Timeout trong API được trả về HTTP 503 cùng `Retry-After: 5`. Khi bộ giới hạn không phản hồi, ứng dụng không bỏ qua giới hạn và không ghi lượt xem/yêu cầu đó. Không tự retry lệnh INCR vì lệnh có thể đã thực thi ở Redis dù client nhận timeout. Session đăng nhập cũng phụ thuộc Redis; các test mock limiter không mô phỏng sự cố lưu session trong filter.

## Cấu trúc

```text
src/main/java/com/trimax/linkhub/
  api/          HTTP controllers, request/response DTO, xử lý lỗi
  config/       Spring Security và phiên Redis
  domain/       Account, SocialLink, ServiceOffering, ContactRequest, DailyVisit
  service/      Nghiệp vụ JPA trong transaction và Redis rate limiter
src/main/resources/
  db/migration/ Schema Flyway có version
  pages/        Trang dashboard và profile
  static/assets/ CSS và JavaScript
src/test/       Test HTTP/DB và cấu hình H2 độc lập
```

EntityManager được dùng trong service với truy vấn tham số hóa; entity không trả trực tiếp ra public API. PostgreSQL là nguồn dữ liệu bền vững. Redis lưu session và counter có TTL. Frontend cùng origin nên chưa cần CORS hay JWT; API client dùng cookie và CSRF token.

## API

Mọi request thay đổi dữ liệu cần CSRF. Gọi `GET /api/auth/csrf`, giữ cookie, lấy `headerName` và `token`, gửi header đó cùng cookie trong POST/PUT/PATCH/DELETE. Sau login lấy CSRF token mới vì session ID và token được thay đổi. Logout cũng yêu cầu CSRF.

| Method | Đường dẫn | Chức năng |
|---|---|---|
| GET | `/api/auth/csrf` | Lấy CSRF token |
| POST | `/api/auth/register` | `{email,password,username,displayName}` |
| POST | `/api/auth/login` | `{email,password}`; nhận session cookie |
| POST | `/api/auth/logout` | Hủy phiên |
| GET | `/api/me` | Tài khoản hiện tại |
| PUT | `/api/me/profile` | `{displayName,bio,published,template}` |
| GET / POST | `/api/me/links` | Danh sách / thêm `{title,url,sortOrder}` |
| PUT / DELETE | `/api/me/links/{id}` | Sửa / xóa link |
| GET / POST | `/api/me/services` | Danh sách / thêm `{title,description,price}` |
| PUT / DELETE | `/api/me/services/{id}` | Sửa / xóa dịch vụ |
| GET / POST | `/api/me/sections` | Danh sách / thêm mục landing page |
| PUT / DELETE | `/api/me/sections/{id}` | Sửa / xóa mục landing page |
| GET | `/api/me/contacts?page=0&size=20` | Hộp thư; size 1–100 |
| PATCH | `/api/me/contacts/{id}` | `{status:"READ"}` hoặc NEW/ARCHIVED |
| GET | `/api/me/analytics?days=30` | Thống kê; days 1–90 |
| GET | `/api/public/{username}` | Profile, links, services |
| POST | `/api/public/{username}/contacts` | `{name,email,message}` |
| POST | `/api/public/{username}/visits` | Ghi một lượt xem |
| GET | `/api/public/{username}/qr` | PNG 320×320 |

Tạo mới trả 201, xóa/logout/ghi lượt xem trả 204. Lỗi thường gặp: 400 dữ liệu sai, 401 chưa đăng nhập/sai mật khẩu, 403 thiếu CSRF, 404 tài nguyên không thuộc quyền hoặc không công khai, 409 trùng dữ liệu, 429 vượt giới hạn. Các API `/api/me/**` lấy chủ sở hữu từ session, không nhận owner ID từ client.

Ví dụ gửi request với PowerShell sau khi ứng dụng chạy:

```powershell
$csrf = Invoke-RestMethod http://localhost:8081/api/auth/csrf -SessionVariable client
$headers = @{ $csrf.headerName = $csrf.token }
$payload = @{ email='tri@example.com'; password='MatKhauDemo123'; username='tri'; displayName='Trí' } | ConvertTo-Json
Invoke-RestMethod http://localhost:8081/api/auth/register -Method Post -WebSession $client -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
```

## Mẫu giao diện profile

Nút **Chế độ tối** ở đầu trang chuyển sáng/tối cho trang đăng nhập, dashboard và profile công khai. Lần đầu dùng theo cài đặt hệ điều hành; sau khi chuyển, lựa chọn được nhớ trên trình duyệt và đồng bộ giữa các tab. Chế độ màu là sở thích của người xem, không đổi mẫu đã lưu của chủ profile. Các mẫu có bảng màu tối riêng; Midnight giữ thiết kế nền tối ở cả hai chế độ. Dark mode không cần thay đổi database.

Trong Dashboard → **Profile của bạn** → **Mẫu giao diện profile**, chọn mẫu rồi xem bản xem trước trên điện thoại. Bấm **Lưu profile** để áp dụng; nếu profile đang công khai, khách truy cập sẽ thấy mẫu mới khi tải lại trang. Có thể chọn và lưu mẫu khi profile còn là bản nháp.

| Giá trị API `template` | Mẫu | Cách hiển thị |
|---|---|---|
| `CLASSIC` | Classic | Xanh nhẹ, thẻ bo tròn, bố cục chính giữa |
| `MINIMAL` | Minimal | Trắng tối giản, link dạng danh sách |
| `STUDIO` | Studio | Tông đất ấm, tiêu đề serif; hai cột trên desktop, một cột trên mobile |
| `MIDNIGHT` | Midnight | Nền tối, thẻ tím, điểm nhấn sáng |
| `BUSINESS` | Business | Landing page xanh dương, giới thiệu nổi bật, lợi ích và quy trình theo cột |
| `PORTFOLIO` | Portfolio | Landing page tông olive, tiêu đề serif lớn, lưới dự án/sản phẩm |
| `CREATOR` | Creator | Landing page hồng tím, thẻ bo tròn, nội dung và hợp tác |

Flyway tự chạy các migration còn thiếu khi khởi động bản mới: V2 thêm mẫu profile, V3 thêm mẫu landing page và bảng `landing_blocks`. Dữ liệu/mẫu đã chọn được giữ nguyên; tài khoản từ trước V2 nhận `CLASSIC`. Không sửa migration đã chạy. API `/api/me` và `/api/public/{username}` trả `template`; `PUT /api/me/profile` chấp nhận đúng 7 giá trị trên, trả 400 nếu mẫu không hợp lệ. Client cũ không gửi `template` hoặc gửi `null` sẽ giữ lựa chọn đang lưu. Preview dùng dữ liệu trong form và nội dung hiện có, không tự xuất bản và không tăng lượt xem.

## Kết hợp profile và landing page

Hiệu ứng **khối trượt lên và hiện dần khi cuộn** hoạt động trên cả 7 mẫu, bao gồm khung xem trước trong dashboard. Animation chạy 850 ms, các mục trong cùng hàng xuất hiện lệch nhau 120 ms. Bấm **Xem lại hiệu ứng** trên trang công khai hoặc dashboard để về đầu và phát lại. Thiết bị bật Reduce motion mặc định hiển thị nội dung ngay; nút **Bật và xem hiệu ứng** cho phép người xem chủ động bật chuyển động cho trang hiện tại.

Business, Portfolio và Creator có hiệu ứng xuất hiện khi cuộn, phần giới thiệu chuyển động nhẹ, thẻ nổi khi hover, FAQ mở mượt, menu bám khi cuộn và đánh dấu mục đang đọc. Có thanh tiến độ đọc cùng nút lên đầu trang. Hiệu ứng tự giảm khi thiết bị bật **Reduce motion**; nội dung không bị ẩn nếu trình duyệt không hỗ trợ hiệu ứng cuộn. Xem hiệu ứng đầy đủ tại trang profile công khai sau khi lưu mẫu.

1. Chọn **Business**, **Portfolio** hoặc **Creator** rồi bấm **Lưu profile**.
2. Trong **Nội dung landing page**, thêm giới thiệu (`ABOUT`), điểm nổi bật (`FEATURE`), bước quy trình (`PROCESS`), dự án/sản phẩm (`PROJECT`) và FAQ (`FAQ`).
3. Nhập tiêu đề, nội dung, link xem thêm tùy chọn và thứ tự. Bấm **Lưu mục** để áp dụng. Mỗi tài khoản có tối đa 30 mục.
4. Mục có thể **Sửa / Ẩn / Hiện / Xóa**. Mục ẩn vẫn được giữ trong dashboard nhưng không được trả về public API. Nội dung được nhóm cố định theo loại; thứ tự sắp xếp áp dụng bên trong từng nhóm.
5. Trang công khai có điều hướng tới các nhóm đang có nội dung, phần giới thiệu lớn, nút **Trao đổi nhu cầu**, dịch vụ/giá và form liên hệ. FAQ bấm để mở câu trả lời. Màn hình nhỏ tự chuyển về một cột.

Mẫu mới không tự tạo dự án hoặc nội dung minh họa thay người dùng. Nội dung đã nhập vẫn có thể hiển thị với 4 mẫu profile cũ và được giữ lại khi chuyển mẫu. Lưu mục trong một profile đang công khai có hiệu lực ngay; muốn soạn riêng thì bỏ chọn hiển thị mục hoặc ẩn toàn bộ profile.

Body POST/PUT `/api/me/sections`:

```json
{
  "type": "PROJECT",
  "title": "Tên dự án của bạn",
  "body": "Mô tả bài toán, vai trò và kết quả công việc.",
  "url": "https://example.com/project",
  "sortOrder": 0,
  "enabled": true
}
```

`url` để `""` nếu không có link, chỉ cho phép HTTP/HTTPS. Public API trả thêm `sections` chứa các mục được bật; mọi thao tác quản lý vẫn yêu cầu session, CSRF và quyền sở hữu.

## Giới hạn MVP và bước kế tiếp

- Tổng lượt xem vẫn là **page views**. Giai đoạn 4 bổ sung khách duy nhất từng ngày, referrer và click link; lọc bot theo User-Agent nhưng vẫn có thể bị giả mạo. Ngày tính theo Asia/Ho_Chi_Minh. Không tính tải QR hoặc GET API thành lượt xem. Xem [chính sách dữ liệu analytics](docs/PHASE4.md).
- Hộp thư và lịch hẹn tự cập nhật qua thông báo SSE; email cần cấu hình SMTP. Chưa có ảnh đại diện/upload, đổi username, reset mật khẩu, xác minh email hay thanh toán.
- Test mặc định dùng H2 và mock Redis limiter. Test này kiểm tra migration, auth/CSRF, quyền sở hữu, QR, liên hệ và analytics; không thay thế kiểm thử Redis session hoặc đồng thời trên PostgreSQL thật.

Lộ trình mở rộng chi tiết: [docs/ROADMAP.md](docs/ROADMAP.md).

## Giai đoạn 2 — Đặt lịch và lịch rảnh

Khởi động lại Spring Boot để Flyway áp dụng V4/V5 và nạp các API mới. PostgreSQL cần extension `btree_gist`; tài khoản migration cần quyền tạo extension, hoặc DBA cài sẵn extension. Không sửa các migration đã áp dụng.

1. Trong **Dịch vụ**, đặt thời lượng 15–480 phút (dịch vụ cũ mặc định 60 phút).
2. Trong **Đặt lịch & lịch rảnh**, chọn múi giờ IANA, thêm khung giờ theo tuần và ngày nghỉ/ngoại lệ, rồi lưu. Ngoại lệ thay thế lịch tuần trong ngày đó; giờ cuối ngày nhập `24:00`. Để danh sách khung giờ rỗng để ngừng nhận lịch mới.
3. Trên profile public, khách chọn dịch vụ, ngày và giờ trống, điền liên hệ rồi gửi. Chỉ cho đặt trong 90 ngày tới, các giờ bắt đầu cách nhau 15 phút tính từ đầu khung rảnh. Giờ hiển thị theo chủ profile, kèm offset để phân biệt giờ lặp lại khi đổi DST.
4. Lịch `PENDING` giữ chỗ ngay. Chủ profile xác nhận (`CONFIRMED`) hoặc hủy (`CANCELLED`); chỉ hoàn tất (`COMPLETED`) lịch đã xác nhận sau giờ kết thúc. Không mở lại lịch đã hủy/hoàn tất. Thay đổi lịch rảnh không hủy lịch đã nhận. Lịch lưu snapshot tên dịch vụ, múi giờ và thời gian, nên xóa/sửa dịch vụ không làm mất lịch cũ.
5. Studio giới hạn sticky của phần thông tin cá nhân trong vùng nội dung phía trên; phần đặt lịch bên dưới chiếm toàn chiều rộng.

PostgreSQL là lớp bảo vệ cuối cùng, không dựa vào Redis hay thao tác kiểm tra rồi chèn:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE bookings ADD CONSTRAINT bookings_no_overlap
EXCLUDE USING gist (
  owner_id WITH =,
  tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status IN ('PENDING', 'CONFIRMED'));
```

`[)` cho phép lịch 09:00–10:00 và 10:00–11:00 liền nhau. Mọi dịch vụ cùng chủ profile chia sẻ một lịch; hai chủ profile vẫn đặt cùng giờ được. `starts_at`/`ends_at` dùng `timestamptz`, API dùng UTC Instant và server tự tính giờ kết thúc từ thời lượng dịch vụ. Khi tranh chấp, SQLSTATE `23P01` trả HTTP 409 / `BOOKING_OVERLAP`; deadlock được retry tối đa 3 lần qua giao dịch mới. Khóa chia sẻ trên chủ profile giữ cấu hình lịch/dịch vụ ổn định trong lúc đặt, không khóa độc quyền giữa các lượt đặt. Lưu lịch rảnh và sửa/xóa dịch vụ dùng khóa ghi tương ứng.

| API | Chức năng |
|---|---|
| GET / PUT `/api/me/availability` | Lịch tuần, timezone và ngoại lệ của chủ profile |
| GET `/api/public/{username}/slots?serviceId={uuid}&date=2026-10-01` | Các khoảng trống, không lộ thông tin khách |
| POST `/api/public/{username}/bookings` | Body: `serviceId`, `startsAt` (ISO UTC), `name`, `email`, `message` |
| GET `/api/me/bookings?page=0` | 20 lịch mỗi trang, mới nhất trước |
| PATCH `/api/me/bookings/{id}` | Body: `{"status":"CONFIRMED"}` / `CANCELLED` / `COMPLETED` |

Các thao tác ghi yêu cầu CSRF; endpoint quản trị yêu cầu đăng nhập và quyền sở hữu. Booking public và truy vấn slot có rate limit Redis. Đã có email/thông báo SSE ở giai đoạn 3. Chưa có tự hết hạn lịch chờ; đây là yêu cầu đặt lịch chờ chủ profile xử lý, không phải giỏ hàng giữ chỗ tạm thời.

## Giai đoạn 3 — Email và thông báo

Dashboard có khối **Lịch hẹn sắp tới** ngay dưới lời chào. Lịch đã xác nhận được nhắc cho chủ profile và gửi email cho khách trước 24 giờ/1 giờ; không gửi nhắc cho lịch chưa xác nhận, đã hủy hoặc quá giờ. Khởi động lại để áp dụng migration **V7**. Chi tiết chính sách nhắc và cấu hình ở [hướng dẫn thông báo](docs/NOTIFICATIONS.md#nhắc-lịch-hẹn).

Đã có outbox lưu cùng giao dịch với liên hệ/đặt lịch, worker SMTP với retry/backoff và lease, thông báo bền vững, SSE, số chưa đọc và đánh dấu đã đọc. Khởi động lại để chạy migration V6. Email mặc định tắt; cấu hình SMTP rồi đặt `MAIL_ENABLED=true` để gửi các thư đang chờ. Xem [hướng dẫn cấu hình và vận hành](docs/NOTIFICATIONS.md).

Kiểm thử thông thường: `.\mvnw.cmd test`. H2 chỉ kiểm tra API và schema portable, không mô phỏng exclusion constraint. Chạy kiểm thử database thật bằng:

```powershell
.\mvnw.cmd test '-Dbooking.pg.url=jdbc:postgresql://localhost:5432/linkhub'
```

Test tạo/xóa schema `booking_test_*` riêng, không thay đổi dữ liệu profile hiện có. Có thể cấu hình `booking.pg.user` và `booking.pg.password` cho database test. Bộ kiểm thử gồm hai giao dịch cùng thấy giờ trống trước khi chèn, overlap, ranh giới `[)`, hủy/đặt lại, cập nhật gây overlap, DST, API/CSRF và quyền sở hữu. Tham khảo [tài liệu range và exclusion của PostgreSQL](https://www.postgresql.org/docs/current/rangetypes.html#RANGETYPES-CONSTRAINT).

Tài liệu kỹ thuật đã đối chiếu: [Spring Session với Redis](https://docs.spring.io/spring-session/reference/guides/boot-redis.html), [lưu SecurityContext khi đăng nhập thủ công](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/persistence.html).
