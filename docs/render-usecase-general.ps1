Add-Type -AssemblyName System.Drawing

$width = 3400
$height = 2600
$out = Join-Path (Split-Path -Parent $PSScriptRoot) 'docs/usecase-general.png'

$bmp = [System.Drawing.Bitmap]::new($width, $height)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.Clear([System.Drawing.Color]::White)

$fontTitle = [System.Drawing.Font]::new('Segoe UI', 36, [System.Drawing.FontStyle]::Bold)
$fontSubtitle = [System.Drawing.Font]::new('Segoe UI', 18, [System.Drawing.FontStyle]::Regular)
$fontPackage = [System.Drawing.Font]::new('Segoe UI', 23, [System.Drawing.FontStyle]::Bold)
$fontActor = [System.Drawing.Font]::new('Segoe UI', 23, [System.Drawing.FontStyle]::Bold)
$fontUseCase = [System.Drawing.Font]::new('Segoe UI', 19, [System.Drawing.FontStyle]::Regular)
$fontSmall = [System.Drawing.Font]::new('Segoe UI', 15, [System.Drawing.FontStyle]::Regular)
$fontFlow = [System.Drawing.Font]::new('Segoe UI', 19, [System.Drawing.FontStyle]::Bold)

$ink = [System.Drawing.Color]::FromArgb(17, 24, 39)
$muted = [System.Drawing.Color]::FromArgb(100, 116, 139)
$line = [System.Drawing.Color]::FromArgb(71, 85, 105)
$boundaryLine = [System.Drawing.Color]::FromArgb(30, 41, 59)

$penBoundary = [System.Drawing.Pen]::new($boundaryLine, 4)
$penPackage = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(148, 163, 184), 2)
$penActor = [System.Drawing.Pen]::new($ink, 5)
$penAssoc = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(105, 148, 163, 184), 2)
$penInclude = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(79, 70, 229), 2)
$penFlow = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(14, 116, 144), 4)
$penInclude.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash

$brushInk = [System.Drawing.SolidBrush]::new($ink)
$brushMuted = [System.Drawing.SolidBrush]::new($muted)
$brushBoundary = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(248, 250, 252))
$brushCitizen = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(236, 253, 245))
$brushNotary = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 247, 237))
$brushAdmin = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(245, 243, 255))
$brushCrypto = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(236, 254, 255))
$brushWhite = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$brushPackageCitizen = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(248, 255, 251))
$brushPackageNotary = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 251, 245))
$brushPackageAdmin = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(250, 248, 255))
$brushPackageCrypto = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(245, 253, 255))

$center = [System.Drawing.StringFormat]::new()
$center.Alignment = [System.Drawing.StringAlignment]::Center
$center.LineAlignment = [System.Drawing.StringAlignment]::Center

$left = [System.Drawing.StringFormat]::new()
$left.Alignment = [System.Drawing.StringAlignment]::Near
$left.LineAlignment = [System.Drawing.StringAlignment]::Center

function Draw-TextCentered {
    param([string]$text, [System.Drawing.Font]$font, [System.Drawing.Brush]$brush, [int]$x, [int]$y, [int]$w, [int]$h)
    $script:g.DrawString($text, $font, $brush, [System.Drawing.RectangleF]::new($x, $y, $w, $h), $script:center)
}

function Draw-TextLeft {
    param([string]$text, [System.Drawing.Font]$font, [System.Drawing.Brush]$brush, [int]$x, [int]$y, [int]$w, [int]$h)
    $script:g.DrawString($text, $font, $brush, [System.Drawing.RectangleF]::new($x, $y, $w, $h), $script:left)
}

function Draw-RoundRect {
    param([int]$x, [int]$y, [int]$w, [int]$h, [int]$r, [System.Drawing.Brush]$brush, [System.Drawing.Pen]$pen)
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $script:g.FillPath($brush, $path)
    $script:g.DrawPath($pen, $path)
    $path.Dispose()
}

