import { useEffect, useState } from 'react';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  getDashboardRequestsChartApi,
  getDashboardRevenueApi,
  getDashboardSummaryApi,
} from '../../features/admin/adminApi';
import type { DashboardSummary, RevenueData, RequestsChartData } from '../../features/admin/adminApi';
import { toApiErrorMessage } from '../../lib/apiError';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer, LineChart, Line } from 'recharts';

export function AdminDashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [revenueData, setRevenueData] = useState<RevenueData[]>([]);
  const [requestsChartData, setRequestsChartData] = useState<RequestsChartData[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pageError, setPageError] = useState('');

  useEffect(() => {
    async function loadData() {
      try {
        const [summaryData, revenueDataRes, requestsChartDataRes] = await Promise.all([
          getDashboardSummaryApi(),
          getDashboardRevenueApi(),
          getDashboardRequestsChartApi(),
        ]);
        setSummary(summaryData);
        setRevenueData(revenueDataRes);
        setRequestsChartData(requestsChartDataRes);
        setPageError('');
      } catch (error) {
        setPageError(toApiErrorMessage(error, 'Không tải được dữ liệu dashboard'));
      } finally {
        setIsLoading(false);
      }
    }
    void loadData();
  }, []);

  if (isLoading) {
    return (
      <DashboardLayout role="admin">
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
          Đang tải dữ liệu...
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout role="admin">
      <div className="page-content" style={{ maxWidth: '1200px', margin: '0 auto', padding: '2rem' }}>
        <div style={{ marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.5rem' }}>Dashboard Thống Kê</h1>
          <p className="muted-text">Cung cấp cái nhìn tổng quan về tình hình kinh doanh và hiệu suất hệ thống.</p>
        </div>

        {pageError && (
          <div className="form-error" style={{ marginBottom: '1.5rem', padding: '1rem', backgroundColor: '#fee2e2', color: '#b91c1c', borderRadius: '8px' }}>
            {pageError}
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1.5rem', marginBottom: '3rem' }}>
          <div className="soft-card" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', borderLeft: '4px solid #3b82f6' }}>
            <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Tổng Số User</span>
            <span style={{ fontSize: '2rem', fontWeight: 700, color: '#1e293b' }}>{summary?.totalUsers || 0}</span>
          </div>
          <div className="soft-card" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', borderLeft: '4px solid #8b5cf6' }}>
            <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Tổng Số CCV</span>
            <span style={{ fontSize: '2rem', fontWeight: 700, color: '#1e293b' }}>{summary?.totalNotaries || 0}</span>
          </div>
          <div className="soft-card" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', borderLeft: '4px solid #f59e0b' }}>
            <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Hồ Sơ Chờ Duyệt</span>
            <span style={{ fontSize: '2rem', fontWeight: 700, color: '#1e293b' }}>{summary?.pendingRequests || 0}</span>
          </div>
          <div className="soft-card" style={{ padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', borderLeft: '4px solid #10b981' }}>
            <span style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase' }}>Hồ Sơ Hoàn Thành</span>
            <span style={{ fontSize: '2rem', fontWeight: 700, color: '#1e293b' }}>{summary?.completedRequests || 0}</span>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: '2rem' }}>
          <div className="soft-card" style={{ padding: '1.5rem' }}>
            <h2 style={{ fontSize: '1.2rem', fontWeight: 600, marginBottom: '1.5rem', color: 'var(--text-primary)' }}>Doanh Thu Theo Tháng</h2>
            <div style={{ height: '300px', width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <LineChart
                  data={revenueData}
                  margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                >
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#e2e8f0" />
                  <XAxis dataKey="month" axisLine={false} tickLine={false} tick={{ fill: '#64748b' }} dy={10} />
                  <YAxis axisLine={false} tickLine={false} tick={{ fill: '#64748b' }} dx={-10} tickFormatter={(value) => `${(value / 1000).toFixed(0)}k`} />
                  <RechartsTooltip
                    contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}
                    formatter={(value: unknown) => [`${Number(value).toLocaleString()} VND`, 'Doanh thu']}
                  />
                  <Line type="monotone" dataKey="revenue" stroke="#3b82f6" strokeWidth={3} dot={{ r: 4, fill: '#3b82f6', strokeWidth: 2, stroke: '#fff' }} activeDot={{ r: 6 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="soft-card" style={{ padding: '1.5rem' }}>
            <h2 style={{ fontSize: '1.2rem', fontWeight: 600, marginBottom: '1.5rem', color: 'var(--text-primary)' }}>Số Lượng Hồ Sơ Theo Dịch Vụ</h2>
            <div style={{ height: '300px', width: '100%' }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={requestsChartData}
                  margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                  layout="vertical"
                >
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#e2e8f0" />
                  <XAxis
                    type="number"
                    allowDecimals={false}
                    axisLine={false}
                    tickLine={false}
                    tick={{ fill: '#64748b' }}
                    tickFormatter={(value) => String(Math.trunc(Number(value)))}
                  />
                  <YAxis dataKey="serviceType" type="category" axisLine={false} tickLine={false} tick={{ fill: '#64748b' }} width={120} />
                  <RechartsTooltip
                    cursor={{ fill: '#f1f5f9' }}
                    contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}
                    formatter={(value: unknown) => [String(value), 'Số lượng']}
                  />
                  <Bar dataKey="count" fill="#8b5cf6" radius={[0, 4, 4, 0]} barSize={24} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
