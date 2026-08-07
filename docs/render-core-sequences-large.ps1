. "$PSScriptRoot\render-blockchain-verification-sequences.ps1"

$participantsCreate = @(
    @{ Id = 'citizen'; Type = 'actor'; X = 135; Label = 'Người dân' },
    @{ Id = 'frontend'; Type = 'system'; X = 520; Label = 'Frontend' },
    @{ Id = 'api'; Type = 'system'; X = 885; Label = 'Backend API' },
    @{ Id = 'db'; Type = 'database'; X = 1250; Label = 'Database' },
    @{ Id = 'minio'; Type = 'system'; X = 1630; Label = 'MinIO Storage' }
)

$messagesCreate = @(
    @{ From = 'citizen'; To = 'frontend'; Y = 350; Label = 'Đăng nhập hệ thống'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 455; Label = 'Request xác thực(credentials)'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 565; Label = 'Kiểm tra tài khoản'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 660; Label = 'Response thông tin hợp lệ'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 765; Label = 'Response đăng nhập thành công'; Return = $true },
    @{ From = 'frontend'; To = 'citizen'; Y = 875; Label = 'Hiển thị phiên đăng nhập'; Return = $true },
    @{ From = 'citizen'; To = 'frontend'; Y = 990; Label = 'Chọn dịch vụ công chứng'; Activate = $true },
    @{ From = 'frontend'; To = 'citizen'; Y = 1090; Label = 'Hiển thị biểu mẫu hồ sơ'; Return = $true },
    @{ From = 'citizen'; To = 'frontend'; Y = 1200; Label = 'Nhập thông tin hồ sơ'; Activate = $true },
    @{ From = 'citizen'; To = 'frontend'; Y = 1310; Label = 'Tải tài liệu lên hệ thống'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 1430; Label = 'Request tạo hồ sơ(metadata, files)'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 1570; Label = 'Lưu metadata hồ sơ'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 1675; Label = 'Response metadata đã lưu'; Return = $true },
    @{ From = 'api'; To = 'minio'; Y = 1815; Label = 'Upload file tài liệu'; Activate = $true },
    @{ From = 'minio'; To = 'api'; Y = 1935; Label = 'Response objectKey/fileUrl'; Return = $true },
    @{ From = 'api'; To = 'api'; Y = 2070; Label = 'Tạo mã hồ sơ công chứng'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 2195; Label = 'Cập nhật mã hồ sơ và đường dẫn file'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 2305; Label = 'Response cập nhật thành công'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 2450; Label = 'Response tạo hồ sơ thành công'; Return = $true },
    @{ From = 'frontend'; To = 'citizen'; Y = 2570; Label = 'Hiển thị trạng thái hồ sơ'; Return = $true }
)

Render-Sequence `
    -outputPath (Join-Path $PSScriptRoot 'sequence-tao-yeu-cau-cong-chung.png') `
    -title 'Sequence tạo yêu cầu công chứng' `
    -participants $participantsCreate `
    -messages $messagesCreate `
    -notes @() `
    -frames @() `
    -width 1780 `
    -height 2840

$participantsProcess = @(
    @{ Id = 'notary'; Type = 'actor'; X = 135; Label = 'Công chứng viên' },
    @{ Id = 'frontend'; Type = 'system'; X = 520; Label = 'Frontend' },
    @{ Id = 'api'; Type = 'system'; X = 885; Label = 'Backend API' },
    @{ Id = 'db'; Type = 'database'; X = 1250; Label = 'Database' },
    @{ Id = 'minio'; Type = 'system'; X = 1630; Label = 'MinIO Storage' }
)

$messagesProcess = @(
    @{ From = 'notary'; To = 'frontend'; Y = 350; Label = 'Đăng nhập hệ thống'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 460; Label = 'Request xác thực'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 570; Label = 'Kiểm tra tài khoản'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 675; Label = 'Response tài khoản hợp lệ'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 790; Label = 'Response đăng nhập thành công'; Return = $true },
    @{ From = 'notary'; To = 'frontend'; Y = 930; Label = 'Xem danh sách hồ sơ'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 1045; Label = 'Request danh sách hồ sơ'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 1160; Label = 'Truy vấn danh sách hồ sơ'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 1270; Label = 'Response danh sách hồ sơ'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 1390; Label = 'Response danh sách hồ sơ'; Return = $true },
    @{ From = 'frontend'; To = 'notary'; Y = 1510; Label = 'Hiển thị danh sách hồ sơ'; Return = $true },
    @{ From = 'notary'; To = 'frontend'; Y = 1650; Label = 'Chọn hồ sơ cần xử lý'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 1770; Label = 'Request chi tiết hồ sơ(requestId)'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 1895; Label = 'Truy xuất thông tin hồ sơ'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 2010; Label = 'Response metadata và tài liệu'; Return = $true },
    @{ From = 'api'; To = 'minio'; Y = 2140; Label = 'Lấy file tài liệu(objectKey)'; Activate = $true },
    @{ From = 'minio'; To = 'api'; Y = 2260; Label = 'Response file/signed URL'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 2390; Label = 'Response chi tiết hồ sơ'; Return = $true },
    @{ From = 'frontend'; To = 'notary'; Y = 2520; Label = 'Hiển thị thông tin và tài liệu'; Return = $true },
    @{ From = 'notary'; To = 'frontend'; Y = 2665; Label = 'Yêu cầu bổ sung hoặc chấp nhận'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 2790; Label = 'Request cập nhật trạng thái'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 2915; Label = 'Cập nhật trạng thái hồ sơ'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 3030; Label = 'Response trạng thái đã cập nhật'; Return = $true },
    @{ From = 'api'; To = 'db'; Y = 3160; Label = 'Lưu thông báo cho người dân'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 3275; Label = 'Response thông báo đã lưu'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 3410; Label = 'Response xử lý thành công'; Return = $true },
    @{ From = 'frontend'; To = 'notary'; Y = 3540; Label = 'Hiển thị kết quả xử lý'; Return = $true }
)

