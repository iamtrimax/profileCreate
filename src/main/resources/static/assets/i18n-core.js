const normalize = value => String(value ?? '').replace(/\s+/g, ' ').trim();
const escapeRegex = value => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function compile(template) {
  const indexes = [];
  let cursor = 0, expression = '^';
  for (const match of template.matchAll(/\{(\d+)\}/g)) {
    expression += escapeRegex(template.slice(cursor, match.index)) + '([\\s\\S]*?)';
    indexes.push(Number(match[1]));
    cursor = match.index + match[0].length;
  }
  expression += escapeRegex(template.slice(cursor)) + '$';
  return {regex: new RegExp(expression), indexes};
}

// Only complete, declared UI messages match. Captured names, titles and other
// user values are preserved, rather than translating arbitrary text fragments.
export function createTranslator(catalog) {
  const entries = Object.entries(catalog).map(([vi, en]) => [normalize(vi), normalize(en)]);
  const forward = new Map(entries), reverse = new Map(entries.map(([vi, en]) => [en, vi]));
  const patterns = entries.filter(([vi]) => /\{\d+\}/.test(vi)).map(([vi, en]) => ({
    vi, en, fromVi: compile(vi), fromEn: compile(en),
    specificity: vi.replace(/\{\d+\}/g, '').length
  })).sort((a, b) => b.specificity - a.specificity);

  function translate(value, language = 'vi', depth = 0) {
    const source = String(value ?? ''), text = normalize(source);
    const dictionary = language === 'en' ? forward : reverse;
    if (dictionary.has(text) && !/\{\d+\}/.test(text)) return preserveSpace(source, dictionary.get(text));
    for (const pattern of patterns) {
      const nested = /^(Bổ sung vào|\{0\} Bấm|\{0\} \(tối đa|\{0\} · Thứ tự)/.test(pattern.vi);
      let compiled = language === 'en' ? pattern.fromVi : pattern.fromEn;
      let match = compiled.regex.exec(text);
      // An English API error can be combined with a Vietnamese retry prompt.
      // Normalize these mixed application-owned messages in either direction.
      if (!match && nested) {
        compiled = language === 'en' ? pattern.fromEn : pattern.fromVi;
        match = compiled.regex.exec(text);
      }
      if (!match) continue;
      const values = new Map(compiled.indexes.map((index, position) => [index, match[position + 1]]));
      if (nested && depth < 3 && values.has(0)) {
        values.set(0, values.get(0).split(/([,;]\s*)/).map(part => translate(part, language, depth + 1)).join(''));
      }
      const template = language === 'en' ? pattern.en : pattern.vi;
      return preserveSpace(source, template.replace(/\{(\d+)\}/g, (_, index) => values.get(Number(index)) ?? ''));
    }
    return source;
  }
  return translate;
}

function preserveSpace(source, text) {
  return (source.match(/^\s*/)?.[0] ?? '') + text + (source.match(/\s*$/)?.[0] ?? '');
}
