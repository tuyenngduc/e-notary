import { useEffect, useMemo, useState } from 'react';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  getBlockchainSummaryApi,
  listBlockchainNodesApi,
  listBlockchainTransactionsApi,
} from '../../features/admin/adminApi';
import type {
  BlockchainNode,
  BlockchainSummary,
  BlockchainTransaction,
} from '../../features/admin/adminApi';
import { toApiErrorMessage } from '../../lib/apiError';

function shortHash(value?: string, head = 12, tail = 10) {
  if (!value) return '-';
  if (value.length <= head + tail + 3) return value;
  return `${value.slice(0, head)}...${value.slice(-tail)}`;
}

function formatDate(value?: string) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

export function AdminBlockchainPage() {
  const [summary, setSummary] = useState<BlockchainSummary | null>(null);
  const [nodes, setNodes] = useState<BlockchainNode[]>([]);
  const [transactions, setTransactions] = useState<BlockchainTransaction[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pageError, setPageError] = useState('');

  useEffect(() => {
    let active = true;

    async function loadData() {
      try {
        const [summaryData, nodeData, transactionData] = await Promise.all([
          getBlockchainSummaryApi(),
          listBlockchainNodesApi(),
          listBlockchainTransactionsApi(),
        ]);
        if (!active) return;
        setSummary(summaryData);
        setNodes(nodeData);
        setTransactions(transactionData);
        setPageError('');
      } catch (error) {
        if (active) {
          setPageError(toApiErrorMessage(error, 'Không tải được dữ liệu blockchain'));
        }
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    }

    void loadData();
    return () => {
      active = false;
    };
  }, []);

  const latestTransaction = useMemo(() => transactions[0], [transactions]);

  if (isLoading) {
    return (
      <DashboardLayout role="admin">
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
          Đang tải dữ liệu blockchain...
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout role="admin">
      <div className="page-content" style={{ maxWidth: '1240px', margin: '0 auto', padding: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: '1rem', marginBottom: '1.5rem', flexWrap: 'wrap' }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 650, color: 'var(--text-primary)', marginBottom: '0.5rem' }}>
              Quản trị Blockchain Besu
            </h1>
            <p className="muted-text" style={{ maxWidth: '720px' }}>
              Theo dõi network, node và transaction hash của văn bản công chứng đã ký số.
            </p>
          </div>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', padding: '0.45rem 0.75rem', borderRadius: '999px', background: '#fff7ed', color: '#9a3412', border: '1px solid #fed7aa', fontWeight: 700, fontSize: '0.85rem' }}>
            {summary?.mode === 'MOCK' ? 'Mock/Sandbox' : summary?.mode || 'Unknown'}
          </span>
        </div>

        {pageError ? (
          <div className="form-error" style={{ marginBottom: '1.5rem', padding: '1rem', backgroundColor: '#fee2e2', color: '#b91c1c', borderRadius: '8px' }}>
            {pageError}
          </div>
        ) : null}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
          <SummaryCard label="Network" value={summary?.networkName || 'Hyperledger Besu Local'} accent="#0f766e" />
          <SummaryCard label="Chain ID" value={String(summary?.chainId ?? 1337)} accent="#2563eb" />
          <SummaryCard label="Latest block" value={(summary?.latestBlock ?? 0).toLocaleString('vi-VN')} accent="#7c3aed" />
          <SummaryCard label="Transactions" value={(summary?.totalTransactions ?? 0).toLocaleString('vi-VN')} accent="#16a34a" />
          <SummaryCard label="Active nodes" value={`${summary?.activeNodes ?? 0}/${summary?.totalNodes ?? 0}`} accent="#ea580c" />
        </div>

        <section className="soft-card" style={{ padding: '1.25rem', marginBottom: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', alignItems: 'center', marginBottom: '1rem', flexWrap: 'wrap' }}>
            <div>
              <h2 style={{ fontSize: '1.15rem', fontWeight: 650, color: 'var(--text-primary)', margin: 0 }}>Node Besu</h2>
            </div>
            {latestTransaction ? (
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                Transaction mới nhất: <b style={{ color: 'var(--text-primary)' }}>{shortHash(latestTransaction.transactionHash)}</b>
              </div>
            ) : null}
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', minWidth: '980px', tableLayout: 'fixed' }}>
              <colgroup>
                <col style={{ width: '150px' }} />
                <col style={{ width: '130px' }} />
                <col style={{ width: '210px' }} />
                <col style={{ width: '220px' }} />
                <col style={{ width: '130px' }} />
                <col style={{ width: '80px' }} />
                <col style={{ width: '120px' }} />
              </colgroup>
              <thead>
                <tr>
                  <th>Node</th>
                  <th>Role</th>
                  <th>Endpoint</th>
                  <th>Validator</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'right' }}>Peers</th>
                  <th style={{ textAlign: 'right' }}>Block height</th>
                </tr>
              </thead>
              <tbody>
                {nodes.map((node) => (
                  <tr key={`${node.nodeName}-${node.validatorAddress || node.endpoint || 'node'}`}>
                    <td style={{ fontWeight: 650, whiteSpace: 'nowrap' }}>{node.nodeName}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>{node.role}</td>
                    <td>
                      <code style={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={node.endpoint || undefined}>
                        {node.endpoint || '-'}
                      </code>
                    </td>
                    <td>
                      <code style={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={node.validatorAddress || undefined}>
                        {shortHash(node.validatorAddress || undefined, 12, 10)}
                      </code>
                    </td>
                    <td><StatusBadge status={node.status} /></td>
                    <td style={{ textAlign: 'right' }}>{node.peerCount ?? '-'}</td>
                    <td style={{ textAlign: 'right' }}>{node.blockHeight.toLocaleString('vi-VN')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="soft-card" style={{ padding: '1.25rem' }}>
          <div style={{ marginBottom: '1rem' }}>
            <h2 style={{ fontSize: '1.15rem', fontWeight: 650, color: 'var(--text-primary)', margin: 0 }}>
              Transaction văn bản công chứng
            </h2>
            <p className="muted-text" style={{ marginTop: '0.25rem' }}>
              Mỗi dòng được tạo sau khi văn bản công chứng cuối cùng đã được ký đủ và có file hash.
            </p>
          </div>

          {transactions.length === 0 ? (
            <div style={{ border: '1px dashed #cbd5e1', borderRadius: '8px', padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
              Chưa có transaction blockchain nào được ghi nhận.
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table className="data-table" style={{ width: '100%', minWidth: '980px' }}>
                <thead>
                  <tr>
                    <th>Thời gian</th>
                    <th>Hồ sơ</th>
                    <th>Document hash</th>
                    <th>Transaction hash</th>
                    <th>Block</th>
                    <th>Node</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {transactions.map((transaction) => (
                    <tr key={transaction.transactionId}>
                      <td>{formatDate(transaction.createdAt)}</td>
                      <td>
                        <div style={{ fontWeight: 650 }}>{transaction.requestCode}</div>
                        <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{shortHash(transaction.requestId, 8, 6)}</div>
                      </td>
                      <td><code title={transaction.documentHash}>{shortHash(transaction.documentHash)}</code></td>
                      <td><code title={transaction.transactionHash}>{shortHash(transaction.transactionHash, 14, 12)}</code></td>
                      <td>{transaction.blockNumber.toLocaleString('vi-VN')}</td>
                      <td>{transaction.nodeName}</td>
                      <td><StatusBadge status={transaction.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </DashboardLayout>
  );
}

function SummaryCard({ label, value, accent }: { label: string; value: string; accent: string }) {
  return (
    <div className="soft-card" style={{ padding: '1.1rem', borderLeft: `4px solid ${accent}` }}>
      <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', textTransform: 'uppercase', fontWeight: 750, marginBottom: '0.45rem' }}>
        {label}
      </div>
      <div style={{ fontSize: '1.35rem', color: 'var(--text-primary)', fontWeight: 750, wordBreak: 'break-word' }}>
        {value}
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const active = status === 'ACTIVE' || status === 'CONFIRMED';
  return (
    <span style={{ display: 'inline-flex', padding: '0.25rem 0.55rem', borderRadius: '999px', fontSize: '0.78rem', fontWeight: 750, background: active ? '#dcfce7' : '#fee2e2', color: active ? '#166534' : '#991b1b' }}>
      {status}
    </span>
  );
}
