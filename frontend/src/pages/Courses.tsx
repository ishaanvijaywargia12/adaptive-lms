import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Search, Filter, BookOpen, Clock, Star, Users, ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import api from "../lib/api";

interface Course {
  id: string;
  title: string;
  description: string;
  difficulty: "BEGINNER" | "INTERMEDIATE" | "ADVANCED";
  price: number;
  thumbnailUrl?: string;
  tags?: string[];
}

const difficultyBadge: Record<string, string> = {
  BEGINNER: "badge-green",
  INTERMEDIATE: "badge-yellow",
  ADVANCED: "badge-red",
};

const CourseCard: React.FC<{ course: Course; index: number }> = ({ course, index }) => (
  <motion.div
    initial={{ opacity: 0, y: 16 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ delay: index * 0.04 }}
    className="card hover:border-slate-600 transition-all duration-200 cursor-pointer group flex flex-col"
  >
    {/* Thumbnail */}
    <div className="h-40 rounded-xl bg-gradient-to-br from-brand-900/40 to-purple-900/30 flex items-center justify-center mb-4 overflow-hidden border border-slate-700/30">
      {course.thumbnailUrl ? (
        <img src={course.thumbnailUrl} alt={course.title} className="w-full h-full object-cover" />
      ) : (
        <BookOpen className="w-10 h-10 text-brand-400/50" />
      )}
    </div>

    {/* Tags */}
    <div className="flex items-center gap-2 flex-wrap mb-2">
      <span className={difficultyBadge[course.difficulty]}>{course.difficulty}</span>
      {course.tags?.slice(0, 2).map((t) => (
        <span key={t} className="badge badge-purple">{t}</span>
      ))}
    </div>

    {/* Title */}
    <h3 className="text-slate-100 font-semibold group-hover:text-white transition-colors line-clamp-2 mb-2 flex-1">
      {course.title}
    </h3>
    <p className="text-slate-500 text-sm line-clamp-2 mb-4">{course.description}</p>

    {/* Footer */}
    <div className="flex items-center justify-between mt-auto">
      <span className="text-white font-bold text-lg">
        {course.price === 0 ? "Free" : `$${course.price}`}
      </span>
      <Link
        to={`/courses/${course.id}`}
        className="btn-primary py-2 px-4 text-sm flex items-center gap-1"
      >
        View <ChevronRight className="w-3.5 h-3.5" />
      </Link>
    </div>
  </motion.div>
);

export default function CourseBrowser() {
  const [search, setSearch] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["courses", search, difficulty, page],
    queryFn: () =>
      api.get("/courses", { params: { search: search || undefined, difficulty: difficulty || undefined, page, size: 12 } })
        .then(r => r.data.data),
    placeholderData: (prev) => prev,
  });

  const courses: Course[] = data?.content ?? [];
  const totalPages: number = data?.totalPages ?? 1;

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-white">Course Library</h1>
        <p className="text-slate-400 mt-1">Explore {data?.totalElements ?? "..."} courses across all categories</p>
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
          <input
            id="course-search"
            type="text"
            placeholder="Search courses..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            className="input pl-10"
          />
        </div>
        <select
          id="difficulty-filter"
          value={difficulty}
          onChange={(e) => { setDifficulty(e.target.value); setPage(0); }}
          className="input w-auto"
        >
          <option value="">All Levels</option>
          <option value="BEGINNER">Beginner</option>
          <option value="INTERMEDIATE">Intermediate</option>
          <option value="ADVANCED">Advanced</option>
        </select>
      </div>

      {/* Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="card h-72 animate-pulse-soft bg-surface-card" />
          ))}
        </div>
      ) : courses.length === 0 ? (
        <div className="text-center py-20">
          <BookOpen className="w-12 h-12 text-slate-600 mx-auto mb-3" />
          <p className="text-slate-400 text-lg">No courses found</p>
          <p className="text-slate-600 text-sm">Try adjusting your search or filters</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
          {courses.map((c, i) => <CourseCard key={c.id} course={c} index={i} />)}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center gap-2 mt-4">
          <button
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
            className="btn-secondary px-4 py-2"
          >
            Previous
          </button>
          <span className="flex items-center px-4 text-slate-400 text-sm">
            {page + 1} / {totalPages}
          </span>
          <button
            onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="btn-secondary px-4 py-2"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
