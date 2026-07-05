import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  BookOpen, CheckCircle, XCircle, Loader2, Clock, AlertCircle,
  Tag, DollarSign, BarChart2, User
} from "lucide-react";
import toast from "react-hot-toast";
import api from "../../lib/api";

// ─── Types ────────────────────────────────────────────────────────────────────

interface Course {
  id: string;
  title: string;
  description?: string;
  difficulty: "BEGINNER" | "INTERMEDIATE" | "ADVANCED";
  status: string;
  price: number;
  tags?: string[];
  thumbnailUrl?: string;
  instructorId: string;
  createdAt?: string;
}

// ─── API ─────────────────────────────────────────────────────────────────────

const fetchPendingCourses = async (): Promise<Course[]> => {
  const { data } = await api.get<{ data: Course[] }>("/courses/pending");
  return data.data ?? [];
};

const approveCourse = async (id: string) => {
  const { data } = await api.post(`/courses/${id}/approve`);
  return data;
};

const rejectCourse = async (id: string) => {
  const { data } = await api.post(`/courses/${id}/reject`);
  return data;
};

// ─── Difficulty badge ─────────────────────────────────────────────────────────

const DIFF_STYLES: Record<string, string> = {
  BEGINNER:     "bg-green-900/40 text-green-300",
  INTERMEDIATE: "bg-blue-900/40 text-blue-300",
  ADVANCED:     "bg-purple-900/40 text-purple-300",
};

// ─── Course Review Card ───────────────────────────────────────────────────────

interface CardProps {
  course: Course;
  onApprove: (id: string) => void;
  onReject: (id: string) => void;
  processing: string | null;
}

const ReviewCard: React.FC<CardProps> = ({ course, onApprove, onReject, processing }) => {
  const isProcessing = processing === course.id;

  return (
    <div className="card border border-yellow-800/30 hover:border-yellow-600/40 transition-all duration-200">
      {/* Thumbnail */}
      <div className="h-36 bg-gradient-to-br from-yellow-900/30 to-amber-900/20 rounded-lg mb-4 overflow-hidden">
        {course.thumbnailUrl ? (
          <img
            src={course.thumbnailUrl}
            alt={course.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <BookOpen className="w-10 h-10 text-yellow-700" />
          </div>
        )}
      </div>

      {/* Content */}
      <div className="space-y-3">
        {/* Status pill */}
        <div className="flex items-center gap-2">
          <span className="inline-flex items-center gap-1.5 text-xs bg-yellow-900/50 text-yellow-300 border border-yellow-700/30 px-2.5 py-1 rounded-full font-medium">
            <Clock className="w-3 h-3" />
            Under Review
          </span>
        </div>

        {/* Title */}
        <h3 className="text-white font-semibold leading-snug line-clamp-2">{course.title}</h3>

        {/* Description */}
        {course.description && (
          <p className="text-slate-400 text-sm line-clamp-3">{course.description}</p>
        )}

        {/* Meta row */}
        <div className="flex flex-wrap gap-2 text-xs">
          <span className={`px-2 py-0.5 rounded-full font-medium ${DIFF_STYLES[course.difficulty]}`}>
            <BarChart2 className="w-3 h-3 inline mr-1" />
            {course.difficulty}
          </span>
          <span className="flex items-center gap-1 bg-slate-800 text-slate-300 px-2 py-0.5 rounded-full">
            <DollarSign className="w-3 h-3" />
            {Number(course.price) === 0 ? "Free" : `$${Number(course.price).toFixed(2)}`}
          </span>
        </div>

        {/* Tags */}
        {course.tags && course.tags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {course.tags.slice(0, 3).map((tag) => (
              <span key={tag} className="inline-flex items-center gap-1 text-xs bg-slate-800 text-slate-400 px-2 py-0.5 rounded-full">
                <Tag className="w-2.5 h-2.5" />
                {tag}
              </span>
            ))}
          </div>
        )}

        {/* Instructor ID (short) */}
        <p className="text-xs text-slate-500 flex items-center gap-1 truncate">
          <User className="w-3 h-3 flex-shrink-0" />
          Instructor: {course.instructorId?.slice(0, 8)}...
        </p>

        {/* Action buttons */}
        <div className="flex gap-2 pt-1">
          <button
            onClick={() => onApprove(course.id)}
            disabled={isProcessing}
            className="flex-1 flex items-center justify-center gap-1.5 text-sm bg-emerald-700 hover:bg-emerald-600 text-white px-3 py-2 rounded-lg transition-colors disabled:opacity-50 font-medium"
          >
            {isProcessing ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <CheckCircle className="w-4 h-4" />
            )}
            Approve
          </button>
          <button
            onClick={() => onReject(course.id)}
            disabled={isProcessing}
            className="flex-1 flex items-center justify-center gap-1.5 text-sm bg-red-900/60 hover:bg-red-800 text-red-300 hover:text-white border border-red-700/30 px-3 py-2 rounded-lg transition-colors disabled:opacity-50 font-medium"
          >
            {isProcessing ? (
              <Loader2 className="w-4 h-4 animate-spin" />
            ) : (
              <XCircle className="w-4 h-4" />
            )}
            Reject
          </button>
        </div>
      </div>
    </div>
  );
};

