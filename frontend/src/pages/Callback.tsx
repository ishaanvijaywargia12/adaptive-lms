import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { signinCallback } from '../lib/auth';
import { Loader2 } from 'lucide-react';

export default function Callback() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleCallback = async () => {
      try {
        await signinCallback();
        navigate('/', { replace: true });
      } catch (err) {
        console.error('Authentication callback error:', err);
        setError('Failed to log in. Please try again.');
      }
    };
    
    handleCallback();
  }, [navigate]);

  if (error) {
    return (
      <div className="flex h-screen w-full items-center justify-center flex-col gap-4">
        <h2 className="text-2xl font-bold text-red-500">Authentication Error</h2>
        <p className="text-gray-600">{error}</p>
        <button 
          onClick={() => navigate('/', { replace: true })}
          className="mt-4 rounded-md bg-primary px-4 py-2 text-white hover:bg-primary/90"
        >
          Return Home
        </button>
      </div>
    );
  }

  return (
    <div className="flex h-screen w-full items-center justify-center flex-col gap-4">
      <Loader2 className="h-8 w-8 animate-spin text-primary" />
      <p className="text-gray-600">Completing login...</p>
    </div>
  );
}
