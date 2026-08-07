import { useEffect, useMemo, useRef, useState } from 'react';
import type { MouseEvent as ReactMouseEvent, PointerEvent as ReactPointerEvent } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  endVideoSessionApi,
  joinVideoRoomApi,
  saveVideoEvidenceApi,
  signVideoDocumentApi,
  verifyVideoTokenApi,
} from '../../features/requests/requestApi';
import { getAuthSession } from '../../lib/authStorage';
import { toApiErrorMessage } from '../../lib/apiError';
import { VIDEO_SIGNALING_WS_URL } from '../../lib/config';
import { getDefaultRouteByRole } from '../../lib/roleRedirect';
import type { VideoSessionResponse } from '../../types/video';

type SetupStage =
  | 'INIT'
  | 'VERIFY_TOKEN'
  | 'JOIN_ROOM'
  | 'GET_MEDIA'
  | 'CONNECT_SIGNALING'
  | 'WAIT_PEER'
  | 'CONNECTED';

interface SignalingMessage {
  type:
    | 'JOIN'
    | 'JOINED'
    | 'READY'
    | 'OFFER'
    | 'ANSWER'
    | 'ICE'
    | 'LEAVE'
    | 'PEER_LEFT'
    | 'ERROR'
    | 'END'
    | 'SESSION_CONTROL'
    | 'DOCUMENT_PRESENTATION'
    | 'CLIENT_CONSENT'
    | 'SIGNATURE_COMPLETED'
    | 'EVIDENCE_CAPTURED'
    | 'RECORDING_STATE';
  roomId?: string;
  token?: string;
  authToken?: string;
  payload?: unknown;
  sender?: string;
  message?: string;
}

interface DocumentPresentation {
  documentId?: string;
  title: string;
  version?: string;
  fileUrl?: string;
  viewUrl?: string;
  downloadUrl?: string;
  contentType?: string | null;
  updatedAt?: string | null;
  content?: string;
  presenter: string;
  startedAt: string;
}

interface EvidenceSnapshot {
  id: string;
  dataUrl: string;
  capturedAt: string;
}

interface SignaturePlacement {
  pageNumber: number;
  xPercent: number;
  yPercent: number;
  widthPercent: number;
  heightPercent: number;
}

const DEFAULT_PRESENTATION: DocumentPresentation = {
  title: 'Bản dự thảo hợp đồng công chứng',
  version: 'Bản trình chiếu cuối cùng',
  presenter: '',
  startedAt: '',
  content:
    '1. Các bên đã kiểm tra thông tin nhân thân, giấy tờ liên quan và năng lực hành vi dân sự.\n\n' +
    '2. Nội dung giao dịch được đọc lại trong phiên công chứng trực tuyến để người dân tự rà soát trước khi xác nhận.\n\n' +
    '3. Người dân chỉ bấm xác nhận đồng ý sau khi đã hiểu đầy đủ quyền, nghĩa vụ và hậu quả pháp lý của văn bản.\n\n' +
    '4. Toàn bộ phiên làm việc được hệ thống ghi hình, ghi âm và lưu lại làm căn cứ đối chiếu.',
};

const ICE_SERVERS: RTCConfiguration = {
  iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
};

function parseIceCandidatePayload(payload: unknown): RTCIceCandidateInit | null {
  if (!payload || typeof payload !== 'object') {
    return null;
  }

  const candidate = payload as Partial<RTCIceCandidateInit>;
  if (!candidate.candidate) {
    return null;
  }

  return {
    candidate: candidate.candidate,
    sdpMid: candidate.sdpMid ?? null,
    sdpMLineIndex: candidate.sdpMLineIndex ?? null,
    usernameFragment: candidate.usernameFragment ?? null,
  };
}

function streamHasVideo(stream: MediaStream | null): boolean {
  return !!stream && stream.getVideoTracks().some((track) => track.enabled);
}

function streamHasAudio(stream: MediaStream | null): boolean {
  return !!stream && stream.getAudioTracks().some((track) => track.enabled);
}

// Module-level reference to the active stream — outside React lifecycle.
// This guarantees track.stop() can always be called regardless of component state.
let _activeMediaStream: MediaStream | null = null;

function stopAllMediaTracks(): void {
  if (_activeMediaStream) {
    _activeMediaStream.getTracks().forEach((track) => track.stop());
    _activeMediaStream = null;
  }
}

function attachStreamToVideo(video: HTMLVideoElement | null, stream: MediaStream | null, muted = false): void {
  if (!video || !stream) {
    return;
  }

  if (video.srcObject !== stream) {
    video.srcObject = stream;
  }
  video.muted = muted;
  video.playsInline = true;

  const playPromise = video.play();
  if (playPromise) {
    playPromise.catch(() => {
      // Mobile browsers can defer playback until the page is visible/focused again.
    });
  }
}

function buildDocumentFileUrl(documentId: string | undefined, action: 'view' | 'download'): string {
  if (!documentId) {
    return '';
  }

  return action === 'view' ? `/api/documents/${documentId}/view` : `/api/documents/${documentId}`;
}

function buildDocumentPageImageUrl(documentId: string | undefined, pageNumber: number, version?: string | null): string {
  if (!documentId) {
    return '';
  }

  const params = version ? `?v=${encodeURIComponent(version)}` : '';
  return `/api/documents/${documentId}/pages/${pageNumber}/image${params}`;
}

function isPdfPresentation(presentation: DocumentPresentation): boolean {
  const contentType = presentation.contentType?.toLowerCase() ?? '';
  const source = `${presentation.fileUrl ?? ''} ${presentation.viewUrl ?? ''} ${presentation.downloadUrl ?? ''} ${presentation.title ?? ''}`.toLowerCase();
  return contentType.includes('pdf') || source.includes('.pdf');
}

function canPreviewInline(presentation: DocumentPresentation): boolean {
  const contentType = presentation.contentType?.toLowerCase() ?? '';
  const source = (presentation.fileUrl || presentation.viewUrl || presentation.title || '').toLowerCase();
  return contentType.startsWith('application/pdf')
    || contentType.startsWith('image/')
    || source.endsWith('.pdf')
    || source.endsWith('.png')
    || source.endsWith('.jpg')
    || source.endsWith('.jpeg');
}

function ContractPresentationViewer({
  active,
  compact = false,
  presentation,
}: {
  active: boolean;
  compact?: boolean;
  presentation: DocumentPresentation;
}) {
  const canPreview = Boolean(active && presentation.viewUrl && canPreviewInline(presentation));

  return (
    <div className={`document-viewer document-file-viewer ${active ? 'active' : ''} ${compact ? 'compact' : ''}`}>
      <div className="document-viewer-head">
        <div>
          <b>{presentation.title}</b>
          <span>{presentation.version ? `Phiên bản ${presentation.version}` : 'Dự thảo hợp đồng'}</span>
        </div>
        {presentation.downloadUrl ? (
          <a href={presentation.downloadUrl} target="_blank" rel="noreferrer" className="document-open-link">
            Mở file
          </a>
        ) : null}
      </div>

      {canPreview ? (
        <iframe src={presentation.viewUrl} title={presentation.title} />
      ) : active && presentation.viewUrl ? (
        <div className="document-placeholder">
          File mẫu văn bản này không hỗ trợ xem trực tiếp trong trình duyệt. Hãy mở file để đối chiếu nội dung khi trình chiếu.
        </div>
      ) : active && presentation.content ? (
        <pre>{presentation.content}</pre>
      ) : (
        <div className="document-placeholder">Công chứng viên chưa trình chiếu dự thảo hợp đồng.</div>
      )}
    </div>
  );
}

