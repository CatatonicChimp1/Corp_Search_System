import React, { useEffect, useState } from "react";
import { getToken } from "./auth";
import { Layout } from "../ui/Layout";

import { LoginPage } from "../pages/LoginPage";
import { SearchPage } from "../pages/SearchPage";
import { DocPage } from "../pages/DocPage";

import { AdminUsersPage } from "../pages/AdminUsersPage";
import { AdminInvitesPage } from "../pages/AdminInvitesPage";
import { AdminAuditPage } from "../pages/AdminAuditPage";
import { AcceptInvitePage } from "../pages/AcceptInvitePage";

import { ImportPage } from "../pages/ImportPage";
import { AnalyticsPage } from "../pages/AnalyticsPage";
import { AdminSourcesPage } from "../pages/AdminSourcesPage";

type Route =
    | { name: "search"; q?: string; sourceId?: number; tag?: string; scopes?: string[] }
    | { name: "doc"; id: number }
    | { name: "admin_users" }
    | { name: "admin_invites" }
    | { name: "admin_audit" }
    | { name: "accept"; token: string }
    | { name: "import" }
    | { name: "admin_sources" }
    | { name: "analytics" };

function parseHash(): Route {
    const raw = location.hash || "#/search";
    const withoutSharp = raw.startsWith("#") ? raw.slice(1) : raw;
    const [pathPart, queryPart] = withoutSharp.split("?");
    const params = new URLSearchParams(queryPart || "");
    const parts = pathPart.split("/").filter(Boolean);

    if (parts[0] === "accept") {
        return { name: "accept", token: params.get("token") || "" };
    }

    if (parts[0] === "doc" && parts[1]) return { name: "doc", id: Number(parts[1]) };
    if (parts[0] === "admin" && parts[1] === "sources") return { name: "admin_sources" };
    if (parts[0] === "admin" && parts[1] === "users") return { name: "admin_users" };
    if (parts[0] === "admin" && parts[1] === "invites") return { name: "admin_invites" };
    if (parts[0] === "admin" && parts[1] === "audit") return { name: "admin_audit" };
    if (parts[0] === "admin" && parts[1] === "import") return { name: "import" };
    if (parts[0] === "admin" && parts[1] === "analytics") return { name: "analytics" };

    const sourceId = params.get("sourceId");
    const scopes = params.get("scopes")?.split(",").map((item) => item.trim()).filter(Boolean);

    return {
        name: "search",
        q: params.get("q") || undefined,
        sourceId: sourceId ? Number(sourceId) : undefined,
        tag: params.get("tag") || undefined,
        scopes: scopes?.length ? scopes : undefined
    };
}

export function App() {
    const [route, setRoute] = useState<Route>(() => parseHash());
    const authed = !!getToken();

    useEffect(() => {
        const onHash = () => setRoute(parseHash());
        window.addEventListener("hashchange", onHash);
        return () => window.removeEventListener("hashchange", onHash);
    }, []);

    if (route.name === "accept") {
        return <AcceptInvitePage token={route.token} />;
    }

    if (!authed) return <LoginPage />;

    return (
        <Layout>
            {route.name === "search" && (
                <SearchPage
                    initialQuery={route.q}
                    initialSourceId={route.sourceId}
                    initialTag={route.tag}
                    initialScopes={route.scopes}
                />
            )}
            {route.name === "doc" && <DocPage id={route.id} />}

            {route.name === "admin_sources" && <AdminSourcesPage />}

            {route.name === "admin_users" && <AdminUsersPage />}
            {route.name === "admin_invites" && <AdminInvitesPage />}
            {route.name === "admin_audit" && <AdminAuditPage />}

            {route.name === "import" && <ImportPage />}
            {route.name === "analytics" && <AnalyticsPage />}
        </Layout>
    );
}