function Draw-Package {
    param([string]$label, [int]$x, [int]$y, [int]$w, [int]$h, [System.Drawing.Brush]$fill)
    Draw-RoundRect $x $y $w $h 18 $fill $script:penPackage
    $script:g.FillRectangle($script:brushWhite, [System.Drawing.Rectangle]::new($x + 20, $y, 360, 56))
    $script:g.DrawRectangle($script:penPackage, [System.Drawing.Rectangle]::new($x + 20, $y, 360, 56))
    Draw-TextCentered $label $script:fontPackage $script:brushInk ($x + 20) $y 360 56
}

function Draw-UseCase {
    param([string]$key, [string]$label, [int]$x, [int]$y, [int]$w, [System.Drawing.Brush]$fill)
    $h = 84
    $rect = [System.Drawing.Rectangle]::new($x, $y, $w, $h)
    $script:g.FillEllipse($fill, $rect)
    $script:g.DrawEllipse($script:penBoundary, $rect)
    Draw-TextCentered $label $script:fontUseCase $script:brushInk $x $y $w $h
    $script:uc[$key] = @{ X = $x; Y = $y; W = $w; H = $h; Cx = $x + ($w / 2); Cy = $y + ($h / 2) }
}

function Draw-Actor {
    param([string]$key, [string]$label, [int]$x, [int]$y)
    $head = [System.Drawing.Rectangle]::new(($x + 43), $y, 58, 58)
    $script:g.DrawEllipse($script:penActor, $head)
    $script:g.DrawLine($script:penActor, ($x + 72), ($y + 58), ($x + 72), ($y + 150))
    $script:g.DrawLine($script:penActor, ($x + 8), ($y + 96), ($x + 136), ($y + 96))
    $script:g.DrawLine($script:penActor, ($x + 72), ($y + 150), ($x + 25), ($y + 225))
    $script:g.DrawLine($script:penActor, ($x + 72), ($y + 150), ($x + 121), ($y + 225))
    Draw-TextCentered $label $script:fontActor $script:brushInk ($x - 85) ($y + 238) 315 72
    $script:actor[$key] = @{ Right = $x + 136; Cx = $x + 72; Cy = $y + 112; Bottom = $y + 225 }
}

function Draw-ArrowHead {
    param([System.Drawing.Pen]$pen, [float]$x1, [float]$y1, [float]$x2, [float]$y2)
    $angle = [Math]::Atan2($y2 - $y1, $x2 - $x1)
    $len = 16
    $a1 = $angle + [Math]::PI * 0.82
    $a2 = $angle - [Math]::PI * 0.82
    $p1 = [System.Drawing.PointF]::new($x2 + $len * [Math]::Cos($a1), $y2 + $len * [Math]::Sin($a1))
    $p2 = [System.Drawing.PointF]::new($x2 + $len * [Math]::Cos($a2), $y2 + $len * [Math]::Sin($a2))
    $script:g.DrawLine($pen, $x2, $y2, $p1.X, $p1.Y)
    $script:g.DrawLine($pen, $x2, $y2, $p2.X, $p2.Y)
}

function Connect-Actor {
    param([string]$actorKey, [string]$ucKey)
    $a = $script:actor[$actorKey]
    $u = $script:uc[$ucKey]
    $script:g.DrawLine($script:penAssoc, [float]$a.Cx, [float]$a.Bottom, [float]$u.Cx, [float]$u.Y)
}

function Connect-Include {
    param([string]$from, [string]$to, [string]$label)
    $u1 = $script:uc[$from]
    $u2 = $script:uc[$to]
    $x1 = [float]($u1.X + $u1.W)
    $y1 = [float]$u1.Cy
    $x2 = [float]$u2.X
    $y2 = [float]$u2.Cy
    $script:g.DrawLine($script:penInclude, $x1, $y1, $x2, $y2)
    Draw-ArrowHead $script:penInclude $x1 $y1 $x2 $y2
}

function Draw-FlowNode {
    param([string]$key, [string]$label, [int]$x, [int]$y, [int]$w)
    Draw-RoundRect $x $y $w 82 12 $script:brushWhite $script:penPackage
    Draw-TextCentered $label $script:fontFlow $script:brushInk $x $y $w 82
    $script:flow[$key] = @{ X = $x; Y = $y; W = $w; H = 82; Cx = $x + ($w / 2); Cy = $y + 41 }
}

