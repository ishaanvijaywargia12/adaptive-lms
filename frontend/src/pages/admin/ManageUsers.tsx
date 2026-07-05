import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  UserPlus, Search, Settings, Loader2, AlertCircle,
  UserCheck, UserX, ShieldCheck, BookOpen, X
} from "lucide-react";
import toast from "react-hot-toast";
import api from "../../lib/api";
import { useState as useLocalState } from "react";
import { motion } from "framer-motion";

// ─── Types ────────────────────────────────────────────────────────────────────

interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  role: "STUDENT" | "INSTRUCTOR" | "ADMIN" | "SUPER_ADMIN";
  active: boolean;
  avatarUrl?: string;
}

interface UsersPage {
  content: User[];
  totalElements: number;
  totalPages: number;
  number: number;
}

// ─── API ─────────────────────────────────────────────────────────────────────

const fetchUsers = async (page: number, search: string, role: string): Promise<UsersPage> => {
  const params: Record<string, any> = { page, size: 20 };
  if (role && role !== "all") params.role = role;
  const { data } = await api.get<{ data: UsersPage }>("/admin/users", { params });
  return data.data;
};

const updateRole = async ({ id, role }: { id: string; role: string }) => {
  const { data } = await api.put(`/admin/users/${id}/role`, null, { params: { role } });
  return data;
};

const deactivateUser = async (id: string) => {
  const { data } = await api.put(`/admin/users/${id}/deactivate`);
  return data;
};

// ─── Role badge ───────────────────────────────────────────────────────────────

const ROLE_STYLES: Record<string, string> = {
  STUDENT:    "bg-blue-900/40 text-blue-300 border border-blue-700/30",
  INSTRUCTOR: "bg-purple-900/40 text-purple-300 border border-purple-700/30",
  ADMIN:      "bg-red-900/40 text-red-300 border border-red-700/30",
  SUPER_ADMIN:"bg-amber-900/40 text-amber-300 border border-amber-700/30",
};

// ─── Invite User Modal ───────────────────────────────────────────────────────

function InviteModal({ onClose }: { onClose: () => void }) {
  const [email, setEmail] = useLocalState("");
  const [role, setRole] = useLocalState("STUDENT");
  const [loading, setLoading] = useLocalState(false);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email.trim()) { toast.error("Please enter an email"); return; }
    setLoading(true);
    try {
      await api.post("/admin/users/invite", { email: email.trim(), role });
      toast.success(`Invitation sent to ${email}!`);
      onClose();
    } catch (err: any) {
      toast.error(err?.response?.data?.message || "Failed to send invite");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }}
        className="card w-full max-w-md mx-4">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-white">Invite User</h2>
          <button onClick={onClose} className="p-2 hover:bg-surface-muted rounded-lg transition-colors">
            <X className="w-5 h-5 text-slate-400" />
          </button>
        </div>
        <form onSubmit={handleInvite} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Email Address</label>
            <input type="email" className="input w-full" placeholder="user@example.com"
              value={email} onChange={e => setEmail(e.target.value)} />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Role</label>
            <select className="input w-full" value={role} onChange={e => setRole(e.target.value)}>
              <option value="STUDENT">Student</option>
              <option value="INSTRUCTOR">Instructor</option>
              <option value="ADMIN">Admin</option>
            </select>
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary flex-1">Cancel</button>
            <button type="submit" disabled={loading} className="btn-primary flex-1">
              {loading ? <Loader2 className="w-4 h-4 animate-spin mx-auto" /> : "Send Invite"}
            </button>
          </div>
        </form>
      </motion.div>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────────────────────

