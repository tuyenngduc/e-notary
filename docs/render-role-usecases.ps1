Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'docs'

$width = 2200
$height = 1500

$ink = [System.Drawing.Color]::FromArgb(17, 24, 39)
$muted = [System.Drawing.Color]::FromArgb(100, 116, 139)
$line = [System.Drawing.Color]::FromArgb(148, 163, 184)
$blue = [System.Drawing.Color]::FromArgb(37, 99, 235)
$teal = [System.Drawing.Color]::FromArgb(14, 116, 144)

$fontTitle = [System.Drawing.Font]::new('Segoe UI', 32, [System.Drawing.FontStyle]::Bold)
$fontSubtitle = [System.Drawing.Font]::new('Segoe UI', 16, [System.Drawing.FontStyle]::Regular)
$fontActor = [System.Drawing.Font]::new('Segoe UI', 22, [System.Drawing.FontStyle]::Bold)
$fontPackage = [System.Drawing.Font]::new('Segoe UI', 21, [System.Drawing.FontStyle]::Bold)
$fontUseCase = [System.Drawing.Font]::new('Segoe UI', 18, [System.Drawing.FontStyle]::Regular)
$fontSmallBold = [System.Drawing.Font]::new('Segoe UI', 16, [System.Drawing.FontStyle]::Bold)

$center = [System.Drawing.StringFormat]::new()
$center.Alignment = [System.Drawing.StringAlignment]::Center
$center.LineAlignment = [System.Drawing.StringAlignment]::Center

function New-Brush($r, $g, $b) {
    return [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb($r, $g, $b))
}

function Draw-TextCentered {
    param($gfx, [string]$text, [System.Drawing.Font]$font, [System.Drawing.Brush]$brush, [int]$x, [int]$y, [int]$w, [int]$h)
    $gfx.DrawString($text, $font, $brush, [System.Drawing.RectangleF]::new($x, $y, $w, $h), $script:center)
}

function Draw-RoundRect {
    param($gfx, [int]$x, [int]$y, [int]$w, [int]$h, [int]$r, [System.Drawing.Brush]$brush, [System.Drawing.Pen]$pen)
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $gfx.FillPath($brush, $path)
    $gfx.DrawPath($pen, $path)
    $path.Dispose()
}

function Draw-Actor {
    param($gfx, [string]$label, [int]$x, [int]$y, [System.Drawing.Pen]$pen, [System.Drawing.Brush]$brush)
    $gfx.DrawEllipse($pen, [System.Drawing.Rectangle]::new($x + 43, $y, 58, 58))
    $gfx.DrawLine($pen, $x + 72, $y + 58, $x + 72, $y + 150)
    $gfx.DrawLine($pen, $x + 8, $y + 96, $x + 136, $y + 96)
    $gfx.DrawLine($pen, $x + 72, $y + 150, $x + 25, $y + 225)
    $gfx.DrawLine($pen, $x + 72, $y + 150, $x + 121, $y + 225)
    $labelX = [Math]::Max(0, $x - 58)
    Draw-TextCentered $gfx $label $script:fontActor $brush $labelX ($y + 235) 260 70
    return @{ X = $x + 72; Right = $x + 136; Cy = $y + 112; Bottom = $y + 225 }
}

function Draw-UseCase {
    param($gfx, [hashtable]$map, [string]$key, [string]$label, [int]$x, [int]$y, [int]$w, [System.Drawing.Brush]$fill, [System.Drawing.Pen]$pen, [System.Drawing.Brush]$textBrush)
    $h = 86
    $rect = [System.Drawing.Rectangle]::new($x, $y, $w, $h)
    $gfx.FillEllipse($fill, $rect)
    $gfx.DrawEllipse($pen, $rect)
    Draw-TextCentered $gfx $label $script:fontUseCase $textBrush $x $y $w $h
    $map[$key] = @{ X = $x; Y = $y; W = $w; H = $h; Cx = $x + ($w / 2); Cy = $y + ($h / 2) }
}

function Draw-ArrowHead {
    param($gfx, [System.Drawing.Pen]$pen, [float]$x1, [float]$y1, [float]$x2, [float]$y2)
    $angle = [Math]::Atan2($y2 - $y1, $x2 - $x1)
    $len = 15
    $a1 = $angle + [Math]::PI * 0.82
    $a2 = $angle - [Math]::PI * 0.82
    $p1 = [System.Drawing.PointF]::new($x2 + $len * [Math]::Cos($a1), $y2 + $len * [Math]::Sin($a1))
    $p2 = [System.Drawing.PointF]::new($x2 + $len * [Math]::Cos($a2), $y2 + $len * [Math]::Sin($a2))
    $gfx.DrawLine($pen, $x2, $y2, $p1.X, $p1.Y)
    $gfx.DrawLine($pen, $x2, $y2, $p2.X, $p2.Y)
}

