import React from "react";
import { clearSession, getEmail, getRole } from "../app/auth";

export function Layout({ children }: { children: React.ReactNode }) {
    const email = getEmail();
    const role = getRole();

    return (
        <div className="container">
            <div className="header">
                <div className="row" style={{ alignItems: "center" }}>
                    <div style={{ fontWeight: 800, fontSize: 18 }}>Corporate Search</div>
                    <div className="muted small">Одна строка поиска по знаниям компании</div>
                </div>

                <div className="row" style={{ alignItems: "center" }}>
                    <div className="muted small">
                        {email} • {role}
                    </div>
                    <div className="nav">
                        <a className="btn" href="#/search">Поиск</a>
                        <a className="btn" href="#/admin/sources">Источники</a>
                        <a className="btn" href="#/admin/users">Пользователи</a>
                        <a className="btn" href="#/admin/invites">Инвайты</a>
                        <a className="btn" href="#/admin/audit">Аудит</a>
                        <a className="btn" href="#/admin/import">Импорт</a>
                        <a className="btn" href="#/admin/analytics">Аналитика</a>
                        <button
                            className="btn danger"
                            onClick={() => {
                                clearSession();
                                location.hash = "#/search";
                                location.reload();
                            }}
                        >
                            Выйти
                        </button>
                    </div>
                </div>
            </div>

            {children}
        </div>
    );
}