function Connect-Flow {
    param([string]$from, [string]$to)
    $a = $script:flow[$from]
    $b = $script:flow[$to]
    $x1 = [float]($a.X + $a.W)
    $y1 = [float]$a.Cy
    $x2 = [float]$b.X
    $y2 = [float]$b.Cy
    $script:g.DrawLine($script:penFlow, $x1, $y1, $x2, $y2)
    Draw-ArrowHead $script:penFlow $x1 $y1 $x2 $y2
}

$uc = @{}
$actor = @{}
$flow = @{}

Draw-TextCentered 'Use Case tổng quát hệ thống công chứng điện tử tích hợp Blockchain và chữ ký hậu lượng tử' $fontTitle $brushInk 120 28 3160 64

$boundary = [System.Drawing.Rectangle]::new(250, 470, 2900, 1960)
$g.FillRectangle($brushBoundary, $boundary)
$g.DrawRectangle($penBoundary, $boundary)
Draw-TextCentered 'Hệ thống công chứng điện tử tích hợp Blockchain và Post-Quantum Cryptography' $fontPackage $brushInk 250 486 2900 52

Draw-Package 'Người dân' 350 600 720 760 $brushPackageCitizen
Draw-Package 'Công chứng viên' 1165 600 760 760 $brushPackageNotary
Draw-Package 'Quản trị viên' 2030 600 900 500 $brushPackageAdmin
Draw-Package 'Blockchain & PQC' 650 1450 2280 690 $brushPackageCrypto

Draw-Actor 'Citizen' 'Người dân' 640 145
Draw-Actor 'Notary' 'Công chứng viên' 1480 145
Draw-Actor 'Admin' 'Quản trị viên' 2450 145

Draw-UseCase 'Login' 'Đăng ký / đăng nhập' 415 700 300 $brushCitizen
Draw-UseCase 'Create' "Tạo yêu cầu`ncông chứng" 740 700 300 $brushCitizen
Draw-UseCase 'Upload' 'Tải lên tài liệu' 415 830 300 $brushCitizen
Draw-UseCase 'Track' "Theo dõi`ntrạng thái hồ sơ" 740 830 300 $brushCitizen
Draw-UseCase 'OnlineReview' "Tham gia đối soát`ntrực tuyến" 415 960 300 $brushCitizen
Draw-UseCase 'CitizenSign' 'Ký số văn bản' 740 960 300 $brushCitizen
Draw-UseCase 'Pay' 'Thanh toán' 415 1090 300 $brushCitizen
Draw-UseCase 'Receive' "Nhận văn bản`ncông chứng điện tử" 740 1090 300 $brushCitizen
Draw-UseCase 'VerifyDoc' "Xác minh tài liệu`ncông chứng" 580 1230 300 $brushCitizen

Draw-UseCase 'Accept' 'Tiếp nhận hồ sơ' 1235 700 300 $brushNotary
Draw-UseCase 'Check' 'Kiểm tra hồ sơ' 1565 700 300 $brushNotary
Draw-UseCase 'RequestMore' "Yêu cầu bổ sung`nhồ sơ" 1235 830 300 $brushNotary
Draw-UseCase 'SetupReview' "Thiết lập phiên`nđối soát trực tuyến" 1565 830 300 $brushNotary
Draw-UseCase 'AuthParticipant' "Xác thực người`ntham gia" 1235 960 300 $brushNotary
Draw-UseCase 'NotarySign' "Ký số xác nhận`ncông chứng" 1565 960 300 $brushNotary
Draw-UseCase 'Record' "Ghi dữ liệu xác minh`nlên Blockchain" 1235 1090 300 $brushNotary

Draw-UseCase 'UserAdmin' 'Quản lý người dùng' 2095 705 330 $brushAdmin
Draw-UseCase 'ServiceAdmin' "Quản lý dịch vụ`ncông chứng" 2470 705 330 $brushAdmin
Draw-UseCase 'TemplateAdmin' 'Quản lý mẫu văn bản' 2095 845 330 $brushAdmin
Draw-UseCase 'RequestAdmin' 'Quản lý hồ sơ' 2470 845 330 $brushAdmin
Draw-UseCase 'Monitor' "Thống kê và giám sát`nhệ thống" 2280 985 330 $brushAdmin

