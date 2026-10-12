# Tuyển dụng và match

Tính năng nằm trong dashboard tại /app#view-recruitment, yêu cầu đăng nhập. Tài khoản có thể vừa tìm việc vừa tuyển dụng. Profile, link, dịch vụ, analytics và lịch hẹn hiện có tiếp tục hoạt động.

## Dùng thử với hai tài khoản

1. Tài khoản ứng viên: mở Tuyển dụng, điền hồ sơ và bật cho phép nhà tuyển dụng tìm thấy mình. Ví dụ vị trí Java Developer, kỹ năng Java và Spring Boot, 3 năm kinh nghiệm, Hồ Chí Minh, Hybrid, 20–30 triệu VND/tháng.
2. Tài khoản nhà tuyển dụng: dán toàn bộ JD vào ô mô tả. Hệ thống tự phân tích, hiện bản xem trước và cho đăng khi đủ thông tin; bổ sung thông tin thiếu trực tiếp vào JD. Không nhập kỹ năng vào trường riêng.
3. Bấm Match dưới bài đăng. Hệ thống giới thiệu một ứng viên phù hợp; Next bỏ qua ứng viên cho JD này. Tải lại rồi Match vẫn giữ ứng viên chưa xử lý.
4. Gửi lời mời. Ứng viên xem JD trong Lời mời & match, rồi đồng ý hoặc từ chối. Email chưa chia sẻ và chat chưa mở khi lời mời còn chờ.
5. Sau khi đồng ý: hai bên thấy email của nhau và có thể mở Trò chuyện & lịch phỏng vấn. Nhà tuyển dụng đề xuất giờ, ứng viên xác nhận hoặc từ chối. Cả hai bên có thể hủy lịch.

## Quy tắc

### Tự động cấu trúc JD

Nội dung JD hỗ trợ Markdown cơ bản: tiêu đề `#`, chữ đậm/nghiêng, gạch ngang chữ, danh sách, trích dẫn, liên kết và khối mã. Mục “Bản JD đã định dạng” cập nhật ngay khi dán hoặc sửa nội dung; bài tuyển dụng trong feed và lời mời dùng cùng bộ hiển thị. HTML được hiển thị như văn bản, liên kết chỉ chấp nhận HTTP(S), mailto và đường dẫn nội bộ. Bản gốc được lưu nguyên văn để sửa và trích xuất; API trả thêm `formattedDescription` làm bản hiển thị. JD văn bản thuần được tự thêm tiêu đề/mục và gạch đầu dòng theo các mục Mô tả công việc, Yêu cầu, Ưu tiên, Quyền lợi, Cách ứng tuyển; các dòng thông tin công ty/vị trí/lương được nhấn mạnh. Dòng bị ngắt khi copy PDF được nối khi nhận diện được. Markdown đã có tiêu đề hoặc khối mã được giữ nguyên. Định dạng chạy cục bộ, không gọi thêm LLM và không bổ sung sự kiện/nội dung mới. Bản xem trước tự mở ngay khi nhập, không phụ thuộc kết quả Groq; nhãn và giá trị đứng riêng dòng khi copy từ PDF cũng được ghép lại. Formatter JavaScript ở trình duyệt và Java ở backend dùng chung fixture kiểm tra bố cục. Feed và lời mời dùng cùng bố cục. Các bài đã lưu cũng có bản hiển thị được tạo từ nội dung gốc. Chưa hỗ trợ bảng, ảnh và danh sách lồng nhau.

Form nhà tuyển dụng gửi `description` tới `POST /jobs/parse-jd`. Kết quả có `data`, `missingFields`, `source` (`rules` hoặc `groq`), `warnings` và `extractionId`. `POST /jobs/from-jd` và `PUT /jobs/{id}/from-jd` nhận nội dung gốc cùng `extractionId` và lưu dữ liệu có cấu trúc. Khi bật LLM, server chỉ lưu đúng kết quả đã phân tích cho cùng tài khoản và nội dung; không gọi LLM lần thứ hai lúc đăng. Các endpoint nhập cấu trúc trước đây vẫn tương thích với dữ liệu seed và tích hợp cũ.