Render-Sequence `
    -outputPath (Join-Path $PSScriptRoot 'sequence-xu-ly-ho-so-cong-chung.png') `
    -title 'Sequence xử lý hồ sơ công chứng' `
    -participants $participantsProcess `
    -messages $messagesProcess `
    -notes @() `
    -frames @() `
    -width 1780 `
    -height 3800

$participantsOnline = @(
    @{ Id = 'citizen'; Type = 'actor'; X = 120; Label = 'Người dân' },
    @{ Id = 'notary'; Type = 'actor'; X = 365; Label = 'Công chứng viên' },
    @{ Id = 'frontend'; Type = 'system'; X = 610; Label = 'Frontend' },
    @{ Id = 'api'; Type = 'system'; X = 855; Label = 'Backend API' },
    @{ Id = 'video'; Type = 'system'; X = 1105; Label = "Video Call`nService" },
    @{ Id = 'signing'; Type = 'system'; X = 1360; Label = "Signing`nService" },
    @{ Id = 'db'; Type = 'database'; X = 1625; Label = 'Database' }
)

$messagesOnline = @(
    @{ From = 'notary'; To = 'frontend'; Y = 350; Label = 'Tạo lịch đối soát trực tuyến'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 455; Label = 'Request tạo lịch hẹn'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 575; Label = 'Lưu lịch hẹn'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 680; Label = 'Response lịch hẹn đã lưu'; Return = $true },
    @{ From = 'api'; To = 'db'; Y = 790; Label = 'Tạo thông báo tham gia'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 895; Label = 'Response thông báo đã lưu'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 1010; Label = 'Response tạo lịch thành công'; Return = $true },
    @{ From = 'frontend'; To = 'citizen'; Y = 1125; Label = 'Thông báo tham gia phiên đối soát'; Return = $true },
    @{ From = 'citizen'; To = 'frontend'; Y = 1260; Label = 'Tham gia phiên video call'; Activate = $true },
    @{ From = 'frontend'; To = 'video'; Y = 1385; Label = 'Request tạo phiên kết nối'; Activate = $true },
    @{ From = 'video'; To = 'frontend'; Y = 1495; Label = 'Response thông tin phiên'; Return = $true },
    @{ From = 'frontend'; To = 'citizen'; Y = 1605; Label = 'Mở phiên video call'; Return = $true },
    @{ From = 'frontend'; To = 'notary'; Y = 1720; Label = 'Mở phiên video call'; Return = $true },
    @{ From = 'notary'; To = 'frontend'; Y = 1845; Label = 'Xác thực danh tính người dân'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 1970; Label = 'Request truy xuất hồ sơ'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 2095; Label = 'Truy xuất thông tin hồ sơ'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 2210; Label = 'Response hồ sơ và văn bản'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 2340; Label = 'Response thông tin hồ sơ'; Return = $true },
    @{ From = 'citizen'; To = 'frontend'; Y = 2485; Label = 'Xác nhận nội dung văn bản'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 2615; Label = 'Request ghi nhận xác nhận'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 2740; Label = 'Cập nhật xác nhận người dân'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 2860; Label = 'Response đã ghi nhận'; Return = $true },
    @{ From = 'citizen'; To = 'frontend'; Y = 3010; Label = 'Thực hiện ký số điện tử'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 3145; Label = 'Request ký số người dân'; Activate = $true },
    @{ From = 'api'; To = 'signing'; Y = 3275; Label = 'Tạo chữ ký số người dân'; Activate = $true },
    @{ From = 'signing'; To = 'api'; Y = 3400; Label = 'Response chữ ký số người dân'; Return = $true },
    @{ From = 'notary'; To = 'frontend'; Y = 3560; Label = 'Ký số xác nhận công chứng'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 3700; Label = 'Request ký số công chứng viên'; Activate = $true },
    @{ From = 'api'; To = 'signing'; Y = 3835; Label = 'Tạo chữ ký số công chứng viên'; Activate = $true },
    @{ From = 'signing'; To = 'api'; Y = 3960; Label = 'Response chữ ký số công chứng viên'; Return = $true },
    @{ From = 'api'; To = 'api'; Y = 4095; Label = 'Tạo văn bản công chứng điện tử hoàn chỉnh'; Activate = $true },
    @{ From = 'api'; To = 'db'; Y = 4240; Label = 'Lưu thông tin văn bản'; Activate = $true },
    @{ From = 'db'; To = 'api'; Y = 4365; Label = 'Response văn bản đã lưu'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 4500; Label = 'Response phát hành văn bản'; Return = $true },
    @{ From = 'frontend'; To = 'citizen'; Y = 4640; Label = 'Hiển thị văn bản công chứng điện tử'; Return = $true }
)

Render-Sequence `
    -outputPath (Join-Path $PSScriptRoot 'sequence-cong-chung-truc-tuyen-va-ky-so.png') `
    -title 'Sequence công chứng trực tuyến và ký số' `
    -participants $participantsOnline `
    -messages $messagesOnline `
    -notes @() `
    -frames @() `
    -width 1760 `
    -height 4940

Write-Host 'Rendered sequence-tao-yeu-cau-cong-chung.png'
Write-Host 'Rendered sequence-xu-ly-ho-so-cong-chung.png'
Write-Host 'Rendered sequence-cong-chung-truc-tuyen-va-ky-so.png'