// ─── Main Page ────────────────────────────────────────────────────────────────

const ReviewCourses: React.FC = () => {
  const [processing, setProcessing] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const { data: courses = [], isLoading, isError, error } = useQuery({
    queryKey: ["pending-courses"],
    queryFn: fetchPendingCourses,
    refetchInterval: 30_000, // auto-refresh every 30s
  });

  const approveMutation = useMutation({
    mutationFn: approveCourse,
    onMutate: (id) => setProcessing(id),
    onSuccess: () => {
      toast.success("Course approved and published! 🎉");
      queryClient.invalidateQueries({ queryKey: ["pending-courses"] });
      setProcessing(null);
    },
    onError: (err: any) => {
      toast.error(err?.response?.data?.message || "Failed to approve course.");
      setProcessing(null);
    },
  });

  const rejectMutation = useMutation({
    mutationFn: rejectCourse,
    onMutate: (id) => setProcessing(id),
    onSuccess: () => {
      toast.success("Course rejected and returned to instructor.");
      queryClient.invalidateQueries({ queryKey: ["pending-courses"] });
      setProcessing(null);
    },
    onError: (err: any) => {
      toast.error(err?.response?.data?.message || "Failed to reject course.");
      setProcessing(null);
    },
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Course Review Queue</h1>
          <p className="text-slate-400 text-sm mt-0.5">
            {isLoading
              ? "Loading..."
              : `${courses.length} course${courses.length !== 1 ? "s" : ""} awaiting your review`}
          </p>
        </div>
        <div className="flex items-center gap-2 bg-yellow-900/30 border border-yellow-700/30 text-yellow-300 px-4 py-2 rounded-lg text-sm">
          <Clock className="w-4 h-4" />
          Pending Approval
        </div>
      </div>

      {/* Info banner */}
      <div className="card border-blue-800/30 bg-blue-950/20 flex items-start gap-3 py-4">
        <AlertCircle className="w-5 h-5 text-blue-400 flex-shrink-0 mt-0.5" />
        <p className="text-slate-300 text-sm">
          Review each course carefully before approving. Approved courses will be immediately visible
          to all students. Rejected courses will be returned to the instructor with a "Rejected" status
          so they can revise and resubmit.
        </p>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-24">
          <Loader2 className="w-8 h-8 text-brand-400 animate-spin" />
        </div>
      ) : isError ? (
        <div className="card text-center py-12">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
          <p className="text-red-400 font-medium">Failed to load pending courses</p>
          <p className="text-slate-500 text-sm mt-1">
            {(error as any)?.response?.data?.message || "Check that the backend is running."}
          </p>
        </div>
      ) : courses.length === 0 ? (
        <div className="card text-center py-24">
          <div className="mx-auto w-20 h-20 bg-emerald-900/20 rounded-full flex items-center justify-center mb-4">
            <CheckCircle className="w-10 h-10 text-emerald-500" />
          </div>
          <h3 className="text-xl font-semibold text-white mb-2">All clear!</h3>
          <p className="text-slate-400">
            No courses waiting for review right now. Check back later.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
          {courses.map((course) => (
            <ReviewCard
              key={course.id}
              course={course}
              onApprove={(id) => approveMutation.mutate(id)}
              onReject={(id) => rejectMutation.mutate(id)}
              processing={processing}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default ReviewCourses;
