# Hướng Dẫn Sử Dụng Video Session Microservice

## Các Tính Năng Chính

✅ **Tạo Video Session Tự Động**: Khi Notary schedule ONLINE appointment, hệ thống tự động tạo video session  
✅ **Token-Based Access**: Client có thể join video call chỉ bằng link + token, không cần đăng nhập  
✅ **Status Tracking**: Theo dõi trạng thái session (PENDING → NOTARY_JOINED → IN_PROGRESS → FINISHED)  
✅ **Duration Calculation**: Tính thời lượng cuộc gọi tự động  
✅ **Security**: Kiểm soát quyền truy cập (chỉ notary và client được phép)  


## Controller
- `VideoSessionController.java` - REST API endpoints:
  - POST `/api/video/sessions` - Tạo video session
  - GET `/api/video/sessions/{id}` - Lấy thông tin session
  - GET `/api/video/appointments/{appointmentId}/session` - Lấy session theo appointment
  - GET `/api/video/verify-token?token={token}` - Verify token (public, không cần auth)
  - POST `/api/video/room/{roomId}/join` - Join vào phòng họp
  - POST `/api/video/sessions/{id}/end` - Kết thúc cuộc gọi
  - POST `/api/video/sessions/{id}/cancel` - Hủy video session
  - GET `/api/video/room/{roomId}` - Simple test page


## Quy Trình Làm Việc

### Scenario: Notary Approve và Schedule Appointment (ONLINE)

```
1. Client tạo yêu cầu công chứng
   POST /api/requests
   {
     "serviceType": "ONLINE",
     "description": "...",
     ...
   }

2. Notary review documents → Trạng thái = PROCESSING

3. Notary lên lịch hẹn
   POST /api/requests/{id}/schedule
   {
     "scheduledTime": "2026-03-15T10:00:00Z"
   }

4. Backend tự động:
   ✓ Tạo Appointment
   ✓ Tạo VideoSession (status=PENDING)
   ✓ Tạo meeting URL: http://localhost:8080/api/video/room/room_xxx?token=yyy
   ✓ Update NotaryRequest.status = SCHEDULED

5. Response trả về:
   {
     "appointmentId": "...",
     "scheduledTime": "2026-03-15T10:00:00Z",
     "meetingUrl": "http://localhost:8080/api/video/room/room_a1b2c3d4?token=eyJ...",
     "status": "PENDING",
     ...
   }

6. Client nhận email chứa meeting link (nếu SMTP đã cấu hình)

7. Client click link → Verify token → UI để join
   POST /api/video/room/{roomId}/join
   → Status: NOTARY_JOINED nếu chỉ notary vào
   → Status: IN_PROGRESS nếu cả hai vào

8. Khi call kết thúc:
   POST /api/video/sessions/{id}/end?reason=Done
   → Status: FINISHED
   → durationSeconds được tính

9. Call được lưu lại:
   - Thời gian kết nối
   - Khi notary vào
   - Khi client vào
   - Thời lượng cuộc gọi
```

## API Endpoints Chi Tiết

### 1. Tạo Video Session (Notary gọi)
```bash
POST /api/video/sessions
Authorization: Bearer {notary_token}
Content-Type: application/json

{
    "appointmentId": "550e8400-e29b-41d4-a716-446655440000"
}
```
**Response:**
```json
{
    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "appointmentId": "550e8400-e29b-41d4-a716-446655440000",
    "sessionToken": "eyJhbGciOiJIUzI1NiIs...",
    "meetingUrl": "http://localhost:8080/api/video/room/room_a1b2c3d4?token=eyJ...",
    "roomId": "room_a1b2c3d4",
    "status": "PENDING",
    "createdAt": "2026-03-11T10:30:00Z"
}
```

### 2. Verify Token (Public, không cần auth)
```bash
GET /api/video/verify-token?token={sessionToken}
```
**Response:** VideoSessionResponse

