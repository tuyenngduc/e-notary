import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { toApiErrorMessage } from '../../../lib/apiError';
import { getDefaultRouteByRole } from '../../../lib/roleRedirect';
import { type RegisterFormValues, registerSchema } from '../authSchemas';

export function RegisterPage() {
  const { register: registerAccount } = useAuth();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: '',
      phoneNumber: '',
      password: '',
      confirmPassword: '',
    },
  });

  const onSubmit = handleSubmit(async (data) => {
    setSubmitError('');
    try {
      const nextSession = await registerAccount({
        email: data.email,
        phoneNumber: data.phoneNumber,
        password: data.password,
      });
      navigate(getDefaultRouteByRole(nextSession.role));
    } catch (error) {
      setSubmitError(toApiErrorMessage(error, 'Đăng ký thất bại'));
    }
  });

  return (
    <main className="auth-screen">
      <section className="auth-card">
        <div className="card-header">
          <h1>Đăng ký</h1>
          <p>Tạo tài khoản khách hàng để sử dụng hệ thống công chứng trực tuyến.</p>
        </div>

        <form className="form-stack" onSubmit={onSubmit} noValidate>
          <label className="field">
            <span>Email</span>
            <input type="email" placeholder="Vui lòng nhập email" {...register('email')} />
            {errors.email && <small>{errors.email.message}</small>}
          </label>

          <label className="field">
            <span>Số điện thoại</span>
            <input type="tel" placeholder="Vui lòng nhập số điện thoại" {...register('phoneNumber')} />
            {errors.phoneNumber && <small>{errors.phoneNumber.message}</small>}
          </label>

          <label className="field">
            <span>Mật khẩu</span>
            <div className="password-wrap">
              <input
                type={showPassword ? 'text' : 'password'}
                placeholder="Tối thiểu 6 ký tự"
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

          <label className="field">
            <span>Nhập lại mật khẩu</span>
            <div className="password-wrap">
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                placeholder="Nhập lại mật khẩu"
                {...register('confirmPassword')}
              />
              <button
                type="button"
                className="password-eye"
                aria-label={showConfirmPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                onClick={() => setShowConfirmPassword((prev) => !prev)}
              >
                {showConfirmPassword ? '🙈' : '👁️'}
              </button>
            </div>
            {errors.confirmPassword && <small>{errors.confirmPassword.message}</small>}
          </label>

          {submitError && <div className="form-error">{submitError}</div>}

          <button type="submit" className="primary-btn w-full" disabled={isSubmitting}>
            {isSubmitting ? 'Đang xử lý...' : 'Đăng ký'}
          </button>
        </form>

        <div className="auth-footer-links">
          <p>
            Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
          </p>
          <p>
            <Link to="/">Quay lại trang chủ</Link>
          </p>
        </div>
      </section>
    </main>
  );
}
