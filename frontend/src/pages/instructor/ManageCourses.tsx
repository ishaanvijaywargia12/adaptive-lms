import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Edit2, BookOpen, Search, X, Loader2, Send, Eye, AlertCircle } from "lucide-react";
import { Link } from "react-router-dom";
import toast from "react-hot-toast";
import api from "../../lib/api";

// ─── Types ──────────────────────────────────────────────────────────────────

interface Course {
  id: string;
  title: string;
  description?: string;
  difficulty: "BEGINNER" | "INTERMEDIATE" | "ADVANCED";
  status: "DRAFT" | "UNDER_REVIEW" | "PUBLISHED" | "ARCHIVED" | "REJECTED";
  price: number;
  tags?: string[];
  thumbnailUrl?: string;
  archived: boolean;
}

interface CreateCoursePayload {
  title: string;
  description: string;
  difficulty: string;
  tags: string[];
  price: number;
  thumbnailUrl: string;
}

// ─── API Calls ───────────────────────────────────────────────────────────────

const fetchMyCourses = async (): Promise<Course[]> => {
  const { data } = await api.get<{ data: Course[] }>("/courses/my");
  return data.data ?? [];
};

const createCourse = async (payload: CreateCoursePayload) => {
  const { data } = await api.post("/courses", payload);
  return data;
};

