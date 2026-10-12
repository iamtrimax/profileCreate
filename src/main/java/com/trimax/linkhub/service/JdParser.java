package com.trimax.linkhub.service;

import com.trimax.linkhub.api.RecruitmentModels.*;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Local, conservative extraction: missing requirements are never invented. */
@Service
public class JdParser {
    private static final List<String> SKILLS=List.of("Java", "Spring Boot", "Spring", "PostgreSQL", "MySQL", "SQL", "Docker", "Kubernetes", "AWS", "Azure", "GCP", "Python", "JavaScript", "TypeScript", "React", "Vue", "Angular", "Node.js", "C#", "C++", ".NET", "PHP", "Laravel", "Go", "Rust", "Redis", "MongoDB", "Git", "Linux", "HTML", "CSS", "Figma", "Excel", "SEO", "Photoshop", "Illustrator", "Power BI", "Tableau", "Kotlin", "Swift", "Flutter", "Dart", "Jira", "Scrum", "REST", "GraphQL", "Kafka", "RabbitMQ", "CI/CD", "Terraform", "English", "tieng Anh");
    private static String plain(String value) {return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT);}
    private static String labeled(String text,String labels) {
        Matcher m=Pattern.compile("(?im)^\\s*(?:[-*•]\\s*)?(?:"+labels+")\\s*[:：]\\s*(.+)$").matcher(text);
        return m.find()?m.group(1).strip():null;
    }
    private static String field(String raw,String labels) {
        // Match accent-insensitively, but preserve the original extracted value.
        String[] lines=raw.split("\\R");
        for(String line:lines)if(labeled(plain(line),labels)!=null){int colon=Math.max(line.indexOf(':'),line.indexOf('：'));return line.substring(colon+1).strip();}
        return null;
    }
    private static boolean contains(String line,String skill) {return Pattern.compile("(?<![\\p{L}\\p{N}])"+Pattern.quote(plain(skill))+"(?![\\p{L}\\p{N}])").matcher(plain(line)).find();}
    private static String markdownText(String value) {
        return value.replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "")
            .replaceAll("(?m)^\\s*(?:>\\s*)?(?:[-*+]\\s+|\\d+[.)]\\s+)?", "")
            .replaceAll("\\*\\*([^\\n]+?)\\*\\*|__([^\\n]+?)__", "$1$2")
            .replaceAll("`([^`\\n]+)`", "$1")
            .replaceAll("\\[([^\\]\\n]+)\\]\\([^\\s)]+\\)", "$1");
    }
    public ParsedJd parse(String markdownRaw) {
        String raw=markdownText(markdownRaw);
        String text=plain(raw);List<String> missing=new ArrayList<>();
        String role=field(raw,"vi tri(?: tuyen dung)?|chuc danh|job title|position|role|title");
        if(role==null) {
            for(String line:raw.split("\\R")) {
                String normalized=plain(line).strip();
                if(normalized.length()<=120&&Pattern.compile("developer|engineer|designer|manager|analyst|nhan vien|chuyen vien|lap trinh|ke toan|marketing|sales").matcher(normalized).find()) {
                    role=line.strip().replaceFirst("(?iu)^(?:tuyển dụng|tuyển|hiring|we are hiring)\\s*[:：-]?\\s*","");break;
                }
            }
        }
        String company=field(raw,"cong ty|doanh nghiep|company|employer");
        String location=field(raw,"dia diem(?: lam viec)?|noi lam viec|location|work location");
        WorkMode mode=null;
        String modeText=field(raw,"hinh thuc(?: lam viec)?|work mode|working mode|work arrangement");
        String modeSource=modeText==null?text:plain(modeText);
        if(modeSource.matches("(?s).*(hybrid|ket hop).*"))mode=WorkMode.HYBRID;
        else if(modeSource.matches("(?s).*(remote|tu xa).*"))mode=WorkMode.REMOTE;
        else if(modeSource.matches("(?s).*(onsite|on-site|tai van phong).*"))mode=WorkMode.ONSITE;
        if(location==null&&mode==WorkMode.REMOTE)location="Từ xa";
        int years=0;
        Matcher experience=Pattern.compile("(\\d{1,2})(?:\\s*[-–]\\s*\\d{1,2})?\\+?\\s*(?:nam|years?)(?:\\s+(?:kinh nghiem|of experience|experience))").matcher(text);
        if(experience.find())years=Integer.parseInt(experience.group(1));
        else {String value=field(raw,"kinh nghiem(?: toi thieu)?|experience|min(?:imum)? experience");if(value!=null){Matcher number=Pattern.compile("\\d{1,2}").matcher(value);if(number.find())years=Integer.parseInt(number.group());}}
        Set<String> required=new LinkedHashSet<>(),preferred=new LinkedHashSet<>();boolean optional=false,requirements=false;
        for(String line:raw.split("\\R")) {
            String n=plain(line);
            if(n.matches(".*(?:yeu cau|requirements|must have|bat buoc).*")){optional=false;requirements=true;}
            if(n.matches(".*(?:quyen loi|benefits|mo ta cong viec|responsibilities).*")){optional=false;requirements=false;}
            boolean preference=n.matches(".*(?:uu tien|preferred|nice.to.have|a plus|advantage).*");
            if(preference&&n.strip().matches("(?:ky nang uu tien|uu tien|preferred(?: skills)?|nice.to.have)\\s*[:：]?"))optional=true;
            Set<String> target=optional||preference?preferred:required;
            String explicit=field(line,"ky nang(?: bat buoc| uu tien)?|required skills|preferred skills|skills");
            if(explicit!=null)for(String skill:explicit.split("[,;]")){String value=skill.strip();if(!value.isEmpty()&&value.length()<=80)target.add(value);}
            if(requirements||preference||optional||explicit!=null)for(String skill:SKILLS)if(contains(line,skill))target.add(skill);
        }
        // For an unsectioned JD, recognize only explicit skill mentions, never the title.
        if(required.isEmpty()&&preferred.isEmpty())for(String line:raw.split("\\R"))if(!line.strip().equals(role))for(String skill:SKILLS)if(contains(line,skill))required.add(skill);
        preferred.removeIf(a->required.stream().anyMatch(b->plain(a).equals(plain(b))));
        if(required.stream().anyMatch(s->s.equalsIgnoreCase("Spring Boot")))required.removeIf(s->s.equalsIgnoreCase("Spring"));
        long min=0,max=999999999999L;
        String salary=field(raw,"luong|muc luong|thu nhap|salary|salary range");
        boolean salaryKnown=salary!=null&&plain(salary).matches(".*(?:thuong luong|thoa thuan|negotiable).*");
        if(salary!=null&&!salaryKnown){Matcher range=Pattern.compile("(\\d[\\d.,]*)\\s*(trieu|tr|million|m)?\\s*[-–]\\s*(\\d[\\d.,]*)\\s*(trieu|tr|million|m|vnd|dong)?",Pattern.CASE_INSENSITIVE).matcher(plain(salary));if(range.find()&&!plain(salary).matches(".*(?:usd|\\$|eur).*")){String unit=range.group(4)!=null?range.group(4):range.group(2);try{min=amount(range.group(1),unit);max=amount(range.group(3),unit);salaryKnown=max>=min&&max<=999999999999L;}catch(NumberFormatException ignored){salaryKnown=false;}}}
        check(role,"vị trí tuyển dụng",120,missing);check(company,"công ty",120,missing);check(location,"địa điểm",120,missing);
        if(mode==null)missing.add("hình thức làm việc (onsite, hybrid hoặc remote)");
        if(years>60)missing.add("kinh nghiệm từ 0 đến 60 năm");
        if(required.isEmpty()||required.size()>30||preferred.size()>30)missing.add("kỹ năng bắt buộc (tối đa 30 kỹ năng)");
        if(!salaryKnown)missing.add("lương VND (ví dụ 20–30 triệu) hoặc ghi rõ lương thỏa thuận");
        return new ParsedJd(new JobInput(role,company,role,markdownRaw,years,location,mode,min,max,List.copyOf(required),List.copyOf(preferred)),List.copyOf(missing));
    }
    private static void check(String value,String label,int max,List<String> missing){if(value==null||value.isBlank()||value.length()>max)missing.add(label+" (tối đa "+max+" ký tự)");}
    private static long amount(String value,String unit){boolean millions=unit!=null&&List.of("trieu","tr","million","m").contains(unit.toLowerCase(Locale.ROOT));return millions?Math.round(Double.parseDouble(value.replace(',','.'))*1000000):Long.parseLong(value.replaceAll("[.,]",""));}
}
