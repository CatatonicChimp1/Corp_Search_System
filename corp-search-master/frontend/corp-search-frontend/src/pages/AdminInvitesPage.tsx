import React, { useEffect, useState } from "react";
import { api, type InviteDto, type CreateInviteRes } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

export function AdminInvitesPage() {
    const role = getRole();
    const [items, setItems] = useState<InviteDto[]>([]);
    const [err, setErr] = useState<string | null>(null);

    const [email, setEmail] = useState("");
    const [invRole, setInvRole] = useState<"USER" | "ADMIN">("USER");
    const [ttlHours, setTtlHours] = useState(72);
    const [created, setCreated] = useState<CreateInviteRes | null>(null);

    async function reload() {
        try {
            setItems(await api.listInvites());
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    useEffect(() => {
        reload();
    }, []);

    if (role !== "ADMIN") {
        return <div className="card muted">Только ADMIN может управлять приглашениями.</div>;
    }

    async function create() {
        try {
            const e = email.trim();
            if (e.length < 3) {
                setErr("Введите email");
                return;
            }
            const res = await api.createInvite({ email: e, role: invRole, ttlHours });
            setCreated(res);
            setEmail("");
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    async function remove(id: number) {
        try {
            await api.deleteInvite(id);
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    return (
        <div className="stack">
            <div className="card stack">
                <div style={{ fontWeight: 900, fontSize: 18 }}>Приглашения (одноразовые)</div>
                <div className="muted small">
                    Создаёте инвайт — отдаёте ссылку сотруднику. Он сам задаёт пароль при активации.
                </div>

                <div className="row">
                    <input className="field" style={{ flex: 1, minWidth: 220 }} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email сотрудника" />
                    <select className="field" style={{ width: 160 }} value={invRole} onChange={(e) => setInvRole(e.target.value as any)}>
                        <option value="USER">USER</option>
                        <option value="ADMIN">ADMIN</option>
                    </select>
                    <input
                        className="field"
                        style={{ width: 160 }}
                        value={String(ttlHours)}
                        onChange={(e) => setTtlHours(Number(e.target.value || 72))}
                        placeholder="ttlHours"
                    />
                </div>
                <button className="btn primary" onClick={create}>Создать инвайт</button>

                {created && (
                    <div className="card stack" style={{ borderColor: "#2a58ff" }}>
                        <div style={{ fontWeight: 800 }}>Ссылка для сотрудника:</div>
                        <div className="muted small">{created.url}</div>
                        <button
                            className="btn"
                            onClick={() => {
                                navigator.clipboard.writeText(created.url).catch(() => {});
                            }}
                        >
                            Скопировать ссылку
                        </button>
                    </div>
                )}
            </div>

            <div className="stack">
                {items.map((i) => {
                    const expired = new Date(i.expiresAt).getTime() < Date.now();
                    return (
                        <div className="card" key={i.id}>
                            <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                                <div className="stack" style={{ gap: 6, flex: 1 }}>
                                    <div style={{ fontWeight: 800 }}>{i.email}</div>
                                    <div className="muted small">
                                        Роль: {i.role} · Создан: {i.createdAt} · Истекает: {i.expiresAt}
                                        <br />
                                        Статус: {i.usedAt ? `Использован (${i.usedAt})` : expired ? "Истёк" : "Активен"}
                                        {i.createdBy ? ` · кем: ${i.createdBy}` : ""}
                                    </div>
                                </div>

                                <div className="row">
                                    <button className="btn danger" onClick={() => remove(i.id)}>
                                        Удалить
                                    </button>
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
