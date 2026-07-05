import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion, AnimatePresence } from "framer-motion";
import {
  CheckCircle, Play, FileText, ChevronDown, ChevronUp,
  Lock, Star, Clock, ChevronLeft, Award, BookOpen,
} from "lucide-react";
import api from "../lib/api";
import toast from "react-hot-toast";

interface Lesson { id: string; title: string; contentType: string; durationSeconds: number; preview: boolean }
interface Module { id: string; title: string; orderIndex: number; lessons: Lesson[] }

const contentIcon: Record<string, React.ReactNode> = {
  VIDEO: <Play className="w-4 h-4" />,
  TEXT: <FileText className="w-4 h-4" />,
  PDF: <FileText className="w-4 h-4" />,
  QUIZ: <Star className="w-4 h-4" />,
};

export default function CoursePlayer() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [activeLesson, setActiveLesson] = useState<Lesson | null>(null);
  const [expandedModules, setExpandedModules] = useState<Set<string>>(new Set());

  const { data: course } = useQuery({
    queryKey: ["course", id],
    queryFn: () => api.get(`/courses/${id}`).then(r => r.data.data),
  });

  const { data: modules } = useQuery<Module[]>({
    queryKey: ["modules", id],
    queryFn: async () => {
      const { data } = await api.get(`/courses/${id}/modules`);
      const mods = data.data || [];
      const modulesWithLessons = await Promise.all(
        mods.map(async (m: any) => {
          const lRes = await api.get(`/modules/${m.id}/lessons`);
          return { ...m, lessons: lRes.data.data || [] };
        })
      );
      return modulesWithLessons;
    },
  });

  // TanStack Query v5: no more onSuccess — use useEffect instead
  useEffect(() => {
    if (modules && modules.length > 0 && !activeLesson) {
      setExpandedModules(new Set([modules[0].id]));
      setActiveLesson(modules[0].lessons?.[0] ?? null);
    }
  }, [modules]);

  const { data: enrollment } = useQuery({
    queryKey: ["enrollment", id],
    queryFn: () => api.get(`/my/enrollments`).then(r =>
      (r.data.data as { courseId: string; progressPercent: number; completedAt: string }[])
        .find(e => e.courseId === id)
    ),
  });

  const completeMutation = useMutation({
    mutationFn: (lessonId: string) => api.post(`/lessons/${lessonId}/complete`),
    onSuccess: () => {
      toast.success("Lesson completed! 🎯 +10 XP");
      qc.invalidateQueries({ queryKey: ["enrollment", id] });
    },
  });

  const enrollMutation = useMutation({
    mutationFn: () => api.post(`/courses/${id}/enroll`),
    onSuccess: () => {
      toast.success("Enrolled successfully! 🎉");
      qc.invalidateQueries({ queryKey: ["enrollment", id] });
    },
  });

  const toggleModule = (moduleId: string) => {
    setExpandedModules(prev => {
      const next = new Set(prev);
      next.has(moduleId) ? next.delete(moduleId) : next.add(moduleId);
      return next;
    });
  };

  const isEnrolled = !!enrollment;
  const moduleList = modules ?? [];

  return (
    <div className="flex h-[calc(100vh-4rem)] gap-0 -mx-6 -mt-6 overflow-hidden animate-fade-in">
      {/* Sidebar */}
      <div className="w-80 flex-shrink-0 bg-surface-card border-r border-slate-700/50 flex flex-col overflow-hidden">
        <div className="p-4 border-b border-slate-700/50">
          <button onClick={() => navigate("/courses")} className="flex items-center gap-2 text-slate-400 hover:text-slate-200 text-sm mb-3 transition-colors">
            <ChevronLeft className="w-4 h-4" /> Back to courses
          </button>
          <h2 className="font-semibold text-white text-sm line-clamp-2">{course?.title}</h2>
          {enrollment && (
            <div className="mt-2">
              <div className="progress-bar">
                <div className="progress-fill" style={{ width: `${enrollment.progressPercent}%` }} />
              </div>
              <p className="text-xs text-slate-500 mt-1">{enrollment.progressPercent}% complete</p>
            </div>
          )}
        </div>

        <div className="flex-1 overflow-y-auto">
          {moduleList.map((mod, mi) => (
            <div key={mod.id} className="border-b border-slate-700/30">
              <button onClick={() => toggleModule(mod.id)} className="w-full flex items-center justify-between px-4 py-3 hover:bg-surface-muted transition-colors text-left">
                <span className="text-xs text-slate-500 flex-shrink-0">Module {mi + 1}</span>
                <div className="flex items-center gap-2">
                  <span className="text-xs text-slate-400 font-medium truncate max-w-[140px]">{mod.title}</span>
                  {expandedModules.has(mod.id) ? <ChevronUp className="w-3.5 h-3.5 text-slate-500" /> : <ChevronDown className="w-3.5 h-3.5 text-slate-500" />}
                </div>
              </button>

              <AnimatePresence>
                {expandedModules.has(mod.id) && (
                  <motion.div
                    initial={{ height: 0, opacity: 0 }}
                    animate={{ height: "auto", opacity: 1 }}
                    exit={{ height: 0, opacity: 0 }}
                    transition={{ duration: 0.2 }}
                    className="overflow-hidden"
                  >
                    {mod.lessons?.map((lesson) => (
                      <button
                        key={lesson.id}
                        onClick={() => isEnrolled && setActiveLesson(lesson)}
                        className={`w-full flex items-center gap-3 px-4 py-2.5 text-left text-sm transition-colors ${activeLesson?.id === lesson.id ? "bg-brand-600/20 text-brand-300" : "text-slate-400 hover:text-slate-200 hover:bg-surface-muted"} ${!isEnrolled && !lesson.preview ? "opacity-50 cursor-not-allowed" : ""}`}
                      >
                        <span className={`flex-shrink-0 ${activeLesson?.id === lesson.id ? "text-brand-400" : "text-slate-600"}`}>
                          {isEnrolled || lesson.preview ? contentIcon[lesson.contentType] : <Lock className="w-4 h-4" />}
                        </span>
                        <span className="flex-1 truncate">{lesson.title}</span>
                        {lesson.durationSeconds > 0 && <span className="text-xs text-slate-600">{Math.round(lesson.durationSeconds / 60)}m</span>}
                      </button>
                    ))}
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          ))}
        </div>
      </div>

      {/* Main content area */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {activeLesson ? (
          <>
            <div className="flex-1 overflow-y-auto p-6">
              <div className="max-w-3xl mx-auto">
                <div className="flex items-center gap-2 mb-4">
                  <span className="badge badge-blue">{activeLesson.contentType}</span>
                  {activeLesson.durationSeconds > 0 && (
                    <span className="flex items-center gap-1 text-xs text-slate-500">
                      <Clock className="w-3 h-3" /> {Math.round(activeLesson.durationSeconds / 60)} min
                    </span>
                  )}
                </div>
                <h1 className="text-2xl font-bold text-white mb-6">{activeLesson.title}</h1>

                {activeLesson.contentType === "VIDEO" && (
                  <div className="rounded-2xl overflow-hidden bg-black aspect-video flex items-center justify-center border border-slate-700">
                    {(activeLesson as any).contentUrl ? (
                      <video
                        key={(activeLesson as any).contentUrl}
                        src={(activeLesson as any).contentUrl}
                        controls
                        className="w-full h-full"
                        onEnded={() => isEnrolled && completeMutation.mutate(activeLesson.id)}
                      />
                    ) : (
                      <div className="text-center">
                        <Play className="w-16 h-16 text-slate-600 mx-auto mb-2" />
                        <p className="text-slate-500">No video uploaded yet</p>
                      </div>
                    )}
                  </div>
                )}
                {activeLesson.contentType === "PDF" && (
                  <div className="rounded-2xl overflow-hidden border border-slate-700" style={{ height: "70vh" }}>
                    {(activeLesson as any).contentUrl ? (
                      <iframe
                        src={(activeLesson as any).contentUrl}
                        title={activeLesson.title}
                        className="w-full h-full"
                      />
                    ) : (
                      <div className="flex items-center justify-center h-full">
                        <div className="text-center">
                          <FileText className="w-16 h-16 text-slate-600 mx-auto mb-2" />
                          <p className="text-slate-500">No PDF uploaded yet</p>
                        </div>
                      </div>
                    )}
                  </div>
                )}
                {activeLesson.contentType === "TEXT" && (
                  <div className="card min-h-48 text-slate-300 prose prose-invert max-w-none">
                    {(activeLesson as any).contentText ? (
                      <pre className="whitespace-pre-wrap font-sans text-slate-200 leading-relaxed">
                        {(activeLesson as any).contentText}
                      </pre>
                    ) : (
                      <p className="text-slate-400">No text content for this lesson.</p>
                    )}
                  </div>
                )}
                {activeLesson.contentType === "QUIZ" && (
                  <div className="card text-center py-12">
                    <Star className="w-12 h-12 text-amber-400 mx-auto mb-3" />
                    <h2 className="text-xl font-semibold text-white mb-2">Quiz Time!</h2>
                    <button className="btn-primary mx-auto" onClick={() => navigate(`/quiz/${activeLesson.id}`)}>Start Quiz</button>
                  </div>
                )}
              </div>
            </div>

            <div className="border-t border-slate-700/50 p-4 bg-surface-card flex items-center justify-between">
              {!isEnrolled ? (
                <button onClick={() => enrollMutation.mutate()} disabled={enrollMutation.isPending} className="btn-primary">
                  {enrollMutation.isPending ? "Enrolling..." : "Enroll to Access This Course"}
                </button>
              ) : (
                <button onClick={() => completeMutation.mutate(activeLesson.id)} disabled={completeMutation.isPending} className="btn-primary flex items-center gap-2">
                  <CheckCircle className="w-4 h-4" />
                  {completeMutation.isPending ? "Saving..." : "Mark as Complete"}
                </button>
              )}
              {enrollment?.completedAt && (
                <div className="flex items-center gap-2 text-emerald-400">
                  <Award className="w-5 h-5" />
                  <span className="text-sm font-medium">Course completed! Certificate issued.</span>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center">
              <BookOpen className="w-16 h-16 text-slate-700 mx-auto mb-3" />
              <p className="text-slate-500">Select a lesson to start learning</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
