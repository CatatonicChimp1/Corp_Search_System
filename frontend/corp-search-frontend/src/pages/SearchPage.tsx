import React, { useEffect, useState } from "react";
import { api, type SavedSearchDto, type SearchRes, type SourceDto } from "../app/api";
import { Filters } from "../components/Filters";
import { ResultList } from "../components/ResultList";
import { SearchBar } from "../components/SearchBar";
import { Toast } from "../ui/Toast";

const DEFAULT_SCOPES = ["DOCUMENTS", "FILES", "SOURCES", "USERS"];

type SearchPageProps = {
    initialQuery?: string;
    initialSourceId?: number;
    initialTag?: string;
    initialScopes?: string[];
};

export function SearchPage({
    initialQuery,
    initialSourceId,
    initialTag,
    initialScopes
}: SearchPageProps) {
    const [sources, setSources] = useState<SourceDto[]>([]);
    const [saved, setSaved] = useState<SavedSearchDto[]>([]);
    const [q, setQ] = useState(initialQuery ?? "");
    const [sourceId, setSourceId] = useState<number | undefined>(initialSourceId);
    const [tag, setTag] = useState(initialTag ?? "");
    const [scopes, setScopes] = useState<string[]>(initialScopes ?? DEFAULT_SCOPES);
    const [res, setRes] = useState<SearchRes | null>(null);
    const [loading, setLoading] = useState(false);
    const [err, setErr] = useState<string | null>(null);
    const [saveName, setSaveName] = useState("");

    async function reloadSaved() {
        try {
            setSaved(await api.listSavedSearches());
        } catch {
            // Sidebar is non-critical for search flow.
        }
    }

    useEffect(() => {
        api.listSources().then(setSources).catch((e) => setErr(String(e?.message || e)));
        void reloadSaved();
    }, []);

    useEffect(() => {
        const nextScopes = initialScopes?.length ? initialScopes : DEFAULT_SCOPES;
        setQ(initialQuery ?? "");
        setSourceId(initialSourceId);
        setTag(initialTag ?? "");
        setScopes(nextScopes);

        if ((initialQuery ?? "").trim() || initialSourceId || (initialTag ?? "").trim()) {
            void executeSearch(initialQuery ?? "", initialSourceId, initialTag ?? "", nextScopes);
        } else {
            setRes(null);
        }
    }, [initialQuery, initialSourceId, initialTag, initialScopes?.join(",")]);

    async function executeSearch(nextQ: string, nextSourceId?: number, nextTag = "", nextScopes: string[] = scopes) {
        setLoading(true);
        localStorage.setItem("last_query", nextQ);

        try {
            const response = await api.search({
                q: nextQ,
                sourceId: nextSourceId,
                tag: nextTag.trim() || undefined,
                scopes: nextScopes
            });
            setRes(response);
        } catch (e: any) {
            setErr(String(e?.message || e));
        } finally {
            setLoading(false);
        }
    }

    function navigateSearch(nextQ: string, nextSourceId?: number, nextTag = "", nextScopes: string[] = scopes) {
        const params = new URLSearchParams();
        if (nextQ.trim()) params.set("q", nextQ.trim());
        if (nextSourceId) params.set("sourceId", String(nextSourceId));
        if (nextTag.trim()) params.set("tag", nextTag.trim());
        if (nextScopes.length) params.set("scopes", nextScopes.join(","));

        const nextHash = params.toString() ? `#/search?${params.toString()}` : "#/search";
        if (location.hash === nextHash) {
            void executeSearch(nextQ, nextSourceId, nextTag, nextScopes);
            return;
        }
        location.hash = nextHash;
    }

    async function save() {
        const name = saveName.trim();
        if (!name) {
            setErr("Введите название сохранённого поиска.");
            return;
        }

        try {
            await api.createSavedSearch({ name, query: q, sourceId, tag: tag.trim() || undefined });
            setSaveName("");
            await reloadSaved();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    function applySaved(item: SavedSearchDto) {
        navigateSearch(item.query || "", item.sourceId ?? undefined, item.tag ?? "", scopes);
    }

    async function removeSaved(id: number) {
        try {
            await api.deleteSavedSearch(id);
            await reloadSaved();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    async function onOpenResult(docId?: number | null) {
        if (!res || !docId) return;
        try {
            await api.click(res.eventId, docId);
        } catch {
            // Analytics should not block navigation.
        }
    }

    return (
        <div className="row" style={{ alignItems: "flex-start" }}>
            <div className="stack" style={{ width: 320, minWidth: 280 }}>
                <div className="card stack">
                    <div style={{ fontWeight: 900 }}>Сохранённые запросы</div>
                    {saved.length === 0 && <div className="muted small">Пока нет сохранённых запросов.</div>}
                    {saved.map((item) => (
                        <div key={item.id} className="card" style={{ padding: 10 }}>
                            <div style={{ fontWeight: 800 }}>{item.name}</div>
                            <div className="muted small">{item.query || "Без текста запроса"}</div>
                            <div className="row" style={{ marginTop: 8 }}>
                                <button className="btn" onClick={() => applySaved(item)}>Открыть</button>
                                <button className="btn danger" onClick={() => removeSaved(item.id)}>Удалить</button>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            <div className="stack" style={{ flex: 1, minWidth: 320 }}>
                <div className="card stack">
                    <SearchBar value={q} onChange={setQ} onSubmit={() => navigateSearch(q, sourceId, tag, scopes)} />
                    <Filters
                        sources={sources}
                        sourceId={sourceId}
                        setSourceId={setSourceId}
                        tag={tag}
                        setTag={setTag}
                        scopes={scopes}
                        setScopes={setScopes}
                    />

                    <div className="row" style={{ alignItems: "center" }}>
                        <input
                            className="field"
                            style={{ flex: 1, minWidth: 220 }}
                            value={saveName}
                            onChange={(e) => setSaveName(e.target.value)}
                            placeholder="Название для сохранения, например: HR отпуска"
                        />
                        <button className="btn" onClick={save}>Сохранить</button>
                    </div>

                    <div className="muted small">
                        URL хранит текст запроса, группу данных, тег и области поиска. Это позволяет делиться
                        ссылкой и нормально использовать back/forward в браузере.
                    </div>
                </div>

                {loading && <div className="card muted">Ищем...</div>}

                {!loading && res && (
                    <div className="card">
                        <div className="row" style={{ justifyContent: "space-between", alignItems: "baseline" }}>
                            <div style={{ fontWeight: 800 }}>Результаты</div>
                            <div className="muted small">
                                Найдено: {res.total}
                                {res.appliedScopes.length > 0 ? ` • области: ${res.appliedScopes.join(", ")}` : ""}
                            </div>
                        </div>
                    </div>
                )}

                {!loading && res && <ResultList hits={res.hits} query={q} onOpen={onOpenResult} />}

                {err && <Toast text={err} onClose={() => setErr(null)} />}
            </div>
        </div>
    );
}