export function VideoRoomPage() {
  const { roomId = '' } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const authSession = useMemo(() => getAuthSession(), []);
  const token = searchParams.get('token') ?? '';

  const [sessionInfo, setSessionInfo] = useState<VideoSessionResponse | null>(null);
  const [statusText, setStatusText] = useState('Đang chuẩn bị phòng họp...');
  const [error, setError] = useState('');
  const [setupStage, setSetupStage] = useState<SetupStage>('INIT');
  const [debugDetails, setDebugDetails] = useState<string>('');
  const [busy, setBusy] = useState(true);
  const [peerConnected, setPeerConnected] = useState(false);
  const [isEnding, setIsEnding] = useState(false);
  const [cameraOn, setCameraOn] = useState(true);
  const [micOn, setMicOn] = useState(true);
  const [showSignModal, setShowSignModal] = useState(false);
  const [signatureData, setSignatureData] = useState<string | null>(null);
  const [signatureTouched, setSignatureTouched] = useState(false);
  const [placementPreviewError, setPlacementPreviewError] = useState('');
  const [signaturePlacement, setSignaturePlacement] = useState<SignaturePlacement>({
    pageNumber: 1,
    xPercent: 58,
    yPercent: 72,
    widthPercent: 28,
    heightPercent: 10,
  });
  const [isSigning, setIsSigning] = useState(false);
  const [sessionStarted, setSessionStarted] = useState(false);
  const [recordingActive, setRecordingActive] = useState(true);
  const [presentationActive, setPresentationActive] = useState(false);
  const [presentation, setPresentation] = useState<DocumentPresentation>(DEFAULT_PRESENTATION);
  const [clientConsentAt, setClientConsentAt] = useState<string | null>(null);
  const [clientSignedAt, setClientSignedAt] = useState<string | null>(null);
  const [notarySignedAt, setNotarySignedAt] = useState<string | null>(null);
  const [evidenceSnapshots, setEvidenceSnapshots] = useState<EvidenceSnapshot[]>([]);
  const [evidenceSaving, setEvidenceSaving] = useState(false);

  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);
  const localStreamRef = useRef<MediaStream | null>(null);
  const signatureCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const signatureDrawingRef = useRef(false);
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const hasCreatedOfferRef = useRef(false);
  const disposedRef = useRef(false);

  const canEndSession = authSession?.role?.toUpperCase() === 'NOTARY';
  const isNotary = authSession?.role?.toUpperCase() === 'NOTARY';
  const isClient = authSession?.role?.toUpperCase() === 'CLIENT';
  const draftDocument = sessionInfo?.draftDocument ?? null;
  const requiresTemplate = sessionInfo?.requiresTemplate !== false;
  const sessionLayoutClass = `video-session-layout ${presentationActive ? 'presenting' : 'standalone'}`;
  const canClientSign = requiresTemplate && isClient && presentationActive && !!clientConsentAt && !clientSignedAt;
  const canNotarySign = isNotary && !!clientConsentAt && (!requiresTemplate || !!clientSignedAt) && !notarySignedAt;
  const signaturePageImageUrl = isPdfPresentation(presentation)
    ? buildDocumentPageImageUrl(presentation.documentId, signaturePlacement.pageNumber, presentation.updatedAt)
    : '';

  useEffect(() => {
    if (!showSignModal) {
      return;
    }

    setPlacementPreviewError('');

    const canvas = signatureCanvasRef.current;
    if (!canvas) {
      return;
    }

    const ratio = window.devicePixelRatio || 1;
    const width = 640;
    const height = 220;
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    canvas.style.width = '100%';
    canvas.style.height = '220px';

    const context = canvas.getContext('2d');
    if (!context) {
      return;
    }

    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, width, height);
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, width, height);
    context.lineCap = 'round';
    context.lineJoin = 'round';
    context.lineWidth = 3;
    context.strokeStyle = '#0f172a';
    setSignatureTouched(false);
    setSignatureData(null);
  }, [showSignModal]);

  useEffect(() => {
    setPlacementPreviewError('');
  }, [signaturePageImageUrl]);

  // Register beforeunload as a last-resort camera/mic release.
  // Do not stop on pagehide: Chrome mobile fires it when users briefly switch apps.
  useEffect(() => {
    const handlePageExit = () => stopAllMediaTracks();
    window.addEventListener('beforeunload', handlePageExit);
    return () => {
      window.removeEventListener('beforeunload', handlePageExit);
    };
  }, []);

  useEffect(() => {
    const resumeVideoElements = () => {
      if (document.visibilityState !== 'visible') {
        return;
      }
      attachStreamToVideo(localVideoRef.current, localStreamRef.current ?? _activeMediaStream, true);
      const remoteStream = remoteVideoRef.current?.srcObject instanceof MediaStream
        ? remoteVideoRef.current.srcObject
        : null;
      attachStreamToVideo(remoteVideoRef.current, remoteStream, false);
    };

    window.addEventListener('focus', resumeVideoElements);
    document.addEventListener('visibilitychange', resumeVideoElements);

    return () => {
      window.removeEventListener('focus', resumeVideoElements);
      document.removeEventListener('visibilitychange', resumeVideoElements);
    };
  }, []);

  const formatSetupError = (stage: SetupStage, rawError: unknown): { message: string; details: string } => {
    const baseMessage = toApiErrorMessage(rawError, 'Không thể khởi tạo video call.');

    const stageLabel: Record<SetupStage, string> = {
      INIT: 'Khởi tạo',
      VERIFY_TOKEN: 'Xác thực token phòng họp',
      JOIN_ROOM: 'Ghi nhận tham gia phòng (API join)',
      GET_MEDIA: 'Bật camera/micro (getUserMedia)',
      CONNECT_SIGNALING: 'Kết nối signaling (WebSocket)',
      WAIT_PEER: 'Bắt tay WebRTC (offer/answer/ice)',
      CONNECTED: 'Đã kết nối',
    };

    const maybeAxios = rawError as {
      isAxiosError?: boolean;
      response?: { status?: number; data?: unknown };
      config?: { url?: string; method?: string; baseURL?: string };
      message?: string;
    };

    const maybeDom = rawError as { name?: string; message?: string };

    const extraLines: string[] = [];
    extraLines.push(`stage=${stage} (${stageLabel[stage]})`);
    extraLines.push(`roomId=${roomId}`);
    extraLines.push(`wsUrl=${VIDEO_SIGNALING_WS_URL}`);
    extraLines.push(`role=${authSession?.role ?? 'unknown'} email=${authSession?.email ?? 'unknown'}`);

    if (maybeAxios?.isAxiosError) {
      extraLines.push(`axiosStatus=${maybeAxios.response?.status ?? 'unknown'}`);
      extraLines.push(`axiosRequest=${maybeAxios.config?.method ?? ''} ${maybeAxios.config?.baseURL ?? ''}${maybeAxios.config?.url ?? ''}`);
    }

    if (maybeDom?.name) {
      extraLines.push(`errorName=${maybeDom.name}`);
    }

    let friendly = baseMessage;

    if (stage === 'GET_MEDIA') {
      const name = maybeDom?.name;
      if (name === 'NotAllowedError' || name === 'SecurityError') {
        friendly = 'Bạn đã chặn quyền camera/micro. Hãy cấp quyền rồi tải lại trang.';
      } else if (name === 'NotFoundError') {
        friendly = 'Không tìm thấy thiết bị camera hoặc micro. Hãy kiểm tra thiết bị rồi thử lại.';
      } else if (name === 'NotReadableError') {
        friendly = 'Không thể truy cập camera/micro (có thể đang bị app khác sử dụng). Hãy đóng app khác rồi thử lại.';
      } else if (name === 'InsecureContextError') {
        friendly =
          'Trên iPhone/iPad, camera & micro chỉ hoạt động khi mở trang bằng HTTPS. ' +
          'Bạn đang mở bằng HTTP (LAN IP), nên Safari sẽ chặn getUserMedia.';
      } else if (name === 'MediaDevicesUnsupportedError') {
        friendly = 'Trình duyệt không hỗ trợ camera/micro (navigator.mediaDevices.getUserMedia không khả dụng).';
      } else if (name === 'TypeError') {
        if (!window.isSecureContext) {
          friendly =
            'Trên iPhone/iPad, camera & micro chỉ hoạt động khi mở trang bằng HTTPS. ' +
            'Bạn đang mở bằng HTTP (LAN IP), nên Safari sẽ chặn getUserMedia.';
        }
      }
    }

    friendly = `${friendly} (Bước lỗi: ${stageLabel[stage]})`;
    return { message: friendly, details: extraLines.join('\n') };
  };

  const sendSignal = (message: SignalingMessage) => {
    const ws = wsRef.current;
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      return;
    }

    ws.send(JSON.stringify(message));
  };

  const buildPresentationPayload = (): DocumentPresentation => ({
    ...DEFAULT_PRESENTATION,
    presenter: authSession?.email ?? 'Công chứng viên',
    startedAt: new Date().toISOString(),
  });

  const buildDraftPresentationPayload = (): DocumentPresentation | null => {
    if (!draftDocument) {
      return buildPresentationPayload();
    }

    if (!draftDocument.documentId) {
      setError('Mẫu văn bản của yêu cầu này thiếu mã file. Vui lòng tải lại file trước khi trình chiếu.');
      return null;
    }

    return {
      documentId: draftDocument.documentId,
      title: draftDocument.displayName || draftDocument.originalFileName || 'Mẫu văn bản hồ sơ',
      version: '',
      fileUrl: draftDocument.filePath,
      viewUrl: buildDocumentFileUrl(draftDocument.documentId, 'view'),
      downloadUrl: buildDocumentFileUrl(draftDocument.documentId, 'download'),
      contentType: draftDocument.contentType,
      updatedAt: draftDocument.updatedAt,
      presenter: authSession?.email ?? 'Công chứng viên',
      startedAt: new Date().toISOString(),
    };
  };

  const applyPresentationPayload = (payload: unknown) => {
    if (!payload || typeof payload !== 'object') {
      return;
    }

    const next = payload as Partial<DocumentPresentation> & { active?: boolean };
    if (typeof next.active === 'boolean') {
      setPresentationActive(next.active);
    }

    setPresentation({
      documentId: next.documentId,
      title: next.title || DEFAULT_PRESENTATION.title,
      version: next.version || DEFAULT_PRESENTATION.version,
      fileUrl: next.fileUrl,
      viewUrl: next.viewUrl,
      downloadUrl: next.downloadUrl,
      contentType: next.contentType,
      updatedAt: next.updatedAt,
      content: next.content || DEFAULT_PRESENTATION.content,
      presenter: next.presenter || '',
      startedAt: next.startedAt || new Date().toISOString(),
    });
  };

  const closeConnections = (notifyLeave: boolean) => {
    if (notifyLeave) {
      sendSignal({ type: 'LEAVE', roomId });
    }

    const ws = wsRef.current;
    wsRef.current = null;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.close();
    }

    const peerConnection = peerConnectionRef.current;
    peerConnectionRef.current = null;
    if (peerConnection) {
      peerConnection.ontrack = null;
      peerConnection.onicecandidate = null;
      peerConnection.close();
    }

    // Stop all media tracks via module-level tracker (most reliable)
    stopAllMediaTracks();
    localStreamRef.current = null;

    if (localVideoRef.current) {
      localVideoRef.current.srcObject = null;
    }

    if (remoteVideoRef.current) {
      remoteVideoRef.current.srcObject = null;
    }

    hasCreatedOfferRef.current = false;
    setPeerConnected(false);
  };

  useEffect(() => {
    let isEffectActive = true;
    disposedRef.current = false;

    if (!roomId || !token || !authSession?.token || !authSession.email) {
      setError('Thiếu thông tin truy cập phòng họp. Vui lòng vào phòng từ giao diện lịch hẹn.');
      setBusy(false);
      return;
    }

    const setupCall = async () => {
      setBusy(true);
      setError('');
      setDebugDetails('');
      let stage: SetupStage = 'INIT';
      const updateStage = (next: SetupStage) => {
        stage = next;
        setSetupStage(next);
      };

      updateStage('INIT');
      const pendingIceCandidates: RTCIceCandidateInit[] = [];
      try {
        updateStage('VERIFY_TOKEN');
        setStatusText('Đang xác thực phiên họp...');
        await verifyVideoTokenApi(token);

        updateStage('JOIN_ROOM');
        setStatusText('Đang ghi nhận tham gia phòng...');
        const joinedSession = await joinVideoRoomApi(roomId, token);
        setSessionInfo(joinedSession);

        updateStage('GET_MEDIA');
        setStatusText('Đang bật camera và micro...');

        if (!window.isSecureContext) {
          const insecureError = new Error(
            'getUserMedia requires a secure context (HTTPS) on iOS Safari when using a LAN IP.',
          ) as Error & { name: string };
          insecureError.name = 'InsecureContextError';
          throw insecureError;
        }

        if (!navigator.mediaDevices?.getUserMedia) {
          const unsupported = new Error('navigator.mediaDevices.getUserMedia is not available.') as Error & {
            name: string;
          };
          unsupported.name = 'MediaDevicesUnsupportedError';
          throw unsupported;
        }

        const localStream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: 'user' },
          audio: true,
        });

        // If the component was unmounted (or this specific effect was cleaned up)
        // while we were awaiting getUserMedia, stop all tracks immediately.
        if (!isEffectActive || disposedRef.current) {
          localStream.getTracks().forEach((track) => track.stop());
          return;
        }

        // Extremely safe fallback: if we somehow already have a stream, stop it before overwriting
        if (_activeMediaStream) {
          _activeMediaStream.getTracks().forEach((t) => t.stop());
        }
        if (localStreamRef.current) {
          localStreamRef.current.getTracks().forEach((t) => t.stop());
        }

        localStreamRef.current = localStream;
        _activeMediaStream = localStream; // module-level tracker for guaranteed cleanup
        setCameraOn(streamHasVideo(localStream));
        setMicOn(streamHasAudio(localStream));

        attachStreamToVideo(localVideoRef.current, localStream, true);

        const createPeerConnection = (): RTCPeerConnection => {
          const oldPc = peerConnectionRef.current;
          if (oldPc) {
            oldPc.ontrack = null;
            oldPc.onicecandidate = null;
            oldPc.close();
          }

          const pc = new RTCPeerConnection(ICE_SERVERS);
          peerConnectionRef.current = pc;

          localStream.getTracks().forEach((track) => {
            pc.addTrack(track, localStream);
          });

          pc.ontrack = (event) => {
            const [remoteStream] = event.streams;
            if (remoteVideoRef.current && remoteStream) {
              attachStreamToVideo(remoteVideoRef.current, remoteStream, false);
              setPeerConnected(true);
              setStatusText('Đã kết nối với đối tác.');
            }
          };

          pc.onicecandidate = (event) => {
            if (!event.candidate) {
              return;
            }

            sendSignal({
              type: 'ICE',
              roomId,
              payload: event.candidate.toJSON(),
            });
          };

          return pc;
        };

        createPeerConnection();

        updateStage('CONNECT_SIGNALING');
        setStatusText('Đang kết nối signaling...');
        const ws = new WebSocket(VIDEO_SIGNALING_WS_URL);
        wsRef.current = ws;

        ws.onopen = () => {
          sendSignal({
            type: 'JOIN',
            roomId,
            token,
            authToken: authSession.token,
          });
          updateStage('WAIT_PEER');
          setStatusText('Đang chờ đối tác vào phòng...');
        };

        ws.onmessage = async (event) => {
          let message: SignalingMessage;
          try {
            message = JSON.parse(event.data) as SignalingMessage;
          } catch (parseError) {
            // eslint-disable-next-line no-console
            console.error('[VideoCall] Failed to parse signaling message', parseError, event.data);
            return;
          }

          if (message.type === 'ERROR') {
            setError(message.message || 'Không thể thiết lập kết nối video.');
            return;
          }

          if (message.type === 'END') {
            setStatusText(message.message || 'Phiên công chứng đã được kết thúc.');
            stopAllMediaTracks();
            localStreamRef.current = null;
            disposedRef.current = true;
            closeConnections(false);
            navigate(getDefaultRouteByRole(authSession?.role), { replace: true });
            return;
          }

          if (message.type === 'SESSION_CONTROL') {
            const payload = message.payload as { action?: string } | undefined;
            setSessionStarted(payload?.action === 'START');
            setStatusText(payload?.action === 'START' ? 'Phiên công chứng đã bắt đầu.' : 'Phiên công chứng đã tạm dừng.');
            return;
          }

          if (message.type === 'DOCUMENT_PRESENTATION') {
            applyPresentationPayload(message.payload);
            setClientConsentAt(null);
            setClientSignedAt(null);
            setNotarySignedAt(null);
            return;
          }

          if (message.type === 'CLIENT_CONSENT') {
            const payload = message.payload as { confirmedAt?: string } | undefined;
            setClientConsentAt(payload?.confirmedAt || new Date().toISOString());
            return;
          }

          if (message.type === 'SIGNATURE_COMPLETED') {
            const payload = message.payload as {
              role?: string;
              signedAt?: string;
              completed?: boolean;
              requestStatus?: string;
            } | undefined;
            const signedAt = payload?.signedAt || new Date().toISOString();
            if (payload?.role === 'CLIENT') {
              setClientSignedAt(signedAt);
            } else if (payload?.role === 'NOTARY') {
              setNotarySignedAt(signedAt);
            }
            if (payload?.completed) {
              setStatusText('Hai bên đã ký số. Hồ sơ đã chuyển sang chờ thanh toán.');
            }
            return;
          }

          if (message.type === 'EVIDENCE_CAPTURED') {
            setStatusText('Công chứng viên đã chụp ảnh bằng chứng đối chiếu.');
            return;
          }

          if (message.type === 'RECORDING_STATE') {
            const payload = message.payload as { active?: boolean } | undefined;
            setRecordingActive(payload?.active !== false);
            return;
          }

          // ── PEER_LEFT ─────────────────────────────────────────────────────
          if (message.type === 'PEER_LEFT') {
            setPeerConnected(false);
            hasCreatedOfferRef.current = false;
            pendingIceCandidates.length = 0;
            if (remoteVideoRef.current) {
              remoteVideoRef.current.srcObject = null;
            }
            const oldPc = peerConnectionRef.current;
            if (oldPc) {
              oldPc.ontrack = null;
              oldPc.onicecandidate = null;
              oldPc.close();
              peerConnectionRef.current = null;
            }
            setStatusText('Đối tác đã rời phòng. Đang chờ kết nối lại...');
            return;
          }

          // ── READY: cả 2 đã vào phòng, bắt đầu negotiate ──────────────────
          if (message.type === 'READY') {
            const offererEmail = (message.payload as { offererEmail?: string } | undefined)?.offererEmail;
            const iAmOfferer = !!offererEmail && offererEmail.toLowerCase() === authSession.email.toLowerCase();

            hasCreatedOfferRef.current = false;
            pendingIceCandidates.length = 0;
            const freshPc = createPeerConnection();

            if (iAmOfferer) {
              hasCreatedOfferRef.current = true;
              const offer = await freshPc.createOffer();
              await freshPc.setLocalDescription(offer);
              sendSignal({ type: 'OFFER', roomId, payload: offer });
              setStatusText('Đang gửi đề nghị kết nối...');
            } else {
              setStatusText('Đang chờ đề nghị kết nối từ đối tác...');
            }
            return;
          }

          // Các message còn lại yêu cầu PC đang hoạt động
          const activePc = peerConnectionRef.current;
          if (!activePc) {
            return;
          }

          // ── OFFER: chúng ta là answerer, dùng PC được tạo ở READY ─────────
          if (message.type === 'OFFER') {
            const offer = message.payload as RTCSessionDescriptionInit;
            await activePc.setRemoteDescription(new RTCSessionDescription(offer));

            // Flush ICE candidates đã buffer trước khi nhận OFFER
            for (const buffered of pendingIceCandidates) {
              await activePc.addIceCandidate(new RTCIceCandidate(buffered));
            }
            pendingIceCandidates.length = 0;

            const answer = await activePc.createAnswer();
            await activePc.setLocalDescription(answer);
            sendSignal({ type: 'ANSWER', roomId, payload: answer });
            setStatusText('Đang hoàn tất bắt tay WebRTC...');
            return;
          }

          // ── ANSWER: chúng ta là offerer, apply answer ─────────────────────
          if (message.type === 'ANSWER') {
            if (activePc.signalingState !== 'have-local-offer') {
              return;
            }
            const answer = message.payload as RTCSessionDescriptionInit;
            await activePc.setRemoteDescription(new RTCSessionDescription(answer));

            // Flush ICE candidates đã buffer trước khi nhận ANSWER
            for (const buffered of pendingIceCandidates) {
              await activePc.addIceCandidate(new RTCIceCandidate(buffered));
            }
            pendingIceCandidates.length = 0;
            return;
          }

          // ── ICE: buffer cho đến khi remote description được set ───────────
          if (message.type === 'ICE') {
            const iceCandidate = parseIceCandidatePayload(message.payload);
            if (!iceCandidate) {
              return;
            }
            if (activePc.remoteDescription) {
              await activePc.addIceCandidate(new RTCIceCandidate(iceCandidate));
            } else {
              pendingIceCandidates.push(iceCandidate);
            }
          }
        };

        ws.onerror = () => {
          setError('Kênh signaling gặp lỗi. Vui lòng thử tải lại phòng.');
        };

        ws.onclose = () => {
          if (!disposedRef.current) {
            setStatusText('Kết nối signaling đã đóng.');
          }
        };
      } catch (setupError) {
        const formatted = formatSetupError(stage, setupError);
        // eslint-disable-next-line no-console
        console.error('[VideoCall] Setup failed', { stage, setupError });
        setError(formatted.message);
        setDebugDetails(formatted.details);
        closeConnections(false);
      } finally {
        setBusy(false);
      }
    };

    void setupCall();

    return () => {
      isEffectActive = false;
      disposedRef.current = true;
      stopAllMediaTracks();
      localStreamRef.current = null;
      closeConnections(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, token]);

  const handleToggleCamera = () => {
    const stream = localStreamRef.current;
    if (!stream) {
      return;
    }

    const tracks = stream.getVideoTracks();
    if (tracks.length === 0) {
      return;
    }

    const nextEnabled = !tracks[0].enabled;
    tracks.forEach((track) => {
      track.enabled = nextEnabled;
    });

    setCameraOn(nextEnabled);
  };

  const handleToggleMic = () => {
    const stream = localStreamRef.current;
    if (!stream) {
      return;
    }

    const tracks = stream.getAudioTracks();
    if (tracks.length === 0) {
      return;
    }

    const nextEnabled = !tracks[0].enabled;
    tracks.forEach((track) => {
      track.enabled = nextEnabled;
    });

    setMicOn(nextEnabled);
  };

  const handleLeaveRoom = () => {
    stopAllMediaTracks();
    localStreamRef.current = null;
    disposedRef.current = true;
    closeConnections(true);
    navigate(getDefaultRouteByRole(authSession?.role), { replace: true });
  };

  const handleEndSession = async () => {
    if (!sessionInfo?.sessionId) {
      handleLeaveRoom();
      return;
    }

    setIsEnding(true);
    setError('');
    try {
      await endVideoSessionApi(sessionInfo.sessionId, 'Kết thúc từ giao diện công chứng viên');
      sendSignal({ type: 'END', roomId, message: 'Phiên họp đã được kết thúc.' });
      stopAllMediaTracks();
      localStreamRef.current = null;
      disposedRef.current = true;
      closeConnections(true);
      navigate('/notary/appointments', { replace: true });
    } catch (endError) {
      setError(toApiErrorMessage(endError, 'Không thể kết thúc phiên họp.'));
    } finally {
      setIsEnding(false);
    }
  };

  const handleStartSession = () => {
    setSessionStarted(true);
    setRecordingActive(true);
    sendSignal({
      type: 'SESSION_CONTROL',
      roomId,
      payload: { action: 'START', startedAt: new Date().toISOString() },
    });
    sendSignal({
      type: 'RECORDING_STATE',
      roomId,
      payload: { active: true, updatedAt: new Date().toISOString() },
    });
    setStatusText('Phiên công chứng đã bắt đầu.');
  };

  const handleTogglePresentation = () => {
    if (presentationActive) {
      setPresentationActive(false);
      sendSignal({
        type: 'DOCUMENT_PRESENTATION',
        roomId,
        payload: { active: false, title: presentation.title, version: presentation.version },
      });
      return;
    }

    if (!draftDocument) {
      setError('Yêu cầu này chưa có file mẫu văn bản. Công chứng viên cần tải file trước khi lên lịch hẹn.');
      return;
    }

    const nextPresentation = buildDraftPresentationPayload();
    if (!nextPresentation) {
      return;
    }

    setPresentation(nextPresentation);
    setPresentationActive(true);
    setClientConsentAt(null);
    setClientSignedAt(null);
    setNotarySignedAt(null);
    sendSignal({
      type: 'DOCUMENT_PRESENTATION',
      roomId,
      payload: { ...nextPresentation, active: true },
    });
  };

  const handleStartDocumentReview = () => {
    const nextPresentation = {
      ...buildPresentationPayload(),
      title: 'Đối soát giấy tờ chứng thực',
      version: 'Phiên đối soát trực tuyến',
      content:
        'Công chứng viên đang đối chiếu giấy tờ gốc/tài liệu nguồn với người dân qua video call.\n\n' +
        'Người dân xác nhận thông tin và tài liệu được đối chiếu là đúng trước khi công chứng viên ký số chứng thực.',
    };
    setPresentation(nextPresentation);
    setPresentationActive(true);
    setClientConsentAt(null);
    setClientSignedAt(null);
    setNotarySignedAt(null);
    sendSignal({
      type: 'DOCUMENT_PRESENTATION',
      roomId,
      payload: { ...nextPresentation, active: true },
    });
  };

  const handleClientConsent = () => {
    const confirmedAt = new Date().toISOString();
    setClientConsentAt(confirmedAt);
    setClientSignedAt(null);
    setNotarySignedAt(null);
    sendSignal({
      type: 'CLIENT_CONSENT',
      roomId,
      payload: { confirmedAt, email: authSession?.email },
    });
  };

  const handleCaptureEvidence = async () => {
    if (!sessionInfo?.sessionId) {
      setError('Chưa có thông tin phiên để lưu ảnh bằng chứng.');
      return;
    }

    const video = remoteVideoRef.current;
    if (!video || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
      setError('Chưa có khung hình khách hàng để chụp bằng chứng.');
      return;
    }

    const width = video.videoWidth || 1280;
    const height = video.videoHeight || 720;
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) {
      setError('Không thể khởi tạo công cụ chụp ảnh bằng chứng.');
      return;
    }

    context.drawImage(video, 0, 0, width, height);
    const capturedAt = new Date().toISOString();
    const snapshot: EvidenceSnapshot = {
      id: `${capturedAt}-${evidenceSnapshots.length + 1}`,
      dataUrl: canvas.toDataURL('image/jpeg', 0.86),
      capturedAt,
    };

    setEvidenceSaving(true);
    setError('');
    try {
      await saveVideoEvidenceApi(sessionInfo.sessionId, snapshot.dataUrl);
      setEvidenceSnapshots((items) => [snapshot, ...items].slice(0, 6));
      sendSignal({
        type: 'EVIDENCE_CAPTURED',
        roomId,
        payload: { capturedAt, by: authSession?.email },
      });
    } catch (captureError) {
      setError(toApiErrorMessage(captureError, 'Không thể lưu ảnh bằng chứng vào hồ sơ.'));
    } finally {
      setEvidenceSaving(false);
    }
  };

  const getCanvasPoint = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const canvas = signatureCanvasRef.current;
    if (!canvas) {
      return { x: 0, y: 0 };
    }

    const rect = canvas.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    const logicalWidth = canvas.width / ratio;
    const logicalHeight = canvas.height / ratio;
    return {
      x: (event.clientX - rect.left) * (logicalWidth / rect.width),
      y: (event.clientY - rect.top) * (logicalHeight / rect.height),
    };
  };

  const handleSignaturePointerDown = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const canvas = signatureCanvasRef.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context || isSigning) {
      return;
    }

    const point = getCanvasPoint(event);
    signatureDrawingRef.current = true;
    canvas.setPointerCapture(event.pointerId);
    context.beginPath();
    context.moveTo(point.x, point.y);
    setSignatureTouched(true);
  };

  const handleSignaturePointerMove = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    if (!signatureDrawingRef.current) {
      return;
    }

    const context = signatureCanvasRef.current?.getContext('2d');
    if (!context) {
      return;
    }

    const point = getCanvasPoint(event);
    context.lineTo(point.x, point.y);
    context.stroke();
  };

  const handleSignaturePointerUp = (event: ReactPointerEvent<HTMLCanvasElement>) => {
    const canvas = signatureCanvasRef.current;
    if (canvas?.hasPointerCapture(event.pointerId)) {
      canvas.releasePointerCapture(event.pointerId);
    }
    signatureDrawingRef.current = false;
  };

  const handleClearSignature = () => {
    const canvas = signatureCanvasRef.current;
    const context = canvas?.getContext('2d');
    if (!canvas || !context) {
      return;
    }

    const ratio = window.devicePixelRatio || 1;
    const logicalWidth = canvas.width / ratio;
    const logicalHeight = canvas.height / ratio;
    context.clearRect(0, 0, logicalWidth, logicalHeight);
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, logicalWidth, logicalHeight);
    setSignatureTouched(false);
    setSignatureData(null);
  };

  const handlePickSignaturePlacement = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (isSigning || !signaturePageImageUrl || placementPreviewError) {
      return;
    }

    const rect = event.currentTarget.getBoundingClientRect();
    const centerX = ((event.clientX - rect.left) / rect.width) * 100;
    const centerY = ((event.clientY - rect.top) / rect.height) * 100;
    const widthPercent = signaturePlacement.widthPercent;
    const heightPercent = signaturePlacement.heightPercent;

    setSignaturePlacement((current) => ({
      ...current,
      xPercent: Math.max(0, Math.min(100 - widthPercent, centerX - widthPercent / 2)),
      yPercent: Math.max(0, Math.min(100 - heightPercent, centerY - heightPercent / 2)),
    }));
  };

  const handleSignContract = async () => {
    const role = authSession?.role?.toUpperCase();
    if (role === 'CLIENT' && !canClientSign) {
      setError('Bạn cần xác nhận đồng ý văn bản trước khi ký số.');
      return;
    }
    if (role === 'NOTARY' && !canNotarySign) {
      setError('Chỉ hiển thị ký số cho công chứng viên sau khi người dân đã ký.');
      return;
    }
    if (!sessionInfo?.sessionId) {
      setError('Chưa có thông tin phiên để ký số.');
      return;
    }
    if (!presentation.documentId) {
      setError('Chưa có văn bản trình chiếu để ký số.');
      return;
    }
    if (!signatureTouched) {
      setError('Vui lòng vẽ chữ ký trước khi xác nhận ký.');
      return;
    }

    setIsSigning(true);
    setError('');
    try {
      const signatureValue = signatureCanvasRef.current?.toDataURL('image/png');
      if (!signatureValue) {
        setError('Không thể đọc dữ liệu chữ ký. Vui lòng ký lại.');
        return;
      }
      setSignatureData(signatureValue);

      const signResult = await signVideoDocumentApi(
        sessionInfo.sessionId,
        presentation.documentId,
        signatureValue,
        signaturePlacement,
      );

      const signedAt = new Date().toISOString();
      if (role === 'CLIENT') {
        setClientSignedAt(signedAt);
      } else if (role === 'NOTARY') {
        setNotarySignedAt(signedAt);
      }
      if (signResult.completed) {
        setStatusText('Hai bên đã ký số. Hồ sơ đã chuyển sang chờ thanh toán.');
      }
      sendSignal({
        type: 'SIGNATURE_COMPLETED',
        roomId,
        payload: {
          role,
          signedAt,
          email: authSession?.email,
          completed: signResult.completed,
          requestStatus: signResult.requestStatus,
        },
      });

      setShowSignModal(false);
      setSignatureData(null);
      setSignatureTouched(false);
    } catch (err) {
      setError(toApiErrorMessage(err, 'Không thể ký số hợp đồng.'));
    } finally {
      setIsSigning(false);
    }
  };

  return (
    <div className="video-room-page">
      <div className="video-room-shell">
        <header className="video-room-header">
          <div>
            <h1>Phòng công chứng trực tuyến</h1>
            <p>
              Room: <b>{roomId}</b>
            </p>
          </div>
          <div
            className="status-badge badge-blue"
            style={{ textTransform: 'none', letterSpacing: 0 }}
            title={`Stage: ${setupStage}`}
          >
            {peerConnected ? 'Đang kết nối 2 chiều' : statusText}
          </div>
        </header>

        {error ? (
          <div className="form-error">
            <div>{error}</div>
            {debugDetails ? (
              <details style={{ marginTop: '0.75rem' }}>
                <summary style={{ cursor: 'pointer' }}>Chi tiết kỹ thuật</summary>
                <pre style={{ marginTop: '0.75rem', whiteSpace: 'pre-wrap' }}>{debugDetails}</pre>
              </details>
            ) : null}
          </div>
        ) : null}

        <section className={sessionLayoutClass}>
          {presentationActive ? (
            <section className="video-document-panel">
              <div className="panel-header">
                <div>
                  <span className="panel-eyebrow">Văn bản trình chiếu</span>
                  <h2>{presentation.title}</h2>
                </div>
                <span className={`status-badge ${clientConsentAt ? 'badge-green' : 'badge-blue'}`}>
                  {clientConsentAt ? 'Đã xác nhận' : 'Đang trình chiếu'}
                </span>
              </div>
              <ContractPresentationViewer active={presentationActive} presentation={presentation} />
            </section>
          ) : null}
          <div className="video-media-card">
            <div className="video-media-head">
              <div>
                <span className="panel-eyebrow">{isNotary ? 'Video người dân' : 'Video công chứng viên'}</span>
                <h2>{peerConnected ? 'Đang kết nối hình ảnh' : 'Đang chờ người tham gia'}</h2>
              </div>
              <div className={`recording-pill ${recordingActive ? 'active' : ''}`}>
                <span className="recording-dot" />
                {recordingActive ? 'Ghi hình/Ghi âm' : 'Chưa ghi'}
              </div>
            </div>

            <div className="video-stage">
              <video ref={remoteVideoRef} autoPlay playsInline />
              {!peerConnected ? <div className="video-empty-state">Chưa có tín hiệu video từ đối tác</div> : null}
            </div>

            <div className="video-local-strip">
              <article className="video-local-tile">
                <div className="video-tile-head">
                  <span>Bạn</span>
                  <span>{cameraOn ? 'Camera bật' : 'Camera tắt'} · {micOn ? 'Mic bật' : 'Mic tắt'}</span>
                </div>
                <video ref={localVideoRef} autoPlay muted playsInline />
              </article>

              <div className="video-session-status">
                <span className={`session-state-dot ${sessionStarted ? 'on' : ''}`} />
                <div>
                  <b>{sessionStarted ? 'Phiên đang diễn ra' : 'Chưa bắt đầu phiên'}</b>
                  <span>{peerConnected ? 'Đường truyền hai chiều đã sẵn sàng' : statusText}</span>
                </div>
              </div>
            </div>
          </div>

          <aside className="video-work-panel">
            {isClient ? (
              <section className="presentation-card">
                <div className="panel-header">
                  <div>
                    <span className="panel-eyebrow">Người dân</span>
                    <h2>Xem văn bản trình chiếu</h2>
                  </div>
                  <span className={`status-badge ${presentationActive ? 'badge-green' : 'badge-gray'}`}>
                    {presentationActive ? 'Đang trình chiếu' : 'Đang chờ'}
                  </span>
                </div>

                {!sessionStarted ? (
                  <div className="document-placeholder">Đang chờ công chứng viên bắt đầu phiên.</div>
                ) : !presentationActive ? (
                  <div className="document-placeholder">Phiên đã bắt đầu. Vui lòng chờ công chứng viên trình chiếu văn bản.</div>
                ) : (
                  <>
                    <ContractPresentationViewer active={presentationActive} compact presentation={presentation} />

                    {!clientConsentAt ? (
                      <button type="button" className="primary-btn w-full" onClick={handleClientConsent}>
                        Xác nhận đồng ý
                      </button>
                    ) : requiresTemplate && !clientSignedAt ? (
                      <>
                        <p className="confirmation-note">Đã xác nhận đồng ý lúc {new Date(clientConsentAt).toLocaleString('vi-VN')}.</p>
                        <button type="button" className="primary-btn w-full" onClick={() => setShowSignModal(true)} disabled={isSigning}>
                          Ký số hợp đồng
                        </button>
                      </>
                    ) : !requiresTemplate ? (
                      <p className="confirmation-note">Đã xác nhận đối soát lúc {new Date(clientConsentAt).toLocaleString('vi-VN')}. Vui lòng chờ công chứng viên ký số chứng thực.</p>
                    ) : clientSignedAt && notarySignedAt ? (
                      <p className="confirmation-note">Hai bên đã ký số. Hồ sơ đã chuyển sang chờ thanh toán.</p>
                    ) : clientSignedAt ? (
                      <p className="confirmation-note">Bạn đã ký số lúc {new Date(clientSignedAt).toLocaleString('vi-VN')}. Vui lòng chờ công chứng viên hoàn tất.</p>
                    ) : (
                      <p className="confirmation-note">Vui lòng chờ công chứng viên hoàn tất.</p>
                    )}
                  </>
                )}
              </section>
            ) : null}

            {isNotary ? (
              <section className="notary-control-card">
                <div className="panel-header">
                  <div>
                    <span className="panel-eyebrow">Công chứng viên</span>
                    <h2>Điều khiển phiên</h2>
                  </div>
                  <span className={`status-badge ${clientConsentAt ? 'badge-green' : 'badge-yellow'}`}>
                    {clientConsentAt ? 'Khách đã đồng ý' : 'Chờ xác nhận'}
                  </span>
                </div>

                {requiresTemplate ? (
                  <div className={`selected-template-chip ${draftDocument ? '' : 'missing'}`}>
                    <span>Dự thảo trình chiếu</span>
                    <b>{draftDocument?.displayName || draftDocument?.originalFileName || 'Chưa có file mẫu văn bản'}</b>
                    {draftDocument ? <small>File gắn riêng với hồ sơ này</small> : null}
                  </div>
                ) : (
                  <div className="selected-template-chip">
                    <span>Luồng đối soát</span>
                    <b>Không cần mẫu văn bản</b>
                    <small>Công chứng viên đối chiếu giấy tờ và ký số chứng thực.</small>
                  </div>
                )}

                {!sessionStarted ? (
                  <div className="notary-control-grid">
                    <button type="button" className="primary-btn" onClick={handleStartSession} disabled={busy || isEnding || !peerConnected}>
                      Bắt đầu phiên
                    </button>
                  </div>
                ) : !presentationActive ? (
                  <>
                    <div className="notary-control-grid">
                      <button
                        type="button"
                        className="primary-btn"
                        onClick={requiresTemplate ? handleTogglePresentation : handleStartDocumentReview}
                        disabled={busy || (requiresTemplate && !draftDocument)}
                      >
                        {requiresTemplate ? 'Trình chiếu văn bản' : 'Bắt đầu đối soát giấy tờ'}
                      </button>
                    </div>
                    <div className="document-placeholder">
                      {requiresTemplate
                        ? 'Sau khi bắt đầu phiên, hãy trình chiếu văn bản để người dân đối soát và xác nhận.'
                        : 'Sau khi bắt đầu phiên, hãy bắt đầu đối soát giấy tờ để người dân xác nhận trước khi ký số chứng thực.'}
                    </div>
                  </>
                ) : !clientConsentAt ? (
                  <>
                    <div className="notary-control-grid">
                      <button type="button" className="link-btn" onClick={handleTogglePresentation} disabled={busy}>
                        Dừng trình chiếu
                      </button>
                      <button type="button" className="link-btn" onClick={() => void handleCaptureEvidence()} disabled={busy || !peerConnected || evidenceSaving}>
                        {evidenceSaving ? 'Đang lưu ảnh...' : 'Chụp ảnh bằng chứng'}
                      </button>
                    </div>
                    <ContractPresentationViewer active={presentationActive} compact presentation={presentation} />
                  </>
                ) : requiresTemplate && !clientSignedAt ? (
                  <>
                    <p className="confirmation-note">Người dân đã xác nhận đồng ý lúc {new Date(clientConsentAt).toLocaleString('vi-VN')}. Đang chờ người dân ký số.</p>
                    <ContractPresentationViewer active={presentationActive} compact presentation={presentation} />
                  </>
                ) : !notarySignedAt ? (
                  <>
                    <p className="confirmation-note">
                      {requiresTemplate && clientSignedAt
                        ? `Người dân đã ký số lúc ${new Date(clientSignedAt).toLocaleString('vi-VN')}.`
                        : `Người dân đã xác nhận đối soát lúc ${new Date(clientConsentAt).toLocaleString('vi-VN')}.`}
                    </p>
                    <div className="notary-control-grid">
                      <button type="button" className="primary-btn" onClick={() => setShowSignModal(true)} disabled={busy || isSigning}>
                        {requiresTemplate ? 'Ký số hợp đồng' : 'Ký số chứng thực'}
                      </button>
                    </div>
                  </>
                ) : (
                  <p className="confirmation-note">Công chứng viên đã ký số lúc {new Date(notarySignedAt).toLocaleString('vi-VN')}. Hồ sơ đã chuyển sang chờ thanh toán.</p>
                )}

                {sessionStarted && presentationActive ? (
                  <div className="evidence-panel">
                    <div className="evidence-panel-head">
                      <b>Bằng chứng đối chiếu</b>
                      <span>{evidenceSnapshots.length} ảnh</span>
                    </div>
                    {evidenceSnapshots.length === 0 ? (
                      <p>Chưa có ảnh chụp CCCD/Hộ chiếu trong phiên.</p>
                    ) : (
                      <div className="evidence-grid">
                        {evidenceSnapshots.map((snapshot) => (
                          <figure key={snapshot.id}>
                            <img src={snapshot.dataUrl} alt="Ảnh bằng chứng đối chiếu" />
                            <figcaption>{new Date(snapshot.capturedAt).toLocaleTimeString('vi-VN')}</figcaption>
                          </figure>
                        ))}
                      </div>
                    )}
                  </div>
                ) : null}
              </section>
            ) : null}
          </aside>
        </section>

        <footer className="video-room-controls">
          <button type="button" className="link-btn" onClick={handleToggleCamera} disabled={busy || !localStreamRef.current}>
            {cameraOn ? 'Tắt camera' : 'Bật camera'}
          </button>
          <button type="button" className="link-btn" onClick={handleToggleMic} disabled={busy || !localStreamRef.current}>
            {micOn ? 'Tắt mic' : 'Bật mic'}
          </button>
          {canEndSession && sessionStarted ? (
            <button type="button" className="ghost-btn danger" onClick={handleEndSession} disabled={busy || isEnding}>
              {isEnding ? 'Đang kết thúc...' : 'Đóng cầu truyền hình'}
            </button>
          ) : null}
          <button type="button" className="ghost-btn" onClick={handleLeaveRoom} disabled={busy || isEnding}>
            Rời phòng
          </button>
        </footer>

        {showSignModal ? (
          <div className="signature-modal-backdrop">
            <section className="signature-modal" role="dialog" aria-modal="true" aria-labelledby="signature-modal-title">
              <div className="signature-modal-head">
                <div>
                  <span className="panel-eyebrow">{isClient ? 'Người dân ký' : 'Công chứng viên ký'}</span>
                  <h2 id="signature-modal-title">{requiresTemplate ? 'Ký lên văn bản công chứng' : 'Ký số chứng thực'}</h2>
                </div>
                <button
                  type="button"
                  className="ghost-btn"
                  onClick={() => {
                    setShowSignModal(false);
                    setSignatureData(null);
                    setSignatureTouched(false);
                    setPlacementPreviewError('');
                  }}
                  disabled={isSigning}
                >
                  Đóng
                </button>
              </div>

              <div className="signature-modal-body">
                <div className="signature-pad-panel">
                  <div className="signature-section-head">
                    <div>
                      <h3>Vẽ chữ ký</h3>
                      <p>Ký trực tiếp trong khung bên dưới.</p>
                    </div>
                    <button type="button" className="ghost-btn" onClick={handleClearSignature} disabled={isSigning}>
                      Xóa
                    </button>
                  </div>
                  <canvas
                    ref={signatureCanvasRef}
                    className="signature-pad-canvas"
                    onPointerDown={handleSignaturePointerDown}
                    onPointerMove={handleSignaturePointerMove}
                    onPointerUp={handleSignaturePointerUp}
                    onPointerCancel={handleSignaturePointerUp}
                  />
                </div>

                <div className="signature-placement-panel">
                  <div className="signature-section-head">
                    <div>
                      <h3>Chọn vị trí trên PDF</h3>
                      <p>Bấm vào đúng trang tài liệu để đặt vùng chữ ký.</p>
                    </div>
                    <label className="signature-page-input">
                      Trang
                      <input
                        type="number"
                        min={1}
                        value={signaturePlacement.pageNumber}
                        onChange={(event) => setSignaturePlacement((current) => ({
                          ...current,
                          pageNumber: Math.max(1, Number(event.target.value) || 1),
                        }))}
                        disabled={isSigning}
                      />
                    </label>
                  </div>
                  <div
                    className={`signature-pdf-placement ${signaturePageImageUrl && !placementPreviewError ? 'has-document' : 'is-placeholder'}`}
                    onClick={handlePickSignaturePlacement}
                  >
                    {signaturePageImageUrl && !placementPreviewError ? (
                      <img
                        src={signaturePageImageUrl}
                        alt={`Trang ${signaturePlacement.pageNumber} của ${presentation.title}`}
                        draggable={false}
                        onError={() => setPlacementPreviewError('Không thể hiển thị trang PDF này. Vui lòng kiểm tra số trang hoặc mở lại tài liệu.')}
                      />
                    ) : (
                      <div className="signature-placement-placeholder">
                        {placementPreviewError || 'Chưa có tài liệu PDF để chọn vị trí ký.'}
                      </div>
                    )}
                    {signaturePageImageUrl && !placementPreviewError ? (
                      <div
                        className="signature-placement-box"
                        style={{
                          left: `${signaturePlacement.xPercent}%`,
                          top: `${signaturePlacement.yPercent}%`,
                          width: `${signaturePlacement.widthPercent}%`,
                          height: `${signaturePlacement.heightPercent}%`,
                        }}
                      >
                        Chữ ký
                      </div>
                    ) : null}
                  </div>
                </div>
              </div>

              {signatureData ? <p className="confirmation-note">Chữ ký đã được gửi lên hệ thống.</p> : null}

              <div className="signature-modal-actions">
                <button
                  type="button"
                  className="ghost-btn"
                  onClick={() => {
                    setShowSignModal(false);
                    setSignatureData(null);
                    setSignatureTouched(false);
                    setPlacementPreviewError('');
                  }}
                  disabled={isSigning}
                >
                  Hủy
                </button>
                <button
                  type="button"
                  className="primary-btn"
                  onClick={handleSignContract}
                  disabled={isSigning || !signatureTouched || (requiresTemplate && (!signaturePageImageUrl || !!placementPreviewError))}
                >
                  {isSigning ? 'Đang ghi chữ ký lên PDF...' : 'Xác nhận ký'}
                </button>
              </div>
            </section>
          </div>
        ) : null}
      </div>
    </div>
  );
}
