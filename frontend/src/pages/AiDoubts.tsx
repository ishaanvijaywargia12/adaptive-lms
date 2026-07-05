import React, { useState, useRef, useEffect, useCallback } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Sparkles, Send, BookOpen, Clock, CheckCircle2, XCircle,
  ChevronDown, ChevronUp, Loader2, Brain, RefreshCw, Copy, Check,
  FileText, MessageSquare, Zap, HelpCircle, Upload, Info
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import api from "../lib/api";

// ─── Types ────────────────────────────────────────────────────────────────────

interface DoubtSession {
  id: string;
  courseId: string;
  questionText: string;
  answerText: string | null;
  status: "PENDING" | "RESOLVED" | "FAILED";
  resolvedAt: string | null;
  createdAt: string;
}

interface SubmitResponse {
  sessionId?: string;
  message?: string;
  // Cache-hit returns full answer directly
  answer?: string;
  sourceChunks?: string[];
  resolvedAt?: string;
}

// ─── Constants ───────────────────────────────────────────────────────

const QUICK_QUESTIONS = [
  "What are the main concepts of this course?",
  "Can you summarize the last module?",
  "What is the most important topic for the exam?",
];

// ─── Status Badge ─────────────────────────────────────────────────────────────

const StatusBadge: React.FC<{ status: DoubtSession["status"] }> = ({ status }) => {
  if (status === "PENDING")
    return (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-900/60 text-amber-300 border border-amber-800">
        <Loader2 className="w-3 h-3 animate-spin" /> Processing
      </span>
    );
  if (status === "RESOLVED")
    return (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-900/60 text-emerald-300 border border-emerald-800">
        <CheckCircle2 className="w-3 h-3" /> Resolved
      </span>
    );
  return (
    <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-900/60 text-red-300 border border-red-800">
      <XCircle className="w-3 h-3" /> Failed
    </span>
  );
};

// ─── Answer Renderer ──────────────────────────────────────────────────────────

