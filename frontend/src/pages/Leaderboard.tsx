import React from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Trophy, Medal, Star, Flame, Crown } from "lucide-react";
import api from "../lib/api";
import { useAuth } from "../contexts/AuthContext";

interface LeaderboardEntry { rank: number; studentId: string; studentName: string; totalPoints: number }

const rankIcon = (rank: number) => {
  if (rank === 1) return <Crown className="w-5 h-5 text-amber-400" />;
  if (rank === 2) return <Medal className="w-5 h-5 text-slate-300" />;
  if (rank === 3) return <Medal className="w-5 h-5 text-amber-700" />;
  return <span className="text-slate-500 font-bold text-sm w-5 text-center">{rank}</span>;
};

const rankBg = (rank: number) => {
  if (rank === 1) return "bg-gradient-to-r from-amber-900/40 to-amber-800/20 border-amber-700/30";
  if (rank === 2) return "bg-gradient-to-r from-slate-800/60 to-slate-700/20 border-slate-600/30";
  if (rank === 3) return "bg-gradient-to-r from-amber-950/40 to-amber-900/10 border-amber-900/30";
  return "bg-surface-muted/40 border-slate-700/20";
};

export default function Leaderboard() {
  const { user } = useAuth();
  const [period, setPeriod] = React.useState<"WEEKLY" | "ALL_TIME">("WEEKLY");

  const { data: entries = [], isLoading } = useQuery<LeaderboardEntry[]>({
    queryKey: ["leaderboard", "global", period],
    queryFn: () => api.get(`/leaderboard?period=${period}`).then(r => r.data.data),
  });

  const { data: myRank } = useQuery({
    queryKey: ["my-rank", period],
    queryFn: () => api.get(`/leaderboard/my-rank?period=${period}`).then(r => r.data.data),
  });

  return (
    <div className="space-y-6 animate-fade-in max-w-2xl mx-auto">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-amber-900/40 flex items-center justify-center">
            <Trophy className="w-5 h-5 text-amber-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-white">Leaderboard</h1>
            <p className="text-slate-400 text-sm">Top learners</p>
          </div>
        </div>
        <div className="flex bg-slate-800 rounded-lg p-1">
          <button
            onClick={() => setPeriod("WEEKLY")}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
              period === "WEEKLY" ? "bg-slate-700 text-white shadow-sm" : "text-slate-400 hover:text-slate-200"
            }`}
          >
            Weekly
          </button>
          <button
            onClick={() => setPeriod("ALL_TIME")}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
              period === "ALL_TIME" ? "bg-slate-700 text-white shadow-sm" : "text-slate-400 hover:text-slate-200"
            }`}
          >
            All Time
          </button>
        </div>
      </div>

      {/* My rank card */}
      {myRank && (
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          className="card bg-gradient-to-r from-brand-900/40 to-purple-900/20 border-brand-800/30"
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-gradient-to-br from-brand-500 to-purple-600 flex items-center justify-center text-sm font-bold text-white">
                {user?.name?.charAt(0) ?? "U"}
              </div>
              <div>
                <p className="text-white font-semibold">{user?.name} (You)</p>
                <p className="text-slate-400 text-sm flex items-center gap-1">
                  <Star className="w-3 h-3 text-amber-400" /> {myRank.totalPoints?.toLocaleString()} XP
                </p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-3xl font-black text-white">#{myRank.rank ?? "—"}</p>
              <p className="text-slate-400 text-xs">Your rank</p>
            </div>
          </div>
        </motion.div>
      )}

      {/* Top 3 podium */}
      {entries.length >= 3 && (
        <div className="grid grid-cols-3 gap-3">
          {[entries[1], entries[0], entries[2]].map((entry, i) => (
            <motion.div
              key={entry.studentId}
              initial={{ opacity: 0, y: i === 1 ? -12 : 12 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: i * 0.1 }}
              className={`card text-center ${i === 1 ? "ring-2 ring-amber-500/40" : ""}`}
            >
              {i === 1 && <Crown className="w-6 h-6 text-amber-400 mx-auto mb-2" />}
              <div className="w-12 h-12 rounded-full bg-gradient-to-br from-brand-500 to-purple-600 flex items-center justify-center text-lg font-bold text-white mx-auto mb-2">
                {entry.studentName?.charAt(0) ?? "?"}
              </div>
              <p className="text-white font-semibold text-sm truncate">{entry.studentName}</p>
              <p className="text-amber-400 font-bold">{entry.totalPoints?.toLocaleString()}</p>
              <p className="text-slate-500 text-xs">#{entry.rank}</p>
            </motion.div>
          ))}
        </div>
      )}

      {/* Full list */}
      <div className="space-y-2">
        {isLoading
          ? Array.from({ length: 10 }).map((_, i) => (
              <div key={i} className="h-14 rounded-xl bg-surface-card animate-pulse-soft" />
            ))
          : entries.map((entry, i) => (
              <motion.div
                key={entry.studentId}
                initial={{ opacity: 0, x: -12 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: i * 0.03 }}
                className={`flex items-center gap-4 p-4 rounded-xl border transition-all ${rankBg(entry.rank)} ${
                  entry.studentId === user?.id ? "ring-1 ring-brand-500/40" : ""
                }`}
              >
                <div className="flex items-center justify-center w-6 flex-shrink-0">
                  {rankIcon(entry.rank)}
                </div>
                <div className="w-9 h-9 rounded-full bg-gradient-to-br from-slate-600 to-slate-700 flex items-center justify-center text-sm font-bold text-white flex-shrink-0">
                  {entry.studentName?.charAt(0) ?? "?"}
                </div>
                <div className="flex-1 min-w-0">
                  <p className={`font-medium truncate ${entry.studentId === user?.id ? "text-brand-300" : "text-slate-200"}`}>
                    {entry.studentName}
                    {entry.studentId === user?.id && " (you)"}
                  </p>
                </div>
                <div className="flex items-center gap-1 text-amber-400 flex-shrink-0">
                  <Star className="w-4 h-4" />
                  <span className="font-bold text-sm">{entry.totalPoints?.toLocaleString()}</span>
                </div>
              </motion.div>
            ))}
      </div>
    </div>
  );
}
