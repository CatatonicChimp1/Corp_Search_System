import React, { useEffect, useState } from "react";
import { api, type DocDto, type SourceDto } from "../app/api";
import { highlight } from "../app/highlight";
import { Toast } from "../ui/Toast";

function isStoredFile(body: string) {
    return body.startsWith("FILE:");
}

export function DocPage({ id }: { id: number }) {
    const [doc, setDoc] = useState<DocDto | null>(null);
    const [sources, setSources] = useState<SourceDto[]>([]);
    const [err, setErr] = useState<string | null>(null);
    const lastQ = localStorage.getItem("last_query") || "";

    useEffect(() => {
        api.getDoc(id).then(setDoc).catch((e) => setErr(String(e?.message || e)));
        api.listSources().then(setSources).catch(() => {});
    }, [id]);

    const source = sources.find((item) => item.id === doc?.sourceId);
    const tags = doc?.tags ?? [];

    if (!doc) return <div className="card muted">Загрузка...</div>;

    return (
        <div className="stack">
            <div className="card stack">
                <div className="row" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
                    <div style={{ fontWeight: 900, fontSize: 18 }}>{doc.title}</div>
                    <a className="btn" href="#/search">Назад</a>
                </div>

                <div className="muted small">
                    Группа данных: {source ? `${source.name} (${source.kind})` : doc.sourceId} • Автор: {doc.author || "—"}
                    <br />
                    Обновлено: {doc.updatedAt}
                </div>

                <div>
                    {tags.map((tag) => (
                        <span className="tag" key={tag}>{tag}</span>
                    ))}
                </div>

                <hr />

                {isStoredFile(doc.body) ? (
                    <div className="stack">
                        <div className="muted">
                            Для этого документа хранится загруженный файл. Его можно скачать отдельно.
                        </div>
                        <div className="row">
                            <a className="btn primary" href={`http://localhost:8080/api/docs/${doc.id}/download`}>
                                Скачать файл
                            </a>
                        </div>
                    </div>
                ) : (
                    <div
                        style={{ whiteSpace: "pre-wrap", lineHeight: 1.55 }}
                        dangerouslySetInnerHTML={{ __html: highlight(doc.body, lastQ) }}
                    />
                )}
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
