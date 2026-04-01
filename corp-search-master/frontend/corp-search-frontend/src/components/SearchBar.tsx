import React from "react";

export function SearchBar({
    value,
    onChange,
    onSubmit
}: {
    value: string;
    onChange: (v: string) => void;
    onSubmit: () => void;
}) {
    return (
        <div className="row">
            <input
                className="field"
                style={{ flex: 1, minWidth: 260 }}
                value={value}
                onChange={(e) => onChange(e.target.value)}
                placeholder="Введите запрос: договор, отпуск, инструкция, клиент..."
                onKeyDown={(e) => {
                    if (e.key === "Enter") onSubmit();
                }}
            />
            <button className="btn primary" onClick={onSubmit}>Найти</button>
        </div>
    );
}