Mặc định dùng bộ trích xuất cục bộ theo quy tắc. Hỗ trợ nhãn tiếng Việt/Anh (Vị trí/Position, Công ty/Company, Địa điểm/Location, Hình thức làm việc/Work mode, Lương/Salary), các mục yêu cầu và ưu tiên, kỹ năng phổ biến và danh sách kỹ năng phân cách bằng dấu phẩy sau nhãn. JD tự do hoặc kỹ năng ngoài danh mục có thể cần bổ sung nhãn vào nội dung. Kinh nghiệm không nêu được hiểu là không đặt mức tối thiểu. Lương thỏa thuận/negotiable không giới hạn khoảng lương khi match; số lương hiện hỗ trợ VND, không quy đổi USD.

### Bật trích xuất bằng Groq

Đặt trong `.env` cạnh `pom.xml` (hoặc biến môi trường của backend), sau đó khởi động lại ứng dụng:

```properties
JD_LLM_ENABLED=true
GROQ_API_KEY=your_server_side_api_key
JD_LLM_MODEL=openai/gpt-oss-120b
```

Model mặc định `openai/gpt-oss-120b` chạy trên Groq; dùng key Groq, không dùng key hay số dư OpenAI. Tạo key tại [Groq Console](https://console.groq.com/keys). Model thay thế cần hỗ trợ JSON Schema strict trong Chat Completions. Hướng dẫn: [Groq Structured Outputs](https://console.groq.com/docs/structured-outputs). Free Plan có giới hạn cả yêu cầu lẫn token; kiểm tra hạn mức thực tế trong console.

Khi bật, JD được gửi tới Groq để đọc văn bản tự do tiếng Việt/Anh và Markdown. Dùng `response_format.json_schema` với JSON Schema strict, các giá trị thiếu được biểu diễn bằng `null`, tách kỹ năng bắt buộc/ưu tiên và kèm trích dẫn nguyên văn. Backend kiểm tra trích dẫn có trong JD, tên công ty/vị trí/địa điểm/kỹ năng khớp căn cứ, kiểu dữ liệu, giới hạn số, hình thức làm việc, đơn vị và khoảng lương. Kết quả AI vẫn cần được nhà tuyển dụng kiểm tra; schema và trích dẫn không bảo đảm hiểu đúng hoàn toàn ngữ nghĩa. JD thiếu trường bắt buộc không được đăng.

API key chỉ ở backend. Không ghi key, JD hay nội dung trả về của nhà cung cấp vào log ứng dụng. Không tự động gửi lại khi lỗi/timeout; không âm thầm thay kết quả Groq bằng bộ quy tắc. Khi chưa bật LLM, bộ quy tắc tiếp tục hoạt động. Bật LLM nhưng thiếu key sẽ báo lỗi cấu hình khi phân tích.

Kết nối timeout 5 giây, yêu cầu timeout 30 giây, tối đa 3.500 completion tokens (bao gồm reasoning), dùng `reasoning_effort=low`. API phân tích và lưu từ JD được giới hạn 12 lần/phút cho mỗi thao tác/tài khoản. Frontend chờ 1,5 giây sau khi sửa và chỉ gửi một yêu cầu phân tích tại một thời điểm. Nút “Phân tích lại” phục hồi sau lỗi.

Lỗi upstream được phân biệt bằng `code` và `retryable`: `GROQ_AUTH_FAILED` cần kiểm tra key; `GROQ_ACCESS_DENIED` cần kiểm tra quyền; `GROQ_MODEL_UNAVAILABLE` cần kiểm tra model; `GROQ_INVALID_REQUEST` cần kiểm tra schema/cấu hình. Các lỗi cấu hình trả 503 và không khuyên thử lại ngay. `GROQ_RATE_LIMITED` trả HTTP 429 kèm `Retry-After`; Free Plan có thể chạm hạn mức phút hoặc ngày, vì vậy thời gian chờ có thể dài. Lỗi kết nối/timeout/quá tải có thể thử lại.

Log chẩn đoán chỉ ghi mã lỗi ứng dụng, HTTP status upstream và `providerRequestId`; không log phản hồi nguyên văn, API key hoặc JD. Chạy `python scripts/check-jd-groq.py` để kiểm tra cấu hình bằng JD giả lập. Script gọi Groq một lần, tiêu thụ quota, không tự thử lại và không in key/nội dung phản hồi.

Cache tối đa 128 JD trong bộ nhớ, mỗi bản 15 phút, gắn với tài khoản và nguyên văn JD. Yêu cầu đồng thời cùng JD dùng chung kết quả. Hết hạn, bị đẩy khỏi cache, khởi động lại server hoặc chuyển sang instance khác sẽ trả 409 khi lưu và yêu cầu phân tích lại. Triển khai nhiều instance cần sticky sessions hoặc thay cache bằng kho dùng chung trước khi dựa vào luồng này.

Kiểm thử cục bộ không gọi API thật và không cần key:

```powershell
.\mvnw.cmd -o '-Dtest=GroqJdExtractorTests,JdExtractionServiceTests,JdParserTests,RecruitmentIntegrationTests' test
```

Ví dụ JD:

```text
Vị trí: Java Developer
Công ty: LinkHub
Địa điểm: Hồ Chí Minh
Hình thức làm việc: Hybrid
Lương: 25–35 triệu VND/tháng
Yêu cầu:
- Từ 2 năm kinh nghiệm với Java, Spring Boot
Ưu tiên:
- Docker, AWS
```

- Điều kiện bắt buộc: bật tìm việc, khác nhà tuyển dụng, tương thích chế độ làm việc; địa điểm giống nhau cho Onsite/Hybrid, Remote không giới hạn địa điểm. Kinh nghiệm, lương và mức phủ kỹ năng được tính điểm và hiển thị các khoảng thiếu.
- Tên vị trí chuẩn hóa Front-End/Frontend, Back-End/Backend, Full-Stack/Fullstack và một số cách viết Developer. Kỹ năng có alias React/ReactJS, TS/TypeScript, HTML/HTML5, CSS/CSS3, JavaScript/ES6+, REST API/RESTful API. Redux không tự động thành Redux Toolkit; không ghép các kỹ năng khai riêng.
- Giới thiệu hồ sơ theo điểm giảm dần; cùng điểm dùng ID để thứ tự ổn định. Next không lặp lại hồ sơ đã xử lý.
- Chỉ hồ sơ đã bật tìm việc mới được giới thiệu. Nhà tuyển dụng không được match chính mình. Trước khi gửi lời mời, hệ thống kiểm tra lại hồ sơ và trạng thái JD.
- Đã Next hoặc gửi lời mời thì không giới thiệu lại người đó cho cùng JD. Có thể thử Match lại khi có ứng viên mới.
- Sau khi gửi lời mời đầu tiên, JD không thể sửa nội dung hoặc tiêu chí. Đóng/mở bài vẫn được phép; tạo bài mới để thay đổi yêu cầu. JD đóng không nhận match mới hoặc chấp nhận lời mời mới; những match đã đồng ý vẫn có thể trao đổi.
- Lịch phỏng vấn trong 90 ngày tới, dài 15–180 phút. Giờ nhập theo múi giờ thiết bị, lưu UTC. Lịch PENDING/CONFIRMED giữ chỗ cho cả hai người; lịch dịch vụ cũ cũng được kiểm tra và tránh giờ phỏng vấn.
- Thông báo tuyển dụng sử dụng chuông/SSE hiện có. Bản này chỉ gửi thông báo trong ứng dụng, không gửi email tuyển dụng tự động.
- Feed phân trang 20 JD; danh sách lời mời/match hiển thị 100 mục mới nhất và hội thoại hiển thị 100 tin nhắn gần nhất.

## Match kết hợp điểm và LLM

Điểm tiêu chí tối đa 100: vị trí 25, kỹ năng bắt buộc 40, kinh nghiệm 10, chế độ làm việc 10, địa điểm 5, lương 5, kỹ năng ưu tiên 5. JD không có kỹ năng ưu tiên nhận trọn 5 điểm mục đó. Kỹ năng được tính theo tỷ lệ khớp; kinh nghiệm chưa đủ giảm điểm; lương chưa giao nhau không loại ngay nhưng hiện cảnh báo cần thương lượng. Đây là mức phù hợp dựa trên dữ liệu khai báo, không phải xác nhận năng lực hay xác suất tuyển thành công.

Hồ sơ có điểm tiêu chí từ 45 và ít nhất một kỹ năng bắt buộc khớp được xét. Tối đa 5 hồ sơ đứng đầu được đánh giá ngữ nghĩa trong một yêu cầu Groq; để tiết kiệm quota, phần JD gửi tối đa 3.000 ký tự, danh sách kỹ năng JD 1.000 ký tự, kỹ năng ứng viên 750 ký tự và giới thiệu 300 ký tự. Điểm tiêu chí vẫn dùng toàn bộ dữ liệu; các hồ sơ còn lại vẫn được xếp theo điểm quy tắc. Điểm có AI = 80% tiêu chí + 20% ngữ nghĩa; không có AI dùng 100% điểm tiêu chí. Chỉ giới thiệu từ 60 điểm, có liên quan tên vị trí hoặc AI đánh giá từ 70. Vì vậy đây là reranking trên shortlist, chưa phải tìm kiếm ngữ nghĩa toàn bộ hồ sơ không có kỹ năng chung.

Bật bằng `MATCH_LLM_ENABLED=true` trong .env; mặc định theo `JD_LLM_ENABLED`. Dùng cùng `GROQ_API_KEY` và `JD_LLM_MODEL`. Có thể tắt riêng match AI bằng `MATCH_LLM_ENABLED=false`. Groq nhận JD và vị trí, giới thiệu, kỹ năng, kinh nghiệm của hồ sơ đã bật tìm việc; không gửi tên hiển thị, email hay ID tài khoản thành các trường riêng. Nội dung tự nhập vẫn có thể chứa thông tin cá nhân, nên giới thiệu nên tập trung vào kinh nghiệm.

Kết quả ngữ nghĩa dùng JSON Schema strict, ID tạm theo thứ tự batch, số nguyên 0–100 và trích dẫn có thật trong JD/hồ sơ. Bản trả về không hợp lệ, lỗi quota, thiếu key hay timeout 8 giây dùng điểm tiêu chí; giao diện phân biệt nguồn điểm. Cache 128 đánh giá trong 15 phút, gắn toàn bộ snapshot JD/hồ sơ; đổi dữ liệu không dùng đánh giá cũ. Không lưu đánh giá AI vào database, không tự retry, không log JD/hồ sơ/key/response. Có thể gọi lại sau lỗi; cache là riêng từng instance.

Gọi LLM ngoài transaction database. Trước khi tạo gợi ý, khóa JD và kiểm tra lại snapshot hồ sơ, opt-in và lịch sử xử lý; thay đổi giữa chừng trả 409 để tìm lại. Gửi lời mời tiếp tục kiểm tra quyền sở hữu, trạng thái bài và điểm phù hợp trên hồ sơ hiện tại; không gọi LLM trong transaction gửi lời mời. Hồ sơ chỉ đạt nhờ ngữ nghĩa cần cache đánh giá còn hiệu lực, nếu hết hạn hoặc chuyển instance thì bấm Match lại. Màn hình có điểm tổng, điểm tiêu chí, điểm AI nếu có, lý do và yêu cầu cần trao đổi thêm.

## Dữ liệu và kiểm tra

Migration Flyway V11 tạo các bảng tuyển dụng bổ sung; không đổi cấu trúc các bảng profile cũ. Migration áp dụng tự động khi ứng dụng mới khởi động. Không cần dependency mới.

API nằm dưới /api/me/recruitment và dùng session, CSRF hiện có. Các thao tác chỉ cho phép chủ JD và ứng viên thuộc match; Match/Next, gửi lời mời và tin nhắn có giới hạn tốc độ.

Chạy kiểm thử: mvnw.cmd -o test. RecruitmentIntegrationTests kiểm tra lọc ứng viên, consent, Next, quyền sở hữu, CSRF, chat, lịch phỏng vấn và tương tác với lịch dịch vụ cũ. Các kiểm thử mặc định dùng H2, không thay thế kiểm chứng đồng thời trên PostgreSQL. Chưa xác nhận bố cục bằng trình duyệt trong phiên triển khai này do không có trình duyệt kết nối.

Thay đổi đang ở mã nguồn local; chưa triển khai lên môi trường production.

## Giao diện hội thoại

Mở chat của match đã đồng ý chuyển sang khung hội thoại riêng; nút Quay lại match khôi phục danh sách. Desktop hiển thị tin nhắn và ô soạn ở cột chính, lịch phỏng vấn ở cột bên. Màn hình dưới 960px thu gọn lịch mặc định. Form đề xuất lịch chỉ dành cho nhà tuyển dụng và nằm trong mục mở rộng. Tin nhắn có nhóm ngày, giờ gọn và cập nhật thêm khi có tin mới; không tự kéo xuống cuối khi người dùng đang đọc lịch sử.
