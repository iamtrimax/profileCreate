# Seed tuyển dụng để test LinkHub

## Chạy seed

Từ thư mục dự án linkhub, dùng cấu hình PostgreSQL trong .env hiện tại. Nếu dịch vụ chưa chạy, mở Docker Desktop rồi chạy:

    docker compose up -d postgres redis

Sau khi PostgreSQL sẵn sàng, chạy seed:

    powershell -ExecutionPolicy Bypass -File .\scripts\seed-recruitment.ps1

Script khởi động ứng dụng ở cổng ngẫu nhiên, chạy migration và seed rồi tự thoát. Không cần dừng ứng dụng đang chạy. PostgreSQL cần hoạt động; Redis cần hoạt động khi đăng nhập và dùng Match/chat trong ứng dụng chính.

Nếu muốn seed rồi tiếp tục chạy ứng dụng trên cổng trong .env (cần cổng đó đang trống):

    powershell -ExecutionPolicy Bypass -File .\scripts\seed-recruitment.ps1 -KeepRunning

Lệnh Maven tương đương cho seed một lần:

    .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=seed-recruitment" "-Dspring-boot.run.arguments=--server.port=0 --linkhub.seed.exit=true"

Script mặc định cho phép Maven tải dependency còn thiếu. Dùng thêm -Offline khi toàn bộ dependency/plugin đã có trong cache.

Profile seed không hoạt động khi chạy ứng dụng thông thường, không được kết hợp với profile prod/production, và tắt gửi mail/nhắc lịch trong tiến trình seed. Các địa chỉ @linkhub.test là tài khoản demo, không nhận email thật.

## Tài khoản

**Mật khẩu chung: LinkHubDemo!2026**. Đăng nhập bằng email, không phải username.

| Email | Vai trò / kịch bản |
| --- | --- |
| demo_recruiter@linkhub.test | Nhà tuyển dụng LinkHub Tech: 5 JD Java/COBOL |
| demo_creative@linkhub.test | Nhà tuyển dụng Studio Creative: 1 JD UX/UI |
| demo_lan@linkhub.test | Java phù hợp, 3 năm kinh nghiệm |
| demo_minh@linkhub.test | Java phù hợp, có Docker và AWS ưu tiên |
| demo_linh@linkhub.test | Java phù hợp, hình thức Linh hoạt; cũng đạt JD Remote |
| demo_an@linkhub.test | Đã match, có chat và 3 lịch phỏng vấn mẫu |
| demo_bao@linkhub.test | Lời mời đang chờ đồng ý |
| demo_chi@linkhub.test | Đã từ chối lời mời trong JD pipeline |
| demo_dung@linkhub.test | Đã Next trong JD pipeline |
| demo_junior@linkhub.test | Có thể được giới thiệu với điểm kinh nghiệm thấp hơn: 1 năm |
| demo_missing@linkhub.test | Có thể được giới thiệu; hiển thị thiếu Spring Boot |
| demo_hanoi@linkhub.test | Bị loại vì địa điểm Hà Nội khác JD Hybrid tại Hồ Chí Minh |
| demo_salary@linkhub.test | Có thể được giới thiệu; lương 45–55 triệu cần thương lượng với JD 25–35 triệu |
| demo_hidden@linkhub.test | Đã tắt tìm việc, không xuất hiện trong Match |
| demo_remote@linkhub.test | Java Remote tại Đà Nẵng, không đạt JD Hybrid |
| demo_designer@linkhub.test | UX/UI Designer, phù hợp JD Studio Creative |

Tổng cộng 16 tài khoản, 14 hồ sơ ứng viên, 6 JD. Mỗi tài khoản có profile công khai, link portfolio mẫu, dịch vụ trao đổi 30 phút miễn phí và lịch rảnh thứ Hai–thứ Sáu, 09:00–17:00 theo giờ Việt Nam để thử cả các tính năng cũ.

## Kịch bản thử

