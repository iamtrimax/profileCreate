package com.trimax.linkhub.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdFormatterTests {
    @Test void formatsPdfWithTitleAndMetadataOnSeparateLines() throws Exception {
        String source=resource("/jd-pdf-input.txt"),expected=resource("/jd-pdf-output.md").strip();
        assertEquals(expected,JdFormatter.format(source));
        assertEquals(words(source),words(expected));
    }
    @Test void doesNotTakeAnotherLabelOrSectionAsMissingMetadataValue() {
        String formatted=JdFormatter.format("CÔNG TY\nĐỊA ĐIỂM\nSeoul\nYêu cầu\nReact");
        assertTrue(formatted.startsWith("**CÔNG TY**\n\n**ĐỊA ĐIỂM** Seoul"));
        assertTrue(formatted.contains("## Yêu cầu\n\n- React"));
    }
    private String resource(String name) throws Exception {
        try(var input=getClass().getResourceAsStream(name)) {
            return new String(java.util.Objects.requireNonNull(input).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    @Test void formatsVietnameseJdAndPreservesAllWordingIncludingFreelanceAndSalary() {
        String source="""
            BẢN MÔ TẢ CÔNG VIỆC Frontend Developer
            Công ty Quarry Systems
            ĐỊA CHỈ LÀM VIỆC Seoul
            Mô tả công việc
            Quarry Systems phát triển nền tảng dành cho các tổ chức
            tài chính và doanh nghiệp lớn.
            Phát triển Dashboard bằng ReactJS và TypeScript.
            Yêu cầu tuyển dụng
            Tối thiểu 2 năm kinh nghiệm Frontend.
            Thành thạo HTML5, CSS3 và JavaScript ES6+.
            Có kinh nghiệm Redux Toolkit hoặc Context API.
            Ưu tiên
            Docker hoặc CI/CD.
            Quyền lợi và chế độ
            Hình thức: Freelance.
            Lương thoả thuận. Phỏng vấn online.
            Thanh toán lương: Ngày 15 hàng tháng.
            """;
        String formatted=JdFormatter.format(source);
        assertTrue(formatted.startsWith("# BẢN MÔ TẢ CÔNG VIỆC"));
        assertTrue(formatted.contains("**Công ty** Quarry Systems"));assertTrue(formatted.contains("## Yêu cầu tuyển dụng"));
        assertTrue(formatted.contains("- Tối thiểu 2 năm kinh nghiệm Frontend.\n- Thành thạo"));
        assertEquals(words(source),words(formatted));assertFalse(formatted.contains("Remote"));
        assertEquals(formatted,JdFormatter.format(formatted));
    }
    @Test void existingMarkdownAndCodeAreKeptIntact() {
        String markdown="# Java Developer\n\n## Yêu cầu\n- **Java**\n- [Ứng tuyển](https://example.com)\n\n```text\n<script>text</script>\n```";
        assertEquals(markdown,JdFormatter.format(markdown));
    }
    @Test void handlesEnglishHeadingsUnpunctuatedListsAndIncompleteJds() {
        String formatted=JdFormatter.format("Company: Example\nRequirements\n- React\n- TypeScript\nBenefits\nFlexible schedule");
        assertTrue(formatted.contains("## Requirements"));assertTrue(formatted.contains("- React\n- TypeScript"));
        assertTrue(formatted.contains("## Benefits"));assertTrue(formatted.contains("Flexible schedule"));
        assertEquals("",JdFormatter.format(" "));assertEquals("Some plain text",JdFormatter.format("Some plain text"));
    }
    private String words(String text){return text.replaceAll("(?m)^#{1,6}\\s+","").replaceAll("(?m)^-\\s+","").replace("**","").replaceAll("\\s+"," ").strip();}
}