Draw-UseCase 'Hash' "Tạo hash văn bản`ncông chứng" 750 1565 330 $brushCrypto
Draw-UseCase 'Tx' "Tạo transaction`nBlockchain" 1170 1565 330 $brushCrypto
Draw-UseCase 'Hybrid' "Ký transaction bằng`nHybrid Signature" 1590 1565 360 $brushCrypto
Draw-UseCase 'ECDSA' "Xác minh`nECDSA Signature" 2040 1565 330 $brushCrypto
Draw-UseCase 'PQSig' "Xác minh`nPQ Signature" 2440 1565 330 $brushCrypto
Draw-UseCase 'Store' "Lưu dữ liệu xác minh`nlên Blockchain" 1170 1735 330 $brushCrypto
Draw-UseCase 'Query' "Truy xuất dữ liệu`nxác minh" 1590 1735 360 $brushCrypto
Draw-UseCase 'Integrity' "Kiểm tra tính toàn vẹn`ntài liệu" 2040 1735 330 $brushCrypto

Connect-Actor 'Citizen' 'Login'
Connect-Actor 'Citizen' 'Create'
Connect-Actor 'Citizen' 'Upload'
Connect-Actor 'Citizen' 'Track'
Connect-Actor 'Citizen' 'OnlineReview'
Connect-Actor 'Citizen' 'CitizenSign'
Connect-Actor 'Citizen' 'Pay'
Connect-Actor 'Citizen' 'Receive'
Connect-Actor 'Citizen' 'VerifyDoc'

Connect-Actor 'Notary' 'Accept'
Connect-Actor 'Notary' 'Check'
Connect-Actor 'Notary' 'RequestMore'
Connect-Actor 'Notary' 'SetupReview'
Connect-Actor 'Notary' 'AuthParticipant'
Connect-Actor 'Notary' 'NotarySign'
Connect-Actor 'Notary' 'Record'

Connect-Actor 'Admin' 'UserAdmin'
Connect-Actor 'Admin' 'ServiceAdmin'
Connect-Actor 'Admin' 'TemplateAdmin'
Connect-Actor 'Admin' 'RequestAdmin'
Connect-Actor 'Admin' 'Monitor'

Connect-Include 'CitizenSign' 'Hash' '<<include>>'
Connect-Include 'NotarySign' 'Hash' '<<include>>'
Connect-Include 'Record' 'Tx' '<<include>>'
Connect-Include 'Tx' 'Hybrid' '<<include>>'
Connect-Include 'Hybrid' 'ECDSA' '<<include>>'
Connect-Include 'Hybrid' 'PQSig' '<<include>>'
Connect-Include 'Hybrid' 'Store' '<<include>>'
Connect-Include 'VerifyDoc' 'Query' '<<include>>'
Connect-Include 'VerifyDoc' 'Integrity' '<<include>>'
Connect-Include 'Integrity' 'ECDSA' '<<include>>'
Connect-Include 'Integrity' 'PQSig' '<<include>>'
Connect-Include 'Monitor' 'Query' '<<include>>'

# Redraw use cases after associations/includes so relationship lines stay behind labels.
Draw-UseCase 'Login' 'Đăng ký / đăng nhập' 415 700 300 $brushCitizen
Draw-UseCase 'Create' "Tạo yêu cầu`ncông chứng" 740 700 300 $brushCitizen
Draw-UseCase 'Upload' 'Tải lên tài liệu' 415 830 300 $brushCitizen
Draw-UseCase 'Track' "Theo dõi`ntrạng thái hồ sơ" 740 830 300 $brushCitizen
Draw-UseCase 'OnlineReview' "Tham gia đối soát`ntrực tuyến" 415 960 300 $brushCitizen
Draw-UseCase 'CitizenSign' 'Ký số văn bản' 740 960 300 $brushCitizen
Draw-UseCase 'Pay' 'Thanh toán' 415 1090 300 $brushCitizen
Draw-UseCase 'Receive' "Nhận văn bản`ncông chứng điện tử" 740 1090 300 $brushCitizen
Draw-UseCase 'VerifyDoc' "Xác minh tài liệu`ncông chứng" 580 1230 300 $brushCitizen

