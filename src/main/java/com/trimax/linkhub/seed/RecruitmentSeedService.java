package com.trimax.linkhub.seed;

import com.trimax.linkhub.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service @Profile("seed-recruitment") @RequiredArgsConstructor
public class RecruitmentSeedService {
    public static final String PASSWORD="LinkHubDemo!2026";
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final Clock clock;
    public static UUID id(String key) {return UUID.nameUUIDFromBytes(("linkhub:recruitment-demo:v1:"+key).getBytes(StandardCharsets.UTF_8));}
    public record Result(int accountsCreated,int accountsTotal,int jobsTotal) {}
    private record Person(String key,String name,String role,String location,String mode,
                          int experience,long salaryMin,long salaryMax,boolean seeking,List<String> skills,String summary) {}
    private static final List<String> JAVA=List.of("java","spring boot","postgresql");
    private static Person javaPerson(String key,String name,int years,String location,String mode,long min,long max,boolean seeking,List<String> skills,String summary) {
        return new Person(key,name,"Java Developer",location,mode,years,min,max,seeking,skills,summary);
    }
    private static final List<Person> PEOPLE=List.of(
        javaPerson("lan","Nguyễn Lan",3,"Hồ Chí Minh","HYBRID",20000000,30000000,true,JAVA,"Backend Java, đã xây dựng API và tích hợp PostgreSQL."),
        javaPerson("minh","Trần Minh",5,"Hồ Chí Minh","HYBRID",25000000,35000000,true,List.of("java","spring boot","postgresql","docker","aws"),"Backend và triển khai dịch vụ trên AWS; có kỹ năng ưu tiên Docker."),
        javaPerson("linh","Lê Linh",4,"Hồ Chí Minh","ANY",22000000,32000000,true,List.of("java","spring boot","redis","docker"),"Linh hoạt hình thức làm việc, kinh nghiệm tối ưu cache Redis."),
        javaPerson("an","Phạm An",4,"Hồ Chí Minh","HYBRID",25000000,33000000,true,JAVA,"Ứng viên có match đã đồng ý và hội thoại mẫu."),
        javaPerson("bao","Võ Bảo",3,"Hồ Chí Minh","HYBRID",23000000,31000000,true,JAVA,"Ứng viên có lời mời đang chờ đồng ý."),
        javaPerson("chi","Đặng Chi",3,"Hồ Chí Minh","HYBRID",21000000,30000000,true,JAVA,"Ứng viên đã từ chối lời mời trong JD pipeline."),
        javaPerson("dung","Hoàng Dũng",5,"Hồ Chí Minh","HYBRID",24000000,34000000,true,JAVA,"Ứng viên đã bị Next trong JD pipeline."),
        javaPerson("junior","Nguyễn Dương",1,"Hồ Chí Minh","HYBRID",15000000,25000000,true,JAVA,"Không đạt JD Java: chưa đủ 2 năm kinh nghiệm."),
        javaPerson("missing","Trần Hà",3,"Hồ Chí Minh","HYBRID",20000000,30000000,true,List.of("java","mysql"),"Không đạt JD Java: thiếu kỹ năng bắt buộc Spring Boot."),
        javaPerson("hanoi","Lê Huy",4,"Hà Nội","HYBRID",20000000,30000000,true,JAVA,"Không đạt JD Hybrid tại Hồ Chí Minh: khác địa điểm."),
        javaPerson("salary","Phạm Nam",6,"Hồ Chí Minh","HYBRID",45000000,55000000,true,JAVA,"Không đạt JD Java: khoảng lương không giao nhau."),
        javaPerson("hidden","Võ Mai",4,"Hồ Chí Minh","HYBRID",20000000,30000000,false,JAVA,"Đã tắt tìm việc: không được giới thiệu dù đáp ứng tiêu chí khác."),
        javaPerson("remote","Nguyễn Thảo",4,"Đà Nẵng","REMOTE",20000000,30000000,true,JAVA,"Phù hợp JD Remote, không phù hợp JD Hybrid."),
        new Person("designer","Trần Phúc","UX/UI Designer","Hồ Chí Minh","HYBRID",3,18000000,25000000,true,List.of("figma","ui design","ux research"),"Thiết kế sản phẩm số. Khác vị trí tuyển Java, phù hợp JD thiết kế.")
    );
    @Transactional public Result seed() {
        // Serialize seed invocations and commit all fixtures together.
        jdbc.queryForObject("select \"installed_rank\" from \"flyway_schema_history\" order by \"installed_rank\" desc limit 1 for update",Integer.class);
        String hash=encoder.encode(PASSWORD);int created=0;
        created+=account("tech","demo_recruiter","LinkHub Tech · Nhà tuyển dụng",hash);
        created+=account("creative","demo_creative","Studio Creative · Nhà tuyển dụng",hash);
        for(Person person:PEOPLE) {
            created+=account(person.key(),"demo_"+person.key(),person.name(),hash);
            UUID owner=id("account:"+person.key());
            if(jdbc.queryForObject("select count(*) from candidate_profiles where owner_id=?",Long.class,owner)==0) {
                jdbc.update("insert into candidate_profiles(owner_id,seeking,desired_role,summary,experience_years,location,work_mode,salary_min,salary_max) values (?,?,?,?,?,?,?,?,?)",owner,person.seeking(),person.role(),person.summary(),person.experience(),person.location(),person.mode(),person.salaryMin(),person.salaryMax());
                for(String skill:person.skills())jdbc.update("insert into candidate_skills(owner_id,skill) values (?,?)",owner,skill);
            }
        }
        job("java-live","tech","Java Backend Developer · Match/Next","LinkHub Tech","Java Developer","HYBRID","Hồ Chí Minh",2,25000000,35000000,true,List.of("java","spring boot"),List.of("docker","aws"),"Phát triển REST API với Java và Spring Boot. Đây là JD trống lịch sử để thử Match/Next, lọc ứng viên và gửi lời mời.");
        job("java-pipeline","tech","Java Developer · Pipeline mẫu","LinkHub Tech","Java Developer","HYBRID","Hồ Chí Minh",2,25000000,35000000,true,List.of("java","spring boot"),List.of("docker"),"JD có lời mời đang chờ, match đã đồng ý, từ chối và ứng viên đã Next để thử các trạng thái.");
        job("java-remote","tech","Java Developer · Remote toàn quốc","LinkHub Tech","Java Developer","REMOTE","Toàn quốc",2,22000000,35000000,true,List.of("java","spring boot"),List.of("postgresql"),"Làm việc từ xa. Địa điểm không phải điều kiện loại bỏ; ứng viên cần chọn Remote hoặc Linh hoạt.");
        job("designer","creative","UX/UI Designer · Studio Creative","Studio Creative","UX/UI Designer","HYBRID","Hồ Chí Minh",2,18000000,28000000,true,List.of("figma","ui design"),List.of("ux research"),"Thiết kế giao diện và trải nghiệm người dùng. JD để kiểm tra nhóm ứng viên khác với Java.");
        job("closed","tech","Java Developer · Bài đã đóng","LinkHub Tech","Java Developer","HYBRID","Hồ Chí Minh",2,25000000,35000000,false,List.of("java","spring boot"),List.of(),"Bài đã đóng: không xuất hiện trên feed, không tìm match mới. Có thể mở lại từ dashboard.");
        job("empty","tech","COBOL Developer · Không có ứng viên","LinkHub Tech","COBOL Developer","REMOTE","Toàn quốc",5,30000000,40000000,true,List.of("cobol"),List.of(),"Không có hồ sơ COBOL trong bộ seed. Dùng để kiểm tra trạng thái hết ứng viên phù hợp.");
        match("pending","java-pipeline","bao","PENDING");
        match("accepted","java-pipeline","an","ACCEPTED");
        match("declined","java-pipeline","chi","DECLINED");
        match("skipped","java-pipeline","dung","SKIPPED");
        message("hello","accepted","tech","Chào An, cảm ơn bạn đã đồng ý kết nối. Mình muốn trao đổi thêm về dự án Java Backend.");
        message("reply","accepted","an","Chào anh/chị, mình rất quan tâm. Mình có thể phỏng vấn trong tuần tới.");
        interview("pending","accepted",3,10,"PENDING","Google Meet · nhà tuyển dụng sẽ gửi link trong chat");
        interview("confirmed","accepted",5,14,"CONFIRMED","Phỏng vấn kỹ thuật tại văn phòng LinkHub Tech, Hồ Chí Minh");
        interview("cancelled","accepted",7,10,"CANCELLED","Lịch mẫu đã hủy, khung giờ không giữ chỗ");
        notifications.record(id("account:bao"),"demo:recruitment:pending:v1","RECRUITMENT_INVITE","Bạn có lời mời tuyển dụng","LinkHub Tech mời bạn trao đổi về vị trí Java Developer. Mở Tuyển dụng để xem JD và phản hồi.","recruitment",List.of());
        notifications.record(id("account:tech"),"demo:recruitment:accepted:v1","RECRUITMENT_REPLY","Bạn có match tuyển dụng mới","Phạm An đã đồng ý lời mời. Bạn có thể mở trò chuyện và lịch phỏng vấn.","recruitment",List.of());
        notifications.record(id("account:an"),"demo:recruitment:interview:v1","RECRUITMENT_INTERVIEW","Bạn có đề xuất lịch phỏng vấn","Mở match với LinkHub Tech để xác nhận hoặc từ chối lịch đang chờ.","recruitment",List.of());
        return new Result(created,PEOPLE.size()+2,6);
    }
    private int account(String key,String username,String name,String hash) {
        UUID owner=id("account:"+key);String email=username+"@linkhub.test";
        var conflicts=jdbc.queryForList("select id from accounts where username=? or email=? or id=?",UUID.class,username,email,owner);
        if(conflicts.stream().anyMatch(existing->!existing.equals(owner)))throw new IllegalStateException("Tài khoản demo trùng với tài khoản có sẵn: "+username+". Không thay đổi dữ liệu.");
        if(!conflicts.isEmpty()) {
            var current=jdbc.queryForMap("select username,email from accounts where id=?",owner);
            if(!username.equals(current.get("username"))||!email.equals(current.get("email")))throw new IllegalStateException("ID seed đã thuộc tài khoản khác: "+username);
            return 0;
        }
        jdbc.update("insert into accounts(id,email,password_hash,username,display_name,bio,published,email_verified,created_at) values (?,?,?,?,?,?,true,true,?)",owner,email,hash,username,name,"Tài khoản demo tuyển dụng LinkHub, chỉ dùng để thử tính năng.",Timestamp.from(clock.instant()));
        jdbc.update("insert into social_links(id,owner_id,title,url,sort_order) values (?,?,?,?,0)",id("link:"+key),owner,"Portfolio mẫu","https://example.com/"+username);
        jdbc.update("insert into service_offerings(id,owner_id,title,description,price,duration_minutes) values (?,?, 'Trao đổi nghề nghiệp','Dịch vụ mẫu để thử lịch hẹn hiện có',0,30)",id("service:"+key),owner);
        for(int day=1;day<=5;day++)jdbc.update("insert into availability_rules(id,owner_id,day_of_week,start_minute,end_minute) values (?,?,?,?,?)",id("availability:"+key+":"+day),owner,day,540,1020);
        return 1;
    }
    private void job(String key,String recruiter,String title,String company,String role,String mode,String location,int experience,long min,long max,boolean open,List<String> required,List<String> preferred,String description) {
        UUID job=id("job:"+key);
        if(jdbc.queryForObject("select count(*) from recruitment_jobs where id=?",Long.class,job)>0)return;
        jdbc.update("insert into recruitment_jobs(id,owner_id,title,company,role,description,min_experience,location,work_mode,salary_min,salary_max,open) values (?,?,?,?,?,?,?,?,?,?,?,?)",job,id("account:"+recruiter),title,company,role,description,experience,location,mode,min,max,open);
        for(String skill:required)jdbc.update("insert into recruitment_job_skills values (?,?,true)",job,skill);
        for(String skill:preferred)jdbc.update("insert into recruitment_job_skills values (?,?,false)",job,skill);
    }
    private void match(String key,String job,String candidate,String status) {
        UUID match=id("match:"+key);
        if(jdbc.queryForObject("select count(*) from recruitment_matches where id=? or (job_id=? and candidate_id=?)",Long.class,match,id("job:"+job),id("account:"+candidate))>0)return;
        jdbc.update("insert into recruitment_matches(id,job_id,candidate_id,status) values (?,?,?,?)",match,id("job:"+job),id("account:"+candidate),status);
    }
    private void message(String key,String match,String sender,String body) {
        UUID message=id("message:"+key),matchId=id("match:"+match);
        if(jdbc.queryForObject("select count(*) from recruitment_messages where id=?",Long.class,message)>0)return;
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select status='ACCEPTED' from recruitment_matches where id=?",Boolean.class,matchId)))return;
        jdbc.update("insert into recruitment_messages(id,match_id,sender_id,body) values (?,?,?,?)",message,matchId,id("account:"+sender),body);
    }
    private void interview(String key,String match,int days,int hour,String status,String details) {
        UUID interview=id("interview:"+key),matchId=id("match:"+match);
        if(jdbc.queryForObject("select count(*) from recruitment_interviews where id=?",Long.class,interview)>0)return;
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select status='ACCEPTED' from recruitment_matches where id=?",Boolean.class,matchId)))return;
        Instant start=LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).plusDays(days).atTime(hour,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        for(UUID participant:List.of(id("account:tech"),id("account:an"))) {
            if(jdbc.queryForObject("select count(*) from recruitment_interviews where (recruiter_id=? or candidate_id=?) and status in ('PENDING','CONFIRMED') and starts_at<? and ends_at>?",Long.class,participant,participant,Timestamp.from(start.plusSeconds(1800)),Timestamp.from(start))>0)return;
            if(jdbc.queryForObject("select count(*) from bookings where owner_id=? and status in ('PENDING','CONFIRMED') and starts_at<? and ends_at>?",Long.class,participant,Timestamp.from(start.plusSeconds(1800)),Timestamp.from(start))>0)return;
        }
        jdbc.update("insert into recruitment_interviews(id,match_id,recruiter_id,candidate_id,starts_at,ends_at,meeting_details,status) values (?,?,?,?,?,?,?,?)",interview,matchId,id("account:tech"),id("account:an"),Timestamp.from(start),Timestamp.from(start.plusSeconds(1800)),details,status);
    }
}
