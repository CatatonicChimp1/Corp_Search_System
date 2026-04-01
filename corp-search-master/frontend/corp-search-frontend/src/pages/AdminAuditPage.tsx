import React, { useEffect, useState } from "react";
import { api, type AuditDto } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

export function AdminAuditPage() {
  const role = getRole();
  const [items, setItems] = useState<AuditDto[]>([]);
  const [err, setErr] = useState<string | null>(null);

  const [action, setAction] = useState("");
  const [actorEmail, setActorEmail] = useState("");
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    try {
      setItems(await api.listAudit({ action: action.trim() || undefined, actorEmail: actorEmail.trim() || undefined }));
    } catch (e: any) {
      setErr(String(e?.message || e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  if (role !== "ADMIN") {
    return <div className="card muted">Только ADMIN может смотреть аудит.</div>;
  }

  return (
    <div className="stack">
      <div className="card stack">
        <div style={{ fontWeight: 900, fontSize: 18 }}>Аудит действий</div>

        <div className="row">
          <input className="field" style={{ width: 260 }} value={action} onChange={(e) => setAction(e.target.value)} placeholder="action (например SEARCH)" />
          <input className="field" style={{ width: 320 }} value={actorEmail} onChange={(e) => setActorEmail(e.target.value)} placeholder="actorEmail" />
          <button className="btn primary" onClick={load}>Применить</button>
        </div>

        <div className="muted small">
          Примеры: LOGIN_OK, LOGIN_FAIL, SEARCH, CREATE_USER, DELETE_USER, CREATE_INVITE, ACCEPT_INVITE…
        </div>
      </div>

      {loading && <div className="card muted">Загрузка…</div>}

      {!loading && (
        <div className="stack">
          {items.map((a) => (
            <div className="card" key={a.id}>
              <div style={{ fontWeight: 800 }}>{a.action}</div>
              <div className="muted small">
                {a.at} · {a.actorEmail || "—"}
              </div>
              {a.meta && (
                <div className="muted" style={{ marginTop: 6, whiteSpace: "pre-wrap" }}>
                  {a.meta}
                </div>
              )}
            </div>
          ))}
          {items.length === 0 && <div className="card muted">Пусто</div>}
        </div>
      )}

      {err && <Toast text={err} onClose={() => setErr(null)} />}
    </div>
  );
}