1. **Match/Next:** đăng nhập demo_recruiter, mở Tuyển dụng → Java Backend Developer · Match/Next. Trên bộ dữ liệu mới, khi tắt match AI có 10 ứng viên được giới thiệu theo điểm: Lan, Minh, Linh, An, Bảo, Chi, Dũng, Dương, Hà và Nam. Dương thiếu kinh nghiệm, Hà thiếu Spring Boot và Nam cần thương lượng lương; thẻ hiển thị các khoảng thiếu. Next lần lượt sẽ không lặp người cho JD này; sau khi xem hết sẽ báo không còn ứng viên. Tải lại rồi bấm Match giữ người chưa xử lý.
2. **Lời mời:** đăng nhập demo_bao, mở Lời mời & match. Có lời mời Pipeline mẫu chờ đồng ý. Khi đồng ý, email và chat được mở; khi từ chối, trạng thái cập nhật cho nhà tuyển dụng. Dùng hai trình duyệt hoặc cửa sổ ẩn danh để thử hai phía cùng lúc.
3. **Chat và lịch:** đăng nhập demo_an hoặc demo_recruiter, mở match đã đồng ý trong Pipeline mẫu. Có 2 tin nhắn và 3 lịch: chờ xác nhận, đã xác nhận, đã hủy. Lịch lần đầu seed đặt vào ngày +3 lúc 10:00, +5 lúc 14:00 và +7 lúc 10:00, dài 30 phút, theo giờ Việt Nam. Xác nhận/từ chối lịch bằng tài khoản An; cả hai bên có thể hủy. Thử đề xuất khung giờ trùng lịch đang giữ chỗ để thấy lỗi.
4. **Lọc hình thức làm việc:** JD Java Remote chỉ match Thảo và Linh trên bộ seed mới; vị trí địa lý không chặn ứng viên Remote.
5. **Nhóm ngành khác:** demo_creative → JD UX/UI Designer chỉ match Phúc trong bộ seed.
6. **Hết ứng viên:** JD COBOL không có ứng viên phù hợp.
7. **Đóng bài:** JD Java Developer · Bài đã đóng không xuất hiện trên feed; mở lại để thử tuyển tiếp.
8. **Thông báo:** tài khoản Bảo, An và nhà tuyển dụng có thông báo mẫu trong chuông. Các thao tác sau đó tạo thông báo thật trong ứng dụng.

Các kết quả trên giả định chưa sửa dữ liệu demo và chưa thêm ứng viên khác có tiêu chí phù hợp trong database.

## Chạy lại

ID demo cố định, toàn bộ seed nằm trong một transaction. Chạy lại không tạo bản trùng, không đặt lại mật khẩu, không mở lại JD đã đóng, không bật lại tìm việc hoặc đổi trạng thái match/lịch bạn đã thao tác. Nếu username/email demo thuộc một tài khoản khác, seed báo lỗi và rollback; không ghi đè tài khoản đó.

Ngày phỏng vấn được tính khi lịch mẫu được tạo lần đầu. Chạy lại giữ nguyên lịch cũ, kể cả khi đã qua, để không thay đổi kết quả test của bạn. Bản seed này không có lệnh xóa/reset dữ liệu.

## Kiểm thử bộ seed

    .\mvnw.cmd -o "-Dtest=RecruitmentSeedTests" test

Kiểm thử dùng H2 trong bộ nhớ, xác nhận đăng nhập demo, nội dung seed, các trường hợp Match/Next và việc chạy lại không ghi đè dữ liệu.

## Thay đổi khi dùng match theo điểm

Match ưu tiên điểm cao thay vì chọn ngẫu nhiên. JD Java Hybrid có thể giới thiệu thêm demo junior, thiếu Spring Boot hoặc lương chưa giao nhau; thẻ hiển thị các khoảng thiếu để nhà tuyển dụng cân nhắc. Các mô tả “không đạt” trong bộ seed cũ mô tả bộ lọc cũ, không còn là điều kiện loại tuyệt đối. Tắt tìm việc, khác chế độ làm việc và khác địa điểm Onsite/Hybrid vẫn bị loại. Với AI bật, thứ tự phụ thuộc đánh giá ngữ nghĩa; kiểm thử seed tắt AI.
