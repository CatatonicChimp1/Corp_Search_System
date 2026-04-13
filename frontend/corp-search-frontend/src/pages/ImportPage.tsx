import React, { useEffect, useMemo, useState } from "react";
import { api, type DbTableDto, type SourceDto } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

type Status = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export function ImportPage() {
    const role = getRole();
    const isAdmin = role === "ADMIN";
    const [sources, setSources] = useState<SourceDto[]>([]);

    const activeSources = useMemo(() => sources.filter((source) => source.isActive), [sources]);

    const [fileSourceId, setFileSourceId] = useState<number | "">("");
    const [fileStatus, setFileStatus] = useState<Status>("PUBLISHED");
    const [fileTagsCsv, setFileTagsCsv] = useState("");
    const [fileTitle, setFileTitle] = useState("");
    const [file, setFile] = useState<File | null>(null);
    const [fileLoading, setFileLoading] = useState(false);
    const [fileOkId, setFileOkId] = useState<number | null>(null);

    const [dbSourceId, setDbSourceId] = useState<number | "">("");
    const [dbStatus, setDbStatus] = useState<Status>("PUBLISHED");
    const [dbTagsCsv, setDbTagsCsv] = useState("");
    const [jdbcUrl, setJdbcUrl] = useState("");
    const [dbUsername, setDbUsername] = useState("");
    const [dbPassword, setDbPassword] = useState("");
    const [rowLimitPerTable, setRowLimitPerTable] = useState(200);
    const [tables, setTables] = useState<DbTableDto[]>([]);
    const [selectedTables, setSelectedTables] = useState<string[]>([]);
    const [inspecting, setInspecting] = useState(false);
    const [dbLoading, setDbLoading] = useState(false);
    const [dbResult, setDbResult] = useState<string | null>(null);

    const [err, setErr] = useState<string | null>(null);

    useEffect(() => {
        api.listSources().then(setSources).catch((e) => setErr(String(e?.message || e)));
    }, []);

    async function submitFileImport() {
        if (!file || fileSourceId === "") {
            setErr("Выберите группу данных и файл.");
            return;
        }

        setFileLoading(true);
        setFileOkId(null);
        try {
            const form = new FormData();
            form.append("sourceId", String(fileSourceId));
            form.append("status", fileStatus);
            form.append("tagsCsv", fileTagsCsv);
            if (fileTitle.trim()) form.append("title", fileTitle.trim());
            form.append("file", file);

            const res = await api.importDoc(form);
            setFileOkId(res.id);
            setFile(null);
            setFileTitle("");
            setFileTagsCsv("");
        } catch (e: any) {
            setErr(String(e?.message || e));
        } finally {
            setFileLoading(false);
        }
    }

    async function inspectDb() {
        if (!jdbcUrl.trim()) {
            setErr("Укажите JDBC URL.");
            return;
        }

        setInspecting(true);
        setTables([]);
        setSelectedTables([]);
        setDbResult(null);
        try {
            const items = await api.inspectDb({
                jdbcUrl: jdbcUrl.trim(),
                username: dbUsername.trim() || undefined,
                password: dbPassword
            });
            setTables(items);
        } catch (e: any) {
            setErr(String(e?.message || e));
        } finally {
            setInspecting(false);
        }
    }

    function toggleTable(tableName: string) {
        setSelectedTables((current) =>
            current.includes(tableName)
                ? current.filter((item) => item !== tableName)
                : [...current, tableName]
        );
    }

    async function submitDbImport() {
        if (dbSourceId === "") {
            setErr("Выберите группу данных для импорта из БД.");
            return;
        }
        if (!jdbcUrl.trim()) {
            setErr("Укажите JDBC URL.");
            return;
        }
        if (selectedTables.length === 0) {
            setErr("Выберите хотя бы одну таблицу.");
            return;
        }

        setDbLoading(true);
        setDbResult(null);
        try {
            const res = await api.importDb({
                jdbcUrl: jdbcUrl.trim(),
                username: dbUsername.trim() || undefined,
                password: dbPassword,
                tables: selectedTables,
                sourceId: dbSourceId,
                status: dbStatus,
                rowLimitPerTable,
                tagsCsv: dbTagsCsv
            });
            setDbResult(`Импортировано документов: ${res.importedDocs}. Таблицы: ${res.tables.join(", ") || "нет данных"}.`);
        } catch (e: any) {
            setErr(String(e?.message || e));
        } finally {
            setDbLoading(false);
        }
    }

    return (
        <div className="stack">
            <div className="card stack">
                <div style={{ fontWeight: 900, fontSize: 18 }}>Импорт файлов</div>
                <div className="muted small">
                    Текстовые форматы индексируются сразу. Для PDF и офисных файлов система пытается
                    извлечь текст и добавить его в полнотекстовый индекс.
                </div>

                <select
                    className="field"
                    value={fileSourceId === "" ? "" : String(fileSourceId)}
                    onChange={(e) => setFileSourceId(e.target.value ? Number(e.target.value) : "")}
                >
                    <option value="">Выберите группу данных</option>
                    {activeSources.map((source) => (
                        <option key={source.id} value={String(source.id)}>
                            {source.name} ({source.kind})
                        </option>
                    ))}
                </select>

                <div className="row">
                    <select className="field" style={{ width: 220 }} value={fileStatus} onChange={(e) => setFileStatus(e.target.value as Status)}>
                        <option value="PUBLISHED">PUBLISHED</option>
                        <option value="DRAFT">DRAFT</option>
                        <option value="ARCHIVED">ARCHIVED</option>
                    </select>
                    <input
                        className="field"
                        style={{ flex: 1 }}
                        value={fileTitle}
                        onChange={(e) => setFileTitle(e.target.value)}
                        placeholder="Заголовок, если нужно переопределить имя файла"
                    />
                </div>

                <input
                    className="field"
                    value={fileTagsCsv}
                    onChange={(e) => setFileTagsCsv(e.target.value)}
                    placeholder="Теги через запятую: hr, it, sales"
                />
                <input className="field" type="file" onChange={(e) => setFile(e.target.files?.[0] || null)} />

                <button className="btn primary" disabled={fileLoading} onClick={submitFileImport}>
                    {fileLoading ? "Импортируем..." : "Импортировать файл"}
                </button>

                {fileOkId && (
                    <div className="card" style={{ borderColor: "#2a58ff" }}>
                        Импорт завершён. Документ id={fileOkId}
                    </div>
                )}
            </div>

            <div className="card stack">
                <div style={{ fontWeight: 900, fontSize: 18 }}>Подключение БД</div>

                {!isAdmin && (
                    <div className="card muted">
                        Подключение внешней БД доступно только ADMIN.
                    </div>
                )}

                <div className="row">
                    <input
                        className="field"
                        style={{ flex: 2, minWidth: 260 }}
                        value={jdbcUrl}
                        onChange={(e) => setJdbcUrl(e.target.value)}
                        placeholder="JDBC URL, например: jdbc:postgresql://localhost:5432/company"
                    />
                    <input
                        className="field"
                        style={{ flex: 1, minWidth: 180 }}
                        value={dbUsername}
                        onChange={(e) => setDbUsername(e.target.value)}
                        placeholder="Пользователь БД"
                    />
                    <input
                        className="field"
                        style={{ flex: 1, minWidth: 180 }}
                        type="password"
                        value={dbPassword}
                        onChange={(e) => setDbPassword(e.target.value)}
                        placeholder="Пароль БД"
                    />
                </div>

                <div className="row">
                    <select
                        className="field"
                        style={{ flex: 1, minWidth: 220 }}
                        value={dbSourceId === "" ? "" : String(dbSourceId)}
                        onChange={(e) => setDbSourceId(e.target.value ? Number(e.target.value) : "")}
                    >
                        <option value="">Группа данных для документов</option>
                        {activeSources.map((source) => (
                            <option key={source.id} value={String(source.id)}>
                                {source.name} ({source.kind})
                            </option>
                        ))}
                    </select>

                    <select className="field" style={{ width: 220 }} value={dbStatus} onChange={(e) => setDbStatus(e.target.value as Status)}>
                        <option value="PUBLISHED">PUBLISHED</option>
                        <option value="DRAFT">DRAFT</option>
                        <option value="ARCHIVED">ARCHIVED</option>
                    </select>

                    <input
                        className="field"
                        style={{ width: 180 }}
                        type="number"
                        min={1}
                        max={2000}
                        value={rowLimitPerTable}
                        onChange={(e) => setRowLimitPerTable(Number(e.target.value) || 200)}
                        placeholder="Лимит строк"
                    />
                </div>

                <input
                    className="field"
                    value={dbTagsCsv}
                    onChange={(e) => setDbTagsCsv(e.target.value)}
                    placeholder="Теги для документов из БД: db, finance"
                />

                <div className="row">
                    <button className="btn" disabled={!isAdmin || inspecting} onClick={inspectDb}>
                        {inspecting ? "Читаем схему..." : "Проверить таблицы"}
                    </button>
                    <button
                        className="btn"
                        disabled={!isAdmin || tables.length === 0}
                        onClick={() => setSelectedTables(tables.map((table) => table.name))}
                    >
                        Выбрать все
                    </button>
                    <button
                        className="btn"
                        disabled={!isAdmin || selectedTables.length === 0}
                        onClick={() => setSelectedTables([])}
                    >
                        Снять выбор
                    </button>
                </div>

                {tables.length > 0 && (
                    <div className="stack">
                        <div style={{ fontWeight: 800 }}>Таблицы</div>
                        <div className="stack" style={{ gap: 8 }}>
                            {tables.map((table) => (
                                <label key={table.name} className="row" style={{ gap: 8 }}>
                                    <input
                                        type="checkbox"
                                        checked={selectedTables.includes(table.name)}
                                        onChange={() => toggleTable(table.name)}
                                    />
                                    <span>
                                        {table.name}
                                        {typeof table.estimatedRows === "number" ? ` (${table.estimatedRows})` : ""}
                                    </span>
                                </label>
                            ))}
                        </div>
                    </div>
                )}

                <button className="btn primary" disabled={!isAdmin || dbLoading} onClick={submitDbImport}>
                    {dbLoading ? "Импортируем..." : "Импортировать из БД"}
                </button>

                {dbResult && (
                    <div className="card" style={{ borderColor: "#2a58ff" }}>
                        {dbResult}
                    </div>
                )}
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
