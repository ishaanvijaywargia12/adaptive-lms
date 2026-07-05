import React, { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Plus, Trash2, ChevronLeft, GripVertical, FileText, Play, CheckCircle, Loader2, Upload, Eye } from "lucide-react";
import toast from "react-hot-toast";
import api from "../../lib/api";

export default function CourseBuilder() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();

  // Fetch course details
  const { data: course, isLoading: loadingCourse } = useQuery({
    queryKey: ["course", id],
    queryFn: () => api.get(`/courses/${id}`).then(r => r.data.data),
  });

  // Fetch modules and lessons
  const { data: modules = [], isLoading: loadingModules } = useQuery({
    queryKey: ["modules", id],
    queryFn: async () => {
      const { data } = await api.get(`/courses/${id}/modules`);
      const mods = data.data || [];
      return await Promise.all(
        mods.map(async (m: any) => {
          const lRes = await api.get(`/modules/${m.id}/lessons`);
          return { ...m, lessons: lRes.data.data || [] };
        })
      );
    },
  });

  // Add Module Mutation
  const addModuleMutation = useMutation({
    mutationFn: (title: string) =>
      api.post(`/courses/${id}/modules`, { title, orderIndex: modules.length }),
    onSuccess: () => {
      toast.success("Module added");
      qc.invalidateQueries({ queryKey: ["modules", id] });
    },
    onError: (e: any) => toast.error(e.response?.data?.message || "Failed to add module"),
  });

  // Delete Module Mutation
  const deleteModuleMutation = useMutation({
    mutationFn: (moduleId: string) => api.delete(`/modules/${moduleId}`),
    onSuccess: () => {
      toast.success("Module deleted");
      qc.invalidateQueries({ queryKey: ["modules", id] });
    },
    onError: (e: any) => toast.error(e.response?.data?.message || "Failed to delete module"),
  });

  // Add Lesson Mutation
  const addLessonMutation = useMutation({
    mutationFn: ({ moduleId, payload }: { moduleId: string; payload: any }) =>
      api.post(`/modules/${moduleId}/lessons`, payload),
    onSuccess: () => {
      toast.success("Lesson added");
      qc.invalidateQueries({ queryKey: ["modules", id] });
    },
    onError: (e: any) => toast.error(e.response?.data?.message || "Failed to add lesson"),
  });

  const handleAddModule = () => {
    const title = prompt("Enter module title:");
    if (title?.trim()) addModuleMutation.mutate(title.trim());
  };

  const handleDeleteModule = (moduleId: string, title: string) => {
    if (!window.confirm(`Delete module "${title}" and all its lessons? This cannot be undone.`)) return;
    deleteModuleMutation.mutate(moduleId);
  };

  const handleAddLesson = (moduleId: string, currentLessonsCount: number) => {
    const title = prompt("Enter lesson title:");
    if (!title?.trim()) return;
    const type = prompt("Enter type (VIDEO, PDF, TEXT):", "VIDEO")?.toUpperCase();
    if (!type || !["VIDEO", "PDF", "TEXT"].includes(type)) {
      toast.error("Invalid content type. Must be VIDEO, PDF, or TEXT.");
      return;
    }
    addLessonMutation.mutate({
      moduleId,
      payload: {
        title: title.trim(),
        contentType: type,
        contentText: "",
        orderIndex: currentLessonsCount,
        preview: currentLessonsCount === 0,
      }
    });
  };

  const handleSubmitForReview = () => {
    api.post(`/courses/${id}/publish`)
      .then(() => {
        toast.success("Submitted for review!");
        navigate("/instructor/courses");
      })
      .catch((e: any) => toast.error(e.response?.data?.message || "Failed to submit for review"));
  };

  if (loadingCourse || loadingModules) {
    return (
      <div className="flex justify-center items-center h-64">
        <Loader2 className="w-8 h-8 text-brand-500 animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button onClick={() => navigate("/instructor/courses")} className="p-2 hover:bg-surface-muted rounded-xl transition-colors">
          <ChevronLeft className="w-5 h-5 text-slate-400" />
        </button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-white">{course?.title}</h1>
          <p className="text-slate-400 text-sm">Course Builder · <span className={`font-medium ${course?.status === "DRAFT" ? "text-amber-400" : course?.status === "UNDER_REVIEW" ? "text-blue-400" : "text-emerald-400"}`}>{course?.status}</span></p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => navigate(`/courses/${id}`)}
            className="btn-secondary flex items-center gap-2"
          >
            <Eye className="w-4 h-4" /> Preview
          </button>
          {course?.status === "DRAFT" && (
            <button className="btn-primary" onClick={handleSubmitForReview}>
              Submit for Review
            </button>
          )}
        </div>
      </div>

      {/* Modules List */}
      <div className="space-y-4">
        {(modules as any[]).map((mod: any, index: number) => (
          <div key={mod.id} className="card p-0 overflow-hidden border border-slate-700/50">
            <div className="bg-surface-muted p-4 flex items-center justify-between border-b border-slate-700/50">
              <div className="flex items-center gap-3">
                <GripVertical className="w-4 h-4 text-slate-500 cursor-grab" />
                <h3 className="font-semibold text-white">Module {index + 1}: {mod.title}</h3>
                <span className="text-xs text-slate-500">({mod.lessons.length} lesson{mod.lessons.length !== 1 ? "s" : ""})</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleAddLesson(mod.id, mod.lessons.length)}
                  className="text-xs bg-slate-800 hover:bg-slate-700 text-white px-3 py-1.5 rounded-lg flex items-center gap-1.5 transition-colors"
                >
                  <Plus className="w-3.5 h-3.5" /> Add Lesson
                </button>
                <button
                  onClick={() => handleDeleteModule(mod.id, mod.title)}
                  disabled={deleteModuleMutation.isPending}
                  className="p-1.5 text-slate-500 hover:text-red-400 hover:bg-red-400/10 rounded-lg transition-colors"
                  title="Delete module"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="p-2">
              {mod.lessons.length === 0 ? (
                <p className="text-sm text-slate-500 p-4 text-center">No lessons yet. Add one to get started.</p>
              ) : (
                <div className="space-y-1">
                  {mod.lessons.map((lesson: any) => (
                    <LessonRow key={lesson.id} lesson={lesson} courseId={id!} />
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}

        <button
          onClick={handleAddModule}
          disabled={addModuleMutation.isPending}
          className="w-full py-4 border-2 border-dashed border-slate-700 rounded-xl text-slate-400 hover:text-white hover:border-brand-500 hover:bg-brand-500/5 transition-all flex items-center justify-center gap-2"
        >
          {addModuleMutation.isPending ? <Loader2 className="w-5 h-5 animate-spin" /> : <Plus className="w-5 h-5" />}
          Add New Module
        </button>
      </div>
    </div>
  );
}

// ─── Lesson Row Subcomponent ──────────────────────────────────────────────────

function LessonRow({ lesson, courseId }: { lesson: any, courseId: string }) {
  const qc = useQueryClient();
  const [uploading, setUploading] = useState(false);

  const deleteLessonMutation = useMutation({
    mutationFn: () => api.delete(`/lessons/${lesson.id}`),
    onSuccess: () => {
      toast.success("Lesson deleted");
      qc.invalidateQueries({ queryKey: ["modules", courseId] });
    },
    onError: (e: any) => toast.error(e.response?.data?.message || "Failed to delete lesson"),
  });

  const handleDelete = () => {
    if (!window.confirm(`Delete lesson "${lesson.title}"? This cannot be undone.`)) return;
    deleteLessonMutation.mutate();
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    try {
      // 1. Get presigned URL
      const { data } = await api.post(`/lessons/${lesson.id}/upload-url`, null, {
        params: { filename: file.name, contentType: file.type }
      });
      const { uploadUrl, objectKey } = data.data;

      // 2. Upload file directly to MinIO
      await fetch(uploadUrl, {
        method: "PUT",
        body: file,
        headers: { "Content-Type": file.type }
      });

      // 3. Confirm upload so backend stores the URL
      await api.post(`/lessons/${lesson.id}/confirm-upload`, null, {
        params: { objectKey }
      });

      toast.success("Content uploaded successfully!");
      qc.invalidateQueries({ queryKey: ["modules", courseId] });
    } catch (err) {
      console.error(err);
      toast.error("Upload failed. Please try again.");
    } finally {
      setUploading(false);
      // reset input so same file can be re-uploaded
      e.target.value = "";
    }
  };

  return (
    <div className="flex items-center justify-between p-3 hover:bg-slate-800/50 rounded-lg group transition-colors">
      <div className="flex items-center gap-3">
        <GripVertical className="w-4 h-4 text-slate-600 cursor-grab opacity-0 group-hover:opacity-100 transition-opacity" />
        {lesson.contentType === "VIDEO" ? <Play className="w-4 h-4 text-blue-400" /> : <FileText className="w-4 h-4 text-purple-400" />}
        <div>
          <p className="text-sm font-medium text-slate-200">{lesson.title}</p>
          <p className="text-xs text-slate-500">{lesson.contentType} {lesson.preview ? "· Free Preview" : ""}</p>
        </div>
      </div>

      <div className="flex items-center gap-2">
        {lesson.contentUrl ? (
          <span className="flex items-center gap-1 text-xs text-emerald-400 bg-emerald-400/10 px-2 py-1 rounded-full">
            <CheckCircle className="w-3 h-3" /> Uploaded
          </span>
        ) : (
          lesson.contentType !== "TEXT" && (
            <label className="cursor-pointer text-xs bg-brand-600 hover:bg-brand-500 text-white px-3 py-1.5 rounded-lg transition-colors flex items-center gap-1.5">
              {uploading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Upload className="w-3.5 h-3.5" />}
              {uploading ? "Uploading..." : "Upload File"}
              <input
                type="file"
                className="hidden"
                accept={lesson.contentType === "VIDEO" ? "video/*" : "application/pdf"}
                onChange={handleUpload}
                disabled={uploading}
              />
            </label>
          )
        )}
        <button
          onClick={handleDelete}
          disabled={deleteLessonMutation.isPending}
          className="p-1.5 text-slate-500 hover:text-red-400 hover:bg-red-400/10 rounded-lg transition-colors"
          title="Delete lesson"
        >
          {deleteLessonMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
        </button>
      </div>
    </div>
  );
}
