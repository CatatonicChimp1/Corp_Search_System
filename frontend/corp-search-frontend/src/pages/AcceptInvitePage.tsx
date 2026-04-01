import React, { useState } from "react";
import { api } from "../app/api";
import { Toast } from "../ui/Toast";

export function AcceptInvitePage({ token }: { token: string }) {
    const [pw, setPw] = useState("");
    const [pw2, setPw2] = useState("");
    const [ok, setOk] = useState(false);
    const [err, setErr] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    async function submit() {
        const p = pw.trim();
        if (!token || token.length < 16) {
            setErr("Некорректный токен приглашения");
            return;
        }
        if (p.length < 6) {
            setErr("Пароль должен быть минимум 6 символов");
            return;
        }
        if (p !== pw2.trim()) {
            setErr("Пароли не совпадают");
            return;
        }
        setLoading(true);
        try {
            await api.acceptInvite(token, p);
            setOk(true);
        } catch (e: any) {
            setErr(String(e?.message || e));
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="container" style={{ maxWidth: 520, paddingTop: 64 }}>
            <div className="card stack">
                <div style={{ fontWeight: 900, fontSize: 20 }}>Принять приглашение</div>
                <div className="muted small">
                    Установите пароль и войдите в систему.
                </div>

                {!ok ? (
                    <>
                        <input className="field" value={pw} onChange={(e) => setPw(e.target.value)} placeholder="Новый пароль (>=6)" type="password" />
                        <input className="field" value={pw2} onChange={(e) => setPw2(e.target.value)} placeholder="Повтор пароля" type="password" />
                        <button className="btn primary" disabled={loading} onClick={submit}>
                            {loading ? "..." : "Активировать"}
                        </button>
                        <div className="muted small">
                            После активации перейдите на страницу входа: <a href="#/search" className="btn">Вход</a>
                        </div>
                    </>
                ) : (
                    <>
                        <div className="card" style={{ borderColor: "#2a58ff" }}>
                            Готово! Приглашение активировано.
                        </div>
                        <a className="btn primary" href="#/search">Перейти ко входу</a>
                    </>
                )}
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
