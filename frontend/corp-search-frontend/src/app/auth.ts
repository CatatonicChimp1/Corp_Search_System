const TOKEN_KEY = "corp_search_token";
const ROLE_KEY = "corp_search_role";
const EMAIL_KEY = "corp_search_email";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function setSession(token: string, email: string, role: string) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(ROLE_KEY, role);
    localStorage.setItem(EMAIL_KEY, email);
}

export function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(EMAIL_KEY);
}

export function getRole(): string | null {
    return localStorage.getItem(ROLE_KEY);
}

export function getEmail(): string | null {
    return localStorage.getItem(EMAIL_KEY);
}
