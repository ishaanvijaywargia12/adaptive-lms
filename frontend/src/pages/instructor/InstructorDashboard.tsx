import React from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "../../contexts/AuthContext";
import { BookOpen, Users, Video, Edit3, Loader2 } from "lucide-react";
import api from "../../lib/api";

const InstructorDashboard: React.FC = () => {
  const { user } = useAuth();

  const { data: myCourses = [], isLoading: loadingCourses } = useQuery({
    queryKey: ["instructor-courses"],
    queryFn: () => api.get("/courses/my").then(r => r.data.data ?? []),
  });

  const { data: liveSessions = [], isLoading: loadingSessions } = useQuery({
    queryKey: ["live-sessions"],
    queryFn: () => api.get("/live-sessions").then(r => r.data.data ?? []),
  });

  const activeCourses = myCourses.filter((c: any) => c.status === "PUBLISHED").length;
  const draftCourses = myCourses.filter((c: any) => c.status === "DRAFT").length;
  
  const myUpcomingSessions = liveSessions.filter(
    (s: any) => s.instructorId === user?.id && s.status === "SCHEDULED"
  ).length;

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Instructor Dashboard</h1>
      </div>

      <div className="card p-6 border-brand-500/20 bg-gradient-to-br from-brand-900/40 to-surface-card">
        <h2 className="text-xl font-semibold text-white mb-2">Welcome back, {user?.name} 👨‍🏫</h2>
        <p className="text-slate-400">Manage your active courses, schedule live sessions, and review student progress here.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="card flex items-center gap-4">
          <div className="p-3 bg-brand-500/10 text-brand-400 rounded-lg">
            <BookOpen className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm text-slate-400">Active Courses</p>
            <p className="text-2xl font-bold text-white">
              {loadingCourses ? <Loader2 className="w-5 h-5 animate-spin" /> : activeCourses}
            </p>
          </div>
        </div>

        <div className="card flex items-center gap-4">
          <div className="p-3 bg-slate-500/10 text-slate-400 rounded-lg">
            <Edit3 className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm text-slate-400">Draft Courses</p>
            <p className="text-2xl font-bold text-white">
              {loadingCourses ? <Loader2 className="w-5 h-5 animate-spin" /> : draftCourses}
            </p>
          </div>
        </div>

        <div className="card flex items-center gap-4">
          <div className="p-3 bg-purple-500/10 text-purple-400 rounded-lg">
            <Video className="w-6 h-6" />
          </div>
          <div>
            <p className="text-sm text-slate-400">Upcoming Live Sessions</p>
            <p className="text-2xl font-bold text-white">
              {loadingSessions ? <Loader2 className="w-5 h-5 animate-spin" /> : myUpcomingSessions}
            </p>
          </div>
        </div>
      </div>

      <div className="card mt-6">
        <h3 className="text-lg font-semibold text-white mb-4">Recent Activity</h3>
        <p className="text-slate-500 text-sm">You have no recent activity. Create a course to get started.</p>
      </div>
    </div>
  );
};

export default InstructorDashboard;
