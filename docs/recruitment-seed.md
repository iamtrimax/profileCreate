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

| Email | Mật khẩu ban đầu | Vai trò / kịch bản |
| --- | --- | --- |
| `demo_recruiter@linkhub.test` | `LinkHubDemo!2026` | Nhà tuyển dụng LinkHub Tech: 5 JD Java/COBOL |
| `demo_creative@linkhub.test` | `LinkHubDemo!2026` | Nhà tuyển dụng Studio Creative: 1 JD UX/UI |
| `demo_lan@linkhub.test` | `LinkHubDemo!2026` | Java phù hợp, 3 năm kinh nghiệm |
| `demo_minh@linkhub.test` | `LinkHubDemo!2026` | Java phù hợp, có Docker và AWS ưu tiên |
| `demo_linh@linkhub.test` | `LinkHubDemo!2026` | Java phù hợp, hình thức Linh hoạt; cũng đạt JD Remote |
| `demo_an@linkhub.test` | `LinkHubDemo!2026` | Đã match, có chat và 3 lịch phỏng vấn mẫu |
| `demo_bao@linkhub.test` | `LinkHubDemo!2026` | Lời mời đang chờ đồng ý |
| `demo_chi@linkhub.test` | `LinkHubDemo!2026` | Đã từ chối lời mời trong JD pipeline |
| `demo_dung@linkhub.test` | `LinkHubDemo!2026` | Đã Next trong JD pipeline |
| `demo_junior@linkhub.test` | `LinkHubDemo!2026` | Có thể được giới thiệu với điểm kinh nghiệm thấp hơn: 1 năm |
| `demo_missing@linkhub.test` | `LinkHubDemo!2026` | Có thể được giới thiệu; hiển thị thiếu Spring Boot |
| `demo_hanoi@linkhub.test` | `LinkHubDemo!2026` | Bị loại vì địa điểm Hà Nội khác JD Hybrid tại Hồ Chí Minh |
| `demo_salary@linkhub.test` | `LinkHubDemo!2026` | Có thể được giới thiệu; lương 45–55 triệu cần thương lượng với JD 25–35 triệu |
| `demo_hidden@linkhub.test` | `LinkHubDemo!2026` | Đã tắt tìm việc, không xuất hiện trong Match |
| `demo_remote@linkhub.test` | `LinkHubDemo!2026` | Java Remote tại Đà Nẵng, không đạt JD Hybrid |
| `demo_designer@linkhub.test` | `LinkHubDemo!2026` | UX/UI Designer, phù hợp JD Studio Creative |

Tổng cộng **146 tài khoản, 134 hồ sơ ứng viên, 246 JD** (12 nhà tuyển dụng và 134 ứng viên). Mỗi tài khoản có profile công khai, link portfolio mẫu, dịch vụ trao đổi 30 phút miễn phí và lịch rảnh thứ Hai–thứ Sáu, 09:00–17:00 theo giờ Việt Nam để thử cả các tính năng cũ.

### Tài khoản nhà tuyển dụng mở rộng

Các tài khoản dưới đây có 24 JD mỗi công ty. Đăng nhập bằng email đầy đủ.

| Email | Mật khẩu ban đầu | Công ty / nhóm nghề |
| --- | --- | --- |
| `demo_recruiter_frontend@linkhub.test` | `LinkHubDemo!2026` | Lotus Digital — React Developer |
| `demo_recruiter_node@linkhub.test` | `LinkHubDemo!2026` | Mekong Software — Node.js Developer |
| `demo_recruiter_python@linkhub.test` | `LinkHubDemo!2026` | Cloud Nine — Python Developer |
| `demo_recruiter_mobile@linkhub.test` | `LinkHubDemo!2026` | Saigon Mobile — Mobile Developer |
| `demo_recruiter_qa@linkhub.test` | `LinkHubDemo!2026` | Quality Labs — QA Engineer |
| `demo_recruiter_devops@linkhub.test` | `LinkHubDemo!2026` | Sky Infrastructure — DevOps Engineer |
| `demo_recruiter_data@linkhub.test` | `LinkHubDemo!2026` | Insight Vietnam — Data Analyst |
| `demo_recruiter_ba@linkhub.test` | `LinkHubDemo!2026` | Bridge Solutions — Business Analyst |
| `demo_recruiter_product@linkhub.test` | `LinkHubDemo!2026` | Nova Product — Product Manager |
| `demo_recruiter_security@linkhub.test` | `LinkHubDemo!2026` | Secure Vietnam — Security Engineer |

### Danh sách email ứng viên mở rộng

Mỗi nhóm có 12 tài khoản. Hồ sơ số 01–11 bật tìm việc; hồ sơ số 12 tắt tìm việc để kiểm tra bộ lọc. Đây là trạng thái ban đầu, có thể thay đổi sau khi bạn thao tác.

#### React Developer (`frontend`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_frontend_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_frontend_12@linkhub.test` | `LinkHubDemo!2026` |

