import Keycloak from "keycloak-js";

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:8180",
  realm: import.meta.env.VITE_KEYCLOAK_REALM || "lms-demo",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "lms-frontend",
});

export const initKeycloak = async (): Promise<boolean> => {
  const authenticated = await keycloak.init({
    onLoad: "check-sso",
    silentCheckSsoRedirectUri: window.location.origin + "/silent-check-sso.html",
    pkceMethod: "S256",
  });
  return authenticated;
};

export const login = () => keycloak.login();
export const logout = () => keycloak.logout({ redirectUri: window.location.origin });
export const getToken = () => keycloak.token;
export const isAuthenticated = () => keycloak.authenticated ?? false;
export const hasRole = (role: string) => keycloak.hasRealmRole(role);
export const getProfile = () => keycloak.tokenParsed;
export const getUserId = () => keycloak.subject;

// Token refresh: refresh if token expires in less than 60 seconds
export const refreshToken = () => keycloak.updateToken(60);

export default keycloak;
