package com.trimax.linkhub.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class RecruitmentModels {
    private RecruitmentModels() {}
    public enum WorkMode { ANY, ONSITE, HYBRID, REMOTE }
    public enum MatchStatus { PRESENTED, SKIPPED, PENDING, ACCEPTED, DECLINED }
    public enum InterviewStatus { PENDING, CONFIRMED, DECLINED, CANCELLED }
    public record CandidateInput(boolean seeking,
        @NotBlank @Size(max=120) String desiredRole, @NotNull @Size(max=3000) String summary,
        @Min(0) @Max(60) int experienceYears, @NotBlank @Size(max=120) String location,
        @NotNull WorkMode workMode, @Min(0) @Max(999999999999L) long salaryMin,
        @Min(0) @Max(999999999999L) long salaryMax,
        @NotNull @Size(min=1,max=30) List<@NotBlank @Size(max=80) String> skills) {}
    public record Candidate(UUID ownerId, String displayName, boolean seeking, String desiredRole,
        String summary, int experienceYears, String location, WorkMode workMode,
        long salaryMin, long salaryMax, List<String> skills) {}
    public record JobInput(@NotBlank @Size(max=120) String title,
        @NotBlank @Size(max=120) String company, @NotBlank @Size(max=120) String role,
        @NotBlank @Size(max=6000) String description, @Min(0) @Max(60) int minExperience,
        @NotBlank @Size(max=120) String location, @NotNull WorkMode workMode,
        @Min(0) @Max(999999999999L) long salaryMin, @Min(0) @Max(999999999999L) long salaryMax,
        @NotNull @Size(min=1,max=30) List<@NotBlank @Size(max=80) String> requiredSkills,
        @NotNull @Size(max=30) List<@NotBlank @Size(max=80) String> preferredSkills) {}
    public record Job(UUID id, UUID ownerId, String recruiterName, String title, String company,
        String role, String description, int minExperience, String location, WorkMode workMode,
        long salaryMin, long salaryMax, boolean open, Instant createdAt,
        List<String> requiredSkills, List<String> preferredSkills, String formattedDescription) {
        public Job(UUID id,UUID ownerId,String recruiterName,String title,String company,String role,String description,
            int minExperience,String location,WorkMode workMode,long salaryMin,long salaryMax,boolean open,Instant createdAt,
            List<String> requiredSkills,List<String> preferredSkills) {
            this(id,ownerId,recruiterName,title,company,role,description,minExperience,location,workMode,salaryMin,salaryMax,open,createdAt,requiredSkills,preferredSkills,description);
        }
    }
    public record OpenInput(boolean open) {}
    public record JdInput(@NotBlank @Size(max=6000) String description,@Size(max=36) String extractionId) {}
    public record ParsedJd(JobInput data, List<String> missingFields, String source, List<String> warnings, String extractionId, String formattedDescription) {
        public ParsedJd(JobInput data,List<String> missingFields,String source,List<String> warnings,String extractionId){this(data,missingFields,source,warnings,extractionId,data.description());}
        public ParsedJd(JobInput data,List<String> missingFields){this(data,missingFields,"rules",List.of(),null);}
        public ParsedJd(JobInput data,List<String> missingFields,String source,List<String> warnings){this(data,missingFields,source,warnings,null);}
    }
    public record MatchAssessment(int score, int ruleScore, Integer llmScore, String source,
        Map<String,Integer> components, List<String> matchedSkills, List<String> missingSkills,
        List<String> reasons, List<String> gaps) {}
    public record Suggestion(UUID matchId, Candidate candidate, List<String> reasons, MatchAssessment assessment) {}
    public record Match(UUID id, MatchStatus status, Job job, Candidate candidate,
        String contactEmail, Instant updatedAt) {}
    public record Reply(@NotNull MatchStatus status) {}
    public record MessageInput(@NotBlank @Size(max=2000) String body) {}
    public record Message(UUID id, UUID senderId, String senderName, String body, Instant createdAt) {}
    public record InterviewInput(@NotNull Instant startsAt, @Min(15) @Max(180) int durationMinutes,
        @NotBlank @Size(max=2000) String meetingDetails) {}
    public record InterviewReply(@NotNull InterviewStatus status) {}
    public record Interview(UUID id, UUID matchId, Instant startsAt, Instant endsAt,
        String meetingDetails, InterviewStatus status) {}
}
