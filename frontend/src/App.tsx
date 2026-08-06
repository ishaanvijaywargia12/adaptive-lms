import React, { Suspense, useState } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "react-hot-toast";
import { motion } from "framer-motion";
import { Loader2 } from "lucide-react";

import { AuthProvider, useAuth } from "./contexts/AuthContext";
import Sidebar from "./components/Sidebar";
import { login } from "./lib/auth";
import Callback from "./pages/Callback";

// Lazy-loaded pages
const Dashboard = React.lazy(() => import("./pages/Dashboard"));
const Courses = React.lazy(() => import("./pages/Courses"));
const CoursePlayer = React.lazy(() => import("./pages/CoursePlayer"));
const Leaderboard = React.lazy(() => import("./pages/Leaderboard"));
const Quiz = React.lazy(() => import("./pages/Quiz"));
const Certificates = React.lazy(() => import("./pages/Certificates"));
const LiveSessions = React.lazy(() => import("./pages/LiveSessions"));
const Notifications = React.lazy(() => import("./pages/Notifications"));
const AiDoubts = React.lazy(() => import("./pages/AiDoubts"));

// Instructor Pages
const InstructorDashboard = React.lazy(() => import("./pages/instructor/InstructorDashboard"));
const ManageCourses = React.lazy(() => import("./pages/instructor/ManageCourses"));
const CourseBuilder = React.lazy(() => import("./pages/instructor/CourseBuilder"));

// Admin Pages
const AdminDashboard = React.lazy(() => import("./pages/admin/AdminDashboard"));
const ManageUsers = React.lazy(() => import("./pages/admin/ManageUsers"));
const ReviewCourses = React.lazy(() => import("./pages/admin/ReviewCourses"));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 2,    // 2 minute cache
      retry: 1,
    },
  },
});

const PageLoader = () => (
  <div className="flex items-center justify-center h-64">
    <Loader2 className="w-8 h-8 text-brand-400 animate-spin" />
  </div>
);

const LandingPage = () => (
  <div className="min-h-screen flex flex-col items-center justify-center text-center px-6 bg-surface">
    <motion.div
      initial={{ opacity: 0, y: 32 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, ease: "easeOut" }}
      className="max-w-2xl"
    >
      {/* Logo */}
      <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-brand-500 to-purple-600 flex items-center justify-center mx-auto mb-8 shadow-2xl shadow-brand-900/60">
        <span className="text-4xl">🎓</span>
      </div>

      <h1 className="text-5xl font-black text-white mb-4 leading-tight">
        Learn  <span className="bg-gradient-to-r from-brand-400 to-purple-400 bg-clip-text text-transparent">Smarter</span>,<br />
        Not Harder
      </h1>

      <p className="text-xl text-slate-400 mb-10 leading-relaxed">
        AI-powered adaptive learning paths tailored to your progress.
        Earn certificates, compete on leaderboards, join live sessions.
      </p>

      <div className="flex flex-col sm:flex-row gap-4 justify-center">
        <button
          onClick={login}
          className="btn-primary text-lg px-8 py-4 shadow-xl shadow-brand-900/50"
        >
          Get Started Free
        </button>
        <button
          onClick={login}
          className="btn-secondary text-lg px-8 py-4"
        >
          Sign In
        </button>
      </div>

      <div className="grid grid-cols-3 gap-6 mt-16 text-center">
        {[
          { stat: "10K+", label: "Active Learners" },
          { stat: "500+", label: "Expert Courses" },
          { stat: "95%", label: "Completion Rate" },
        ].map(({ stat, label }) => (
          <div key={label} className="card">
            <p className="text-3xl font-black text-white">{stat}</p>
            <p className="text-slate-400 text-sm mt-1">{label}</p>
          </div>
        ))}
      </div>
    </motion.div>
  </div>
);

const AppShell: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="flex min-h-screen bg-surface">
      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed(c => !c)} />
      <main
        className="flex-1 overflow-auto transition-all duration-250"
        style={{ marginLeft: collapsed ? 72 : 256 }}
      >
        <div className="p-6 max-w-7xl mx-auto">
          {children}
        </div>
      </main>
    </div>
  );
};

const RequireRole: React.FC<{ role: "INSTRUCTOR" | "ADMIN"; children: React.ReactNode }> = ({ role, children }) => {
  const { user } = useAuth();
  if (!user?.roles?.includes(role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
};

const RoleBasedRedirect = () => {
  const { user } = useAuth();
  if (user?.roles?.includes("ADMIN")) return <Navigate to="/admin" replace />;
  if (user?.roles?.includes("INSTRUCTOR")) return <Navigate to="/instructor" replace />;
  return <Navigate to="/dashboard" replace />;
};

const ProtectedRoutes = () => {
  const { authenticated, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-surface">
        <div className="text-center">
          <Loader2 className="w-10 h-10 text-brand-400 animate-spin mx-auto mb-4" />
          <p className="text-slate-400">Loading...</p>
        </div>
      </div>
    );
  }

  if (!authenticated) return <LandingPage />;

  return (
    <AppShell>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/courses" element={<Courses />} />
          <Route path="/courses/:id" element={<CoursePlayer />} />
          <Route path="/quiz/:lessonId" element={<Quiz />} />
          <Route path="/leaderboard" element={<Leaderboard />} />
          <Route path="/certificates" element={<Certificates />} />
          <Route path="/live" element={<LiveSessions />} />
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/ai-doubts" element={<AiDoubts />} />
          
          {/* Instructor Routes */}
          <Route path="/instructor" element={<RequireRole role="INSTRUCTOR"><InstructorDashboard /></RequireRole>} />
          <Route path="/instructor/courses" element={<RequireRole role="INSTRUCTOR"><ManageCourses /></RequireRole>} />
          <Route path="/instructor/courses/:id" element={<RequireRole role="INSTRUCTOR"><CourseBuilder /></RequireRole>} />
          
          {/* Admin Routes */}
          <Route path="/admin" element={<RequireRole role="ADMIN"><AdminDashboard /></RequireRole>} />
          <Route path="/admin/courses" element={<RequireRole role="ADMIN"><ReviewCourses /></RequireRole>} />
          <Route path="/admin/users" element={<RequireRole role="ADMIN"><ManageUsers /></RequireRole>} />

          <Route path="/" element={<RoleBasedRedirect />} />
          <Route path="*" element={<RoleBasedRedirect />} />
        </Routes>
      </Suspense>
    </AppShell>
  );
};

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, "")}>
          <Toaster
            position="top-right"
            toastOptions={{
              style: { background: "#1e293b", color: "#e2e8f0", border: "1px solid #334155" },
              success: { iconTheme: { primary: "#34d399", secondary: "#1e293b" } },
              error: { iconTheme: { primary: "#f87171", secondary: "#1e293b" } },
            }}
          />
          <Routes>
            <Route path="/callback" element={<Callback />} />
            <Route path="/*" element={<ProtectedRoutes />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
