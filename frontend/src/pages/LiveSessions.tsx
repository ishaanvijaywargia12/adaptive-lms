import React, { useEffect, useRef, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  Video, Mic, MicOff, Camera, CameraOff, Users, MessageSquare,
  Hand, PhoneOff, Calendar, Plus, X, Loader2,
} from "lucide-react";
import api from "../lib/api";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useAuth } from "../contexts/AuthContext";
import { getToken } from "../lib/keycloak";
import { format } from "date-fns";
import toast from "react-hot-toast";

interface LiveSession {
  id: string; title: string; courseId: string; status: "SCHEDULED" | "LIVE" | "ENDED";
  scheduledAt: string; roomId?: string; instructorId: string;
}

// ─── Live Room Component ────────────────────────────────────────────────────────
function LiveRoom({ session, onLeave }: { session: LiveSession; onLeave: () => void }) {
  const { user } = useAuth();
  const qc = useQueryClient();
  const [micOn, setMicOn] = useState(true);
  const [camOn, setCamOn] = useState(true);
  const [handRaised, setHandRaised] = useState(false);
  const [participants, setParticipants] = useState<string[]>([]);
  const [chatMessages, setChatMessages] = useState<{ sender: string; text: string }[]>([]);
  const [chatInput, setChatInput] = useState("");
  const stompRef = useRef<Client | null>(null);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [stream, setStream] = useState<MediaStream | null>(null);

  const endSessionMutation = useMutation({
    mutationFn: () => api.post(`/live-sessions/${session.id}/end`),
    onSuccess: () => {
      toast.success("Session ended");
      qc.invalidateQueries({ queryKey: ["live-sessions"] });
      onLeave();
    },
  });

  // Acquire local media stream
  useEffect(() => {
    let activeStream: MediaStream | null = null;
    const startMedia = async () => {
      try {
        const ms = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
        activeStream = ms;
        setStream(ms);
        if (videoRef.current) {
          videoRef.current.srcObject = ms;
        }
      } catch (err) {
        console.error("Failed to acquire media stream", err);
        toast.error("Could not access camera/microphone");
      }
    };
    startMedia();
    return () => {
      if (activeStream) {
        activeStream.getTracks().forEach(track => track.stop());
      }
    };
  }, []);

  // Sync micOn/camOn with stream tracks
  useEffect(() => {
    if (stream) {
      stream.getAudioTracks().forEach(track => {
        track.enabled = micOn;
      });
      stream.getVideoTracks().forEach(track => {
        track.enabled = camOn;
      });
    }
  }, [micOn, camOn, stream]);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => {
        const wsUrl = import.meta.env.VITE_WS_URL || (import.meta.env.VITE_API_URL || "").replace(/\/api$/, "") + "/ws";
        return new SockJS(wsUrl);
      },
      connectHeaders: { Authorization: `Bearer ${getToken()}` },
      onConnect: () => {
        client.subscribe(`/topic/room/${session.roomId}`, (msg) => {
          const signal = JSON.parse(msg.body);
          if (signal.type === "PARTICIPANT_JOINED") {
            setParticipants(p => [...new Set([...p, signal.userId])]);
          }
        });
        client.subscribe(`/topic/room/${session.roomId}/chat`, (msg) => {
          const data = JSON.parse(msg.body);
          setChatMessages(prev => [...prev, { sender: data.sender, text: data.text }]);
        });
        client.publish({
          destination: `/app/room/${session.roomId}/events`,
          body: JSON.stringify({ type: "PARTICIPANT_JOINED", userId: user?.id }),
        });
      },
    });
    client.activate();
    stompRef.current = client;
    return () => { client.deactivate(); };
  }, [session.roomId, user?.id]);

  const sendChat = () => {
    if (!chatInput.trim() || !stompRef.current) return;
    stompRef.current.publish({
      destination: `/app/room/${session.roomId}/chat`,
      body: JSON.stringify({ sender: user?.name, text: chatInput }),
    });
    setChatInput("");
  };

  const raiseHand = () => {
    setHandRaised(h => !h);
    toast(handRaised ? "Hand lowered" : "✋ Hand raised!");
    stompRef.current?.publish({
      destination: `/app/room/${session.roomId}/events`,
      body: JSON.stringify({ type: "RAISE_HAND", userId: user?.id, raised: !handRaised }),
    });
  };

  const handleLeave = () => {
    stompRef.current?.deactivate();
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
    }
    // If instructor, end the session; students just leave
    if ((user as any)?.isInstructor) {
      endSessionMutation.mutate();
    } else {
      onLeave();
    }
  };

  return (
    <div className="flex h-[calc(100vh-5rem)] gap-4 -mx-6 -mt-6 p-6 animate-fade-in">
      {/* Main video area */}
      <div className="flex-1 flex flex-col gap-4">
        <div className="flex-1 rounded-2xl bg-black flex items-center justify-center border border-slate-700 relative overflow-hidden group">
          {camOn && stream ? (
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted // Mute local video to prevent feedback loop
              className="w-full h-full object-cover mirror"
              style={{ transform: "scaleX(-1)" }} // Mirror effect for local view
            />
          ) : (
            <div className="text-center">
              <CameraOff className="w-16 h-16 text-slate-700 mx-auto mb-2" />
              <p className="text-slate-500">Camera is off</p>
            </div>
          )}
          
          <div className="absolute top-4 left-4">
            <span className="badge badge-red flex items-center gap-1.5 animate-pulse-soft">
              <span className="w-2 h-2 rounded-full bg-red-400" /> LIVE
            </span>
          </div>
          <div className="absolute top-4 right-4 bg-black/60 px-3 py-1 rounded-full backdrop-blur-sm border border-white/10">
            <span className="text-xs text-slate-300 font-medium">{session.title}</span>
          </div>
          <div className="absolute bottom-4 left-4 bg-black/60 px-3 py-1.5 rounded-lg backdrop-blur-sm border border-white/10 flex items-center gap-2 transition-opacity opacity-0 group-hover:opacity-100">
            <span className="text-sm font-medium text-white">{user?.name} (You)</span>
            {!micOn && <MicOff className="w-3.5 h-3.5 text-red-400" />}
          </div>
        </div>

        {/* Controls */}
        <div className="flex items-center justify-center gap-3">
          <button onClick={() => setMicOn(m => !m)}
            className={`w-12 h-12 rounded-full flex items-center justify-center transition-colors ${micOn ? "bg-surface-muted hover:bg-slate-600" : "bg-red-600 hover:bg-red-700"}`}>
            {micOn ? <Mic className="w-5 h-5 text-white" /> : <MicOff className="w-5 h-5 text-white" />}
          </button>
          <button onClick={() => setCamOn(c => !c)}
            className={`w-12 h-12 rounded-full flex items-center justify-center transition-colors ${camOn ? "bg-surface-muted hover:bg-slate-600" : "bg-red-600 hover:bg-red-700"}`}>
            {camOn ? <Camera className="w-5 h-5 text-white" /> : <CameraOff className="w-5 h-5 text-white" />}
          </button>
          <button onClick={raiseHand}
            className={`w-12 h-12 rounded-full flex items-center justify-center transition-colors ${handRaised ? "bg-amber-600" : "bg-surface-muted hover:bg-slate-600"}`}>
            <Hand className="w-5 h-5 text-white" />
          </button>
          <button onClick={handleLeave} disabled={endSessionMutation.isPending}
            className="w-12 h-12 rounded-full bg-red-600 hover:bg-red-700 flex items-center justify-center transition-colors">
            {endSessionMutation.isPending ? <Loader2 className="w-5 h-5 text-white animate-spin" /> : <PhoneOff className="w-5 h-5 text-white" />}
          </button>
        </div>
      </div>

      {/* Sidebar */}
      <div className="w-72 flex flex-col gap-4">
        <div className="card flex-shrink-0">
          <p className="text-sm font-semibold text-slate-300 flex items-center gap-2 mb-3">
            <Users className="w-4 h-4 text-brand-400" /> Participants ({participants.length + 1})
          </p>
          <div className="flex items-center justify-between p-2 rounded-lg bg-brand-900/20 border border-brand-800/30">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-brand-600 flex items-center justify-center text-xs font-bold text-white shadow-lg">
                {user?.name?.charAt(0)}
              </div>
              <span className="text-sm text-slate-300 font-medium">{user?.name} (you)</span>
            </div>
            <div className="flex gap-1.5">
              {!micOn && <MicOff className="w-3.5 h-3.5 text-red-400" />}
              {handRaised && <Hand className="w-3.5 h-3.5 text-amber-400" />}
            </div>
          </div>
        </div>

        <div className="card flex-1 flex flex-col overflow-hidden">
          <p className="text-sm font-semibold text-slate-300 flex items-center gap-2 mb-3">
            <MessageSquare className="w-4 h-4 text-green-400" /> Live Chat
          </p>
          <div className="flex-1 overflow-y-auto space-y-2 mb-3">
            {chatMessages.map((msg, i) => (
              <div key={i} className="text-xs">
                <span className="text-brand-400 font-medium">{msg.sender}: </span>
                <span className="text-slate-300">{msg.text}</span>
              </div>
            ))}
            {chatMessages.length === 0 && <p className="text-slate-600 text-xs">No messages yet</p>}
          </div>
          <div className="flex gap-2">
            <input value={chatInput} onChange={(e) => setChatInput(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && sendChat()}
              placeholder="Type a message..." className="input text-xs py-2 flex-1" />
            <button onClick={sendChat} className="btn-primary py-2 px-3 text-xs">Send</button>
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Schedule Session Modal ─────────────────────────────────────────────────────
function ScheduleModal({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const [form, setForm] = useState({ title: "", courseId: "", scheduledAt: "" });
  const [loading, setLoading] = useState(false);

  const { data: myCourses = [] } = useQuery({
    queryKey: ["instructor-courses"],
    queryFn: () => api.get("/courses/my").then(r => r.data.data),
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.title || !form.courseId || !form.scheduledAt) {
      toast.error("Please fill all fields");
      return;
    }
    setLoading(true);
    try {
      await api.post("/live-sessions", {
        ...form,
        description: "",
        scheduledAt: new Date(form.scheduledAt).toISOString().slice(0, 19),
      });
      toast.success("Session scheduled!");
      qc.invalidateQueries({ queryKey: ["live-sessions"] });
      onClose();
    } catch (e: any) {
      toast.error(e.response?.data?.message || "Failed to schedule session");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        className="card w-full max-w-md mx-4"
      >
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-white">Schedule Live Session</h2>
          <button onClick={onClose} className="p-2 hover:bg-surface-muted rounded-lg transition-colors">
            <X className="w-5 h-5 text-slate-400" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Session Title</label>
            <input
              className="input w-full"
              placeholder="e.g. Week 3: Live Q&A"
              value={form.title}
              onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Course</label>
            <select
              className="input w-full"
              value={form.courseId}
              onChange={e => setForm(f => ({ ...f, courseId: e.target.value }))}
            >
              <option value="">Select a course...</option>
              {(myCourses as any[]).map((c: any) => (
                <option key={c.id} value={c.id}>{c.title}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">Date & Time</label>
            <input
              type="datetime-local"
              className="input w-full"
              value={form.scheduledAt}
              min={new Date().toISOString().slice(0, 16)}
              onChange={e => setForm(f => ({ ...f, scheduledAt: e.target.value }))}
            />
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="btn-secondary flex-1">Cancel</button>
            <button type="submit" disabled={loading} className="btn-primary flex-1">
              {loading ? <Loader2 className="w-4 h-4 animate-spin mx-auto" /> : "Schedule Session"}
            </button>
          </div>
        </form>
      </motion.div>
    </div>
  );
}

// ─── Main Live Sessions List ────────────────────────────────────────────────────
export default function LiveSessions() {
  const { user } = useAuth();
  const [activeSession, setActiveSession] = useState<LiveSession | null>(null);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const qc = useQueryClient();

  const { data: sessions = [], isLoading } = useQuery<LiveSession[]>({
    queryKey: ["live-sessions"],
    queryFn: () => api.get("/live-sessions").then(r => r.data.data),
    refetchInterval: 30_000,
  });

  const startMutation = useMutation({
    mutationFn: (id: string) => api.post(`/live-sessions/${id}/start`),
    onSuccess: (res, id) => {
      toast.success("Session started! Students are being notified.");
      qc.invalidateQueries({ queryKey: ["live-sessions"] });
      // Navigate into the live room with updated session data
      const started = (sessions as LiveSession[]).find(s => s.id === id);
      if (started) setActiveSession({ ...started, status: "LIVE", roomId: res.data.data.roomId });
    },
    onError: (e: any) => toast.error(e.response?.data?.message || "Failed to start session"),
  });

  const liveSessions = (sessions as LiveSession[]).filter(s => s.status === "LIVE");
  const upcoming = (sessions as LiveSession[]).filter(s => s.status === "SCHEDULED");

  if (activeSession?.status === "LIVE") {
    return <LiveRoom session={activeSession} onLeave={() => setActiveSession(null)} />;
  }

  return (
    <>
      {showScheduleModal && <ScheduleModal onClose={() => setShowScheduleModal(false)} />}
      <div className="space-y-6 animate-fade-in">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white">Live Sessions</h1>
            <p className="text-slate-400 text-sm mt-1">Interactive virtual classrooms</p>
          </div>
          {(user as any)?.isInstructor && (
            <button
              onClick={() => setShowScheduleModal(true)}
              className="btn-primary flex items-center gap-2"
            >
              <Plus className="w-4 h-4" /> Schedule Session
            </button>
          )}
        </div>

        {/* Live Now */}
        {liveSessions.length > 0 && (
          <div>
            <h2 className="text-lg font-semibold text-white mb-3 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-red-400 animate-pulse" /> Live Now
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {liveSessions.map(session => (
                <motion.div key={session.id} initial={{ opacity: 0 }} animate={{ opacity: 1 }}
                  className="card border-red-800/30 bg-gradient-to-br from-red-900/20 to-surface-card">
                  <div className="flex items-start justify-between mb-3">
                    <div>
                      <p className="font-semibold text-white">{session.title}</p>
                      <p className="text-sm text-slate-400">{session.courseId}</p>
                    </div>
                    <span className="badge badge-red">LIVE</span>
                  </div>
                  <button onClick={() => setActiveSession(session)} className="btn-primary flex items-center gap-2 w-full justify-center">
                    <Video className="w-4 h-4" /> Join Session
                  </button>
                </motion.div>
              ))}
            </div>
          </div>
        )}

        {/* Upcoming */}
        <div>
          <h2 className="text-lg font-semibold text-white mb-3">Upcoming Sessions</h2>
          {isLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {[1, 2].map(i => <div key={i} className="h-32 rounded-2xl bg-surface-card animate-pulse-soft" />)}
            </div>
          ) : upcoming.length === 0 ? (
            <div className="card text-center py-12">
              <Calendar className="w-10 h-10 text-slate-700 mx-auto mb-3" />
              <p className="text-slate-500">No upcoming sessions scheduled</p>
              {(user as any)?.isInstructor && (
                <button onClick={() => setShowScheduleModal(true)} className="text-brand-400 text-sm hover:underline mt-2">
                  Schedule one now →
                </button>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {upcoming.map(session => (
                <motion.div key={session.id} initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
                  className="card hover:border-slate-600 transition-all">
                  <div className="flex items-start justify-between mb-3">
                    <div>
                      <p className="font-semibold text-white">{session.title}</p>
                      <p className="text-sm text-slate-400 flex items-center gap-1 mt-1">
                        <Calendar className="w-3.5 h-3.5" />
                        {session.scheduledAt ? format(new Date(session.scheduledAt), "MMM d, h:mm a") : "TBD"}
                      </p>
                    </div>
                    <span className="badge badge-blue">SCHEDULED</span>
                  </div>
                  {(user as any)?.isInstructor && session.instructorId === user?.id && (
                    <button
                      onClick={() => startMutation.mutate(session.id)}
                      disabled={startMutation.isPending}
                      className="btn-primary text-sm py-2 w-full flex items-center justify-center gap-2"
                    >
                      {startMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Video className="w-4 h-4" />}
                      Start Session
                    </button>
                  )}
                </motion.div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
