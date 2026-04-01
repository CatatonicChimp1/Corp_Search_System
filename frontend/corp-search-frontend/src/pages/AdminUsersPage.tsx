import React, { useEffect, useState } from "react";
import { api, type UserDto } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

export function AdminUsersPage() {
    const role = getRole();
    const [items, setItems] = useState<UserDto[]>([]);
    const [err, setErr] = useState<string | null>(null);

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [newRole, setNewRole] = useState<"USER" | "ADMIN">("USER");

    const [pwUserId, setPwUserId] = useState<number | "">("");
    const [pwValue, setPwValue] = useState("");

    async function reload() {
        try {
            setItems(await api.listUsers());
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    useEffect(() => {
        reload();
    }, []);

    if (role !== "ADMIN") {
        return <div className="card muted">Только ADMIN может управлять пользователями.</div>;
    }

    async function create() {
        try {
            const normalizedEmail = email.trim();
            const normalizedPassword = password.trim();
            if (!normalizedEmail || normalizedPassword.length < 6) {
                setErr("Введите email и пароль длиной не менее 6 символов.");
                return;
            }
            await api.createUser({ email: normalizedEmail, password: normalizedPassword, role: newRole });
            setEmail("");
            setPassword("");
            setNewRole("USER");
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    async function changeRole(user: UserDto, nextRole: "USER" | "ADMIN") {
        try {
            await api.updateUserRole(user.id, nextRole);
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    async function resetPassword() {
        try {
            if (pwUserId === "" || pwValue.trim().length < 6) {
                setErr("Выберите пользователя и задайте новый пароль длиной не менее 6 символов.");
                return;
            }
            await api.resetUserPassword(pwUserId, pwValue.trim());
            setPwUserId("");
            setPwValue("");
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    async function remove(id: number) {
        try {
            await api.deleteUser(id);
            await reload();
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    return (
        <div className="stack">
            <div className="card stack">
                <div style={{ fontWeight: 900, fontSize: 18 }}>Пользователи</div>
                <div className="muted small">
                    Здесь администратор может создавать пользователей, менять роли и сбрасывать пароли.
                </div>

                <div className="row">
                    <input className="field" style={{ flex: 1, minWidth: 220 }} value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" />
                    <input className="field" style={{ width: 240 }} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Пароль (не менее 6 символов)" type="password" />
                    <select className="field" style={{ width: 160 }} value={newRole} onChange={(e) => setNewRole(e.target.value as "USER" | "ADMIN")}>
                        <option value="USER">USER</option>
                        <option value="ADMIN">ADMIN</option>
                    </select>
                </div>
                <button className="btn primary" onClick={create}>Добавить пользователя</button>

                <hr />

                <div className="row" style={{ alignItems: "center" }}>
                    <select
                        className="field"
                        style={{ width: 320 }}
                        value={pwUserId === "" ? "" : String(pwUserId)}
                        onChange={(e) => setPwUserId(e.target.value ? Number(e.target.value) : "")}
                    >
                        <option value="">Выберите пользователя для сброса пароля</option>
                        {items.map((user) => (
                            <option key={user.id} value={String(user.id)}>
                                {user.email} ({user.role})
                            </option>
                        ))}
                    </select>

                    <input
                        className="field"
                        style={{ width: 260 }}
                        value={pwValue}
                        onChange={(e) => setPwValue(e.target.value)}
                        placeholder="Новый пароль"
                        type="password"
                    />

                    <button className="btn" onClick={resetPassword}>Сбросить пароль</button>
                </div>
            </div>

            <div className="stack">
                {items.map((user) => (
                    <div className="card" key={user.id}>
                        <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                            <div className="stack" style={{ gap: 6, flex: 1 }}>
                                <div style={{ fontWeight: 800 }}>{user.email}</div>
                                <div className="muted small">Роль: {user.role}</div>
                            </div>

                            <div className="row">
                                <button className="btn" onClick={() => changeRole(user, user.role === "ADMIN" ? "USER" : "ADMIN")}>
                                    Сделать {user.role === "ADMIN" ? "USER" : "ADMIN"}
                                </button>
                                <button className="btn danger" onClick={() => remove(user.id)}>
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
