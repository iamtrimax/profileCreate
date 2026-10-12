package com.trimax.linkhub.service;

import com.trimax.linkhub.api.RecruitmentModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class RecruitmentService {
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final Clock clock;
    private final RecruitmentScorer scorer;
    private final RecruitmentRanking ranking;
    private final PlatformTransactionManager transactions;
    private record Pair(UUID id, UUID jobId, UUID recruiter, UUID candidate, MatchStatus status) {}
    private static String normalized(String value) { return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private static List<String> skills(List<String> input) { return input.stream().map(RecruitmentService::normalized).distinct().sorted().toList(); }
    private void salary(long min,long max) { if(max<min) throw bad("Mức lương tối đa phải lớn hơn hoặc bằng tối thiểu."); }
    private void lockAccount(UUID owner) { jdbc.queryForObject("select id from accounts where id=? for update",UUID.class,owner); }
    public Candidate candidate(UUID owner) {
        return jdbc.query("select c.*,a.display_name from candidate_profiles c join accounts a on a.id=c.owner_id where c.owner_id=?",
            (r,n)->new Candidate(owner,r.getString("display_name"),r.getBoolean("seeking"),r.getString("desired_role"),r.getString("summary"),r.getInt("experience_years"),r.getString("location"),WorkMode.valueOf(r.getString("work_mode")),r.getLong("salary_min"),r.getLong("salary_max"),candidateSkills(owner)),owner).stream().findFirst().orElse(null);
    }
    private List<String> candidateSkills(UUID owner) {return jdbc.queryForList("select skill from candidate_skills where owner_id=? order by skill",String.class,owner);}
    @Transactional public Candidate saveCandidate(UUID owner,CandidateInput in) {
        salary(in.salaryMin(),in.salaryMax()); lockAccount(owner);
        int changed=jdbc.update("update candidate_profiles set seeking=?,desired_role=?,summary=?,experience_years=?,location=?,work_mode=?,salary_min=?,salary_max=?,updated_at=current_timestamp where owner_id=?",
            in.seeking(),in.desiredRole().strip(),in.summary().strip(),in.experienceYears(),in.location().strip(),in.workMode().name(),in.salaryMin(),in.salaryMax(),owner);
        if(changed==0)jdbc.update("insert into candidate_profiles(owner_id,seeking,desired_role,summary,experience_years,location,work_mode,salary_min,salary_max) values (?,?,?,?,?,?,?,?,?)",
            owner,in.seeking(),in.desiredRole().strip(),in.summary().strip(),in.experienceYears(),in.location().strip(),in.workMode().name(),in.salaryMin(),in.salaryMax());
        jdbc.update("delete from candidate_skills where owner_id=?",owner);
        for(String skill:skills(in.skills()))jdbc.update("insert into candidate_skills values (?,?)",owner,skill);
        return candidate(owner);
    }
    private Job mapJob(ResultSet r) throws SQLException {
        UUID id=r.getObject("id",UUID.class);
        return new Job(id,r.getObject("owner_id",UUID.class),r.getString("display_name"),r.getString("title"),r.getString("company"),r.getString("role"),r.getString("description"),r.getInt("min_experience"),r.getString("location"),WorkMode.valueOf(r.getString("work_mode")),r.getLong("salary_min"),r.getLong("salary_max"),r.getBoolean("open"),r.getTimestamp("created_at").toInstant(),jobSkills(id,true),jobSkills(id,false),JdFormatter.format(r.getString("description")));
    }
    private List<String> jobSkills(UUID job,boolean required) {return jdbc.queryForList("select skill from recruitment_job_skills where job_id=? and required=? order by skill",String.class,job,required);}
    private Job job(UUID id) {return jdbc.query("select j.*,a.display_name from recruitment_jobs j join accounts a on a.id=j.owner_id where j.id=?",(r,n)->mapJob(r),id).stream().findFirst().orElseThrow(RecruitmentService::missing);}
    private Job ownedJob(UUID owner,UUID id,boolean lock) {
        if(lock)jdbc.query("select id from recruitment_jobs where id=? and owner_id=? for update",(r,n)->r.getObject(1,UUID.class),id,owner).stream().findFirst().orElseThrow(RecruitmentService::missing);
        Job job=job(id);if(!job.ownerId().equals(owner))throw missing();return job;
    }
    public List<Job> jobs(UUID owner) {return jdbc.query("select j.*,a.display_name from recruitment_jobs j join accounts a on a.id=j.owner_id where j.owner_id=? order by j.created_at desc,j.id",(r,n)->mapJob(r),owner);}
    public List<Job> feed(int page) {return jdbc.query("select j.*,a.display_name from recruitment_jobs j join accounts a on a.id=j.owner_id where j.open=true order by j.created_at desc,j.id limit 20 offset ?",(r,n)->mapJob(r),page*20);}
    @Transactional public Job saveJob(UUID owner,UUID id,JobInput in) {
        salary(in.salaryMin(),in.salaryMax());if(in.workMode()==WorkMode.ANY)throw bad("JD cần hình thức làm việc cụ thể.");
        lockAccount(owner);
        if(id==null) {
            if(jdbc.queryForObject("select count(*) from recruitment_jobs where owner_id=?",Long.class,owner)>=100)throw bad("Tối đa 100 bài tuyển dụng cho mỗi tài khoản.");
            id=UUID.randomUUID();
            jdbc.update("insert into recruitment_jobs(id,owner_id,title,company,role,description,min_experience,location,work_mode,salary_min,salary_max) values (?,?,?,?,?,?,?,?,?,?,?)",id,owner,in.title().strip(),in.company().strip(),in.role().strip(),in.description().strip(),in.minExperience(),in.location().strip(),in.workMode().name(),in.salaryMin(),in.salaryMax());
        } else {
            ownedJob(owner,id,true);
            if(jdbc.queryForObject("select count(*) from recruitment_matches where job_id=? and status in ('PENDING','ACCEPTED','DECLINED')",Long.class,id)>0)throw conflict("JD đã gửi lời mời. Hãy đóng bài và tạo JD mới để thay đổi yêu cầu.");
            jdbc.update("update recruitment_jobs set title=?,company=?,role=?,description=?,min_experience=?,location=?,work_mode=?,salary_min=?,salary_max=? where id=?",in.title().strip(),in.company().strip(),in.role().strip(),in.description().strip(),in.minExperience(),in.location().strip(),in.workMode().name(),in.salaryMin(),in.salaryMax(),id);
            jdbc.update("delete from recruitment_matches where job_id=? and status='PRESENTED'",id);
            jdbc.update("delete from recruitment_job_skills where job_id=?",id);
        }
        List<String> required=skills(in.requiredSkills());
        for(String skill:required)jdbc.update("insert into recruitment_job_skills values (?,?,true)",id,skill);
        for(String skill:skills(in.preferredSkills()))if(!required.contains(skill))jdbc.update("insert into recruitment_job_skills values (?,?,false)",id,skill);
        return job(id);
    }
    @Transactional public Job setOpen(UUID owner,UUID id,boolean open) {ownedJob(owner,id,true);jdbc.update("update recruitment_jobs set open=? where id=?",open,id);return job(id);}
    private boolean eligible(Job job,Candidate c) {return c!=null&&ranking.recommendable(job,c);}
    private record Discovery(Job job,UUID current,List<Candidate> candidates) {}
    // Groq runs outside database transactions; recheck snapshots under the job lock before publishing.
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Suggestion discover(UUID owner,UUID jobId,UUID skipId) {
        var tx=new TransactionTemplate(transactions);
        Discovery discovery=tx.execute(status->{
            Job job=ownedJob(owner,jobId,true);if(!job.open())throw conflict("Bài tuyển dụng đã đóng.");
            if(skipId!=null&&jdbc.update("update recruitment_matches set status='SKIPPED',updated_at=current_timestamp where id=? and job_id=? and status='PRESENTED'",skipId,jobId)==0)throw conflict("Ứng viên này đã được xử lý. Hãy tải lại.");
            var current=jdbc.queryForList("select id from recruitment_matches where job_id=? and status='PRESENTED'",UUID.class,jobId);
            if(!current.isEmpty()) {
                UUID match=current.getFirst();
                UUID candidateId=jdbc.queryForObject("select candidate_id from recruitment_matches where id=?",UUID.class,match);
                Candidate c=candidate(candidateId);
                if(c!=null)return new Discovery(job,match,List.of(c));
                jdbc.update("update recruitment_matches set status='SKIPPED',updated_at=current_timestamp where id=?",match);
            }
            var ids=jdbc.queryForList("select c.owner_id from candidate_profiles c where c.seeking=true and c.owner_id<>? and not exists (select 1 from recruitment_matches m where m.job_id=? and m.candidate_id=c.owner_id)",UUID.class,owner,jobId);
            return new Discovery(job,null,ids.stream().map(this::candidate).filter(Objects::nonNull).toList());
        });
        var ranked=ranking.rank(discovery.job(),discovery.candidates());
        if(ranked.isEmpty()) {
            if(discovery.current()==null)return null;
            tx.executeWithoutResult(status->{ownedJob(owner,jobId,true);jdbc.update("update recruitment_matches set status='SKIPPED',updated_at=current_timestamp where id=? and status='PRESENTED'",discovery.current());});
            return discover(owner,jobId,null);
        }
        var selected=ranked.getFirst();
        return tx.execute(status->{
            Job latest=ownedJob(owner,jobId,true);
            lockAccount(selected.candidate().ownerId());
            Candidate actual=candidate(selected.candidate().ownerId());
            if(!latest.open()||!latest.equals(discovery.job())||!selected.candidate().equals(actual)||!eligible(latest,actual))
                throw conflict("JD hoặc hồ sơ vừa thay đổi. Hãy bấm Match lại.");
            var current=jdbc.queryForList("select id from recruitment_matches where job_id=? and status='PRESENTED'",UUID.class,jobId);
            UUID match=discovery.current();
            if(match!=null) {
                if(!current.contains(match))throw conflict("Ứng viên đã được xử lý. Hãy tải lại.");
            }else {
                if(!current.isEmpty()||jdbc.queryForObject("select count(*) from recruitment_matches where job_id=? and candidate_id=?",Long.class,jobId,actual.ownerId())>0)
                    throw conflict("Danh sách ứng viên vừa thay đổi. Hãy bấm Match lại.");
                match=UUID.randomUUID();
                jdbc.update("insert into recruitment_matches(id,job_id,candidate_id,status) values (?,?,?,'PRESENTED')",match,jobId,actual.ownerId());
            }
            return new Suggestion(match,actual,selected.assessment().reasons(),selected.assessment());
        });
    }
    private Pair pair(UUID viewer,UUID id,boolean lock) {
        return jdbc.query("select m.id,m.job_id,j.owner_id,m.candidate_id,m.status from recruitment_matches m join recruitment_jobs j on j.id=m.job_id where m.id=? and (j.owner_id=? or m.candidate_id=?)"+(lock?" for update":""),
            (r,n)->new Pair(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getObject(4,UUID.class),MatchStatus.valueOf(r.getString(5))),id,viewer,viewer).stream().findFirst().orElseThrow(RecruitmentService::missing);
    }
    private Match match(UUID viewer,Pair p) {
        String email=p.status()==MatchStatus.ACCEPTED?notifications.ownerEmail(viewer.equals(p.recruiter())?p.candidate():p.recruiter()):null;
        Instant updated=jdbc.queryForObject("select updated_at from recruitment_matches where id=?",Timestamp.class,p.id()).toInstant();
        return new Match(p.id(),p.status(),job(p.jobId()),candidate(p.candidate()),email,updated);
    }
    public List<Match> matches(UUID viewer) {
        return jdbc.queryForList("select m.id from recruitment_matches m join recruitment_jobs j on j.id=m.job_id where (j.owner_id=? or m.candidate_id=?) and m.status in ('PENDING','ACCEPTED','DECLINED') order by m.updated_at desc,m.id limit 100",UUID.class,viewer,viewer).stream().map(id->match(viewer,pair(viewer,id,false))).toList();
    }
    @Transactional public Match invite(UUID owner,UUID id) {
        Pair initial=pair(owner,id,false);if(!initial.recruiter().equals(owner))throw missing();
        Job job=ownedJob(owner,initial.jobId(),true);lockAccount(initial.candidate());Pair p=pair(owner,id,true);
        if(p.status()==MatchStatus.PENDING||p.status()==MatchStatus.ACCEPTED)return match(owner,p);
        if(p.status()!=MatchStatus.PRESENTED||!job.open()||!eligible(job,candidate(p.candidate())))throw conflict("Ứng viên hoặc JD không còn phù hợp để gửi lời mời.");
        jdbc.update("update recruitment_matches set status='PENDING',updated_at=current_timestamp where id=?",id);
        notify(p.candidate(),"invite:"+id,"RECRUITMENT_INVITE","Bạn có lời mời tuyển dụng",job.company()+" mời bạn trao đổi về vị trí "+job.title());
        return match(owner,pair(owner,id,false));
    }
    @Transactional public Match reply(UUID candidate,UUID id,MatchStatus next) {
        Pair initial=pair(candidate,id,false);if(!initial.candidate().equals(candidate))throw missing();
        ownedJob(initial.recruiter(),initial.jobId(),true);Pair p=pair(candidate,id,true);
        if(next!=MatchStatus.ACCEPTED&&next!=MatchStatus.DECLINED)throw bad("Chỉ có thể đồng ý hoặc từ chối lời mời.");
        if(p.status()==next)return match(candidate,p);
        if(p.status()!=MatchStatus.PENDING)throw conflict("Lời mời đã được xử lý.");
        if(next==MatchStatus.ACCEPTED&&!job(p.jobId()).open())throw conflict("Bài tuyển dụng đã đóng.");
        jdbc.update("update recruitment_matches set status=?,updated_at=current_timestamp where id=?",next.name(),id);
        notify(p.recruiter(),"reply:"+id,"RECRUITMENT_REPLY",next==MatchStatus.ACCEPTED?"Bạn có match tuyển dụng mới":"Ứng viên đã từ chối lời mời",job(p.jobId()).title()+" · "+candidate(candidate).displayName());
        return match(candidate,pair(candidate,id,false));
    }
    private Pair accepted(UUID viewer,UUID id) {Pair p=pair(viewer,id,false);if(p.status()!=MatchStatus.ACCEPTED)throw conflict("Hai bên cần đồng ý match trước khi liên hệ hoặc đặt lịch.");return p;}
    public List<Message> messages(UUID viewer,UUID id) {
        accepted(viewer,id);return jdbc.query("select m.*,a.display_name from recruitment_messages m join accounts a on a.id=m.sender_id where m.match_id=? order by m.created_at desc,m.id desc limit 100",(r,n)->new Message(r.getObject("id",UUID.class),r.getObject("sender_id",UUID.class),r.getString("display_name"),r.getString("body"),r.getTimestamp("created_at").toInstant()),id).reversed();
    }
    @Transactional public void send(UUID viewer,UUID id,String body) {
        Pair p=accepted(viewer,id);UUID message=UUID.randomUUID();jdbc.update("insert into recruitment_messages(id,match_id,sender_id,body) values (?,?,?,?)",message,id,viewer,body.strip());
        notify(viewer.equals(p.recruiter())?p.candidate():p.recruiter(),"message:"+message,"RECRUITMENT_MESSAGE","Tin nhắn tuyển dụng mới",job(p.jobId()).title()+" · "+body.strip());
    }
    private Interview mapInterview(ResultSet r) throws SQLException {return new Interview(r.getObject("id",UUID.class),r.getObject("match_id",UUID.class),r.getTimestamp("starts_at").toInstant(),r.getTimestamp("ends_at").toInstant(),r.getString("meeting_details"),InterviewStatus.valueOf(r.getString("status")));}
    public List<Interview> interviews(UUID viewer,UUID id) {accepted(viewer,id);return jdbc.query("select * from recruitment_interviews where match_id=? order by starts_at desc,id",(r,n)->mapInterview(r),id);}
    private void lockParticipants(Pair p) {var ids=new ArrayList<>(List.of(p.recruiter(),p.candidate()));ids.sort(Comparator.naturalOrder());for(UUID id:ids)lockAccount(id);}
    private boolean occupied(UUID person,Instant start,Instant end) {
        return jdbc.queryForObject("select count(*) from recruitment_interviews where (recruiter_id=? or candidate_id=?) and status in ('PENDING','CONFIRMED') and starts_at<? and ends_at>?",Long.class,person,person,Timestamp.from(end),Timestamp.from(start))>0
            || jdbc.queryForObject("select count(*) from bookings where owner_id=? and status in ('PENDING','CONFIRMED') and starts_at<? and ends_at>?",Long.class,person,Timestamp.from(end),Timestamp.from(start))>0;
    }
    @Transactional public Interview propose(UUID owner,UUID id,InterviewInput in) {
        Pair p=accepted(owner,id);if(!p.recruiter().equals(owner))throw missing();lockParticipants(p);
        Instant now=clock.instant(),end=in.startsAt().plusSeconds(in.durationMinutes()*60L);
        if(!in.startsAt().isAfter(now)||in.startsAt().isAfter(now.plusSeconds(90*86400L)))throw bad("Chọn giờ phỏng vấn trong 90 ngày tới.");
        if(occupied(p.recruiter(),in.startsAt(),end)||occupied(p.candidate(),in.startsAt(),end))throw conflict("Một trong hai người đã có lịch trong khung giờ này.");
        UUID interview=UUID.randomUUID();jdbc.update("insert into recruitment_interviews(id,match_id,recruiter_id,candidate_id,starts_at,ends_at,meeting_details,status) values (?,?,?,?,?,?,?,'PENDING')",interview,id,p.recruiter(),p.candidate(),Timestamp.from(in.startsAt()),Timestamp.from(end),in.meetingDetails().strip());
        notify(p.candidate(),"interview:"+interview,"RECRUITMENT_INTERVIEW","Bạn có đề xuất lịch phỏng vấn",job(p.jobId()).title()+" · "+in.startsAt()+" · "+in.meetingDetails().strip());
        return interviews(owner,id).stream().filter(i->i.id().equals(interview)).findFirst().orElseThrow();
    }
    @Transactional public Interview interviewReply(UUID viewer,UUID matchId,UUID id,InterviewStatus next) {
        Pair p=accepted(viewer,matchId);lockParticipants(p);
        Interview current=jdbc.query("select * from recruitment_interviews where id=? and match_id=? for update",(r,n)->mapInterview(r),id,matchId).stream().findFirst().orElseThrow(RecruitmentService::missing);
        if(next==current.status())return current;
        boolean candidate=viewer.equals(p.candidate());
        boolean allowed=(current.status()==InterviewStatus.PENDING && ((candidate&&(next==InterviewStatus.CONFIRMED||next==InterviewStatus.DECLINED))||next==InterviewStatus.CANCELLED))||(current.status()==InterviewStatus.CONFIRMED&&next==InterviewStatus.CANCELLED);
        if(!allowed)throw conflict("Không thể chuyển trạng thái lịch phỏng vấn này.");
        if(next==InterviewStatus.CONFIRMED&&!current.startsAt().isAfter(clock.instant()))throw bad("Lịch phỏng vấn đã qua.");
        jdbc.update("update recruitment_interviews set status=? where id=?",next.name(),id);
        notify(candidate?p.recruiter():p.candidate(),"interview:"+id+":"+next,"RECRUITMENT_INTERVIEW","Lịch phỏng vấn được cập nhật",job(p.jobId()).title()+" · "+next+" · "+current.startsAt());
        return new Interview(id,matchId,current.startsAt(),current.endsAt(),current.meetingDetails(),next);
    }
    private void notify(UUID owner,String key,String kind,String title,String body) {
        // Recruitment is delivered through the existing in-app notification stream.
        notifications.record(owner,key,kind,title,body,"recruitment",List.of());
    }
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy bài tuyển dụng hoặc match.");}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
