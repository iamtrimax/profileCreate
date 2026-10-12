package com.trimax.linkhub.service;

import com.trimax.linkhub.api.RecruitmentModels.*;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;

@Service
public class RecruitmentScorer {
    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT).strip().replaceAll("\\s+",
                " ");
    }

    static String role(String value) {
        return normalize(value).replaceAll("front[ -]?end", "frontend").replaceAll("back[ -]?end", "backend")
                .replaceAll("full[ -]?stack", "fullstack")
                .replaceAll("\\b(lập trình viên|developer|engineer|dev)\\b", "developer")
                .replaceAll("\\b(junior|senior|middle|mid|fresher)\\b", "").replaceAll("\\s+", " ").strip();
    }

    static String skill(String value) {
        String key = normalize(value).replaceAll("[ ._-]", "");
        return switch (key) {
            case "react", "reactjs" -> "react";
            case "node", "nodejs" -> "nodejs";
            case "js", "javascript", "javascriptes6", "javascriptes6+", "es6", "es6+" -> "javascript";
            case "ts", "typescript" -> "typescript";
            case "html", "html5" -> "html";
            case "css", "css3" -> "css";
            case "restapi", "restfulapi" -> "restapi";
            case "postgres", "postgresql" -> "postgresql";
            default -> key;
        };
    }

    public boolean compatible(Job job, Candidate c) {
        return c != null && c.seeking() && !job.ownerId().equals(c.ownerId())
                && (c.workMode() == WorkMode.ANY || c.workMode() == job.workMode())
                && (job.workMode() == WorkMode.REMOTE || normalize(job.location()).equals(normalize(c.location())));
    }

    public MatchAssessment assess(Job job, Candidate c, Integer semantic) {
        Set<String> offered = new HashSet<>();
        c.skills().forEach(s -> offered.add(skill(s)));
        List<String> matched = job.requiredSkills().stream().filter(s -> offered.contains(skill(s))).toList();
        List<String> missing = job.requiredSkills().stream().filter(s -> !offered.contains(skill(s))).toList();
        String jr = role(job.role()), cr = role(c.desiredRole());
        Set<String> jt = new HashSet<>(List.of(jr.split("\\s+"))), ct = new HashSet<>(List.of(cr.split("\\s+")));
        jt.remove("developer");
        ct.remove("developer");
        Set<String> intersection = new HashSet<>(jt);
        intersection.retainAll(ct);
        int position = jr.equals(cr) ? 25 : intersection.isEmpty() ? 0 : 15;
        int required = job.requiredSkills().isEmpty() ? 40
                : (int) Math.round(40.0 * matched.size() / job.requiredSkills().size());
        int experience = job.minExperience() == 0 ? 10
                : (int) Math.round(10.0 * Math.min(1.0, (double) c.experienceYears() / job.minExperience()));
        boolean salary = c.salaryMin() <= job.salaryMax() && job.salaryMin() <= c.salaryMax();
        List<String> preferred = job.preferredSkills().stream().filter(s -> offered.contains(skill(s))).toList();
        int bonus = job.preferredSkills().isEmpty() ? 5
                : (int) Math.round(5.0 * preferred.size() / job.preferredSkills().size());
        Map<String, Integer> components = new LinkedHashMap<>();
        components.put("role", position);
        components.put("requiredSkills", required);
        components.put("experience", experience);
        components.put("workMode", compatible(job, c) ? 10 : 0);
        components.put("location",
                job.workMode() == WorkMode.REMOTE || normalize(job.location()).equals(normalize(c.location())) ? 5 : 0);
        components.put("salary", salary ? 5 : 0);
        components.put("preferredSkills", bonus);
        int rule = components.values().stream().mapToInt(Integer::intValue).sum();
        int score = semantic == null ? rule : (int) Math.round(rule * .8 + semantic * .2);
        List<String> reasons = new ArrayList<>(), gaps = new ArrayList<>();
        if (position == 25)
            reasons.add("Vị trí phù hợp sau khi chuẩn hóa tên");
        else if (position > 0)
            reasons.add("Vị trí có chuyên môn liên quan");
        else
            gaps.add("Tên vị trí khác JD; cần xem xét chuyên môn");
        if (!matched.isEmpty())
            reasons.add("Kỹ năng bắt buộc khớp: " + String.join(", ", matched));
        if (!missing.isEmpty())
            gaps.add("Chưa ghi nhận kỹ năng: " + String.join(", ", missing));
        if (c.experienceYears() >= job.minExperience())
            reasons.add(c.experienceYears() + " năm kinh nghiệm · yêu cầu từ " + job.minExperience() + " năm");
        else
            gaps.add("Kinh nghiệm " + c.experienceYears() + " / " + job.minExperience() + " năm yêu cầu");
        if (salary)
            reasons.add("Khoảng lương tương thích");
        else
            gaps.add("Khoảng lương chưa giao nhau; cần thương lượng");
        if (!preferred.isEmpty())
            reasons.add("Kỹ năng ưu tiên: " + String.join(", ", preferred));
        if (semantic != null)
            reasons.add("Đánh giá ngữ nghĩa bằng AI: " + semantic + "/100");
        return new MatchAssessment(score, rule, semantic, semantic == null ? "rules" : "rules+groq",
                Map.copyOf(components), matched, missing, List.copyOf(reasons), List.copyOf(gaps));
    }

    public boolean shortlist(Job job, Candidate c) {
        if (!compatible(job, c))
            return false;
        MatchAssessment a = assess(job, c, null);
        return a.ruleScore() >= 45 && !a.matchedSkills().isEmpty();
    }

    public boolean recommend(MatchAssessment a) {
        return a.score() >= 60 && (a.components().get("role") > 0 || (a.llmScore() != null && a.llmScore() >= 70));
    }
}
