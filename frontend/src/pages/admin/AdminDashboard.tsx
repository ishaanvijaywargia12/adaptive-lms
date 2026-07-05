import React from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../contexts/AuthContext";
import { Users, BookOpen, ClipboardCheck, Loader2, CheckCircle, Clock } from "lucide-react";
import { Link } from "react-router-dom";
import api from "../../lib/api";

const AdminDashboard: React.FC = () => {
  const { user } = useAuth();

  // Fetch users count
  const { data: usersData } = useQuery({
    queryKey: ["admin-users-count"],
    queryFn: () => api.get("/admin/users?page=0&size=1").then((r) => r.data.data),
  });

  // Fetch pending courses count
  const { data: pendingCourses = [], isLoading: loadingPending } = useQuery({
    queryKey: ["pending-courses"],
    queryFn: () => api.get("/courses/pending").then((r) => r.data.data ?? []),
  });

  const totalUsers = usersData?.totalElements ?? 0;
  const pendingCount = Array.isArray(pendingCourses) ? pendingCourses.length : 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white">Administration Dashboard</h1>
        <p className="text-slate-400 text-sm mt-0.5">
          Logged in as Admin — {user?.email}
        </p>
      </div>

      {/* Welcome banner */}
      <div className="card border-red-700/20 bg-gradient-to-br from-red-900/20 to-surface-card">
        <h2 className="text-lg font-semibold text-white mb-1">Platform Overview</h2>
        <p className="text-slate-400 text-sm">
          Use these tools to manage users and review course submissions before they go live.
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div className="card flex items-center gap-4">
          <div className="p-3 bg-blue-500/10 text-blue-400 rounded-xl">
            <Users className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm text-slate-400">Total Users</p>
            <p className="text-2xl font-bold text-white">{totalUsers}</p>
          </div>
        </div>

        <div className="card flex items-center gap-4">
          <div className="p-3 bg-yellow-500/10 text-yellow-400 rounded-xl">
            <Clock className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm text-slate-400">Awaiting Review</p>
            <p className="text-2xl font-bold text-white">
              {loadingPending ? <Loader2 className="w-5 h-5 animate-spin" /> : pendingCount}
            </p>
          </div>
        </div>

        <div className="card flex items-center gap-4">
          <div className="p-3 bg-red-500/10 text-red-400 rounded-xl">
            <CheckCircle className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm text-slate-400">Active Admins</p>
            <p className="text-2xl font-bold text-white">1</p>
          </div>
        </div>
      </div>

      {/* Quick Actions */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Link
          to="/admin/courses"
          className="card hover:border-yellow-600/40 transition-all group"
        >
          <div className="flex items-center gap-4">
            <div className="p-3 bg-yellow-900/30 text-yellow-400 rounded-xl group-hover:bg-yellow-900/50 transition-colors">
              <ClipboardCheck className="w-6 h-6" />
            </div>
            <div>
              <p className="font-semibold text-white">Review Courses</p>
              <p className="text-sm text-slate-400 mt-0.5">
                {pendingCount > 0
                  ? `${pendingCount} course${pendingCount !== 1 ? "s" : ""} waiting for approval`
                  : "No courses pending review"}
              </p>
            </div>
          </div>
        </Link>

        <Link
          to="/admin/users"
          className="card hover:border-blue-600/40 transition-all group"
        >
          <div className="flex items-center gap-4">
            <div className="p-3 bg-blue-900/30 text-blue-400 rounded-xl group-hover:bg-blue-900/50 transition-colors">
              <Users className="w-6 h-6" />
            </div>
            <div>
              <p className="font-semibold text-white">Manage Users</p>
              <p className="text-sm text-slate-400 mt-0.5">
                {totalUsers > 0 ? `${totalUsers} registered users` : "View and manage all users"}
              </p>
            </div>
          </div>
        </Link>
      </div>
    </div>
  );
};

export default AdminDashboard;
