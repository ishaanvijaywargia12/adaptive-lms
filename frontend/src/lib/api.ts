import axios from "axios";
import { getToken } from "./auth";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL || "/api",
  headers: {
    "Content-Type": "application/json",
    "X-Tenant-ID": import.meta.env.VITE_TENANT_ID || "demo",
  },
});

// Attach JWT on every request
api.interceptors.request.use(async (config) => {
  const token = await getToken();
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
      import("./auth").then(({ login }) => login());
    }
    return Promise.reject(err);
  }
);

export default api;
