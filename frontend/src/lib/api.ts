import axios from "axios";
import { getToken, refreshToken } from "./keycloak";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL || "/api",
  headers: {
    "Content-Type": "application/json",
    "X-Tenant-ID": import.meta.env.VITE_TENANT_ID || "demo",
  },
});

// Attach Keycloak JWT on every request
api.interceptors.request.use(async (config) => {
  try {
    await refreshToken();
  } catch {
    // token refresh failed — user will be redirected to login by 401 handler
  }
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // Ensure tenant header is always set
  if (!config.headers["X-Tenant-ID"]) {
    config.headers["X-Tenant-ID"] = import.meta.env.VITE_TENANT_ID || "demo";
  }
  return config;
});

// Global error handler
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      import("./keycloak").then(({ login }) => login());
    }
    return Promise.reject(err);
  }
);

export default api;