Draw-UseCase 'Accept' 'Tiếp nhận hồ sơ' 1235 700 300 $brushNotary
Draw-UseCase 'Check' 'Kiểm tra hồ sơ' 1565 700 300 $brushNotary
Draw-UseCase 'RequestMore' "Yêu cầu bổ sung`nhồ sơ" 1235 830 300 $brushNotary
Draw-UseCase 'SetupReview' "Thiết lập phiên`nđối soát trực tuyến" 1565 830 300 $brushNotary
Draw-UseCase 'AuthParticipant' "Xác thực người`ntham gia" 1235 960 300 $brushNotary
Draw-UseCase 'NotarySign' "Ký số xác nhận`ncông chứng" 1565 960 300 $brushNotary
Draw-UseCase 'Record' "Ghi dữ liệu xác minh`nlên Blockchain" 1235 1090 300 $brushNotary

Draw-UseCase 'UserAdmin' 'Quản lý người dùng' 2095 705 330 $brushAdmin
Draw-UseCase 'ServiceAdmin' "Quản lý dịch vụ`ncông chứng" 2470 705 330 $brushAdmin
Draw-UseCase 'TemplateAdmin' 'Quản lý mẫu văn bản' 2095 845 330 $brushAdmin
Draw-UseCase 'RequestAdmin' 'Quản lý hồ sơ' 2470 845 330 $brushAdmin
Draw-UseCase 'Monitor' "Thống kê và giám sát`nhệ thống" 2280 985 330 $brushAdmin

Draw-UseCase 'Hash' "Tạo hash văn bản`ncông chứng" 750 1565 330 $brushCrypto
Draw-UseCase 'Tx' "Tạo transaction`nBlockchain" 1170 1565 330 $brushCrypto
Draw-UseCase 'Hybrid' "Ký transaction bằng`nHybrid Signature" 1590 1565 360 $brushCrypto
Draw-UseCase 'ECDSA' "Xác minh`nECDSA Signature" 2040 1565 330 $brushCrypto
Draw-UseCase 'PQSig' "Xác minh`nPQ Signature" 2440 1565 330 $brushCrypto
Draw-UseCase 'Store' "Lưu dữ liệu xác minh`nlên Blockchain" 1170 1735 330 $brushCrypto
Draw-UseCase 'Query' "Truy xuất dữ liệu`nxác minh" 1590 1735 360 $brushCrypto
Draw-UseCase 'Integrity' "Kiểm tra tính toàn vẹn`ntài liệu" 2040 1735 330 $brushCrypto

$flowBox = [System.Drawing.Rectangle]::new(565, 2250, 2270, 120)
$g.FillRectangle([System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(240, 253, 250)), $flowBox)
$g.DrawRectangle([System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(45, 212, 191), 2), $flowBox)
Draw-TextLeft 'Luồng xác minh trọng tâm:' $fontFlow $brushInk 595 2270 260 72
Draw-FlowNode 'Doc' 'Văn bản công chứng' 865 2270 310
Draw-FlowNode 'Sha' 'SHA-256 Hash' 1225 2270 240
Draw-FlowNode 'TxFlow' "Transaction`nchứa Hash" 1515 2270 250
Draw-FlowNode 'HybridFlow' "Ký transaction`nECDSA + PQC" 1815 2270 300
Draw-FlowNode 'VerifyFlow' 'Blockchain Verification' 2165 2270 340
Connect-Flow 'Doc' 'Sha'
Connect-Flow 'Sha' 'TxFlow'
Connect-Flow 'TxFlow' 'HybridFlow'
Connect-Flow 'HybridFlow' 'VerifyFlow'

$legend = [System.Drawing.Rectangle]::new(2580, 1165, 350, 170)
$g.FillRectangle($brushWhite, $legend)
$g.DrawRectangle($penPackage, $legend)
Draw-TextCentered 'Ký hiệu UML' $fontSmall $brushInk 2580 1178 350 28
$g.DrawLine($penAssoc, 2615, 1235, 2695, 1235)
Draw-TextLeft 'Association actor - use case' $fontSmall $brushMuted 2715 1220 200 34
$g.DrawLine($penInclude, 2615, 1285, 2695, 1285)
Draw-ArrowHead $penInclude 2615 1285 2695 1285
Draw-TextLeft '<<include>> nội bộ' $fontSmall $brushMuted 2715 1270 200 34

$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
Write-Output $out