const publishCourse = async (id: string) => {
  const { data } = await api.post(`/courses/${id}/publish`);
  return data;
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

const STATUS_STYLES: Record<string, string> = {
  DRAFT:        "bg-slate-700 text-slate-300",
  UNDER_REVIEW: "bg-yellow-900/50 text-yellow-300",
  PUBLISHED:    "bg-emerald-900/50 text-emerald-300",
  ARCHIVED:     "bg-red-900/50 text-red-300",
  REJECTED:     "bg-red-900/50 text-red-400",
};

const DIFFICULTY_STYLES: Record<string, string> = {
  BEGINNER:     "bg-green-900/40 text-green-300",
  INTERMEDIATE: "bg-blue-900/40 text-blue-300",
  ADVANCED:     "bg-purple-900/40 text-purple-300",
};

// ─── Create Course Modal ──────────────────────────────────────────────────────

interface CreateModalProps {
  open: boolean;
  onClose: () => void;
}

const CreateCourseModal: React.FC<CreateModalProps> = ({ open, onClose }) => {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    title: "",
    description: "",
    difficulty: "BEGINNER",
    price: "0",
    tagsInput: "",
    thumbnailUrl: "",
  });

  const mutation = useMutation({
    mutationFn: createCourse,
    onSuccess: () => {
      toast.success("Course created successfully! 🎉");
      queryClient.invalidateQueries({ queryKey: ["my-courses"] });
      onClose();
      setForm({ title: "", description: "", difficulty: "BEGINNER", price: "0", tagsInput: "", thumbnailUrl: "" });
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.message || "Failed to create course.";
      toast.error(msg);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title.trim()) {
      toast.error("Title is required.");
      return;
    }
    const tags = form.tagsInput
      .split(",")
      .map((t) => t.trim())
      .filter(Boolean);
    mutation.mutate({
      title: form.title.trim(),
      description: form.description.trim(),
      difficulty: form.difficulty,
      price: parseFloat(form.price) || 0,
      tags,
      thumbnailUrl: form.thumbnailUrl.trim(),
    });
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
      <div className="bg-surface-card border border-slate-700 rounded-2xl w-full max-w-xl shadow-2xl shadow-black/50">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-slate-700">
          <div>
            <h2 className="text-xl font-bold text-white">Create New Course</h2>
            <p className="text-sm text-slate-400 mt-0.5">Fill in the details below. You can always edit later.</p>
          </div>
          <button onClick={onClose} className="p-2 rounded-lg hover:bg-slate-700 text-slate-400 hover:text-white transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Title */}
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">
              Course Title <span className="text-red-400">*</span>
            </label>
            <input
              id="course-title"
              type="text"
              placeholder="e.g. Introduction to Machine Learning"
              maxLength={500}
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="w-full bg-surface border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder-slate-500 outline-none focus:border-brand-500 transition-colors"
            />
          </div>

          {/* Description */}
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Description</label>
            <textarea
              id="course-description"
              placeholder="What will students learn in this course?"
              rows={3}
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              className="w-full bg-surface border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder-slate-500 outline-none focus:border-brand-500 transition-colors resize-none"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            {/* Difficulty */}
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1.5">Difficulty</label>
              <select
                id="course-difficulty"
                value={form.difficulty}
                onChange={(e) => setForm({ ...form, difficulty: e.target.value })}
                className="w-full bg-surface border border-slate-700 rounded-lg px-4 py-2.5 text-white outline-none focus:border-brand-500 transition-colors"
              >
                <option value="BEGINNER">Beginner</option>
                <option value="INTERMEDIATE">Intermediate</option>
                <option value="ADVANCED">Advanced</option>
              </select>
            </div>

            {/* Price */}
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1.5">Price (0 = Free)</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-sm">$</span>
                <input
                  id="course-price"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.price}
                  onChange={(e) => setForm({ ...form, price: e.target.value })}
                  className="w-full bg-surface border border-slate-700 rounded-lg pl-7 pr-4 py-2.5 text-white outline-none focus:border-brand-500 transition-colors"
                />
              </div>
            </div>
          </div>

          {/* Tags */}
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Tags (comma-separated)</label>
            <input
              id="course-tags"
              type="text"
              placeholder="e.g. python, ai, data-science"
              value={form.tagsInput}
              onChange={(e) => setForm({ ...form, tagsInput: e.target.value })}
              className="w-full bg-surface border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder-slate-500 outline-none focus:border-brand-500 transition-colors"
            />
          </div>

          {/* Thumbnail URL */}
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Thumbnail URL (optional)</label>
            <input
              id="course-thumbnail"
              type="url"
              placeholder="https://..."
              value={form.thumbnailUrl}
              onChange={(e) => setForm({ ...form, thumbnailUrl: e.target.value })}
              className="w-full bg-surface border border-slate-700 rounded-lg px-4 py-2.5 text-white placeholder-slate-500 outline-none focus:border-brand-500 transition-colors"
            />
          </div>

          {/* Actions */}
          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 btn-secondary"
              disabled={mutation.isPending}
            >
              Cancel
            </button>
            <button
              type="submit"
              id="create-course-submit"
              disabled={mutation.isPending}
              className="flex-1 btn-primary flex items-center justify-center gap-2"
            >
              {mutation.isPending ? (
                <><Loader2 className="w-4 h-4 animate-spin" /> Creating...</>
              ) : (
                <><Plus className="w-4 h-4" /> Create Course</>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ─── Course Card ─────────────────────────────────────────────────────────────

interface CourseCardProps {
  course: Course;
  onPublish: (id: string) => void;
  publishing: string | null;
}

const CourseCard: React.FC<CourseCardProps> = ({ course, onPublish, publishing }) => (
  <div className="card hover:border-brand-500/30 transition-all duration-200 group">
    {/* Thumbnail */}
    <div className="h-32 bg-gradient-to-br from-brand-900/50 to-purple-900/30 rounded-lg mb-4 overflow-hidden">
      {course.thumbnailUrl ? (
        <img src={course.thumbnailUrl} alt={course.title} className="w-full h-full object-cover" />
      ) : (
        <div className="w-full h-full flex items-center justify-center">
          <BookOpen className="w-10 h-10 text-brand-600" />
        </div>
      )}
    </div>

    {/* Info */}
    <div className="space-y-2">
      <div className="flex items-start justify-between gap-2">
        <h3 className="text-white font-semibold text-sm leading-tight line-clamp-2 flex-1">{course.title}</h3>
        <span className={`text-xs px-2 py-0.5 rounded-full shrink-0 font-medium ${STATUS_STYLES[course.status]}`}>
          {course.status.replace("_", " ")}
        </span>
      </div>

      {course.description && (
        <p className="text-slate-400 text-xs line-clamp-2">{course.description}</p>
      )}

      <div className="flex items-center gap-2 flex-wrap">
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${DIFFICULTY_STYLES[course.difficulty]}`}>
          {course.difficulty}
        </span>
        {course.tags?.slice(0, 2).map((tag) => (
          <span key={tag} className="text-xs bg-slate-800 text-slate-400 px-2 py-0.5 rounded-full">{tag}</span>
        ))}
      </div>

      <div className="text-white font-bold text-sm">
        {course.price === 0 ? "Free" : `$${Number(course.price).toFixed(2)}`}
      </div>

      {/* Actions */}
      <div className="flex gap-2 pt-1">
        {course.status === "DRAFT" && (
          <button
            onClick={() => onPublish(course.id)}
            disabled={publishing === course.id}
            className="flex-1 flex items-center justify-center gap-1.5 text-xs bg-brand-600 hover:bg-brand-500 text-white px-3 py-1.5 rounded-lg transition-colors disabled:opacity-50"
          >
            {publishing === course.id ? (
              <Loader2 className="w-3 h-3 animate-spin" />
            ) : (
              <Send className="w-3 h-3" />
            )}
            Submit for Review
          </button>
        )}
        {course.status === "PUBLISHED" && (
          <div className="flex-1 flex items-center justify-center gap-1.5 text-xs text-emerald-400 py-1.5">
            <Eye className="w-3 h-3" /> Live
          </div>
        )}
        {course.status === "UNDER_REVIEW" && (
          <div className="flex-1 flex items-center justify-center gap-1.5 text-xs text-yellow-400 py-1.5">
            <Loader2 className="w-3 h-3 animate-spin" /> Under Review
          </div>
        )}
        {course.status === "REJECTED" && (
          <div className="flex-1 flex items-center justify-center gap-1.5 text-xs text-red-400 py-1.5">
            <AlertCircle className="w-3 h-3" /> Rejected
          </div>
        )}
        <Link to={`/instructor/courses/${course.id}`} className="p-1.5 rounded-lg bg-surface-muted hover:bg-brand-600/20 border border-slate-700 hover:border-brand-500/50 text-slate-400 hover:text-brand-300 transition-colors flex items-center gap-2">
          <Edit2 className="w-3 h-3" /> <span className="text-xs font-medium">Build</span>
        </Link>
      </div>
    </div>
  </div>
);

// ─── Main Page ───────────────────────────────────────────────────────────────

const ManageCourses: React.FC = () => {
  const [showCreate, setShowCreate] = useState(false);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [publishing, setPublishing] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const { data: courses = [], isLoading, isError, error } = useQuery({
    queryKey: ["my-courses"],
    queryFn: fetchMyCourses,
    retry: 1,
  });

  const publishMutation = useMutation({
    mutationFn: publishCourse,
    onMutate: (id) => setPublishing(id),
    onSuccess: () => {
      toast.success("Course submitted for review! ✅");
      queryClient.invalidateQueries({ queryKey: ["my-courses"] });
      setPublishing(null);
    },
    onError: (err: any) => {
      toast.error(err?.response?.data?.message || "Failed to submit course.");
      setPublishing(null);
    },
  });

  const filtered = courses.filter((c) => {
    const matchSearch = c.title.toLowerCase().includes(search.toLowerCase());
    const matchStatus = statusFilter === "ALL" || c.status === statusFilter;
    return matchSearch && matchStatus;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">My Courses</h1>
          <p className="text-slate-400 text-sm mt-0.5">{courses.length} course{courses.length !== 1 ? "s" : ""} total</p>
        </div>
        <button
          id="create-course-btn"
          onClick={() => setShowCreate(true)}
          className="btn-primary flex items-center gap-2"
        >
          <Plus className="w-4 h-4" />
          Create Course
        </button>
      </div>

      {/* Filters */}
      <div className="card flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search your courses..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-surface border border-slate-700 rounded-lg pl-10 pr-4 py-2 text-white outline-none focus:border-brand-500 transition-colors placeholder-slate-500"
          />
        </div>
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="bg-surface border border-slate-700 rounded-lg px-4 py-2 text-white outline-none focus:border-brand-500"
        >
          <option value="ALL">All Status</option>
          <option value="DRAFT">Draft</option>
          <option value="UNDER_REVIEW">Under Review</option>
          <option value="PUBLISHED">Published</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-brand-400 animate-spin" />
        </div>
      ) : isError ? (
        <div className="card text-center py-12">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
          <p className="text-red-400 font-medium">Failed to load courses</p>
          <p className="text-slate-500 text-sm mt-1">
            {(error as any)?.response?.data?.message || "Check that the backend is running."}
          </p>
        </div>
      ) : filtered.length === 0 ? (
        <div className="card text-center py-16">
          <div className="mx-auto w-16 h-16 bg-surface rounded-full flex items-center justify-center mb-4">
            <BookOpen className="w-8 h-8 text-slate-500" />
          </div>
          {courses.length === 0 ? (
            <>
              <h3 className="text-lg font-medium text-white mb-2">No courses yet</h3>
              <p className="text-slate-400 mb-6">You haven't created any courses. Start by clicking the button above!</p>
              <button onClick={() => setShowCreate(true)} className="btn-primary inline-flex items-center gap-2">
                <Plus className="w-4 h-4" /> Create your first course
              </button>
            </>
          ) : (
            <>
              <h3 className="text-lg font-medium text-white mb-2">No results</h3>
              <p className="text-slate-400">No courses match your current filters.</p>
            </>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filtered.map((course) => (
            <CourseCard
              key={course.id}
              course={course}
              onPublish={(id) => publishMutation.mutate(id)}
              publishing={publishing}
            />
          ))}
        </div>
      )}

      {/* Create Modal */}
      <CreateCourseModal open={showCreate} onClose={() => setShowCreate(false)} />
    </div>
  );
};

export default ManageCourses;
