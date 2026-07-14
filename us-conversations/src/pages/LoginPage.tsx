import React, { useState } from 'react';
import { supabase } from '../utils/supabase';
import { typescale, elevation, motionTokens } from '../theme/tokens';

interface LoginPageProps {
  tokens: Record<string, string>;
}

export const LoginPage: React.FC<LoginPageProps> = ({ tokens }) => {
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);
    setSuccessMsg(null);
    setLoading(true);

    try {
      if (isLogin) {
        // SignIn
        const { error } = await supabase.auth.signInWithPassword({ email, password });
        if (error) throw error;
      } else {
        // SignUp
        const { error } = await supabase.auth.signUp({ email, password });
        if (error) throw error;
        setSuccessMsg('Account created successfully! You are now logged in.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'An error occurred during authentication.');
    } finally {
      setLoading(false);
    }
  };

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '0.8rem 1rem',
    borderRadius: '8px',
    background: tokens['surface-container-lowest'],
    border: `1px solid ${tokens['outline-variant']}`,
    color: tokens['on-surface'],
    ...typescale['body-large'],
    outline: 'none',
    boxSizing: 'border-box',
    fontFamily: 'inherit',
    marginBottom: '1rem',
  };

  const btnPrimary: React.CSSProperties = {
    width: '100%',
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '0.85rem 1rem',
    borderRadius: '10px',
    cursor: 'pointer',
    fontFamily: 'inherit',
    ...typescale['label-large'],
    background: `linear-gradient(135deg, #059669, ${tokens['primary']})`,
    color: '#fff',
    border: 'none',
    boxShadow: `0 4px 12px ${tokens['primary-container']}`,
    transition: `all ${motionTokens.duration.short4} ${motionTokens.easing.standard}`,
    opacity: loading ? 0.7 : 1,
  };

  return (
    <div style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '80vh', padding: '1rem'
    }}>
      <div className="animate-fade-in" style={{
        background: tokens['surface-container'],
        backdropFilter: 'blur(12px)',
        border: `1px solid ${tokens['outline-variant']}`,
        borderRadius: '24px',
        ...elevation.level3,
        padding: '3rem 2.5rem',
        width: '100%',
        maxWidth: '420px',
        textAlign: 'center'
      }}>
        <h1 style={{ ...typescale['display-small'], color: tokens['on-surface'], margin: '0 0 0.5rem 0' }}>
          Welcome Back
        </h1>
        <p style={{ ...typescale['body-large'], color: tokens['on-surface-variant'], marginBottom: '2.5rem' }}>
          {isLogin ? 'Sign in to sync your progress.' : 'Create an account to save your progress.'}
        </p>

        <form onSubmit={handleSubmit} style={{ textAlign: 'left' }}>
          <label style={{ display: 'block', ...typescale['label-medium'], color: tokens['on-surface-variant'], marginBottom: '0.4rem' }}>
            Email Address
          </label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            style={inputStyle}
            placeholder="you@example.com"
          />

          <label style={{ display: 'block', ...typescale['label-medium'], color: tokens['on-surface-variant'], marginBottom: '0.4rem' }}>
            Password
          </label>
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={inputStyle}
            placeholder="••••••••"
            minLength={6}
          />

          {errorMsg && (
            <div style={{ ...typescale['body-small'], color: tokens['error'], background: tokens['error-container'], border: `1px solid ${tokens['error']}40`, borderRadius: '8px', padding: '0.75rem', marginBottom: '1rem' }}>
              ⚠️ {errorMsg}
            </div>
          )}

          {successMsg && (
            <div style={{ ...typescale['body-small'], color: '#059669', background: '#D1FAE5', border: `1px solid #34D399`, borderRadius: '8px', padding: '0.75rem', marginBottom: '1rem' }}>
              ✓ {successMsg}
            </div>
          )}

          <button type="submit" disabled={loading} style={btnPrimary}>
            {loading ? 'Processing...' : isLogin ? 'Sign In' : 'Sign Up'}
          </button>
        </form>

        <div style={{ marginTop: '2rem', ...typescale['body-medium'], color: tokens['on-surface-variant'] }}>
          {isLogin ? "Don't have an account? " : "Already have an account? "}
          <button
            type="button"
            onClick={() => { setIsLogin(!isLogin); setErrorMsg(null); setSuccessMsg(null); }}
            style={{
              background: 'none', border: 'none', color: tokens['primary'],
              fontWeight: 600, cursor: 'pointer', padding: 0, fontFamily: 'inherit',
              textDecoration: 'underline'
            }}
          >
            {isLogin ? 'Sign up here' : 'Sign in here'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
