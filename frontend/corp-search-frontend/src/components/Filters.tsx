import React from "react";
import type { SourceDto } from "../app/api";

const SCOPE_LABELS: Record<string, string> = {
    DOCUMENTS: "Документы",
    FILES: "Файлы",
    SOURCES: "Источники",
    USERS: "Пользователи"
};

export function Filters({
    sources,
    sourceId,
    setSourceId,
    tag,
    setTag,
    scopes,
    setScopes
}: {
    sources: SourceDto[];
    sourceId?: number;
    setSourceId: (v?: number) => void;
    tag: string;
    setTag: (v: string) => void;
    scopes: string[];
    setScopes: (v: string[]) => void;
}) {
    function toggleScope(scope: string) {
        setScopes(
            scopes.includes(scope)
                ? scopes.filter((item) => item !== scope)
                : [...scopes, scope]
        );
    }

    return (
        <div className="stack" style={{ gap: 12 }}>
            <div className="row">
                <select
                    className="field"
                    style={{ width: 260 }}
                    value={sourceId ? String(sourceId) : ""}
                    onChange={(e) => setSourceId(e.target.value ? Number(e.target.value) : undefined)}
                >
                    <option value="">Все источники</option>
                    {sources
                        .filter((source) => source.isActive)
                        .map((source) => (
                            <option key={source.id} value={String(source.id)}>
                                {source.name} ({source.kind})
                            </option>
                        ))}
                </select>

                <input
                    className="field"
                    style={{ width: 260 }}
                    value={tag}
                    onChange={(e) => setTag(e.target.value)}
                    placeholder="Тег, например: hr, it, sales"
                />
            </div>

            <div className="row" style={{ flexWrap: "wrap" }}>
                {Object.entries(SCOPE_LABELS).map(([scope, label]) => (
                    <label key={scope} className="row" style={{ gap: 8 }}>
                        <input
                            type="checkbox"
                            checked={scopes.includes(scope)}
                            onChange={() => toggleScope(scope)}
                        />
                        <span className="small">{label}</span>
                    </label>
                ))}
            </div>
        </div>
    );
}
