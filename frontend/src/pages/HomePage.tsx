import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { verifyPublicDocumentApi } from '../features/verification/verificationApi';
import type { PublicDocumentVerificationResult } from '../features/verification/verificationApi';
import { toApiErrorMessage } from '../lib/apiError';
import { toContractTypeLabel, toDocTypeLabel, toRequestStatusMeta } from '../lib/enumLabels';
import { getDefaultRouteByRole } from '../lib/roleRedirect';

const features = [
  {
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3l7 4v5c0 5-3.4 8-7 9-3.6-1-7-4-7-9V7l7-4z" />
      </svg>
    ),
    title: 'An toàn',
    description: 'Mã hóa end-to-end và xác thực hai yếu tố bảo vệ tài liệu của bạn',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M13 2L4 14h6l-1 8 9-12h-6l1-8z" />
      </svg>
    ),
    title: 'Nhanh chóng',
    description: 'Xử lý yêu cầu trong vài phút với lịch hẹn linh hoạt',
  },
  {
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3l8 4-8 4-8-4 8-4z" />
        <path d="M6 11v4c0 2 2.7 3.5 6 3.5s6-1.5 6-3.5v-4" />
      </svg>
    ),
    title: 'Chuyên nghiệp',
    description: 'Công chứng viên được xác minh và có kinh nghiệm',
  },
];

