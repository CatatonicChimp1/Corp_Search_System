import React from "react";
import type { SearchHit } from "../app/api";
import { highlight } from "../app/highlight";

const KIND_LABELS: Record<SearchHit["kind"], string> = {
    DOCUMENT: "Документ",
    FILE: "Файл",
    SOURCE: "Источник",
    USER: "Пользователь"
};

export function ResultList({
    hits,
    query,
    onOpen
}: {
    hits: SearchHit[];
    query: string;
    onOpen: (docId?: number | null) => void;
}) {
    if (hits.length === 0) {
        return <div className="card muted">Ничего не найдено. Попробуйте изменить текст запроса или фильтры.</div>;
    }

    return (
        <div className="stack">
            {hits.map((hit, index) => {
                const tags = hit.tags ?? [];

                return (
                    <a
                        key={`${hit.kind}-${hit.docId ?? hit.sourceId ?? index}-${hit.title}`}
                        className="card"
                        href={hit.route}
                        onClick={() => onOpen(hit.docId)}
                    >
                        <div className="row" style={{ justifyContent: "space-between", alignItems: "center", gap: 12 }}>
                            <div style={{ fontWeight: 800, marginBottom: 6 }}>{hit.title}</div>
                            <span className="tag">{KIND_LABELS[hit.kind]}</span>
                        </div>

                        <div
                            className="muted"
                            style={{ lineHeight: 1.4 }}
                            dangerouslySetInnerHTML={{ __html: highlight(hit.snippet, query) }}
                        />

                        <div className="row" style={{ marginTop: 8, flexWrap: "wrap" }}>
                            {hit.match && <span className="tag">Совпадение: {hit.match}</span>}
                            {hit.sourceName && (
                                <span className="tag">
                                    {hit.sourceName}{hit.sourceKind ? ` (${hit.sourceKind})` : ""}
                                </span>
                            )}
                            {tags.map((tag) => (
                                <span className="tag" key={tag}>{tag}</span>
                            ))}
                        </div>
                    </a>
                );
            })}
        </div>
    );
}