const ManageUsers: React.FC = () => {
  const qc = useQueryClient();
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("all");
  const [page, setPage] = useState(0);
  const [actionUserId, setActionUserId] = useState<string | null>(null);
  const [showInviteModal, setShowInviteModal] = useLocalState(false);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["admin-users", page, roleFilter],
    queryFn: () => fetchUsers(page, search, roleFilter),
  });

  const users: User[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 1;

  // Filter by search client-side (since backend doesn't support text search)
  const filtered = users.filter((u) => {
    if (!search) return true;
    const full = `${u.firstName} ${u.lastName} ${u.email}`.toLowerCase();
    return full.includes(search.toLowerCase());
  });

  const roleMutation = useMutation({
    mutationFn: updateRole,
    onMutate: ({ id }) => setActionUserId(id),
    onSuccess: () => {
      toast.success("Role updated successfully");
      qc.invalidateQueries({ queryKey: ["admin-users"] });
      setActionUserId(null);
    },
    onError: (err: any) => {
      toast.error(err?.response?.data?.message || "Failed to update role");
      setActionUserId(null);
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: deactivateUser,
    onMutate: (id) => setActionUserId(id),
    onSuccess: () => {
      toast.success("User deactivated");
      qc.invalidateQueries({ queryKey: ["admin-users"] });
      setActionUserId(null);
    },
    onError: (err: any) => {
      toast.error(err?.response?.data?.message || "Failed to deactivate user");
      setActionUserId(null);
    },
  });

  return (
    <div className="space-y-6">
      {showInviteModal && <InviteModal onClose={() => setShowInviteModal(false)} />}
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">User Management</h1>
          <p className="text-slate-400 text-sm mt-0.5">
            {data?.totalElements ?? "..."} total users
          </p>
        </div>
        <button
          onClick={() => setShowInviteModal(true)}
          className="btn-primary flex items-center gap-2"
        >
          <UserPlus className="w-4 h-4" /> Invite User
        </button>
      </div>

      {/* Filters */}
      <div className="card flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search by name or email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-surface border border-slate-700 rounded-lg pl-10 pr-4 py-2 text-white outline-none focus:border-brand-500 transition-colors placeholder-slate-500"
          />
        </div>
        <select
          value={roleFilter}
          onChange={(e) => { setRoleFilter(e.target.value); setPage(0); }}
          className="bg-surface border border-slate-700 rounded-lg px-4 py-2 text-white outline-none focus:border-brand-500"
        >
          <option value="all">All Roles</option>
          <option value="STUDENT">Students</option>
          <option value="INSTRUCTOR">Instructors</option>
          <option value="ADMIN">Admins</option>
        </select>
      </div>

      {/* Table */}
      <div className="card overflow-hidden p-0">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-8 h-8 text-brand-400 animate-spin" />
          </div>
        ) : isError ? (
          <div className="text-center py-12">
            <AlertCircle className="w-10 h-10 text-red-400 mx-auto mb-3" />
            <p className="text-red-400 font-medium">Failed to load users</p>
            <p className="text-slate-500 text-sm mt-1">
              {(error as any)?.response?.data?.message || "Check that the backend is running."}
            </p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-12">
            <UserCheck className="w-10 h-10 text-slate-600 mx-auto mb-3" />
            <p className="text-slate-400">No users found</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-surface-muted text-slate-400 uppercase text-xs">
                <tr>
                  <th className="px-5 py-3.5">Name</th>
                  <th className="px-5 py-3.5">Email</th>
                  <th className="px-5 py-3.5">Role</th>
                  <th className="px-5 py-3.5">Status</th>
                  <th className="px-5 py-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-700/40">
                {filtered.map((u) => {
                  const isActing = actionUserId === u.id;
                  return (
                    <tr
                      key={u.id}
                      className="hover:bg-surface-muted/50 transition-colors"
                    >
                      {/* Name */}
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-brand-600 to-purple-600 flex items-center justify-center text-xs font-bold text-white flex-shrink-0">
                            {u.firstName?.charAt(0)?.toUpperCase() ?? "?"}
                          </div>
                          <span className="font-medium text-white">
                            {u.firstName} {u.lastName}
                          </span>
                        </div>
                      </td>

                      {/* Email */}
                      <td className="px-5 py-4 text-slate-400">{u.email}</td>

                      {/* Role */}
                      <td className="px-5 py-4">
                        <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${ROLE_STYLES[u.role] ?? ""}`}>
                          {u.role}
                        </span>
                      </td>

                      {/* Status */}
                      <td className="px-5 py-4">
                        <span className={`text-xs px-2.5 py-1 rounded-full font-medium ${
                          u.active
                            ? "bg-emerald-900/40 text-emerald-300 border border-emerald-700/30"
                            : "bg-slate-700/40 text-slate-400"
                        }`}>
                          {u.active ? "Active" : "Inactive"}
                        </span>
                      </td>

                      {/* Actions */}
                      <td className="px-5 py-4">
                        <div className="flex items-center justify-end gap-1.5">
                          {isActing ? (
                            <Loader2 className="w-4 h-4 animate-spin text-brand-400" />
                          ) : (
                            <>
                              {/* Promote to Instructor */}
                              {u.role === "STUDENT" && (
                                <button
                                  onClick={() => roleMutation.mutate({ id: u.id, role: "INSTRUCTOR" })}
                                  title="Promote to Instructor"
                                  className="p-1.5 rounded-lg hover:bg-purple-900/30 text-slate-400 hover:text-purple-300 transition-colors"
                                >
                                  <BookOpen className="w-4 h-4" />
                                </button>
                              )}
                              {/* Demote to Student */}
                              {u.role === "INSTRUCTOR" && (
                                <button
                                  onClick={() => roleMutation.mutate({ id: u.id, role: "STUDENT" })}
                                  title="Demote to Student"
                                  className="p-1.5 rounded-lg hover:bg-blue-900/30 text-slate-400 hover:text-blue-300 transition-colors"
                                >
                                  <ShieldCheck className="w-4 h-4" />
                                </button>
                              )}
                              {/* Deactivate */}
                              {u.active && u.role !== "ADMIN" && u.role !== "SUPER_ADMIN" && (
                                <button
                                  onClick={() => {
                                    if (confirm(`Deactivate ${u.firstName} ${u.lastName}?`)) {
                                      deactivateMutation.mutate(u.id);
                                    }
                                  }}
                                  title="Deactivate user"
                                  className="p-1.5 rounded-lg hover:bg-red-900/30 text-slate-400 hover:text-red-400 transition-colors"
                                >
                                  <UserX className="w-4 h-4" />
                                </button>
                              )}
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex justify-center gap-2 p-4 border-t border-slate-700/40">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="btn-secondary px-4 py-2 text-sm"
            >
              Previous
            </button>
            <span className="flex items-center px-4 text-slate-400 text-sm">
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="btn-secondary px-4 py-2 text-sm"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default ManageUsers;
