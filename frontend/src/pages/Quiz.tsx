import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery, useMutation } from "@tanstack/react-query";
import { Clock, CheckCircle, XCircle, ArrowLeft, Loader2, Star } from "lucide-react";
import toast from "react-hot-toast";
import api from "../lib/api";

// ─── Type contracts matching backend DTOs ─────────────────────────────────────
interface QuizOption { id: string; optionText: string; }
interface QuizQuestion {
  id: string; questionText: string; questionType: "MULTIPLE_CHOICE" | "SHORT_ANSWER";
  options?: QuizOption[];
}
interface QuizAttemptState {
  attemptId: string; questions: QuizQuestion[];
  timeLimitSeconds: number; deadlineAt?: string;
}
interface QuestionReview {
  questionId: string; correct: boolean;   // backend field is 'correct', not 'isCorrect'
  selectedAnswer?: string; correctAnswer?: string; explanation?: string;
}
interface QuizSubmitResponse {
  passed: boolean;
  scorePercent: number; // backend field is 'scorePercent', not 'score'
  questionReviews: QuestionReview[];
}

export default function Quiz() {
  const { lessonId } = useParams<{ lessonId: string }>();
  const navigate = useNavigate();

  const [attemptState, setAttemptState] = useState<QuizAttemptState | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [result, setResult] = useState<QuizSubmitResponse | null>(null);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);

  // 1. Fetch Quiz by Lesson ID
  const { data: quiz, isLoading: loadingQuiz } = useQuery({
    queryKey: ["quiz-by-lesson", lessonId],
    queryFn: () => api.get(`/quizzes/by-lesson/${lessonId}`).then(r => r.data.data),
  });

  // 2. Start Quiz Mutation
  const startQuizMutation = useMutation({
    mutationFn: (qId: string) => api.post(`/quizzes/${qId}/start`),
    onSuccess: (res) => {
      const data = res.data.data;
      setAttemptState(data);
      if (data.timeLimitSeconds > 0) {
        setTimeLeft(data.timeLimitSeconds);
      }
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to start quiz");
    }
  });

  // 3. Submit Quiz Mutation
  const submitQuizMutation = useMutation({
    mutationFn: () => api.post(`/quizzes/attempts/${attemptState.attemptId}/submit`, { answers }),
    onSuccess: (res) => {
      setResult(res.data.data);
      if (res.data.data.passed) {
        toast.success("Quiz passed! You earned XP!");
      } else {
        toast.error("Quiz failed. Try again!");
      }
    },
    onError: (err: any) => {
      toast.error(err.response?.data?.message || "Failed to submit quiz");
    }
  });

  // Timer effect
  useEffect(() => {
    if (timeLeft === null || result) return;
    if (timeLeft <= 0) {
      submitQuizMutation.mutate();
      return;
    }
    const timer = setInterval(() => {
      setTimeLeft(prev => (prev ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [timeLeft, result]);

  const handleStart = () => {
    if (quiz) startQuizMutation.mutate(quiz.id);
  };

  const handleOptionSelect = (questionId: string, optionId: string) => {
    setAnswers(prev => ({ ...prev, [questionId]: optionId }));
  };

  const handleSubmit = () => {
    if (Object.keys(answers).length < attemptState.questions.length) {
      if (!window.confirm("You have unanswered questions. Submit anyway?")) {
        return;
      }
    }
    submitQuizMutation.mutate();
  };

  if (loadingQuiz) {
    return (
      <div className="flex justify-center items-center h-64">
        <Loader2 className="w-8 h-8 text-brand-500 animate-spin" />
      </div>
    );
  }

  if (!quiz) {
    return (
      <div className="max-w-2xl mx-auto text-center py-20">
        <h2 className="text-xl font-semibold text-white">Quiz not found</h2>
        <button onClick={() => navigate(-1)} className="mt-4 text-brand-400 hover:text-brand-300">Go Back</button>
      </div>
    );
  }

  // Pre-Start Screen
  if (!attemptState && !result) {
    return (
      <div className="max-w-2xl mx-auto pt-10">
        <button onClick={() => navigate(-1)} className="flex items-center gap-2 text-slate-400 hover:text-white mb-6 transition-colors">
          <ArrowLeft className="w-4 h-4" /> Back to Course
        </button>
        <div className="card text-center py-12">
          <Star className="w-16 h-16 text-amber-400 mx-auto mb-4" />
          <h1 className="text-3xl font-bold text-white mb-2">{quiz.title}</h1>
          <p className="text-slate-400 mb-8">Passing Score: {quiz.passingScore}% • Max Attempts: {quiz.maxAttempts}</p>
          <button 
            onClick={handleStart}
            disabled={startQuizMutation.isPending}
            className="btn-primary text-lg px-8 py-3 w-full sm:w-auto"
          >
            {startQuizMutation.isPending ? <Loader2 className="w-5 h-5 animate-spin mx-auto" /> : "Start Quiz"}
          </button>
        </div>
      </div>
    );
  }

  // Result Screen
  if (result) {
    return (
      <div className="max-w-2xl mx-auto pt-10">
        <div className="card text-center py-12 border border-slate-700/50">
          {result.passed ? (
            <CheckCircle className="w-20 h-20 text-emerald-400 mx-auto mb-4" />
          ) : (
            <XCircle className="w-20 h-20 text-red-400 mx-auto mb-4" />
          )}
          <h1 className="text-4xl font-bold text-white mb-2">
            {result.scorePercent}%
          </h1>
          <p className={`text-lg font-medium mb-8 ${result.passed ? "text-emerald-400" : "text-red-400"}`}>
            {result.passed ? "Congratulations, you passed!" : "You failed this attempt."}
          </p>
          <button onClick={() => navigate(-1)} className="btn-primary mb-12">Return to Course</button>
          
          <div className="text-left space-y-6 mt-8">
            <h2 className="text-2xl font-bold text-white mb-6">Review Answers</h2>
            {result.questionReviews?.map((review: QuestionReview, idx: number) => (
              <div key={review.questionId} className={`card border ${review.correct ? 'border-emerald-500/50' : 'border-red-500/50'}`}>
                <div className="flex items-start justify-between gap-4 mb-4">
                  <h3 className="text-lg font-medium text-white">
                    <span className="text-slate-400 mr-2">{idx + 1}.</span>
                    {attemptState?.questions?.find(q => q.id === review.questionId)?.questionText || "Question text hidden"}
                  </h3>
                  {review.correct ? (
                    <CheckCircle className="w-6 h-6 text-emerald-400 shrink-0" />
                  ) : (
                    <XCircle className="w-6 h-6 text-red-400 shrink-0" />
                  )}
                </div>
                <div className="space-y-2 text-sm">
                  <div className="flex gap-2">
                    <span className="text-slate-400 w-24 shrink-0">Your answer:</span>
                    <span className={review.correct ? 'text-emerald-300' : 'text-red-300'}>
                      {review.selectedAnswer || "—"}
                    </span>
                  </div>
                  {!review.correct && (
                    <div className="flex gap-2">
                      <span className="text-slate-400 w-24 shrink-0">Correct answer:</span>
                      <span className="text-emerald-400">{review.correctAnswer || "—"}</span>
                    </div>
                  )}
                  {review.explanation && (
                    <div className="mt-4 p-3 bg-slate-800/50 rounded-lg text-slate-300 border border-slate-700/50">
                      <strong>Explanation:</strong> {review.explanation}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // Quiz Attempt Screen
  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  return (
    <div className="max-w-3xl mx-auto pt-6 pb-20">
      <div className="flex items-center justify-between mb-8 pb-4 border-b border-slate-700">
        <h1 className="text-2xl font-bold text-white">{quiz.title}</h1>
        {timeLeft !== null && (
          <div className={`flex items-center gap-2 px-4 py-2 rounded-lg font-mono text-lg ${timeLeft < 60 ? "bg-red-500/20 text-red-400" : "bg-slate-800 text-slate-300"}`}>
            <Clock className="w-5 h-5" />
            {formatTime(timeLeft)}
          </div>
        )}
      </div>

      <div className="space-y-8">
        {attemptState.questions.map((question: any, index: number) => (
          <div key={question.id} className="card">
            <h3 className="text-lg font-medium text-white mb-4">
              <span className="text-slate-400 mr-2">{index + 1}.</span>
              {question.questionText}
            </h3>
            <div className="space-y-3">
              {question.questionType === "SHORT_ANSWER" ? (
                <textarea
                  className="w-full p-3 rounded-xl bg-surface-muted border border-slate-700 text-white placeholder-slate-500 focus:outline-none focus:border-brand-500 resize-none"
                  rows={4}
                  placeholder="Type your answer here..."
                  value={answers[question.id] || ""}
                  onChange={e => setAnswers(prev => ({ ...prev, [question.id]: e.target.value }))}
                />
              ) : (
                question.options?.map((option: QuizOption) => (
                  <label 
                    key={option.id} 
                    className={`flex items-center gap-3 p-4 rounded-xl border cursor-pointer transition-all ${
                      answers[question.id] === option.id 
                        ? "bg-brand-600/20 border-brand-500 text-brand-100" 
                        : "bg-surface-muted border-slate-700 hover:border-slate-500 text-slate-300"
                    }`}
                  >
                    <input
                      type="radio"
                      name={question.id}
                      value={option.id}
                      checked={answers[question.id] === option.id}
                      onChange={() => handleOptionSelect(question.id, option.id)}
                      className="w-4 h-4 text-brand-500 bg-slate-800 border-slate-600 focus:ring-brand-600 focus:ring-offset-slate-900"
                    />
                    <span>{option.optionText}</span>
                  </label>
                ))
              )}
            </div>
          </div>
        ))}
      </div>

      <div className="mt-8 flex justify-end">
        <button
          onClick={handleSubmit}
          disabled={submitQuizMutation.isPending}
          className="btn-primary px-8 py-3 text-lg"
        >
          {submitQuizMutation.isPending ? <Loader2 className="w-5 h-5 animate-spin mx-auto" /> : "Submit Answers"}
        </button>
      </div>
    </div>
  );
}
