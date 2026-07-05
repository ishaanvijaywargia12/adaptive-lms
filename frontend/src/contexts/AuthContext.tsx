import React, { createContext, useContext, useEffect, useState } from "react";
import { initKeycloak, isAuthenticated, getProfile, hasRole, login } from "../lib/keycloak";

interface AuthUser {
  id: string;
  email: string;
  name: string;
  roles: string[];
  isStudent: boolean;
  isInstructor: boolean;
  isAdmin: boolean;
}

interface AuthContextType {
  user: AuthUser | null;
  loading: boolean;
  authenticated: boolean;
  login: () => void;
}

const AuthContext = createContext<AuthContextType>({
  user: null, loading: true, authenticated: false, login,
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    initKeycloak().then((auth) => {
      setAuthenticated(auth);
      if (auth) {
        const profile = getProfile() as Record<string, unknown>;
        const roles: string[] = (
          (profile?.realm_access as { roles?: string[] })?.roles ?? []
        );
        setUser({
          id: profile?.sub as string,
          email: profile?.email as string,
          name: `${profile?.given_name ?? ""} ${profile?.family_name ?? ""}`.trim(),
          roles,
          isStudent: roles.includes("STUDENT"),
          isInstructor: roles.includes("INSTRUCTOR"),
          isAdmin: roles.includes("ADMIN"),
        });
      }
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, authenticated, login }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