function Connect-ActorToUseCase {
    param($gfx, $actor, $uc, [System.Drawing.Pen]$pen)
    $gfx.DrawLine($pen, [float]$actor.Right, [float]$actor.Cy, [float]$uc.X, [float]$uc.Cy)
}

function Connect-Include {
    param($gfx, $from, $to, [System.Drawing.Pen]$pen)
    $x1 = [float]($from.X + $from.W)
    $y1 = [float]$from.Cy
    $x2 = [float]$to.X
    $y2 = [float]$to.Cy
    $gfx.DrawLine($pen, $x1, $y1, $x2, $y2)
    Draw-ArrowHead $gfx $pen $x1 $y1 $x2 $y2
}

function Render-RoleDiagram {
    param(
        [string]$fileName,
        [string]$title,
        [string]$actorLabel,
        [string]$clusterLabel,
        [array]$primaryUseCases,
        [array]$cryptoUseCases,
        [array]$includePairs,
        [System.Drawing.Brush]$roleBrush,
        [System.Drawing.Brush]$rolePanelBrush
    )

    $bmp = [System.Drawing.Bitmap]::new($script:width, $script:height)
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gfx.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $gfx.Clear([System.Drawing.Color]::White)

    $brushInk = [System.Drawing.SolidBrush]::new($script:ink)
    $brushMuted = [System.Drawing.SolidBrush]::new($script:muted)
    $brushBoundary = New-Brush 248 250 252
    $brushCrypto = New-Brush 236 254 255
    $brushWhite = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
    $penBoundary = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(30, 41, 59), 4)
    $penPanel = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(148, 163, 184), 2)
    $penActor = [System.Drawing.Pen]::new($script:ink, 5)
    $penUseCase = [System.Drawing.Pen]::new($script:ink, 3)
    $penAssoc = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(90, 148, 163, 184), 2)
    $penInclude = [System.Drawing.Pen]::new($script:blue, 2)
    $penInclude.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash

    Draw-TextCentered $gfx $title $script:fontTitle $brushInk 80 30 2040 60

    $gfx.FillRectangle($brushBoundary, [System.Drawing.Rectangle]::new(250, 190, 1790, 1190))
    $gfx.DrawRectangle($penBoundary, [System.Drawing.Rectangle]::new(250, 190, 1790, 1190))
    Draw-TextCentered $gfx 'Hệ thống công chứng điện tử tích hợp Blockchain và PQC' $script:fontPackage $brushInk 250 210 1790 48

    $actor = Draw-Actor $gfx $actorLabel 75 565 $penActor $brushInk

    Draw-RoundRect $gfx 310 330 820 780 18 $rolePanelBrush $penPanel
    $gfx.FillRectangle($brushWhite, [System.Drawing.Rectangle]::new(345, 330, 430, 56))
    $gfx.DrawRectangle($penPanel, [System.Drawing.Rectangle]::new(345, 330, 430, 56))
    Draw-TextCentered $gfx $clusterLabel $script:fontPackage $brushInk 345 330 430 56

    Draw-RoundRect $gfx 1190 330 780 780 18 (New-Brush 245 253 255) $penPanel
    $gfx.FillRectangle($brushWhite, [System.Drawing.Rectangle]::new(1225, 330, 400, 56))
    $gfx.DrawRectangle($penPanel, [System.Drawing.Rectangle]::new(1225, 330, 400, 56))
    Draw-TextCentered $gfx 'Blockchain & PQC nội bộ' $script:fontPackage $brushInk 1225 330 400 56

    $map = @{}
    $startX = 370
    $startY = 440
    $colGap = 375
    $rowGap = 125
    for ($i = 0; $i -lt $primaryUseCases.Count; $i++) {
        $uc = $primaryUseCases[$i]
        $x = $startX + (($i % 2) * $colGap)
        $y = $startY + ([Math]::Floor($i / 2) * $rowGap)
        Draw-UseCase $gfx $map $uc.Key $uc.Label $x $y 330 $roleBrush $penUseCase $brushInk
    }

    $cryptoPositions = @(
        @{ X = 1225; Y = 445 }, @{ X = 1570; Y = 445 },
        @{ X = 1225; Y = 580 }, @{ X = 1570; Y = 580 },
        @{ X = 1225; Y = 715 }, @{ X = 1570; Y = 715 },
        @{ X = 1225; Y = 850 }, @{ X = 1570; Y = 850 }
    )
    for ($i = 0; $i -lt $cryptoUseCases.Count; $i++) {
        $uc = $cryptoUseCases[$i]
        $pos = $cryptoPositions[$i]
        Draw-UseCase $gfx $map $uc.Key $uc.Label $pos.X $pos.Y 310 $brushCrypto $penUseCase $brushInk
    }

    foreach ($uc in $primaryUseCases) {
        Connect-ActorToUseCase $gfx $actor $map[$uc.Key] $penAssoc
    }
    foreach ($pair in $includePairs) {
        Connect-Include $gfx $map[$pair.From] $map[$pair.To] $penInclude
    }

    foreach ($uc in $primaryUseCases) {
        $entry = $map[$uc.Key]
        Draw-UseCase $gfx $map $uc.Key $uc.Label $entry.X $entry.Y $entry.W $roleBrush $penUseCase $brushInk
    }
    foreach ($uc in $cryptoUseCases) {
        $entry = $map[$uc.Key]
        Draw-UseCase $gfx $map $uc.Key $uc.Label $entry.X $entry.Y $entry.W $brushCrypto $penUseCase $brushInk
    }

    $flowY = 1215
    $gfx.FillRectangle((New-Brush 240 253 250), [System.Drawing.Rectangle]::new(330, $flowY, 1540, 95))
    $gfx.DrawRectangle([System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(45, 212, 191), 2), [System.Drawing.Rectangle]::new(330, $flowY, 1540, 95))
    Draw-TextCentered $gfx 'Văn bản công chứng' $script:fontSmallBold $brushInk 390 ($flowY + 23) 260 46
    Draw-TextCentered $gfx 'SHA-256 Hash' $script:fontSmallBold $brushInk 700 ($flowY + 23) 210 46
    Draw-TextCentered $gfx "Transaction`nchứa Hash" $script:fontSmallBold $brushInk 955 ($flowY + 12) 220 68
    Draw-TextCentered $gfx "Ký transaction`nECDSA + PQC" $script:fontSmallBold $brushInk 1220 ($flowY + 12) 260 68
    Draw-TextCentered $gfx 'Blockchain Verification' $script:fontSmallBold $brushInk 1530 ($flowY + 23) 300 46
    $penFlow = [System.Drawing.Pen]::new($script:teal, 4)
    $gfx.DrawLine($penFlow, 650, $flowY + 47, 700, $flowY + 47)
    Draw-ArrowHead $gfx $penFlow 650 ($flowY + 47) 700 ($flowY + 47)
    $gfx.DrawLine($penFlow, 910, $flowY + 47, 955, $flowY + 47)
    Draw-ArrowHead $gfx $penFlow 910 ($flowY + 47) 955 ($flowY + 47)
    $gfx.DrawLine($penFlow, 1175, $flowY + 47, 1220, $flowY + 47)
    Draw-ArrowHead $gfx $penFlow 1175 ($flowY + 47) 1220 ($flowY + 47)
    $gfx.DrawLine($penFlow, 1480, $flowY + 47, 1530, $flowY + 47)
    Draw-ArrowHead $gfx $penFlow 1480 ($flowY + 47) 1530 ($flowY + 47)

    $out = Join-Path $script:outDir $fileName
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $gfx.Dispose()
    $bmp.Dispose()
    Write-Output $out
}

