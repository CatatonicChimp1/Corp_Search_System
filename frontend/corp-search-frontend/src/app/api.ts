import { getToken } from "./auth";

const API_BASE = "http://localhost:8080";

export type LoginRes = { token: string; email: string; role: string };

export type SourceDto = {
    id: number;
    name: string;
    kind: string;
    description?: string | null;
    isActive: boolean;
};

export type SearchHit = {
    kind: "DOCUMENT" | "FILE" | "SOURCE" | "USER";
    title: string;
    snippet: string;
    route: string;
    docId?: number | null;
    sourceId?: number | null;
    sourceName?: string | null;
    sourceKind?: string | null;
    match?: string | null;
    tags: string[];
    meta: Record<string, string>;
};

export type SearchRes = {
    eventId: number;
    query: string;
    total: number;
    appliedScopes: string[];
    hits: SearchHit[];
};

export type DocDto = {
    id: number;
    sourceId: number;
    title: string;
    body: string;
    author?: string | null;
    tags: string[];
    createdAt: string;
    updatedAt: string;
};

export type UserDto = { id: number; email: string; role: string };

export type InviteDto = {
    id: number;
    email: string;
    role: string;
    expiresAt: string;
    usedAt?: string | null;
    createdAt: string;
    createdBy?: string | null;
};

export type CreateInviteRes = { inviteId: number; token: string; url: string };

export type AuditDto = {
    id: number;
    at: string;
    actorEmail?: string | null;
    action: string;
    meta?: string | null;
};

export type AggDto = { query: string; count: number };

export type AnalyticsSummaryDto = {
    totalDocuments: number;
    publishedDocuments: number;
    totalGroups: number;
    activeGroups: number;
    totalSearches: number;
    searchesLast7Days: number;
    zeroResultSearches: number;
    zeroResultRate: number;
    clickedSearches: number;
    clickThroughRate: number;
    importsTotal: number;
    importsLast7Days: number;
};

export type GroupMetricDto = {
    sourceId: number;
    sourceName: string;
    documents: number;
};

export type DbTableDto = {
    name: string;
    estimatedRows?: number | null;
};

export type DbImportRes = {
    importedDocs: number;
    tables: string[];
};

export type SavedSearchDto = {
    id: number;
    name: string;
    query: string;
    sourceId?: number | null;
    tag?: string | null;
    createdAt: string;
};

export type PermDto = { email: string; canRead: boolean; canWrite: boolean };

async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const token = getToken();
    const headers: Record<string, string> = {
        "Content-Type": "application/json",
        ...(init?.headers as Record<string, string> | undefined)
    };
    if (token) headers.Authorization = `Bearer ${token}`;

    const res = await fetch(`${API_BASE}${path}`, { ...init, headers });
    if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `HTTP ${res.status}`);
    }
    return res.json() as Promise<T>;
}