const AnswerBlock: React.FC<{ answer: string; sourceChunks?: string[] }> = ({ answer, sourceChunks }) => {
  const [copied, setCopied] = useState(false);
  const [showSources, setShowSources] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(answer);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // Parse sections (bold markdown headers → JSX)
  const renderAnswer = (text: string) => {
    return text.split("\n").map((line, i) => {
      // Bold header like **Direct Answer:**
      const boldMatch = line.match(/^\*\*(.+?)\*\*(.*)$/);
      if (boldMatch) {
        return (
          <p key={i} className="mb-2">
            <span className="font-bold text-white">{boldMatch[1]}</span>
            <span className="text-slate-300">{boldMatch[2]}</span>
          </p>
        );
      }
      if (line.trim() === "") return <br key={i} />;
      return <p key={i} className="text-slate-300 mb-1 leading-relaxed">{line}</p>;
    });
  };

  return (
    <div className="space-y-3">
      {/* Answer text */}
      <div className="relative bg-gradient-to-br from-slate-800/80 to-slate-900/80 rounded-xl p-4 border border-slate-700/50">
        <button
          onClick={handleCopy}
          className="absolute top-3 right-3 btn-ghost p-1.5 text-slate-500 hover:text-slate-300"
          title="Copy answer"
        >
          {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
        </button>
        <div className="pr-8 text-sm leading-relaxed">
          {renderAnswer(answer)}
        </div>
      </div>

      {/* Source chunks toggle */}
      {sourceChunks && sourceChunks.length > 0 && (
        <div>
          <button
            onClick={() => setShowSources(s => !s)}
            className="flex items-center gap-2 text-xs text-slate-500 hover:text-slate-300 transition-colors"
          >
            <FileText className="w-3.5 h-3.5" />
            {showSources ? "Hide" : "Show"} {sourceChunks.length} source excerpt{sourceChunks.length !== 1 ? "s" : ""}
            {showSources ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </button>
          <AnimatePresence>
            {showSources && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: "auto" }}
                exit={{ opacity: 0, height: 0 }}
                className="overflow-hidden mt-2 space-y-2"
              >
                {sourceChunks.map((chunk, i) => (
                  <div key={i} className="bg-slate-800/50 border border-slate-700/40 rounded-lg p-3">
                    <p className="text-xs font-semibold text-slate-500 mb-1">Excerpt {i + 1}</p>
                    <p className="text-xs text-slate-400 leading-relaxed line-clamp-4">{chunk}</p>
                  </div>
                ))}
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
};

// ─── Doubt Card ───────────────────────────────────────────────────────────────

const DoubtCard: React.FC<{ session: DoubtSession; onRefresh: (id: string) => void }> = ({ session, onRefresh }) => {
  const [expanded, setExpanded] = useState(session.status === "RESOLVED");

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="card p-0 overflow-hidden"
    >
      {/* Header */}
      <button
        onClick={() => setExpanded(e => !e)}
        className="w-full flex items-start gap-3 p-4 text-left hover:bg-slate-700/20 transition-colors"
      >
        <div className="w-8 h-8 rounded-lg bg-brand-900/50 flex items-center justify-center flex-shrink-0 mt-0.5">
          <HelpCircle className="w-4 h-4 text-brand-400" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-medium text-slate-200 leading-snug line-clamp-2">{session.questionText}</p>
          <div className="flex items-center gap-3 mt-1.5">
            <StatusBadge status={session.status} />
            <span className="text-xs text-slate-500">
              {new Date(session.createdAt).toLocaleString()}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0 ml-2">
          {session.status === "PENDING" && (
            <button
              onClick={(e) => { e.stopPropagation(); onRefresh(session.id); }}
              className="btn-ghost p-1.5"
              title="Check for answer"
            >
              <RefreshCw className="w-3.5 h-3.5" />
            </button>
          )}
          {expanded ? <ChevronUp className="w-4 h-4 text-slate-500" /> : <ChevronDown className="w-4 h-4 text-slate-500" />}
        </div>
      </button>

      {/* Answer */}
      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="px-4 pb-4 border-t border-slate-700/40 pt-4">
              {session.status === "PENDING" && (
                <div className="flex items-center gap-3 py-6 justify-center">
                  <div className="flex gap-1">
                    {[0, 1, 2].map(i => (
                      <motion.div
                        key={i}
                        className="w-2 h-2 bg-brand-400 rounded-full"
                        animate={{ y: [0, -8, 0] }}
                        transition={{ duration: 0.8, repeat: Infinity, delay: i * 0.15 }}
                      />
                    ))}
                  </div>
                  <p className="text-sm text-slate-400">AI is analyzing your question...</p>
                </div>
              )}
              {session.status === "RESOLVED" && session.answerText && (
                <AnswerBlock answer={session.answerText} />
              )}
              {session.status === "FAILED" && (
                <div className="flex items-center gap-2 text-red-400 py-4">
                  <XCircle className="w-4 h-4" />
                  <p className="text-sm">Processing failed. Please try submitting your question again.</p>
                </div>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
};

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function AiDoubtsPage() {
  const queryClient = useQueryClient();
  const [selectedCourse, setSelectedCourse] = useState<string>("");
  const [question, setQuestion] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Fetch all published courses
  const { data: allCourses = [] } = useQuery({
    queryKey: ["all-published-courses"],
    queryFn: async () => {
      const res = await api.get("/courses", { params: { size: 100 } });
      const courses = res.data.data.content || [];
      return courses.filter((c: any) => c.status === "PUBLISHED");
    },
  });

  // Set default course if available
  useEffect(() => {
    if (allCourses.length > 0 && !selectedCourse) {
      setSelectedCourse(allCourses[0].id);
    }
  }, [allCourses, selectedCourse]);

  // Fetch doubt history from backend
  const { data: sessions = [] } = useQuery<DoubtSession[]>({
    queryKey: ["my-doubts"],
    queryFn: () => api.get("/v1/rag/doubts/my").then(r => r.data.data),
    retry: 1
  });

  // Submit doubt
  const submitMutation = useMutation({
    mutationFn: (payload: { courseId: string; question: string }) =>
      api.post<any>("/v1/rag/doubts", payload).then(r => r.data.data),
    onSuccess: (data: SubmitResponse) => {
      if (data.answer) {
        // Cache hit — full answer returned synchronously
        toast.success("⚡ Answer retrieved instantly from cache!", { duration: 3000 });
        queryClient.invalidateQueries({ queryKey: ["my-doubts"] });
      } else {
        toast.success("✅ Doubt submitted! AI is working on your answer...", { duration: 4000 });
        queryClient.invalidateQueries({ queryKey: ["my-doubts"] });
        // Poll for the answer
        setTimeout(() => queryClient.invalidateQueries({ queryKey: ["my-doubts"] }), 5000);
      }
      setQuestion("");
    },
    onError: (err: any) => {
      toast.error("Failed to submit doubt. Please try again.");
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!question.trim() || question.length < 10) {
      toast.error("Please enter a question with at least 10 characters.");
      return;
    }
    if (!selectedCourse) {
      toast.error("Please select a course first.");
      return;
    }
    submitMutation.mutate({ courseId: selectedCourse, question: question.trim() });
  };

  const handleRefresh = (sessionId: string) => {
    queryClient.invalidateQueries({ queryKey: ["my-doubts"] });
    toast("Checking for updated answer...", { icon: "🔄" });
  };

  // Auto-resize textarea
  const handleTextareaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setQuestion(e.target.value);
    e.target.style.height = "auto";
    e.target.style.height = Math.min(e.target.scrollHeight, 160) + "px";
  };

  const isPending = submitMutation.isPending;

  return (
    <div className="space-y-6 animate-fade-in max-w-4xl mx-auto">

      {/* ── Header ── */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <div className="flex items-center gap-3 mb-1">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-600 to-brand-600 flex items-center justify-center shadow-lg shadow-violet-900/50">
              <Sparkles className="w-5 h-5 text-white" />
            </div>
            <h1 className="text-2xl font-bold text-white">AI Doubt Resolution</h1>
          </div>
          <p className="text-slate-400 text-sm ml-13">
            Ask any question about your course content — powered by RAG, Qdrant vector search & GPT-4o-mini
          </p>
        </div>

        {/* How it works pill */}
        <div className="flex items-center gap-3 flex-wrap">
          {[
            { icon: Brain, label: "Embeds question", color: "text-violet-400" },
            { icon: Zap, label: "Searches Qdrant", color: "text-blue-400" },
            { icon: Sparkles, label: "GPT-4o-mini answers", color: "text-brand-400" },
          ].map(({ icon: Icon, label, color }) => (
            <div key={label} className="flex items-center gap-1.5 text-xs text-slate-500 bg-surface-muted px-3 py-1.5 rounded-full border border-slate-700/50">
              <Icon className={`w-3.5 h-3.5 ${color}`} />
              {label}
            </div>
          ))}
        </div>
      </div>

      {/* ── Submit Form ── */}
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        animate={{ opacity: 1, y: 0 }}
        className="card border-brand-700/30 bg-gradient-to-br from-surface-card to-slate-900/50"
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Course selector */}
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              Course
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {allCourses.map((course: any) => (
                <button
                  key={course.id}
                  type="button"
                  onClick={() => setSelectedCourse(course.id)}
                  className={`text-left p-2.5 rounded-lg border text-xs font-medium transition-all duration-200 ${
                    selectedCourse === course.id
                      ? "bg-brand-600/20 border-brand-600/60 text-brand-300"
                      : "bg-surface-muted border-slate-700/50 text-slate-400 hover:border-slate-500 hover:text-slate-300"
                  }`}
                >
                  <BookOpen className={`w-3.5 h-3.5 mb-1 ${selectedCourse === course.id ? "text-brand-400" : "text-slate-500"}`} />
                  {course.title}
                </button>
              ))}
              {allCourses.length === 0 && (
                <p className="text-sm text-slate-500 col-span-full">No published courses available.</p>
              )}
            </div>
          </div>

          {/* Question Input */}
          <div>
            <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">
              Your Question
            </label>
            <div className="relative">
              <textarea
                ref={textareaRef}
                value={question}
                onChange={handleTextareaChange}
                placeholder="e.g. What is the difference between abstract classes and interfaces?"
                className="input resize-none min-h-[80px] pr-14"
                rows={3}
              />
              <button
                type="submit"
                disabled={isPending || question.trim().length < 10}
                className="absolute right-3 bottom-3 w-9 h-9 bg-brand-600 hover:bg-brand-700 disabled:opacity-40 disabled:cursor-not-allowed rounded-lg flex items-center justify-center transition-all duration-200 active:scale-95 shadow-lg shadow-brand-900/50"
              >
                {isPending ? (
                  <Loader2 className="w-4 h-4 text-white animate-spin" />
                ) : (
                  <Send className="w-4 h-4 text-white" />
                )}
              </button>
            </div>
            <div className="flex items-center justify-between mt-2">
              <p className="text-xs text-slate-500">{question.length}/2000 characters</p>
              {question.trim().length < 10 && question.length > 0 && (
                <p className="text-xs text-amber-500">Minimum 10 characters</p>
              )}
            </div>
          </div>

          {/* Quick suggestions */}
          <div>
            <p className="text-xs text-slate-500 mb-2">Quick questions:</p>
            <div className="flex flex-wrap gap-2">
              {QUICK_QUESTIONS.map(q => (
                <button
                  key={q}
                  type="button"
                  onClick={() => {
                    setQuestion(q);
                    if (textareaRef.current) {
                      textareaRef.current.style.height = "auto";
                      textareaRef.current.style.height = textareaRef.current.scrollHeight + "px";
                    }
                  }}
                  className="text-xs px-3 py-1.5 rounded-full border border-slate-700/50 text-slate-400 hover:text-slate-200 hover:border-slate-500 bg-surface-muted transition-all line-clamp-1 max-w-[260px]"
                >
                  {q}
                </button>
              ))}
            </div>
          </div>
        </form>
      </motion.div>

      {/* ── System Status Cards ── */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        {[
          {
            icon: Brain,
            label: "Embedding Model",
            value: "text-embedding-ada-002",
            sub: "1536-dim vectors · OpenAI",
            color: "from-violet-900/40 to-purple-900/20",
            border: "border-violet-800/30",
            iconColor: "text-violet-400",
            dot: "bg-emerald-400",
          },
          {
            icon: Zap,
            label: "Vector Database",
            value: "Qdrant v1.9.1",
            sub: "Cosine similarity · Tenant-isolated",
            color: "from-blue-900/40 to-cyan-900/20",
            border: "border-blue-800/30",
            iconColor: "text-blue-400",
            dot: "bg-emerald-400",
          },
          {
            icon: Sparkles,
            label: "Generation Model",
            value: "gpt-4o-mini",
            sub: "Top-K=5 · 24h Redis cache",
            color: "from-brand-900/40 to-blue-900/20",
            border: "border-brand-800/30",
            iconColor: "text-brand-400",
            dot: "bg-emerald-400",
          },
        ].map(({ icon: Icon, label, value, sub, color, border, iconColor, dot }) => (
          <motion.div
            key={label}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className={`bg-gradient-to-br ${color} border ${border} rounded-xl p-4`}
          >
            <div className="flex items-start gap-3">
              <div className="w-8 h-8 rounded-lg bg-slate-800/50 flex items-center justify-center flex-shrink-0">
                <Icon className={`w-4 h-4 ${iconColor}`} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">{label}</p>
                  <span className={`w-1.5 h-1.5 rounded-full ${dot} flex-shrink-0`} />
                </div>
                <p className="text-sm font-bold text-white mt-0.5">{value}</p>
                <p className="text-xs text-slate-500 mt-0.5">{sub}</p>
              </div>
            </div>
          </motion.div>
        ))}
      </div>

      {/* ── Ingestion Helper (for instructors demo) ── */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="card border-slate-700/40 bg-gradient-to-r from-slate-800/50 to-slate-900/50"
      >
        <div className="flex items-start gap-4">
          <div className="w-10 h-10 rounded-xl bg-emerald-900/40 border border-emerald-800/40 flex items-center justify-center flex-shrink-0">
            <Upload className="w-5 h-5 text-emerald-400" />
          </div>
          <div className="flex-1">
            <h3 className="font-semibold text-white text-sm">Index Course Material (Instructors)</h3>
            <p className="text-xs text-slate-400 mt-1">
              Upload PDFs to MinIO, then trigger the RAG ingestion pipeline. Qdrant will store 1536-dim embeddings
              of each 1000-char chunk with tenant + course isolation.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <div className="font-mono text-xs bg-slate-900/80 text-emerald-300 px-3 py-1.5 rounded-lg border border-slate-700/50">
                POST /api/v1/rag/materials/{"{courseId}"}/ingest
              </div>
              <div className="font-mono text-xs bg-slate-900/80 text-brand-300 px-3 py-1.5 rounded-lg border border-slate-700/50">
                ?minioKey=lms-content/...
              </div>
            </div>
          </div>
        </div>
      </motion.div>

      {/* ── Doubt History ── */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <MessageSquare className="w-4 h-4 text-slate-400" />
            <h2 className="text-base font-semibold text-white">Your Doubts</h2>
            {sessions.length > 0 && (
              <span className="text-xs bg-surface-muted text-slate-400 px-2 py-0.5 rounded-full border border-slate-700/50">
                {sessions.length}
              </span>
            )}
          </div>
          <button
            onClick={() => queryClient.invalidateQueries({ queryKey: ["my-doubts"] })}
            className="btn-ghost text-xs flex items-center gap-1.5"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Refresh
          </button>
        </div>

        <AnimatePresence mode="popLayout">
          {sessions.length === 0 ? (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="card text-center py-16"
            >
              <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-violet-900/40 to-brand-900/40 flex items-center justify-center mx-auto mb-4">
                <Sparkles className="w-8 h-8 text-violet-400" />
              </div>
              <h3 className="text-white font-semibold mb-2">No doubts yet</h3>
              <p className="text-slate-400 text-sm max-w-xs mx-auto">
                Ask your first question above. The AI will search through course PDFs and generate a structured answer.
              </p>
            </motion.div>
          ) : (
            <div className="space-y-3">
              {sessions.map(session => (
                <DoubtCard
                  key={session.id}
                  session={session}
                  onRefresh={handleRefresh}
                />
              ))}
            </div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
