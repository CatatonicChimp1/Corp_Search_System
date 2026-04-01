import React, { useEffect, useState } from "react";
import { api, type PermDto, type SourceDto } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

export function SourcePermissionsPage({ source }: { source: SourceDto }) {
    const role = getRole();
    const [items, setItems] = useState<PermDto[]>([]);
    const [email, setEmail] = useState("");
    const [canRead, setCanRead] = useState(true);
    const [canWrite, setCanWrite] = useState(false);
    const [err, setErr] = useState<string | null>(null);

    async function reload() {
        try { setItems(await api.listSourcePerms(source.id)); } catch (e:any) { setErr(String(e?.message||e)); }
    }

    useEffect(() => { reload(); }, [source.id]);

    if (role !== "ADMIN") return <div className="card muted">Только ADMIN.</div>;

    async function add() {
        try {
            await api.setSourcePerm(source.id, { email: email.trim(), canRead, canWrite });
            setEmail("");
            setCanRead(true);
            setCanWrite(false);
            await reload();
        } catch (e:any) { setErr(String(e?.message||e)); }
    }

    async function remove(e: string) {
        try { await api.deleteSourcePerm(source.id, e); await reload(); } catch (er:any) { setErr(String(er?.message||er)); }
    }

    return (
        <div className="stack">
            <div className="card stack">
                <div style={{ fontWeight: 900 }}>Права на источник: {source.name}</div>

                <div className="row">
                    <input className="field" style={{ flex: 1 }} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email пользователя" />
                    <label className="muted small" style={{ display: "flex", gap: 8, alignItems: "center" }}>
                        <input type="checkbox" checked={canRead} onChange={(e) => setCanRead(e.target.checked)} /> read
                    </label>
                    <label className="muted small" style={{ display: "flex", gap: 8, alignItems: "center" }}>
                        <input type="checkbox" checked={canWrite} onChange={(e) => setCanWrite(e.target.checked)} /> write
                    </label>
                    <button className="btn" onClick={add}>Добавить/обновить</button>
                </div>
            </div>

            <div className="stack">
                {items.map(p => (
                    <div key={p.email} className="card">
                        <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                            <div>
                                <div style={{ fontWeight: 800 }}>{p.email}</div>
                                <div className="muted small">read: {String(p.canRead)} · write: {String(p.canWrite)}</div>
                            </div>
                            <button className="btn danger" onClick={() => remove(p.email)}>Удалить</button>
                        </div>
                    </div>
                ))}
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
