import React, { useEffect, useState } from "react";
import { api, type AggDto, type AnalyticsSummaryDto, type GroupMetricDto } from "../app/api";
import { getRole } from "../app/auth";
import { Toast } from "../ui/Toast";

function percent(value: number) {
    return `${(value * 100).toFixed(1)}%`;
}

function MetricCard({ title, value, hint }: { title: string; value: string | number; hint: string }) {
    return (
        <div className="card stack" style={{ minWidth: 200, flex: 1 }}>
            <div className="muted small">{title}</div>
            <div style={{ fontWeight: 900, fontSize: 24 }}>{value}</div>
            <div className="muted small">{hint}</div>
        </div>
    );
}

export function AnalyticsPage() {
    const role = getRole();
    const [summary, setSummary] = useState<AnalyticsSummaryDto | null>(null);
    const [top, setTop] = useState<AggDto[]>([]);
    const [zero, setZero] = useState<AggDto[]>([]);
    const [groups, setGroups] = useState<GroupMetricDto[]>([]);
    const [err, setErr] = useState<string | null>(null);

    async function load() {
        try {
            const [summaryRes, topRes, zeroRes, groupsRes] = await Promise.all([
                api.analyticsSummary(),
                api.topQueries(20),
                api.zeroResults(20),
                api.analyticsGroups(10)
            ]);
            setSummary(summaryRes);
            setTop(topRes);
            setZero(zeroRes);
            setGroups(groupsRes);
        } catch (e: any) {
            setErr(String(e?.message || e));
        }
    }

    useEffect(() => {
        void load();
    }, []);

    if (role !== "ADMIN") return <div className="card muted">Только ADMIN.</div>;

    return (
        <div className="stack">
            {summary && (
                <div className="row" style={{ flexWrap: "wrap", alignItems: "stretch" }}>
                    <MetricCard title="Документы" value={summary.totalDocuments} hint={`Опубликовано: ${summary.publishedDocuments}`} />
                    <MetricCard title="Группы данных" value={summary.totalGroups} hint={`Активно: ${summary.activeGroups}`} />
                    <MetricCard title="Поиски" value={summary.totalSearches} hint={`За 7 дней: ${summary.searchesLast7Days}`} />
                    <MetricCard title="Нулевые результаты" value={summary.zeroResultSearches} hint={percent(summary.zeroResultRate)} />
                    <MetricCard title="Клики по выдаче" value={summary.clickedSearches} hint={percent(summary.clickThroughRate)} />
                    <MetricCard title="Импорты" value={summary.importsTotal} hint={`За 7 дней: ${summary.importsLast7Days}`} />
                </div>
            )}

            <div className="row" style={{ alignItems: "flex-start" }}>
                <div className="stack" style={{ flex: 1 }}>
                    <div className="card">
                        <div style={{ fontWeight: 900 }}>Топ запросов</div>
                        <div className="stack" style={{ marginTop: 10 }}>
                            {top.map((item) => (
                                <div key={item.query} className="row" style={{ justifyContent: "space-between" }}>
                                    <div>{item.query}</div>
                                    <div className="muted">{item.count}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                <div className="stack" style={{ flex: 1 }}>
                    <div className="card">
                        <div style={{ fontWeight: 900 }}>Запросы без результата</div>
                        <div className="stack" style={{ marginTop: 10 }}>
                            {zero.map((item) => (
                                <div key={item.query} className="row" style={{ justifyContent: "space-between" }}>
                                    <div>{item.query}</div>
                                    <div className="muted">{item.count}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>

                <div className="stack" style={{ flex: 1 }}>
                    <div className="card">
                        <div style={{ fontWeight: 900 }}>Наполнение групп данных</div>
                        <div className="stack" style={{ marginTop: 10 }}>
                            {groups.map((item) => (
                                <div key={item.sourceId} className="row" style={{ justifyContent: "space-between" }}>
                                    <div>{item.sourceName}</div>
                                    <div className="muted">{item.documents}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {err && <Toast text={err} onClose={() => setErr(null)} />}
        </div>
    );
}
