import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';

const assets = new URL('../../main/resources/static/assets/', import.meta.url);
const core = await readFile(new URL('i18n-core.js', assets), 'utf8');
const {createTranslator} = await import('data:text/javascript;base64,' + Buffer.from(core).toString('base64'));
const catalog = JSON.parse(await readFile(new URL('i18n-en.json', assets), 'utf8'));
const translate = createTranslator(catalog);
const t = (value, language) => translate(translate(value, 'vi'), language);

for (const [vi, en] of Object.entries(catalog)) {
  const source = vi.replace(/\{\d+\}/g, 'Example');
  const expected = en.replace(/\{\d+\}/g, 'Example');
  assert.equal(t(source, 'en'), expected, vi);
  assert.equal(t(expected, 'en'), expected, `English must be idempotent: ${vi}`);
}
assert.equal(t('Đang phân tích JD…', 'en'), 'Analyzing job description…');
assert.equal(t('Sửa JD', 'en'), 'Edit job description');
assert.equal(t('Trang 12', 'en'), 'Page 12');
assert.equal(t('Page 12', 'vi'), 'Trang 12');
assert.equal(t('Xin chào, Đăng nhập', 'en'), 'Hello, Đăng nhập', 'Do not translate a captured name');
assert.equal(t('Ứng viên: Bạn', 'en'), 'Candidate: Bạn');
assert.equal(t('Bổ sung vào JD: công ty, địa điểm', 'en'), 'Add to the job description: company, location');
assert.equal(t('Bổ sung vào nội dung JD: vị trí tuyển dụng; công ty (tối đa 120 ký tự)', 'en'), 'Add to the job description: job role; company (up to 120 characters)');
assert.equal(t('Groq phản hồi quá thời gian chờ. Vui lòng thử lại sau. Bấm Phân tích lại để thử lại.', 'en'), 'Groq timed out. Please try again later. Click Analyze again to retry.');
assert.equal(t('Groq timed out. Please try again later. Bấm Phân tích lại để thử lại.', 'vi'), 'Groq phản hồi quá thời gian chờ. Vui lòng thử lại sau. Bấm Phân tích lại để thử lại.');
assert.equal(t('3 lịch đã xác nhận đang diễn ra hoặc bắt đầu trong 24 giờ tới. Có 2 lịch sắp tới đang chờ bạn xác nhận.', 'en'), '3 confirmed appointments are in progress or start within the next 24 hours. 2 upcoming appointments await your confirmation.');
assert.equal(t('9 năm kinh nghiệm · yêu cầu từ 4 năm', 'en'), '9 years of experience · at least 4 years required');
assert.equal(t('  Đăng nhập  ', 'en'), '  Log in  ');
assert.equal(t('One unrelated sentence written by a user.', 'vi'), 'One unrelated sentence written by a user.');
assert.equal(t('Đây là nội dung do người dùng nhập.', 'en'), 'Đây là nội dung do người dùng nhập.');

// All static UI text and accessible attributes must be covered. Language names,
// template brand names and location examples are intentionally kept as authored.
const exempt = new Set(['Tiếng Việt', 'Ngôn ngữ / Language', 'Hồ Chí Minh', 'Profile · LinkHub VN']);
for (const file of ['../../pages/app.html', '../../pages/profile.html', 'metrics.html']) {
  const html = await readFile(new URL(file, assets), 'utf8');
  const values = [...html.matchAll(/>([^<>]+)</g)].map(match => match[1]);
  values.push(...[...html.matchAll(/(?:title|placeholder|aria-label|alt)="([^"]+)"/g)].map(match => match[1]));
  for (const value of values) {
    const text = value.replace(/\s+/g, ' ').trim();
    if (!/[À-ɏ\u1e00-\u1eff]/.test(text) || exempt.has(text)) continue;
    assert.notEqual(t(text, 'en'), text, `Missing static translation in ${file}: ${text}`);
  }
}
console.log(`PASS: ${Object.keys(catalog).length} catalogue entries, dynamic messages, API errors, static coverage and preserved user parameters`);
