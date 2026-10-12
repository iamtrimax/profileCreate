package com.trimax.linkhub;

import com.trimax.linkhub.service.JdParser;
import com.trimax.linkhub.api.RecruitmentModels.WorkMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdParserTests {
    private final JdParser parser=new JdParser();
    @Test void markdownFormattingDoesNotChangeExtractedFieldsAndIsPreserved() {
        String raw="""
            # Vị trí: Java Developer
            **Công ty:** LinkHub
            - **Địa điểm:** Hà Nội
            **Hình thức làm việc:** Hybrid
            **Lương:** 20–30 triệu
            ## Yêu cầu
            - Từ **2 năm kinh nghiệm** với `Java`, **Spring Boot**
            ## Ưu tiên
            - Docker
            """;
        var result=parser.parse(raw);assertTrue(result.missingFields().isEmpty(),result.missingFields().toString());
        assertEquals("Java Developer",result.data().role());assertEquals("LinkHub",result.data().company());
        assertEquals("Hà Nội",result.data().location());assertEquals(2,result.data().minExperience());
        assertTrue(result.data().requiredSkills().containsAll(java.util.List.of("Java","Spring Boot")));
        assertEquals(java.util.List.of("Docker"),result.data().preferredSkills());assertEquals(raw,result.data().description());
    }
    @Test void vietnameseJdExtractsStructuredRequirements() {
        var result=parser.parse("""
            Vị trí: Java Developer
            Công ty: LinkHub
            Địa điểm: Hồ Chí Minh
            Hình thức làm việc: Hybrid
            Lương: 20–30 triệu VND/tháng
            Yêu cầu:
            - Từ 2–4 năm kinh nghiệm với Java, Spring Boot và PostgreSQL
            Ưu tiên:
            - Docker, AWS
            """);
        assertTrue(result.missingFields().isEmpty(),result.missingFields().toString());
        var data=result.data();assertEquals("Java Developer",data.role());assertEquals("Hồ Chí Minh",data.location());
        assertEquals(WorkMode.HYBRID,data.workMode());assertEquals(2,data.minExperience());
        assertEquals(20000000,data.salaryMin());assertEquals(30000000,data.salaryMax());
        assertTrue(data.requiredSkills().containsAll(java.util.List.of("Java","Spring Boot","PostgreSQL")));
        assertFalse(data.requiredSkills().contains("Spring"));assertFalse(data.requiredSkills().contains("Docker"));
        assertTrue(data.preferredSkills().containsAll(java.util.List.of("Docker","AWS")));
    }
    @Test void englishRemoteAndNegotiableSalary() {
        var result=parser.parse("""
            Position: Product Designer
            Company: Studio
            Work mode: Remote
            Salary: Negotiable
            Required skills: Figma, User Research
            Preferred skills: English
            """);
        assertTrue(result.missingFields().isEmpty());assertEquals("Từ xa",result.data().location());
        assertEquals(999999999999L,result.data().salaryMax());
        assertTrue(result.data().requiredSkills().contains("User Research"));
        assertEquals(java.util.List.of("English"),result.data().preferredSkills());
    }
    @Test void incompleteJdDoesNotInventCompanyLocationOrSalary() {
        var result=parser.parse("Java Developer\nYêu cầu: Java, Spring Boot");
        assertFalse(result.missingFields().isEmpty());assertNull(result.data().company());
        assertNull(result.data().location());assertNull(result.data().workMode());
    }
    @Test void rejectsUsdAndReversedSalaryAndKeepsOriginalDescription() {
        String jd="Position: Developer\nCompany: Test\nLocation: Hanoi\nWork mode: Onsite\nRequired skills: Java\nSalary: 2000-3000 USD";
        assertFalse(parser.parse(jd).missingFields().isEmpty());
        assertFalse(parser.parse(jd.replace("2000-3000 USD","30-20 triệu")).missingFields().isEmpty());
        assertEquals(jd,parser.parse(jd).data().description());
    }
    @Test void readsVndThousandsAndDoesNotConfuseJavaWithJavascript() {
        var result=parser.parse("Position: Frontend Developer\nCompany: Test\nLocation: Hanoi\nWork mode: Onsite\nRequirements:\nJavaScript and React\nSalary: 20.000.000 - 30.000.000 VND");
        assertTrue(result.missingFields().isEmpty());assertEquals(20000000,result.data().salaryMin());
        assertFalse(result.data().requiredSkills().contains("Java"));assertTrue(result.data().requiredSkills().contains("JavaScript"));
    }
}