export const api = {
    login(email: string, password: string) {
        return request<LoginRes>("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({ email, password })
        });
    },

    listSources() {
        return request<SourceDto[]>("/api/sources");
    },

    createSource(req: { name: string; kind: string; description?: string }) {
        return request<SourceDto>("/api/sources", {
            method: "POST",
            body: JSON.stringify(req)
        });
    },

    updateSource(id: number, req: { name: string; kind: string; description?: string | null; isActive: boolean }) {
        return request<{ ok: boolean }>(`/api/sources/${id}`, {
            method: "PUT",
            body: JSON.stringify(req)
        });
    },

    deleteSource(id: number) {
        return request<{ ok: boolean }>(`/api/sources/${id}`, { method: "DELETE" });
    },

    search(params: { q: string; sourceId?: number; tag?: string; scopes?: string[]; limit?: number; offset?: number }) {
        const sp = new URLSearchParams();
        sp.set("q", params.q);
        if (params.sourceId) sp.set("sourceId", String(params.sourceId));
        if (params.tag) sp.set("tag", params.tag);
        if (params.scopes?.length) sp.set("scopes", params.scopes.join(","));
        if (params.limit) sp.set("limit", String(params.limit));
        if (params.offset) sp.set("offset", String(params.offset));
        return request<SearchRes>(`/api/search?${sp.toString()}`);
    },

    getDoc(id: number) {
        return request<DocDto>(`/api/docs/${id}`);
    },

    createDoc(req: { sourceId: number; title: string; body: string; author?: string; tagsCsv?: string }) {
        return request<{ id: number }>("/api/docs", {
            method: "POST",
            body: JSON.stringify(req)
        });
    },

    updateDoc(id: number, req: { title: string; body: string; author?: string; tagsCsv?: string }) {
        return request<{ ok: boolean }>(`/api/docs/${id}`, {
            method: "PUT",
            body: JSON.stringify(req)
        });
    },

    deleteDoc(id: number) {
        return request<{ ok: boolean }>(`/api/docs/${id}`, { method: "DELETE" });
    },

    listUsers() {
        return request<UserDto[]>("/api/users");
    },

    createUser(req: { email: string; password: string; role: string }) {
        return request<UserDto>("/api/users", {
            method: "POST",
            body: JSON.stringify(req)
        });
    },

    updateUserRole(id: number, role: string) {
        return request<{ ok: boolean }>(`/api/users/${id}/role`, {
            method: "PUT",
            body: JSON.stringify({ role })
        });
    },

    resetUserPassword(id: number, newPassword: string) {
        return request<{ ok: boolean }>(`/api/users/${id}/password`, {
            method: "PUT",
            body: JSON.stringify({ newPassword })
        });
    },

    deleteUser(id: number) {
        return request<{ ok: boolean }>(`/api/users/${id}`, { method: "DELETE" });
    },

    listInvites() {
        return request<InviteDto[]>("/api/invites");
    },

    createInvite(req: { email: string; role: string; ttlHours: number }) {
        return request<CreateInviteRes>("/api/invites", {
            method: "POST",
            body: JSON.stringify(req)
        });
    },

    deleteInvite(id: number) {
        return request<{ ok: boolean }>(`/api/invites/${id}`, { method: "DELETE" });
    },

    acceptInvite(token: string, password: string) {
        return request<{ ok: boolean }>("/api/public/accept-invite", {
            method: "POST",
            body: JSON.stringify({ token, password })
        });
    },

    listAudit(params?: { action?: string; actorEmail?: string }) {
        const sp = new URLSearchParams();
        if (params?.action) sp.set("action", params.action);
        if (params?.actorEmail) sp.set("actorEmail", params.actorEmail);
        const qs = sp.toString();
        return request<AuditDto[]>(`/api/audit${qs ? `?${qs}` : ""}`);
    },

    click(eventId: number, docId: number) {
        return request<{ ok: boolean }>("/api/analytics/click", {
            method: "POST",
            body: JSON.stringify({ eventId, docId })
        });
    },

    topQueries(limit = 20) {
        return request<AggDto[]>(`/api/analytics/top-queries?limit=${limit}`);
    },

    zeroResults(limit = 20) {
        return request<AggDto[]>(`/api/analytics/zero-results?limit=${limit}`);
    },

    analyticsSummary() {
        return request<AnalyticsSummaryDto>("/api/analytics/summary");
    },

    analyticsGroups(limit = 10) {
        return request<GroupMetricDto[]>(`/api/analytics/groups?limit=${limit}`);
    },

    listSavedSearches() {
        return request<SavedSearchDto[]>("/api/saved-searches");
    },

    createSavedSearch(req: { name: string; query: string; sourceId?: number; tag?: string }) {
        return request<{ id: number }>("/api/saved-searches", {
            method: "POST",
            body: JSON.stringify(req)
        });
    },

    deleteSavedSearch(id: number) {
        return request<{ ok: boolean }>(`/api/saved-searches/${id}`, { method: "DELETE" });
    },

    listSourcePerms(sourceId: number) {
        return request<PermDto[]>(`/api/sources/${sourceId}/permissions`);
    },

    setSourcePerm(sourceId: number, req: { email: string; canRead: boolean; canWrite: boolean }) {
        return request<{ ok: boolean }>(`/api/sources/${sourceId}/permissions`, {
            method: "POST",
            body: JSON.stringify(req)
        });
    },

    deleteSourcePerm(sourceId: number, email: string) {
        return request<{ ok: boolean }>(
            `/api/sources/${sourceId}/permissions?email=${encodeURIComponent(email)}`,
            { method: "DELETE" }
        );
    },

    async importDoc(form: FormData) {
        const token = getToken();
        const res = await fetch(`${API_BASE}/api/docs/import`, {
            method: "POST",
            headers: token ? { Authorization: `Bearer ${token}` } : undefined,
            body: form
        });
        if (!res.ok) throw new Error(await res.text());
        return await res.json() as Promise<{ id: number }>;
    },

    inspectDb(req: { jdbcUrl: string; username?: string; password?: string }) {
        return request<DbTableDto[]>("/api/import/db/inspect", {
            method: "POST",
            body: JSON.stringify(req)
        });
    },

    importDb(req: {
        jdbcUrl: string;
        username?: string;
        password?: string;
        tables: string[];
        sourceId: number;
        status?: "DRAFT" | "PUBLISHED" | "ARCHIVED";
        rowLimitPerTable?: number;
        tagsCsv?: string;
    }) {
        return request<DbImportRes>("/api/docs/import-db", {
            method: "POST",
            body: JSON.stringify(req)
        });
    }
};
