import React from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Bell, BellOff, Check, CheckCheck } from "lucide-react";
import api from "../lib/api";
import { formatDistanceToNow } from "date-fns";

interface Notification { id: string; title: string; message: string; type: string; read: boolean; createdAt: string }

const typeColor: Record<string, string> = {
  LESSON_COMPLETE: "bg-blue-900/40 text-blue-400",
  QUIZ_PASSED: "bg-amber-900/40 text-amber-400",
  COURSE_COMPLETE: "bg-emerald-900/40 text-emerald-400",
  BADGE_EARNED: "bg-purple-900/40 text-purple-400",
  ASSIGNMENT_GRADED: "bg-cyan-900/40 text-cyan-400",
  ENROLLMENT_CONFIRMED: "bg-green-900/40 text-green-400",
  default: "bg-slate-700/40 text-slate-400",
};

export default function Notifications() {
  const qc = useQueryClient();

  const { data: notifications = [], isLoading } = useQuery<Notification[]>({
    queryKey: ["notifications"],
    queryFn: () => api.get("/notifications").then(r => r.data.data),
  });

  const markReadMutation = useMutation({
    mutationFn: (id: string) => api.patch(`/notifications/${id}/read`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });

  const markAllMutation = useMutation({
    mutationFn: () => api.patch("/notifications/read-all"),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications"] }),
  });

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <div className="space-y-4 animate-fade-in max-w-2xl mx-auto">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-purple-900/40 flex items-center justify-center relative">
            <Bell className="w-5 h-5 text-purple-400" />
            {unreadCount > 0 && (
              <span className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 rounded-full text-xs text-white flex items-center justify-center font-bold">
                {unreadCount > 9 ? "9+" : unreadCount}
              </span>
            )}
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white">Notifications</h1>
            <p className="text-slate-400 text-sm">{unreadCount} unread</p>
          </div>
        </div>
        {unreadCount > 0 && (
          <button
            onClick={() => markAllMutation.mutate()}
            disabled={markAllMutation.isPending}
            className="btn-secondary flex items-center gap-2 text-sm"
          >
            <CheckCheck className="w-4 h-4" /> Mark all read
          </button>
        )}
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map(i => <div key={i} className="h-20 rounded-xl bg-surface-card animate-pulse-soft" />)}
        </div>
      ) : notifications.length === 0 ? (
        <div className="card text-center py-16">
          <BellOff className="w-12 h-12 text-slate-700 mx-auto mb-3" />
          <p className="text-slate-400">No notifications yet</p>
          <p className="text-slate-600 text-sm mt-1">Activity from courses and achievements will appear here</p>
        </div>
      ) : (
        <div className="space-y-2">
          {notifications.map((n, i) => (
            <motion.div
              key={n.id}
              initial={{ opacity: 0, x: -12 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: i * 0.03 }}
              className={`flex items-start gap-4 p-4 rounded-xl border transition-all cursor-pointer group ${
                n.read
                  ? "bg-surface-card/60 border-slate-700/30 opacity-70"
                  : "bg-surface-card border-slate-600/50 hover:border-slate-500"
              }`}
              onClick={() => !n.read && markReadMutation.mutate(n.id)}
            >
              {/* Type indicator */}
              <div className={`w-2 h-2 rounded-full mt-2 flex-shrink-0 ${n.read ? "bg-slate-600" : "bg-brand-400"}`} />

              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between gap-2 mb-1">
                  <p className="text-sm font-semibold text-slate-200 group-hover:text-white transition-colors">
                    {n.title}
                  </p>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <span className={`text-xs px-2 py-0.5 rounded-full ${typeColor[n.type] ?? typeColor.default}`}>
                      {n.type.replace(/_/g, " ")}
                    </span>
                    {n.read && <Check className="w-3.5 h-3.5 text-slate-600" />}
                  </div>
                </div>
                <p className="text-sm text-slate-400 line-clamp-2">{n.message}</p>
                <p className="text-xs text-slate-600 mt-1">
                  {n.createdAt ? formatDistanceToNow(new Date(n.createdAt), { addSuffix: true }) : ""}
                </p>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