### 3. Join Room
```bash
POST /api/video/room/{roomId}/join
Authorization: Bearer {client_or_notary_token}
```
**Response:**
```json
{
    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "status": "IN_PROGRESS",
    "notaryJoinedAt": "2026-03-11T10:35:00Z",
    "clientJoinedAt": "2026-03-11T10:36:00Z"
}
```

### 4. End Session
```bash
POST /api/video/sessions/{sessionId}/end?reason=Normal%20end
Authorization: Bearer {token}
```

### 5. Cancel Session
```bash
POST /api/video/sessions/{sessionId}/cancel?reason=No%20show
Authorization: Bearer {token}
```

## Tích Hợp Với NotaryRequestService

Method `scheduleAppointment` đã được update:

```java
// Nếu là ONLINE, tự động tạo VideoSession
if (request.getServiceType() == ServiceType.ONLINE) {
    VideoSession session = new VideoSession();
    session.setAppointment(saved);
    
    String roomId = "room_" + UUID.randomUUID().toString().substring(0, 8);
    String sessionToken = UUID.randomUUID().toString();
    
    session.setRoomId(roomId);
    session.setSessionToken(sessionToken);
    
    String meetingUrl = baseUrl + "/api/video/room/" + roomId + "?token=" + sessionToken;
    session.setMeetingUrl(meetingUrl);
    
    videoSessionRepository.save(session);
    
    // Update appointment
    saved.setMeetingUrl(meetingUrl);
    appointmentRepository.save(saved);
}
```

## Database Schema

```sql
CREATE TABLE video_sessions (
    session_id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(appointment_id),
    
    session_token VARCHAR(255) UNIQUE NOT NULL,
    meeting_url VARCHAR(500),
    room_id VARCHAR(100) UNIQUE NOT NULL,
    
    status VARCHAR(20) DEFAULT 'PENDING',
    -- PENDING | NOTARY_JOINED | IN_PROGRESS | FINISHED | CANCELLED
    
    notary_joined_at TIMESTAMPTZ,
    client_joined_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    
    duration_seconds BIGINT,
    notes TEXT,
    
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indexes cho performance
CREATE INDEX idx_video_sessions_appointment ON video_sessions(appointment_id);
CREATE INDEX idx_video_sessions_room_id ON video_sessions(room_id);
CREATE INDEX idx_video_sessions_status ON video_sessions(status);
CREATE INDEX idx_video_sessions_created_at ON video_sessions(created_at DESC);
```

## Environment Configuration

### application.yml
```yaml
app:
  meeting:
    base-url: ${MEETING_BASE_URL:http://localhost:8080}
  mail:
    enabled: ${MAIL_ENABLED:true}

spring:
  mail:
    host: ${MAIL_HOST:}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
```

### Environment Variables
```bash
# Development
export MEETING_BASE_URL="http://localhost:8080"
export MAIL_ENABLED=true
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="your-account@gmail.com"
export MAIL_PASSWORD="your-app-password"

# Production
export MEETING_BASE_URL="https://yourdomain.com"
export MAIL_ENABLED=true
export MAIL_HOST="smtp.yourprovider.com"
export MAIL_PORT="587"
export MAIL_USERNAME="smtp-user"
export MAIL_PASSWORD="smtp-password"
```

> Nếu chưa cấu hình SMTP, backend vẫn schedule appointment bình thường và ghi log cảnh báo, không làm fail API.

## Security & Authorization

1. **Create Video Session**: Chỉ Notary hoặc Admin
2. **Join Room**: Chỉ notary hoặc client của appointment đó
3. **Verify Token**: Public, dùng session token thay vì JWT
4. **End/Cancel Session**: Chỉ Notary hoặc Admin

## Next Steps - Frontend Integration

Frontend cần implement:

1. **Display Meeting Link**: Hiển thị link từ email hoặc API
2. **Token Verification**: Gọi `/api/video/verify-token` để verify
3. **WebRTC Library**: Integrate Jitsi Meet, Daily.co, hoặc tự build
4. **Join Room**: Gọi POST `/api/video/room/{roomId}/join` khi user enter room
5. **End Call**: Gọi POST `/api/video/sessions/{id}/end` khi user exit
6. **UI Components**:
   - Video display area
   - Participant list
   - Chat (optional)
   - Share screen (optional)
   - Recording (optional)