export function HomePage() {
  const { isAuthenticated, session } = useAuth();
  const [verificationFile, setVerificationFile] = useState<File | null>(null);
  const [verificationResult, setVerificationResult] = useState<PublicDocumentVerificationResult | null>(null);
  const [verificationError, setVerificationError] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);

  const handleVerifyDocument = async () => {
    if (!verificationFile) {
      setVerificationError('Vui lòng chọn file cần xác minh.');
      return;
    }

    setIsVerifying(true);
    setVerificationError('');
    setVerificationResult(null);
    try {
      const result = await verifyPublicDocumentApi(verificationFile);
      setVerificationResult(result);
    } catch (error) {
      setVerificationError(toApiErrorMessage(error, 'Không thể xác minh tài liệu. Vui lòng thử lại.'));
    } finally {
      setIsVerifying(false);
    }
  };

  const requestStatusMeta = toRequestStatusMeta(verificationResult?.requestStatus);
  const resultState = verificationResult?.verified ? 'verified' : 'not-found';

  return (
    <main className="landing-page">
      <nav className="top-nav">
        <div className="container nav-inner">
          <div className="brand">Công Chứng Điện Tử</div>
          <div className="nav-links">
            {isAuthenticated ? (
              <Link className="link-btn primary-link-btn" to={getDefaultRouteByRole(session?.role)}>
                Vào dashboard
              </Link>
            ) : (
              <>
                <Link className="link-btn" to="/login">
                  Đăng nhập
                </Link>
                <Link className="link-btn primary-link-btn" to="/register">
                  Đăng ký
                </Link>
              </>
            )}
          </div>
        </div>
      </nav>

      <section className="container hero-section">
        <h1>Công chứng điện tử an toàn và tiện lợi</h1>
        <p>
          Nền tảng giúp bạn gửi hồ sơ, theo dõi tiến độ và làm việc với công chứng viên trực tuyến một cách nhanh
          chóng, minh bạch.
        </p>
        <div className="hero-actions">
          <Link className="primary-btn" to={isAuthenticated ? getDefaultRouteByRole(session?.role) : '/register'}>
            Bắt đầu ngay
          </Link>
          <Link className="ghost-btn" to="/login">
            Đăng nhập hệ thống
          </Link>
        </div>
      </section>

      <section className="container public-verification-section" aria-labelledby="public-verification-title">
        <div className="public-verification-shell">
          <div className="public-verification-copy">
            <span className="section-eyebrow">Tra cứu công khai</span>
            <h2 id="public-verification-title">Xác minh tài liệu công chứng</h2>
            <p>
              Tải lên tài liệu đã nhận từ hệ thống để kiểm tra dấu vết ký số, trạng thái hồ sơ và giao dịch blockchain
              đã ghi nhận. Không cần đăng nhập.
            </p>
            <div className="verification-trust-row" aria-label="Thông tin xác minh">
              <span>SHA-256</span>
              <span>Chữ ký số</span>
              <span>Blockchain</span>
            </div>
          </div>

          <div className="public-verification-card">
            <div className="verification-card-head">
              <div>
                <span>Kiểm tra tài liệu</span>
                <strong>Đối chiếu file với dữ liệu công chứng</strong>
              </div>
              <span className="verification-mode-pill">Public</span>
            </div>

            <div className={`verification-upload-box ${verificationFile ? 'has-file' : ''}`}>
              <input
                id="public-document-verification-file"
                type="file"
                onChange={(event) => {
                  setVerificationFile(event.target.files?.[0] ?? null);
                  setVerificationResult(null);
                  setVerificationError('');
                }}
              />
              <label htmlFor="public-document-verification-file">
                <span className="verification-upload-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24">
                    <path d="M12 3v12" />
                    <path d="m7 8 5-5 5 5" />
                    <path d="M5 15v3a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3v-3" />
                  </svg>
                </span>
                <span className="verification-upload-content">
                  <span>File tài liệu</span>
                  <strong>{verificationFile ? verificationFile.name : 'Chọn file để xác minh'}</strong>
                  <small>
                    {verificationFile
                      ? `${(verificationFile.size / 1024 / 1024).toFixed(2)} MB`
                      : 'Hỗ trợ PDF hoặc tài liệu được tải từ hệ thống'}
                  </small>
                </span>
              </label>
            </div>

            <button type="button" className="primary-btn w-full" onClick={handleVerifyDocument} disabled={isVerifying || !verificationFile}>
              {isVerifying ? 'Đang xác minh...' : 'Xác minh file'}
            </button>

            {verificationError ? <div className="form-error">{verificationError}</div> : null}

            {verificationResult ? (
              <div className={`verification-result ${resultState}`}>
                <div className="verification-result-head">
                  <span>{verificationResult.verified ? 'Đã xác thực' : 'Không khớp hệ thống'}</span>
                  <strong>{verificationResult.message}</strong>
                </div>

                <dl className="verification-result-grid">
                  <ResultItem label="SHA-256" value={shortHash(verificationResult.fileHash)} title={verificationResult.fileHash} />
                  {verificationResult.verified ? (
                    <>
                      <ResultItem label="Mã hồ sơ" value={verificationResult.requestCode || 'N/A'} />
                      <ResultItem label="Loại văn bản" value={toContractTypeLabel(verificationResult.contractType) || 'N/A'} />
                      <ResultItem label="Trạng thái hồ sơ" value={requestStatusMeta.label || verificationResult.requestStatus || 'N/A'} />
                      <ResultItem
                        label="Tài liệu"
                        value={toDocTypeLabel(verificationResult.documentType) || verificationResult.documentName || 'N/A'}
                      />
                      <ResultItem label="Chữ ký hợp lệ" value={String(verificationResult.signedSignatureCount ?? 0)} />
                      <ResultItem label="Blockchain" value={verificationResult.blockchainStatus || 'N/A'} />
                      <ResultItem label="Block" value={String(verificationResult.blockNumber ?? 'N/A')} />
                      <ResultItem
                        label="Transaction"
                        value={shortHash(verificationResult.transactionHash)}
                        title={verificationResult.transactionHash || undefined}
                      />
                      <ResultItem
                        label="Xác nhận lúc"
                        value={verificationResult.confirmedAt ? new Date(verificationResult.confirmedAt).toLocaleString('vi-VN') : 'N/A'}
                      />
                    </>
                  ) : null}
                </dl>
              </div>
            ) : null}
          </div>
        </div>
      </section>

      <section className="container feature-grid-section">
        <h2>Tại sao chọn chúng tôi</h2>
        <div className="feature-grid">
          {features.map((feature) => (
            <article className="feature-card" key={feature.title}>
              <div className="feature-icon" aria-hidden="true">
                {feature.icon}
              </div>
              <h3>{feature.title}</h3>
              <p>{feature.description}</p>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}

function ResultItem({ label, value, title }: { label: string; value: string; title?: string | null }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd title={title || undefined}>{value}</dd>
    </div>
  );
}

function shortHash(value: string | null | undefined) {
  if (!value) {
    return 'N/A';
  }
  return value.length > 18 ? `${value.slice(0, 10)}...${value.slice(-8)}` : value;
}
