import React from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import { Award, Download, ExternalLink, CheckCircle, Calendar } from "lucide-react";
import api from "../lib/api";
import { format } from "date-fns";

interface Certificate {
  id: string; courseId: string; courseTitle: string; studentName: string; issuedAt: string;
  certificateUrl: string; verificationCode: string; qrCodeUrl?: string;
}

export default function Certificates() {
  const { data: certs = [], isLoading } = useQuery<Certificate[]>({
    queryKey: ["my-certificates"],
    queryFn: () => api.get("/my/certificates").then(r => r.data.data),
  });

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">My Certificates</h1>
          <p className="text-slate-400 text-sm mt-1">{certs.length} certificate{certs.length !== 1 ? "s" : ""} earned</p>
        </div>
        <div className="w-10 h-10 rounded-xl bg-emerald-900/40 flex items-center justify-center">
          <Award className="w-5 h-5 text-emerald-400" />
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[1, 2].map(i => <div key={i} className="h-48 rounded-2xl bg-surface-card animate-pulse-soft" />)}
        </div>
      ) : certs.length === 0 ? (
        <div className="card text-center py-16">
          <Award className="w-14 h-14 text-slate-700 mx-auto mb-4" />
          <h2 className="text-xl font-semibold text-slate-300 mb-2">No certificates yet</h2>
          <p className="text-slate-500">Complete a course to earn your first certificate!</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {certs.map((cert, i) => (
            <motion.div
              key={cert.id}
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: i * 0.08 }}
              className="card bg-gradient-to-br from-emerald-900/20 via-surface-card to-brand-900/20 border-emerald-800/20 hover:border-emerald-700/40 transition-all"
            >
              {/* Certificate visual header */}
              <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                  <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 flex items-center justify-center shadow-lg shadow-emerald-900/50">
                    <Award className="w-6 h-6 text-white" />
                  </div>
                  <div>
                    <p className="text-emerald-400 font-semibold text-sm">Certificate of Completion</p>
                    <p className="text-white font-medium text-sm mt-0.5">{cert.courseTitle}</p>
                    <p className="text-slate-400 text-xs flex items-center gap-1 mt-0.5">
                      <Calendar className="w-3 h-3" />
                      {cert.issuedAt ? format(new Date(cert.issuedAt), "MMM d, yyyy") : "—"}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-1 text-emerald-400">
                  <CheckCircle className="w-4 h-4" />
                  <span className="text-xs font-medium">Verified</span>
                </div>
              </div>

              {/* Verification code */}
              <div className="bg-surface-muted rounded-xl px-4 py-2.5 mb-4 border border-slate-700/40">
                <p className="text-xs text-slate-500 mb-1">Verification Code</p>
                <p className="text-xs font-mono text-slate-300 select-all">{cert.verificationCode}</p>
              </div>

              {/* QR Code placeholder */}
              {cert.qrCodeUrl && (
                <div className="w-20 h-20 rounded-xl bg-white p-1.5 mb-4 flex-shrink-0">
                  <img src={cert.qrCodeUrl} alt="QR Code" className="w-full h-full" />
                </div>
              )}

              {/* Actions */}
              <div className="flex gap-2">
                {cert.certificateUrl && (
                  <a
                    href={cert.certificateUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn-primary flex items-center gap-2 text-sm py-2"
                  >
                    <Download className="w-4 h-4" /> Download PDF
                  </a>
                )}
                <a
                  href={`/public/verify/${cert.verificationCode}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn-secondary flex items-center gap-2 text-sm py-2"
                >
                  <ExternalLink className="w-4 h-4" /> Verify
                </a>
              </div>
            </motion.div>
          ))}
        </div>
      )}
    </div>
  );
}
