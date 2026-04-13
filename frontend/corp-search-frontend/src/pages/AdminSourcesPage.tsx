import React, { useEffect, useState } from "react";
import { api, type SourceDto } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

const KINDS = ["HR", "IT", "FINANCE", "FILES", "CRM", "MAIL", "DB", "MANUAL", "OTHER"];

export function AdminSourcesPage() {
    const role = getRole();
    const [items, setItems] = useState<SourceDto[]>([]);
    const [err, setErr] = useState<string | null>(null);
    const [name, setName] = useState("");
    const [kind, setKind] = useState("HR");
    const [description, setDescription] = useState("");

    async function reload() {
        try {
            setItems(await api.listSources());
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    useEffect(() => {
        void reload();
    }, []);

    if (role !== "ADMIN") {
        return <div className="card muted">Только ADMIN может управлять группами данных.</div>;
    }

    async function create() {
        try {
            await api.createSource({ name, kind, description: description || undefined });
            setName("");
            setDescription("");
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    async function toggleActive(source: SourceDto) {
        try {
            await api.updateSource(source.id, {
                name: source.name,
                kind: source.kind,
                description: source.description ?? null,
                isActive: !source.isActive
            });
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    async function remove(id: number) {
        try {
            await api.deleteSource(id);
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    return (
        <div className="stack">
            <div className="card stack">
                <div style={{ fontWeight: 900, fontSize: 18 }}>Группы данных</div>
                <div className="muted small">
                    Группа данных описывает логическую область корпоративной информации: HR, IT, продажи,
                    бухгалтерию, файловое хранилище, CRM или внешнюю БД.
                </div>

                <div className="row">
                    <input
                        className="field"
                        style={{ flex: 1, minWidth: 220 }}
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="Название группы"
                    />
                    <select className="field" style={{ width: 180 }} value={kind} onChange={(e) => setKind(e.target.value)}>
                        {KINDS.map((item) => (
                            <option key={item} value={item}>
                                {item}
                            </option>
                        ))}
                    </select>
                </div>

                <input
                    className="field"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Краткое описание группы"
                />
                <button className="btn primary" onClick={create}>Добавить группу</button>
            </div>

            <div className="stack">
                {items.map((source) => (
                    <div key={source.id} className="card">
                        <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                            <div className="stack" style={{ gap: 6, flex: 1 }}>
                                <div style={{ fontWeight: 800 }}>
                                    {source.name} <span className="muted small">({source.kind})</span>
                                </div>
                                <div className="muted small">{source.description || "Без описания"}</div>
                                <div className="muted small">
                                    Статус: {source.isActive ? "Активна" : "Отключена"}
                                </div>
                            </div>

                            <div className="row">
                                <button className="btn" onClick={() => toggleActive(source)}>
                                    {source.isActive ? "Отключить" : "Включить"}
                                </button>
                                <button className="btn danger" onClick={() => remove(source.id)}>
                                    Удалить
                                </button>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
