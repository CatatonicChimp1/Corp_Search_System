import React, { useEffect, useState } from "react";
import { api, type SourceDto } from "../app/api";
import { Toast } from "../ui/Toast";

export function ImportPage() {
    const [sources, setSources] = useState<SourceDto[]>([]);
    const [sourceId, setSourceId] = useState<number | "">("");
    const [status, setStatus] = useState<"DRAFT" | "PUBLISHED" | "ARCHIVED">("PUBLISHED");
    const [tagsCsv, setTagsCsv] = useState("");
    const [title, setTitle] = useState("");
    const [file, setFile] = useState<File | null>(null);

    const [err, setErr] = useState<string | null>(null);
    const [okId, setOkId] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        api.listSources().then(setSources).catch((e) => setErr(String(e?.message || e)));
    }, []);

    async function submit() {
        if (!file || sourceId === "") {
            setErr("Выберите источник и файл.");
            return;
        }

        setLoading(true);
        setOkId(null);
        try {
            const form = new FormData();
            form.append("sourceId", String(sourceId));
            form.append("status", status);
            form.append("tagsCsv", tagsCsv);
            if (title.trim()) form.append("title", title.trim());
            form.append("file", file);
            const res = await api.importDoc(form);
            setOkId(res.id);
            setFile(null);
            setTitle("");
            setTagsCsv("");
        } catch (e: any) {
            setErr(String(e?.message || e));
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="stack">
            <div className="card stack">
                <div style={{ fontWeight: 900, fontSize: 18 }}>Импорт документов</div>
                <div className="muted small">
                    Текстовые форматы индексируются сразу. Для PDF и офисных файлов система теперь пытается извлечь текст и
                    добавить его в полнотекстовый индекс.
                </div>

                <select className="field" value={sourceId === "" ? "" : String(sourceId)} onChange={(e) => setSourceId(e.target.value ? Number(e.target.value) : "")}>
                    <option value="">Выберите источник</option>
                    {sources.filter((source) => source.isActive).map((source) => (
                        <option key={source.id} value={String(source.id)}>{source.name} ({source.kind})</option>
                    ))}
                </select>

                <div className="row">
                    <select className="field" style={{ width: 220 }} value={status} onChange={(e) => setStatus(e.target.value as any)}>
                        <option value="PUBLISHED">PUBLISHED</option>
                        <option value="DRAFT">DRAFT</option>
                        <option value="ARCHIVED">ARCHIVED</option>
                    </select>
                    <input className="field" style={{ flex: 1 }} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Заголовок, опционально" />
                </div>

                <input className="field" value={tagsCsv} onChange={(e) => setTagsCsv(e.target.value)} placeholder="Теги через запятую: hr, it, sales" />

                <input className="field" type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} />

                <button className="btn primary" disabled={loading} onClick={submit}>{loading ? "..." : "Импортировать"}</button>

                {okId && <div className="card" style={{ borderColor: "#2a58ff" }}>Импорт завершён. Документ id={okId}</div>}
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