$citizen = @(
    @{ Key = 'Login'; Label = 'Đăng ký / đăng nhập' },
    @{ Key = 'Create'; Label = "Tạo yêu cầu`ncông chứng" },
    @{ Key = 'Upload'; Label = 'Tải lên tài liệu' },
    @{ Key = 'Track'; Label = "Theo dõi`ntrạng thái hồ sơ" },
    @{ Key = 'OnlineReview'; Label = "Tham gia đối soát`ntrực tuyến" },
    @{ Key = 'CitizenSign'; Label = 'Ký số văn bản' },
    @{ Key = 'Pay'; Label = 'Thanh toán' },
    @{ Key = 'Receive'; Label = "Nhận văn bản`ncông chứng điện tử" },
    @{ Key = 'VerifyDoc'; Label = "Xác minh tài liệu`ncông chứng" }
)
$notary = @(
    @{ Key = 'Accept'; Label = 'Tiếp nhận hồ sơ' },
    @{ Key = 'Check'; Label = 'Kiểm tra hồ sơ' },
    @{ Key = 'RequestMore'; Label = "Yêu cầu bổ sung`nhồ sơ" },
    @{ Key = 'SetupReview'; Label = "Thiết lập phiên`nđối soát trực tuyến" },
    @{ Key = 'AuthParticipant'; Label = "Xác thực người`ntham gia" },
    @{ Key = 'NotarySign'; Label = "Ký số xác nhận`ncông chứng" },
        @{ Key = 'Record'; Label = "Ghi dữ liệu xác minh`nlên Blockchain" }
)
$admin = @(
    @{ Key = 'UserAdmin'; Label = 'Quản lý người dùng' },
    @{ Key = 'ServiceAdmin'; Label = "Quản lý dịch vụ`ncông chứng" },
    @{ Key = 'TemplateAdmin'; Label = 'Quản lý mẫu văn bản' },
    @{ Key = 'RequestAdmin'; Label = 'Quản lý hồ sơ' },
    @{ Key = 'Monitor'; Label = "Thống kê và giám sát`nhệ thống" }
)

