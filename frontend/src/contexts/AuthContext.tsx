import React, { createContext, useContext, useEffect, useState } from "react";
import { getUser, login, userManager } from "../lib/auth";

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
    const loadUser = () => {
      getUser().then((oidcUser) => {
        const auth = !!(oidcUser && !oidcUser.expired);
        setAuthenticated(auth);
        if (auth && oidcUser) {
          const profile = oidcUser.profile;
          const roles: string[] = (profile.roles as string[]) || [];
          setUser({
            id: profile.sub,
            email: profile.email as string,
            name: `${profile.given_name ?? ""} ${profile.family_name ?? ""}`.trim(),
            roles,
            isStudent: roles.includes("STUDENT"),
            isInstructor: roles.includes("INSTRUCTOR"),
            isAdmin: roles.includes("ADMIN"),
          });
        } else {
          setUser(null);
        }
        setLoading(false);
      }).catch(() => setLoading(false));
    };

    loadUser();

    // Subscribe to auth state changes
    userManager.events.addUserLoaded(loadUser);
    userManager.events.addUserUnloaded(loadUser);
    
    return () => {
      userManager.events.removeUserLoaded(loadUser);
      userManager.events.removeUserUnloaded(loadUser);
    };
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading, authenticated, login }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