#### Node.js Developer (`node`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_node_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_node_12@linkhub.test` | `LinkHubDemo!2026` |

#### Python Developer (`python`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_python_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_python_12@linkhub.test` | `LinkHubDemo!2026` |

#### Mobile Developer (`mobile`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_mobile_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_mobile_12@linkhub.test` | `LinkHubDemo!2026` |

#### QA Engineer (`qa`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_qa_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_qa_12@linkhub.test` | `LinkHubDemo!2026` |

#### DevOps Engineer (`devops`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_devops_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_devops_12@linkhub.test` | `LinkHubDemo!2026` |

#### Data Analyst (`data`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_data_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_data_12@linkhub.test` | `LinkHubDemo!2026` |

#### Business Analyst (`ba`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_ba_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_ba_12@linkhub.test` | `LinkHubDemo!2026` |

#### Product Manager (`product`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_product_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_product_12@linkhub.test` | `LinkHubDemo!2026` |

#### Security Engineer (`security`)

| Email | Mật khẩu ban đầu |
| --- | --- |
| `demo_security_01@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_02@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_03@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_04@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_05@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_06@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_07@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_08@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_09@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_10@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_11@linkhub.test` | `LinkHubDemo!2026` |
| `demo_security_12@linkhub.test` | `LinkHubDemo!2026` |

Mục Tài khoản liệt kê đủ **146 email test**: 16 tài khoản kịch bản gốc, 10 nhà tuyển dụng mở rộng và 120 ứng viên mở rộng. Mật khẩu trên là mật khẩu khi tạo seed; nếu đã đổi mật khẩu thì dùng mật khẩu đã đổi. Chạy lại seed không đặt lại mật khẩu.


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

## Bộ dữ liệu mở rộng

Mỗi nhóm dưới đây có 1 nhà tuyển dụng, 12 ứng viên và 24 JD (6 nhóm dự án × 4 cấu hình): Junior Hybrid tại Hồ Chí Minh, Middle Onsite tại Hà Nội, Middle Hybrid tại Đà Nẵng và Senior Remote toàn quốc.

| Mã nhóm | Vị trí | Công ty |
| --- | --- | --- |
| frontend | React Developer | Lotus Digital |
| node | Node.js Developer | Mekong Software |
| python | Python Developer | Cloud Nine |
| mobile | Mobile Developer | Saigon Mobile |
| qa | QA Engineer | Quality Labs |
| devops | DevOps Engineer | Sky Infrastructure |
| data | Data Analyst | Insight Vietnam |
| ba | Business Analyst | Bridge Solutions |
| product | Product Manager | Nova Product |
| security | Security Engineer | Secure Vietnam |

Email nhà tuyển dụng: `demo_recruiter_<mã nhóm>@linkhub.test` (ví dụ `demo_recruiter_frontend@linkhub.test`). Email ứng viên: `demo_<mã nhóm>_01@linkhub.test` đến `demo_<mã nhóm>_12@linkhub.test`. Mật khẩu chung vẫn là `LinkHubDemo!2026`.

Ứng viên có 0–6 năm kinh nghiệm, mức lương kỳ vọng 12–46 triệu đồng/tháng, địa điểm luân phiên Hồ Chí Minh/Hà Nội/Đà Nẵng và hình thức Hybrid/Onsite/Remote/Linh hoạt. Hồ sơ số 12 mỗi nhóm tắt tìm việc để kiểm tra bộ lọc; 11 hồ sơ còn lại bật tìm việc. Mỗi JD mới đều có ứng viên để thử Match/Next. Các kịch bản Java, UX/UI, chat và lịch phỏng vấn cũ vẫn được giữ nguyên.

Nếu đã chạy bản seed cũ, chạy lại cùng lệnh seed để bổ sung các tài khoản, hồ sơ và JD còn thiếu. Bản mở rộng hiện có 146 tài khoản, 134 hồ sơ ứng viên và 246 JD cố định; nếu đã có bản 46 JD thì chỉ bổ sung thêm 200 JD. Không cần xóa database.

### Các công việc bổ sung

Mỗi công ty có thêm 20 JD cho 5 lĩnh vực: Thương mại điện tử, Tài chính số, Logistics, Y tế số và Giáo dục trực tuyến. Mỗi lĩnh vực có 4 cấu hình Junior Hybrid tại Hồ Chí Minh, Middle Onsite tại Hà Nội, Middle Hybrid tại Đà Nẵng và Senior Remote toàn quốc. Tên bài có lĩnh vực dự án để dễ phân biệt; mức lương thay đổi theo cấp bậc và dự án (12–53 triệu đồng/tháng).

Các JD cũ giữ nguyên ID và nội dung đã chỉnh sửa. Bộ seed có 246 JD; số bài thực tế trong database có thể cao hơn nếu bạn đã tạo thêm bài tuyển dụng.