## Testing

### Test 1: Schedule ONLINE Appointment
```bash
# Get auth token
TOKEN=$(curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"notary@example.com","password":"password"}' | jq -r '.token')

# Create appointment
curl -X POST http://localhost:8080/api/requests/550e8400-e29b-41d4-a716-446655440000/schedule \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "scheduledTime": "2026-03-15T10:00:00Z"
  }'
```

### Test 2: Verify Token
```bash
curl "http://localhost:8080/api/video/verify-token?token={sessionToken}"
```

### Test 3: Join Room
```bash
curl -X POST http://localhost:8080/api/video/room/{roomId}/join \
  -H "Authorization: Bearer {token}"
```

## Files Checklist

### New Files Created
- ✅ `src/main/java/com/actvn/enotary/entity/VideoSession.java`
- ✅ `src/main/java/com/actvn/enotary/enums/VideoSessionStatus.java`
- ✅ `src/main/java/com/actvn/enotary/repository/VideoSessionRepository.java`
- ✅ `src/main/java/com/actvn/enotary/service/VideoSessionService.java`
- ✅ `src/main/java/com/actvn/enotary/controller/VideoSessionController.java`
- ✅ `src/main/java/com/actvn/enotary/dto/request/CreateVideoSessionRequest.java`
- ✅ `src/main/java/com/actvn/enotary/dto/request/JoinVideoSessionRequest.java`
- ✅ `src/main/java/com/actvn/enotary/dto/response/VideoSessionResponse.java`
- ✅ `src/main/resources/db/migration/V4__video_sessions.sql`
- ✅ `VIDEO_SESSION_API.md` - Detailed API documentation
- ✅ `VIDEO_SESSION_GUIDE.md` - This file

### Modified Files
- ✅ `src/main/java/com/actvn/enotary/service/NotaryRequestService.java`
  - Added `VideoSessionRepository` dependency
  - Added automatic VideoSession creation for ONLINE appointments
  - Added `baseUrl` configuration
- ✅ `src/main/java/com/actvn/enotary/service/AppointmentEmailService.java`
  - Sends meeting-link email to client after ONLINE scheduling
  - Graceful fallback when SMTP is missing/unavailable
  
- ✅ `src/main/resources/application.yml`
  - Added `app.meeting.base-url` configuration
  - Added `app.mail.*` and `spring.mail.*` configuration

## Build & Run

```bash
# Clean build
./mvnw clean compile

# Run tests (if any)
./mvnw test

# Package
./mvnw package

# Run
java -jar target/enotary-0.0.1-SNAPSHOT.jar

# Or with environment variable
MEETING_BASE_URL="https://yourdomain.com" \
java -jar target/enotary-0.0.1-SNAPSHOT.jar
```

## Lưu Ý Quan Trọng

1. **Database Migration**: V4__video_sessions.sql sẽ chạy tự động khi app start (Flyway)
2. **Session Token**: UUID format, không phải JWT
3. **Meeting URL**: Chứa room_id + token, cho phép verify bằng token mà không cần login
4. **Status Transitions**: 
   - PENDING → NOTARY_JOINED (khi notary join)
   - NOTARY_JOINED → IN_PROGRESS (khi client join)
   - Bất kỳ state → FINISHED (khi call end)
   - PENDING → CANCELLED (khi hủy trước khi ai join)

5. **Duration Calculation**: Được tính từ `clientJoinedAt` cho đến `endedAt`
6. **No Recording**: Hiện tại chỉ theo dõi metadata, không lưu video recording

## Future Enhancements

- [ ] Video Recording
- [ ] Screen Sharing
- [ ] Call Timeout Auto-Cancel
- [x] Email Notifications
- [ ] Call Quality Metrics
- [ ] WebRTC Integration (Jitsi/Daily.co)
- [ ] Participant Management
- [ ] Call Analytics

---

**Status**: ✅ Complete & Ready to Deploy  
**Last Updated**: March 19, 2026  
**Version**: 1.1.0