$cryptoCitizen = @(
    @{ Key = 'Hash'; Label = "Tạo lại hash`ntài liệu nhận được" },
    @{ Key = 'Query'; Label = "Truy xuất transaction`nchứa hash" },
    @{ Key = 'Integrity'; Label = "Đối chiếu hash`nvới Blockchain" },
    @{ Key = 'ECDSA'; Label = "Xác minh chữ ký`nECDSA transaction" },
    @{ Key = 'PQSig'; Label = "Xác minh chữ ký`nML-DSA/PQC" }
)
$cryptoNotary = @(
    @{ Key = 'Hash'; Label = "Tạo hash văn bản`ncông chứng" },
    @{ Key = 'Tx'; Label = "Tạo transaction`nchứa hash" },
    @{ Key = 'Hybrid'; Label = "Ký transaction bằng`nHybrid Signature" },
    @{ Key = 'Store'; Label = "Ghi transaction`nlên Blockchain" },
    @{ Key = 'ECDSA'; Label = "Ký / xác minh`nECDSA transaction" },
    @{ Key = 'PQSig'; Label = "Ký / xác minh`nML-DSA/PQC" }
)
$cryptoAdmin = @(
    @{ Key = 'Query'; Label = "Truy xuất transaction`nBlockchain" },
    @{ Key = 'Integrity'; Label = "Kiểm tra hash và`ntính toàn vẹn" },
    @{ Key = 'ECDSA'; Label = "Xác minh chữ ký`nECDSA transaction" },
    @{ Key = 'PQSig'; Label = "Xác minh chữ ký`nML-DSA/PQC" }
)

Render-RoleDiagram `
    -fileName 'usecase-nguoi-dan.png' `
    -title 'Use Case Người dân trong hệ thống công chứng điện tử tích hợp Blockchain và PQC' `
    -actorLabel 'Người dân' `
    -clusterLabel 'Use Case Người dân' `
    -primaryUseCases $citizen `
    -cryptoUseCases $cryptoCitizen `
    -includePairs @(
        @{ From = 'VerifyDoc'; To = 'Hash' },
        @{ From = 'VerifyDoc'; To = 'Query' },
        @{ From = 'VerifyDoc'; To = 'Integrity' },
        @{ From = 'Integrity'; To = 'ECDSA' },
        @{ From = 'Integrity'; To = 'PQSig' }
    ) `
    -roleBrush (New-Brush 236 253 245) `
    -rolePanelBrush (New-Brush 248 255 251)

Render-RoleDiagram `
    -fileName 'usecase-cong-chung-vien.png' `
    -title 'Use Case Công chứng viên trong hệ thống công chứng điện tử tích hợp Blockchain và PQC' `
    -actorLabel 'Công chứng viên' `
    -clusterLabel 'Use Case Công chứng viên' `
    -primaryUseCases $notary `
    -cryptoUseCases $cryptoNotary `
    -includePairs @(
        @{ From = 'NotarySign'; To = 'Hash' },
        @{ From = 'Record'; To = 'Tx' },
        @{ From = 'Tx'; To = 'Hybrid' },
        @{ From = 'Hybrid'; To = 'Store' },
        @{ From = 'Hybrid'; To = 'ECDSA' },
        @{ From = 'Hybrid'; To = 'PQSig' }
    ) `
    -roleBrush (New-Brush 255 247 237) `
    -rolePanelBrush (New-Brush 255 251 245)

Render-RoleDiagram `
    -fileName 'usecase-quan-tri-vien.png' `
    -title 'Use Case Quản trị viên trong hệ thống công chứng điện tử tích hợp Blockchain và PQC' `
    -actorLabel 'Quản trị viên' `
    -clusterLabel 'Use Case Quản trị viên' `
    -primaryUseCases $admin `
    -cryptoUseCases $cryptoAdmin `
    -includePairs @(
        @{ From = 'Monitor'; To = 'Query' },
        @{ From = 'Monitor'; To = 'Integrity' },
        @{ From = 'Integrity'; To = 'ECDSA' },
        @{ From = 'Integrity'; To = 'PQSig' }
    ) `
    -roleBrush (New-Brush 245 243 255) `
    -rolePanelBrush (New-Brush 250 248 255)
