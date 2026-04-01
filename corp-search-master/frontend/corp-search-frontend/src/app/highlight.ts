export function highlight(text: string, q: string): string {
    const source = text ?? "";
    const query = q.trim();
    if (!query) return escapeHtml(source);

    const terms = query
        .split(/\s+/)
        .map((item) => item.trim())
        .filter((item) => item.length >= 2)
        .sort((left, right) => right.length - left.length)
        .slice(0, 8);

    if (terms.length === 0) return escapeHtml(source);

    const matcher = new RegExp(terms.map(escapeRegExp).join("|"), "ig");
    let html = "";
    let lastIndex = 0;

    for (const match of source.matchAll(matcher)) {
        const index = match.index ?? -1;
        const value = match[0] ?? "";
        if (index < 0 || !value) continue;

        html += escapeHtml(source.slice(lastIndex, index));
        html += `<mark>${escapeHtml(value)}</mark>`;
        lastIndex = index + value.length;
    }

    html += escapeHtml(source.slice(lastIndex));
    return html;
}

function escapeRegExp(value: string) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function escapeHtml(value: string) {
    return value
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
