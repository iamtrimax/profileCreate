package com.trimax.linkhub.service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Presentation only: original text remains the source for extraction and matching. */
public final class JdFormatter {
    private JdFormatter() {}
    private static String key(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).strip().replaceAll("[:：]+$","").strip();
    }
    private static String section(String line) {
        return switch(key(line)) {
            case "mo ta cong viec","noi dung cong viec","trach nhiem","job description","responsibilities" -> "Mô tả công việc";
            case "yeu cau","yeu cau tuyen dung","yeu cau cong viec","yeu cau ung vien","requirements","qualifications" -> "Yêu cầu ứng viên";
            case "uu tien","ky nang uu tien","diem cong","nice to have","nice-to-have","preferred qualifications" -> "Ưu tiên";
            case "quyen loi","quyen loi va che do","phuc loi","che do dai ngo","benefits" -> "Quyền lợi & chế độ";
            case "cach ung tuyen","huong dan ung tuyen","thong tin ung tuyen","how to apply" -> "Cách ứng tuyển";
            case "ve cong ty","gioi thieu cong ty","about us","about the company" -> "Giới thiệu công ty";
            default -> null;
        };
    }
    private static String metadata(String line) {
        var match=Pattern.compile("^(Công ty|Company|Vị trí tuyển dụng|Vị trí|Position|Địa chỉ công ty|Địa chỉ làm việc|Địa điểm|Location|Ngành nghề|Trạng thái|Hình thức|Lương|Salary|Thanh toán lương)([:：]?)(\\s+)(.+)$",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(line);
        return match.matches()?"**"+match.group(1)+match.group(2)+"** "+match.group(4):line;
    }
    private static boolean metadataLabel(String line) {
        return Set.of("cong ty","company","vi tri tuyen dung","vi tri","position","dia chi cong ty",
            "dia chi lam viec","dia diem","location","nganh nghe","trang thai","hinh thuc","luong",
            "salary","thanh toan luong").contains(key(line));
    }
    private static boolean title(String line) {
        String k=key(line);
        return k.equals("ban mo ta cong viec")||k.equals("job description")
            ||k.startsWith("ban mo ta cong viec ")||k.startsWith("job description ");
    }
    public static String format(String source) {
        if(source==null||source.isBlank())return "";
        String text=source.replace("\r\n","\n").replace('\r','\n').strip();
        // Keep authored Markdown intact, including fences, links and nested lists.
        if(Pattern.compile("(?m)^\\s{0,3}(?:#{1,6}\\s|```|~~~)").matcher(text).find())return text;
        List<String> lines=Arrays.asList(text.split("\n",-1));
        StringBuilder out=new StringBuilder();List<String> paragraph=new ArrayList<>();
        boolean bullets=false,started=false;
        for(int i=0;i<lines.size();i++) {
            String line=lines.get(i).strip();
            if(line.isEmpty()){flush(out,paragraph,bullets);continue;}
            if(!started&&out.isEmpty()&&title(line)) {
                String k=key(line);
                if((k.equals("ban mo ta cong viec")||k.equals("job description"))&&i+1<lines.size()) {
                    String next=lines.get(i+1).strip();
                    if(!next.isEmpty()&&!metadataLabel(next)&&section(next)==null&&metadata(next).equals(next)) {
                        line+=" "+next;i++;
                    }
                }
                out.append("# ").append(line).append("\n\n");continue;
            }
            String heading=section(line);
            if(heading!=null) {
                flush(out,paragraph,bullets);
                // Preserve the source heading as well as its language/wording.
                out.append("\n## ").append(line.replaceAll("[:：]+$","")).append("\n\n");
                bullets=!heading.equals("Giới thiệu công ty");started=true;continue;
            }
            if(!started) {
                if(metadataLabel(line)) {
                    String label=line;List<String> value=new ArrayList<>();
                    while(i+1<lines.size()) {
                        String next=lines.get(i+1).strip();
                        if(next.isEmpty()||metadataLabel(next)||section(next)!=null||!metadata(next).equals(next))break;
                        if(!value.isEmpty()&&Character.isUpperCase(next.codePointAt(0)))break;
                        value.add(next);i++;
                    }
                    out.append("**").append(label).append("**");
                    if(!value.isEmpty())out.append(" ").append(String.join(" ",value));
                    out.append("\n\n");continue;
                }
                // Introductory metadata stays verbatim instead of being guessed into new fields.
                out.append(metadata(line)).append("\n\n");continue;
            }
            boolean marked=line.matches("^(?:[-*•]|[0-9]+[.)])\\s+.*");
            if(marked){flush(out,paragraph,bullets);line=line.replaceFirst("^(?:[-*•]|[0-9]+[.)])\\s+","");}
            else if(!paragraph.isEmpty()&&Character.isUpperCase(line.codePointAt(0)))flush(out,paragraph,bullets);
            paragraph.add(line);
            if(line.matches(".*[.!?;。]$"))flush(out,paragraph,bullets);
        }
        flush(out,paragraph,bullets);
        return out.toString().strip();
    }
    private static void flush(StringBuilder out,List<String> paragraph,boolean bullets) {
        if(paragraph.isEmpty())return;
        if(bullets)out.append("- ");
        out.append(String.join(" ",paragraph)).append(bullets?"\n":"\n\n");paragraph.clear();
    }
}
