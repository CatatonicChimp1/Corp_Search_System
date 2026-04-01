import React from "react";

export function Toast({ text, onClose }: { text: string; onClose: () => void }) {
    return (
        <div className="card" style={{ position: "fixed", bottom: 16, right: 16, width: 360 }}>
            <div className="row" style={{ justifyContent: "space-between", alignItems: "center" }}>
                <div>{text}</div>
                <button className="btn" onClick={onClose}>OK</button>
            </div>
        </div>
    );
}
