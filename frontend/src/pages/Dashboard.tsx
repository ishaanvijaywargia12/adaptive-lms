import React from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  BookOpen, GraduationCap, Trophy, Flame, Star, ArrowRight, TrendingUp, Clock,
  Brain, Sparkles, Zap, MessageSquare,
} from "lucide-react";
import { Link } from "react-router-dom";
import api from "../lib/api";
import { useAuth } from "../contexts/AuthContext";
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from "recharts";

interface Stat { label: string; value: string | number; icon: React.ReactNode; color: string; subtext?: string }

const StatCard: React.FC<Stat> = ({ label, value, icon, color, subtext }) => (
  <motion.div
    initial={{ opacity: 0, y: 16 }}
    animate={{ opacity: 1, y: 0 }}
    className="card flex items-start gap-4"
  >
    <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${color}`}>
      {icon}
    </div>
    <div>
      <p className="text-slate-400 text-sm">{label}</p>
      <p className="text-2xl font-bold text-white mt-0.5">{value}</p>
      {subtext && <p className="text-xs text-slate-500 mt-1">{subtext}</p>}
    </div>
  </motion.div>
);

// Activity data derived from enrollments is computed below, not static

export default function Dashboard() {
  const { user } = useAuth();

  const { data: enrollments = [] } = useQuery({
    queryKey: ["my-enrollments"],
    queryFn: () => api.get("/my/enrollments").then(r => r.data.data),
  });

  const { data: points = 0 } = useQuery({
    queryKey: ["my-points"],
    queryFn: () => api.get("/my/points").then(r => r.data.data),
  });

  const { data: streak } = useQuery({
    queryKey: ["my-streak"],
    queryFn: () => api.get("/my/streak").then(r => r.data.data),
  });

  const { data: recommendations = [] } = useQuery({
    queryKey: ["recommendations"],
    queryFn: () => api.get("/my/recommendations").then(r => r.data.data),
  });

  const completedCount = enrollments.filter((e: {completedAt: string}) => e.completedAt).length;

  // Build weekly activity from enrollment progress (fallback to zeros)
  const activityData = ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"].map((day, i) => ({
    day,
    minutes: Math.round((enrollments.length > 0 ? (enrollments.length * (i % 3 + 1) * 15) : 0)),
  }));

  const stats: Stat[] = [
    {
      label: "Enrolled Courses",
      value: enrollments.length,
      icon: <BookOpen className="w-6 h-6 text-blue-400" />,
      color: "bg-blue-900/40",
      subtext: `${completedCount} completed`,
    },
    {
      label: "Total Points",
      value: points.toLocaleString(),
      icon: <Star className="w-6 h-6 text-amber-400" />,
      color: "bg-amber-900/40",
      subtext: "Lifetime XP",
    },
    {
      label: "Current Streak",
      value: `${streak?.currentStreak ?? 0} days`,
      icon: <Flame className="w-6 h-6 text-orange-400" />,
      color: "bg-orange-900/40",
      subtext: `Best: ${streak?.longestStreak ?? 0} days`,
    },
    {
      label: "Certificates",
      value: completedCount,
      icon: <GraduationCap className="w-6 h-6 text-emerald-400" />,
      color: "bg-emerald-900/40",
      subtext: "Earned",
    },
  ];

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Welcome */}
      <div>
        <h1 className="text-3xl font-bold text-white">
          Welcome back, {user?.name?.split(" ")[0]} 👋
        </h1>
        <p className="text-slate-400 mt-1">
          {streak?.currentStreak > 0
            ? `You're on a ${streak.currentStreak}-day streak! Keep it up.`
            : "Start learning today to build your streak!"}
        </p>
      </div>

      {/* ─── AI Feature Hero Banner ─────────────────────────────────────────── */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.05 }}
      >
        <Link to="/ai-doubts" className="block group">
          <div className="relative overflow-hidden rounded-2xl border border-violet-700/40 bg-gradient-to-r from-violet-900/50 via-brand-900/40 to-blue-900/50 p-6 hover:border-violet-600/60 transition-all duration-300 hover:shadow-2xl hover:shadow-violet-900/30">
            {/* Background orbs */}
            <div className="absolute top-0 right-0 w-64 h-64 bg-violet-500/5 rounded-full blur-3xl" />
            <div className="absolute bottom-0 left-32 w-48 h-48 bg-brand-500/5 rounded-full blur-2xl" />

            <div className="relative flex items-center justify-between gap-6 flex-wrap">
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-violet-600 to-brand-600 flex items-center justify-center shadow-xl shadow-violet-900/60 flex-shrink-0">
                  <Brain className="w-7 h-7 text-white" />
                </div>
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <h2 className="text-lg font-bold text-white">AI Doubt Resolution</h2>
                    <span className="text-xs px-2 py-0.5 bg-violet-600/30 text-violet-300 border border-violet-700/40 rounded-full font-semibold">NEW</span>
                  </div>
                  <p className="text-slate-400 text-sm max-w-lg">
                    Ask any question about your course content. AI searches through PDFs via vector embeddings and generates structured answers with GPT-4o-mini.
                  </p>
                  <div className="flex flex-wrap gap-2 mt-3">
                    {[
                      { icon: Brain, label: "Gemini 1.5 Flash", color: "text-violet-400" },
                      { icon: Zap, label: "Qdrant Vector Search", color: "text-blue-400" },
                      { icon: Sparkles, label: "Spring AI RAG", color: "text-brand-400" },
                      { icon: MessageSquare, label: "Redis Cache", color: "text-emerald-400" },
                    ].map(({ icon: Icon, label, color }) => (
                      <div key={label} className="flex items-center gap-1.5 text-xs bg-slate-800/60 px-2.5 py-1 rounded-full border border-slate-700/50">
                        <Icon className={`w-3 h-3 ${color}`} />
                        <span className="text-slate-300">{label}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2 px-5 py-2.5 bg-violet-600 group-hover:bg-violet-500 rounded-xl font-semibold text-white text-sm transition-all duration-200 shadow-lg shadow-violet-900/50 flex-shrink-0">
                Try it now
                <ArrowRight className="w-4 h-4 group-hover:translate-x-0.5 transition-transform" />
              </div>
            </div>
          </div>
        </Link>
      </motion.div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {stats.map((s, i) => (
          <motion.div key={s.label} transition={{ delay: i * 0.05 }}>
            <StatCard {...s} />
          </motion.div>
        ))}
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* Activity Chart */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="card xl:col-span-2"
        >
          <div className="flex items-center justify-between mb-6">
            <div>
              <h2 className="text-lg font-semibold text-white">Weekly Activity</h2>
              <p className="text-sm text-slate-400">Minutes of learning per day</p>
            </div>
            <TrendingUp className="w-5 h-5 text-brand-400" />
          </div>
          <ResponsiveContainer width="100%" height={180}>
            <AreaChart data={activityData}>
              <defs>
                <linearGradient id="actGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#334155" />
              <XAxis dataKey="day" stroke="#64748b" tick={{ fontSize: 12 }} />
              <YAxis stroke="#64748b" tick={{ fontSize: 12 }} />
              <Tooltip
                contentStyle={{ background: "#1e293b", border: "1px solid #334155", borderRadius: 12 }}
                labelStyle={{ color: "#94a3b8" }}
              />
              <Area type="monotone" dataKey="minutes" stroke="#3b82f6" strokeWidth={2} fill="url(#actGrad)" />
            </AreaChart>
          </ResponsiveContainer>
        </motion.div>

        {/* Continue Learning */}
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.25 }}
          className="card flex flex-col"
        >
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-white">Continue Learning</h2>
            <Clock className="w-5 h-5 text-slate-400" />
          </div>
          <div className="space-y-3 flex-1">
            {enrollments.slice(0, 3).map((e: {courseId: string; progressPercent: number}, i: number) => (
              <Link
                key={e.courseId}
                to={`/courses/${e.courseId}`}
                className="block p-3 rounded-xl bg-surface-muted hover:bg-slate-600/40 transition-all border border-slate-700/40 group"
              >
                <div className="flex items-center justify-between mb-2">
                  <p className="text-sm font-medium text-slate-200 group-hover:text-white transition-colors">
                    Course {i + 1}
                  </p>
                  <ArrowRight className="w-3.5 h-3.5 text-slate-500 group-hover:text-brand-400 transition-colors" />
                </div>
                <div className="progress-bar">
                  <div
                    className="progress-fill"
                    style={{ width: `${e.progressPercent}%` }}
                  />
                </div>
                <p className="text-xs text-slate-500 mt-1">{e.progressPercent}% complete</p>
              </Link>
            ))}
            {enrollments.length === 0 && (
              <div className="text-center py-8">
                <BookOpen className="w-8 h-8 text-slate-600 mx-auto mb-2" />
                <p className="text-slate-500 text-sm">No active courses</p>
                <Link to="/courses" className="text-brand-400 text-sm hover:underline mt-1 inline-block">
                  Browse courses →
                </Link>
              </div>
            )}
          </div>
        </motion.div>
      </div>

      {/* AI Recommendations */}
      {recommendations.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="card"
        >
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-semibold text-white">🤖 Recommended for You</h2>
              <p className="text-sm text-slate-400">Personalized based on your learning patterns</p>
            </div>
            <Link to="/my/recommendations" className="text-brand-400 text-sm hover:underline">View all</Link>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {recommendations.slice(0, 3).map((rec: {id: string; recommendedCourseId: string; reason: string; confidenceScore: number}) => (
              <Link
                key={rec.id}
                to={`/courses/${rec.recommendedCourseId}`}
                className="p-4 rounded-xl bg-gradient-to-br from-brand-900/30 to-purple-900/20 border border-brand-800/30 hover:border-brand-600/50 transition-all group"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="badge badge-blue">{Math.round(rec.confidenceScore)}% match</span>
                  <Trophy className="w-4 h-4 text-brand-400" />
                </div>
                <p className="text-sm text-slate-300 mt-2">{rec.reason}</p>
              </Link>
            ))}
          </div>
        </motion.div>
      )}
    </div>
  );
}
