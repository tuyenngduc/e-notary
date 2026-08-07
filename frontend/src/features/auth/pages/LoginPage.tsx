import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { toApiErrorMessage } from '../../../lib/apiError';
import { getDefaultRouteByRole } from '../../../lib/roleRedirect';
import { API_BASE_URL } from '../../../lib/config';
import { type LoginFormValues, loginSchema } from '../authSchemas';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [submitError, setSubmitError] = useState('');
  const [debugDetails, setDebugDetails] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const formatLoginError = (error: unknown) => {
    const axiosLike = error as {
      isAxiosError?: boolean;
      response?: { status?: number; data?: unknown };
      config?: { url?: string; method?: string; baseURL?: string };
      message?: string;
    };

    const baseMessage = toApiErrorMessage(error, 'Đăng nhập thất bại');
    const isInvalidCredentials = baseMessage.toLowerCase().includes('email hoặc mật khẩu không chính xác');

    const lines: string[] = [];
    if (!isInvalidCredentials) {
      lines.push(`origin=${window.location.origin}`);
      lines.push(`API_BASE_URL=${API_BASE_URL || '(same-origin via Vite proxy)'}`);
      if (axiosLike?.isAxiosError) {
        lines.push(`axiosStatus=${axiosLike.response?.status ?? '(no response - network/CORS?)'}`);
        lines.push(`axiosRequest=${axiosLike.config?.method ?? ''} ${axiosLike.config?.baseURL ?? ''}${axiosLike.config?.url ?? ''}`);
      }
    }

    const friendly =
      axiosLike?.isAxiosError && !axiosLike.response
        ? `${baseMessage}. Có thể iPhone bị chặn truy cập API (Firewall/CORS) hoặc sai cấu hình API_BASE_URL.`
        : baseMessage;

    return { friendly, details: lines.join('\n') };
  };

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const onSubmit = handleSubmit(async (data) => {
    setSubmitError('');
    setDebugDetails('');
    try {
      const nextSession = await login(data);
      const redirectPath =
        (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
      navigate(redirectPath || getDefaultRouteByRole(nextSession.role));
    } catch (error) {
      const formatted = formatLoginError(error);
      setSubmitError(formatted.friendly);
      setDebugDetails(formatted.details);
    }
  });

  return (
    <main className="auth-screen">
      <section className="auth-card">
        <div className="card-header">
          <h1>Đăng nhập</h1>
          <p>Đăng nhập để quản lý yêu cầu công chứng và lịch hẹn của bạn.</p>
        </div>

        <form className="form-stack" onSubmit={onSubmit} noValidate>
          <label className="field">
            <span>Email</span>
            <input type="email" placeholder="Vui lòng nhập email" {...register('email')} />
            {errors.email && <small>{errors.email.message}</small>}
          </label>

          <label className="field">
            <span>Mật khẩu</span>
            <div className="password-wrap">
              <input
                type={showPassword ? 'text' : 'password'}
                placeholder="••••••••"
                {...register('password')}
              />
              <button
                type="button"
                className="password-eye"
                aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                onClick={() => setShowPassword((prev) => !prev)}
              >
                {showPassword ? '🙈' : '👁️'}
              </button>
            </div>
            {errors.password && <small>{errors.password.message}</small>}
          </label>

          {submitError ? (
            <div className="form-error">
              <div>{submitError}</div>
              {debugDetails ? (
                <details style={{ marginTop: '0.75rem' }}>
                  <summary style={{ cursor: 'pointer' }}>Chi tiết kỹ thuật</summary>
                  <pre style={{ marginTop: '0.75rem', whiteSpace: 'pre-wrap' }}>{debugDetails}</pre>
                </details>
              ) : null}
            </div>
          ) : null}

          <button type="submit" className="primary-btn w-full" disabled={isSubmitting}>
            {isSubmitting ? 'Đang xử lý...' : 'Đăng nhập'}
          </button>
        </form>

        <div className="auth-footer-links">
          <p>
            Chưa có tài khoản? <Link to="/register">Đăng ký</Link>
          </p>
          <p>
            <Link to="/">Quay lại trang chủ</Link>
          </p>
        </div>
      </section>
    </main>
  );
}
