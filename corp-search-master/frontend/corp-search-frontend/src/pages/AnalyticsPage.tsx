import React, { useEffect, useState } from "react";
import { api, type AggDto } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

export function AnalyticsPage() {
    const role = getRole();
    const [top, setTop] = useState<AggDto[]>([]);
    const [zero, setZero] = useState<AggDto[]>([]);
    const [err, setErr] = useState<string | null>(null);

    async function load() {
        try {
            setTop(await api.topQueries(20));
            setZero(await api.zeroResults(20));
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    useEffect(() => { load(); }, []);

    if (role !== "ADMIN") return <div className="card muted">Только ADMIN.</div>;

    return (
        <div className="row" style={{ alignItems: "flex-start" }}>
            <div className="stack" style={{ flex: 1 }}>
                <div className="card">
                    <div style={{ fontWeight: 900 }}>Top queries</div>
                    <div className="stack" style={{ marginTop: 10 }}>
                        {top.map((a) => (
                            <div key={a.query} className="row" style={{ justifyContent: "space-between" }}>
                                <div>{a.query}</div><div className="muted">{a.count}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            <div className="stack" style={{ flex: 1 }}>
                <div className="card">
                    <div style={{ fontWeight: 900 }}>Zero-results (кандидаты на контент)</div>
                    <div className="stack" style={{ marginTop: 10 }}>
                        {zero.map((a) => (
                            <div key={a.query} className="row" style={{ justifyContent: "space-between" }}>
                                <div>{a.query}</div><div className="muted">{a.count}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
