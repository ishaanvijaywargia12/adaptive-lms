import React, { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import {
  LayoutDashboard, BookOpen, GraduationCap, Trophy, Bell,
  Video, Zap, LogOut, X, ChevronRight, Star,
  Users, Settings, ShieldAlert, Brain, ClipboardCheck
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { logout } from "../lib/auth";

type NavItem = { icon: React.ElementType, label: string, to: string };
type NavGroup = { title: string, items: NavItem[] };

const learningItems: NavItem[] = [
  { icon: LayoutDashboard, label: "Dashboard", to: "/dashboard" },
  { icon: BookOpen, label: "My Courses", to: "/courses" },
  { icon: Brain, label: "AI Doubts", to: "/ai-doubts" },
  { icon: GraduationCap, label: "Certificates", to: "/certificates" },
  { icon: Trophy, label: "Leaderboard", to: "/leaderboard" },
  { icon: Video, label: "Live Sessions", to: "/live" },
  { icon: Bell, label: "Notifications", to: "/notifications" },
];

const teachingItems: NavItem[] = [
  { icon: LayoutDashboard, label: "Instructor Dashboard", to: "/instructor" },
  { icon: BookOpen, label: "Manage Courses", to: "/instructor/courses" },
];

const adminItems: NavItem[] = [
  { icon: ShieldAlert, label: "Admin Dashboard", to: "/admin" },
  { icon: ClipboardCheck, label: "Review Courses", to: "/admin/courses" },
  { icon: Users, label: "Manage Users", to: "/admin/users" },
];

const Sidebar: React.FC<{ collapsed: boolean; onToggle: () => void }> = ({ collapsed, onToggle }) => {
  const { user } = useAuth();

  return (
    <motion.aside
      animate={{ width: collapsed ? 72 : 256 }}
      transition={{ duration: 0.25, ease: "easeInOut" }}
      className="fixed left-0 top-0 h-full bg-surface-card border-r border-slate-700/50 z-40 flex flex-col overflow-hidden"
    >
      {/* Logo */}
      <div className="flex items-center gap-3 px-5 py-4 border-b border-slate-700/50 h-16">
        <div className="w-8 h-8 rounded-lg bg-brand-600 flex items-center justify-center flex-shrink-0 shadow-lg shadow-brand-900/50">
          <Star className="w-4 h-4 text-white" />
        </div>
        <AnimatePresence>
          {!collapsed && (
            <motion.span
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              className="font-bold text-white text-lg whitespace-nowrap"
            >
              AdaptiveLMS
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      {/* Nav */}
      <nav className="flex-1 px-3 py-4 space-y-6 overflow-y-auto">
        
        {/* Learning Section (Always Visible) */}
        <div className="space-y-1">
          {!collapsed && <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Learning</p>}
          {learningItems.map(({ icon: Icon, label, to }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group relative ${
                  isActive
                    ? "bg-brand-600/20 text-brand-400 border border-brand-700/30"
                    : "text-slate-400 hover:text-slate-200 hover:bg-surface-muted"
                }`
              }
            >
              {({ isActive }) => (
                <>
                  <Icon className={`w-5 h-5 flex-shrink-0 ${isActive ? "text-brand-400" : ""}`} />
                  <AnimatePresence>
                    {!collapsed && (
                      <motion.span
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="text-sm font-medium whitespace-nowrap"
                      >
                        {label}
                      </motion.span>
                    )}
                  </AnimatePresence>
                </>
              )}
            </NavLink>
          ))}
        </div>

        {/* Teaching Section */}
        {user?.roles?.includes("INSTRUCTOR") && (
          <div className="space-y-1">
            {!collapsed && <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Teaching</p>}
            {teachingItems.map(({ icon: Icon, label, to }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group relative ${
                    isActive
                      ? "bg-purple-600/20 text-purple-400 border border-purple-700/30"
                      : "text-slate-400 hover:text-slate-200 hover:bg-surface-muted"
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon className={`w-5 h-5 flex-shrink-0 ${isActive ? "text-purple-400" : ""}`} />
                    <AnimatePresence>
                      {!collapsed && (
                        <motion.span
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          className="text-sm font-medium whitespace-nowrap"
                        >
                          {label}
                        </motion.span>
                      )}
                    </AnimatePresence>
                  </>
                )}
              </NavLink>
            ))}
          </div>
        )}

        {/* Administration Section */}
        {user?.roles?.includes("ADMIN") && (
          <div className="space-y-1">
            {!collapsed && <p className="px-3 text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Administration</p>}
            {adminItems.map(({ icon: Icon, label, to }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 group relative ${
                    isActive
                      ? "bg-red-600/20 text-red-400 border border-red-700/30"
                      : "text-slate-400 hover:text-slate-200 hover:bg-surface-muted"
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon className={`w-5 h-5 flex-shrink-0 ${isActive ? "text-red-400" : ""}`} />
                    <AnimatePresence>
                      {!collapsed && (
                        <motion.span
                          initial={{ opacity: 0 }}
                          animate={{ opacity: 1 }}
                          exit={{ opacity: 0 }}
                          className="text-sm font-medium whitespace-nowrap"
                        >
                          {label}
                        </motion.span>
                      )}
                    </AnimatePresence>
                  </>
                )}
              </NavLink>
            ))}
          </div>
        )}

      </nav>

      {/* User profile */}
      <div className="px-3 py-4 border-t border-slate-700/50">
        <div className="flex items-center gap-3 px-2 py-2">
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-brand-500 to-purple-600 flex items-center justify-center flex-shrink-0 text-xs font-bold text-white">
            {user?.name?.charAt(0)?.toUpperCase() ?? "U"}
          </div>
          <AnimatePresence>
            {!collapsed && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                className="flex-1 min-w-0"
              >
                <p className="text-sm font-medium text-slate-200 truncate">{user?.name}</p>
                <p className="text-xs text-slate-500 truncate">{user?.email}</p>
              </motion.div>
            )}
          </AnimatePresence>
          <AnimatePresence>
            {!collapsed && (
              <motion.button
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                onClick={logout}
                className="btn-ghost p-1.5 ml-auto"
                title="Logout"
              >
                <LogOut className="w-4 h-4" />
              </motion.button>
            )}
          </AnimatePresence>
        </div>
      </div>

      {/* Collapse toggle */}
      <button
        onClick={onToggle}
        className="absolute top-4 -right-3 w-6 h-6 bg-surface-card border border-slate-600 rounded-full flex items-center justify-center text-slate-400 hover:text-slate-200 transition-colors z-50"
      >
        {collapsed ? <ChevronRight className="w-3 h-3" /> : <X className="w-3 h-3" />}
      </button>
    </motion.aside>
  );
};

export default Sidebar;
