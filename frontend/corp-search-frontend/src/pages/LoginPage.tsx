import React, { useState } from "react";
import { api } from "../app/api";
import { setSession } from "../app/auth";
import { Toast } from "../ui/Toast";

export function LoginPage() {
    const [email, setEmail] = useState("admin@local");
    const [password, setPassword] = useState("admin12345");
    const [err, setErr] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    async function submit(e: React.ChangeEvent) {
        e.preventDefault();
        setLoading(true);
        try {
            const res = await api.login(email, password);
            setSession(res.token, res.email, res.role);
            location.hash = "#/search";
            location.reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="container" style={{ maxWidth: 520, paddingTop: 64 }}>
            <div className="card stack">
                <div style={{ fontWeight: 800, fontSize: 20 }}>Вход</div>

                <form className="stack" onSubmit={submit}>
                    <input className="field" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" />
                    <input className="field" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Пароль" type="password" />
                    <button className="btn primary" disabled={loading} type="submit">
                        {loading ? "..." : "Войти"}
                    </button>
                </form>
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
