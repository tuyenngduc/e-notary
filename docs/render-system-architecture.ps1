Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$fontTitle = New-Object System.Drawing.Font('Arial', 24, [System.Drawing.FontStyle]::Bold)
$fontSection = New-Object System.Drawing.Font('Arial', 16, [System.Drawing.FontStyle]::Bold)
$fontBox = New-Object System.Drawing.Font('Arial', 12, [System.Drawing.FontStyle]::Bold)
$fontSmall = New-Object System.Drawing.Font('Arial', 10, [System.Drawing.FontStyle]::Regular)
$fontNote = New-Object System.Drawing.Font('Arial', 9, [System.Drawing.FontStyle]::Italic)

$colorInk = [System.Drawing.Color]::FromArgb(17, 24, 39)
$colorMuted = [System.Drawing.Color]::FromArgb(75, 85, 99)
$colorBlue = [System.Drawing.Color]::FromArgb(37, 99, 235)
$colorGreen = [System.Drawing.Color]::FromArgb(22, 163, 74)
$colorOrange = [System.Drawing.Color]::FromArgb(217, 119, 6)
$colorAppBg = [System.Drawing.Color]::FromArgb(239, 246, 255)
$colorVerifyBg = [System.Drawing.Color]::FromArgb(240, 253, 244)
$colorBesuBg = [System.Drawing.Color]::FromArgb(255, 247, 237)
$colorUserBg = [System.Drawing.Color]::FromArgb(248, 250, 252)
$colorWhite = [System.Drawing.Color]::White

$brushInk = New-Object System.Drawing.SolidBrush($colorInk)
$brushMuted = New-Object System.Drawing.SolidBrush($colorMuted)
$brushBlue = New-Object System.Drawing.SolidBrush($colorBlue)
$brushGreen = New-Object System.Drawing.SolidBrush($colorGreen)
$brushOrange = New-Object System.Drawing.SolidBrush($colorOrange)
$brushAppBg = New-Object System.Drawing.SolidBrush($colorAppBg)
$brushVerifyBg = New-Object System.Drawing.SolidBrush($colorVerifyBg)
$brushBesuBg = New-Object System.Drawing.SolidBrush($colorBesuBg)
$brushUserBg = New-Object System.Drawing.SolidBrush($colorUserBg)
$brushWhite = New-Object System.Drawing.SolidBrush($colorWhite)

$penInk = New-Object System.Drawing.Pen($colorInk, 2)
$penMuted = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(148, 163, 184), 1.5)
$penBlue = New-Object System.Drawing.Pen($colorBlue, 2.5)
$penGreen = New-Object System.Drawing.Pen($colorGreen, 2.5)
$penOrange = New-Object System.Drawing.Pen($colorOrange, 2.5)
$penDashed = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(100, 116, 139), 1.5)
$penDashed.DashPattern = @(6, 5)

function New-Format($align, $lineAlign) {
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = $align
    $format.LineAlignment = $lineAlign
    $format.Trimming = [System.Drawing.StringTrimming]::Word
    return $format
}

function Draw-Text($gfx, $text, $font, $brush, $x, $y, $w, $h, $align = 'Center', $lineAlign = 'Center') {
    $rect = New-Object System.Drawing.RectangleF -ArgumentList $x, $y, $w, $h
    $gfx.DrawString($text, $font, $brush, $rect, (New-Format $align $lineAlign))
}

function Draw-RoundedRect($gfx, $pen, $brush, $x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    if ($brush) { $gfx.FillPath($brush, $path) }
    $gfx.DrawPath($pen, $path)
    $path.Dispose()
}

function Draw-Box($gfx, $x, $y, $w, $h, $title, $subtitle, $borderPen, $fillBrush) {
    Draw-RoundedRect $gfx $borderPen $fillBrush $x $y $w $h 10
    $titleHeight = 30
    if ($title.Contains("`n") -or $title.Length -gt 18) {
        $titleHeight = 44
    }
    Draw-Text $gfx $title $fontBox $brushInk ($x + 8) ($y + 8) ($w - 16) $titleHeight
    if ($subtitle) {
        $subtitleY = $y + 12 + $titleHeight
        Draw-Text $gfx $subtitle $fontSmall $brushMuted ($x + 12) $subtitleY ($w - 24) ($h - $titleHeight - 18)
    }
}

function Draw-Group($gfx, $x, $y, $w, $h, $title, $subtitle, $pen, $brush) {
    Draw-RoundedRect $gfx $pen $brush $x $y $w $h 14
    Draw-Text $gfx $title $fontSection $brushInk ($x + 18) ($y + 14) ($w - 36) 30 'Near' 'Center'
    Draw-Text $gfx $subtitle $fontNote $brushMuted ($x + 18) ($y + 45) ($w - 36) 28 'Near' 'Center'
}

function Draw-Actor($gfx, $x, $y, $label, $roleText) {
    $gfx.DrawEllipse($penInk, $x + 44, $y, 28, 28)
    $gfx.DrawLine($penInk, $x + 58, $y + 28, $x + 58, $y + 76)
    $gfx.DrawLine($penInk, $x + 25, $y + 48, $x + 91, $y + 48)
    $gfx.DrawLine($penInk, $x + 58, $y + 76, $x + 26, $y + 118)
    $gfx.DrawLine($penInk, $x + 58, $y + 76, $x + 90, $y + 118)
    Draw-Text $gfx $label $fontBox $brushInk ($x - 22) ($y + 126) 160 42
    Draw-Text $gfx $roleText $fontSmall $brushMuted ($x - 24) ($y + 168) 170 54
}

function Draw-Arrow($gfx, $fromX, $fromY, $toX, $toY, $label, $pen, $brush, $labelOffsetY = -26) {
    $gfx.DrawLine($pen, $fromX, $fromY, $toX, $toY)
    $angle = [Math]::Atan2($toY - $fromY, $toX - $fromX)
    $arrowLen = 12
    $a1 = $angle + [Math]::PI * 0.82
    $a2 = $angle - [Math]::PI * 0.82
    $points = @(
        (New-Object System.Drawing.PointF -ArgumentList $toX, $toY),
        (New-Object System.Drawing.PointF -ArgumentList ($toX + $arrowLen * [Math]::Cos($a1)), ($toY + $arrowLen * [Math]::Sin($a1))),
        (New-Object System.Drawing.PointF -ArgumentList ($toX + $arrowLen * [Math]::Cos($a2)), ($toY + $arrowLen * [Math]::Sin($a2)))
    )
    $gfx.FillPolygon($brush, $points)
    if ($label) {
        $lx = [Math]::Min($fromX, $toX)
        $ly = [Math]::Min($fromY, $toY) + $labelOffsetY
        $lw = [Math]::Max(180, [Math]::Abs($toX - $fromX))
        Draw-Text $gfx $label $fontSmall $brushMuted $lx $ly $lw 42
    }
}

function Draw-Database($gfx, $x, $y, $w, $h, $label, $subtitle) {
    $gfx.FillRectangle($brushWhite, $x, $y + 18, $w, $h - 36)
    $gfx.DrawRectangle($penMuted, $x, $y + 18, $w, $h - 36)
    $gfx.FillEllipse($brushWhite, $x, $y, $w, 36)
    $gfx.DrawEllipse($penMuted, $x, $y, $w, 36)
    $gfx.DrawArc($penMuted, $x, $y + $h - 36, $w, 36, 0, 180)
    $gfx.DrawLine($penMuted, $x, $y + 18, $x, $y + $h - 18)
    $gfx.DrawLine($penMuted, $x + $w, $y + 18, $x + $w, $y + $h - 18)
    Draw-Text $gfx $label $fontBox $brushInk $x ($y + 10) $w 28
    Draw-Text $gfx $subtitle $fontSmall $brushMuted ($x + 8) ($y + 54) ($w - 16) 46
}

$width = 1900
$height = 1280
$bmp = New-Object System.Drawing.Bitmap($width, $height)
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gfx.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$gfx.Clear([System.Drawing.Color]::White)

Draw-Text $gfx 'Kiến trúc tổng thể hệ thống công chứng điện tử tích hợp Blockchain và chữ ký hậu lượng tử' $fontTitle $brushInk 90 28 1720 56

# Users
Draw-Group $gfx 55 135 250 850 'Người dùng hệ thống' 'Actors tương tác với application layer' $penMuted $brushUserBg
Draw-Actor $gfx 115 205 'Người dân' "Tạo yêu cầu`ntải tài liệu`nký văn bản"
Draw-Actor $gfx 115 470 'Công chứng viên' "Kiểm tra hồ sơ`nxác thực từ xa`nký xác nhận"
Draw-Actor $gfx 115 735 'Quản trị viên' "Quản trị dịch vụ`nngười dùng`nhạ tầng"

# Application layer
Draw-Group $gfx 365 125 950 500 'Website công chứng điện tử' 'Application layer - xử lý nghiệp vụ công chứng điện tử' $penBlue $brushAppBg
Draw-Box $gfx 410 220 185 78 'Frontend ReactJS' 'Cổng người dân, công chứng viên, quản trị viên' $penMuted $brushWhite
Draw-Box $gfx 690 220 215 78 'Backend Spring Boot' 'API nghiệp vụ, phân quyền, điều phối quy trình' $penMuted $brushWhite
Draw-Box $gfx 985 220 150 78 'Signing Service' 'Ký điện tử/ký số văn bản công chứng' $penMuted $brushWhite
Draw-Box $gfx 1150 205 145 95 "Video Call`nService" 'Đối soát trực tuyến' $penMuted $brushWhite

Draw-Database $gfx 420 380 170 120 'PostgreSQL' "Metadata hồ sơ, tài liệu, chữ ký"
Draw-Database $gfx 665 380 170 120 'MinIO Storage' "PDF gốc và PDF công chứng"
Draw-Box $gfx 910 390 160 90 'Payment Service' 'Thanh toán phí công chứng' $penMuted $brushWhite
Draw-Box $gfx 1100 390 180 90 'Email / Notification' 'Email lịch hẹn, thông báo trạng thái' $penMuted $brushWhite

# Verification layer
Draw-Group $gfx 365 705 950 420 'Blockchain Verification Layer' 'Verification layer - xác minh toàn vẹn sau khi phát hành' $penGreen $brushVerifyBg
Draw-Box $gfx 410 805 165 82 'Hash Service' 'Tạo SHA-256 từ Final notarized PDF' $penMuted $brushWhite
Draw-Box $gfx 610 805 240 90 "PQC / Hybrid`nSignature Service" 'Ký transaction bằng ECDSA + PQC' $penMuted $brushWhite
Draw-Box $gfx 885 805 180 82 'Blockchain Service' 'Tạo, gửi và theo dõi transaction' $penMuted $brushWhite
Draw-Box $gfx 1095 805 190 90 "Blockchain`nTransaction Storage" 'Lưu transactionHash, blockNumber, status' $penMuted $brushWhite

Draw-RoundedRect $gfx $penGreen $brushWhite 455 960 760 122 10
Draw-Text $gfx "Final notarized PDF -> SHA-256 Hash -> Hybrid Signature (ECDSA + PQC)`n-> Blockchain Transaction -> Blockchain Verification" $fontBox $brushInk 475 980 720 48
Draw-Text $gfx 'Luồng nổi bật từ văn bản công chứng sang dữ liệu xác minh blockchain' $fontSmall $brushMuted 475 1032 720 30

# Besu side
Draw-Group $gfx 1390 180 425 735 'Hyperledger Besu Network' 'Permissioned blockchain network và smart contract xác minh' $penOrange $brushBesuBg
Draw-Box $gfx 1460 295 285 90 'Smart Contract' "Ghi documentHash, documentId, timestamp, transactionHash" $penMuted $brushWhite
Draw-Box $gfx 1460 445 285 88 'Besu Validator Nodes' 'Xác thực transaction và ghi block' $penMuted $brushWhite
Draw-Box $gfx 1460 595 285 88 'Blockchain Ledger' 'Chỉ lưu dữ liệu xác minh, không lưu PDF' $penMuted $brushWhite
Draw-Box $gfx 1460 745 285 88 'Blockchain Verification' 'Truy vấn hash on-chain để đối chiếu tài liệu' $penMuted $brushWhite

# Main arrows
Draw-Arrow $gfx 305 560 410 260 '' $penBlue $brushBlue

Draw-Arrow $gfx 595 260 690 260 '' $penBlue $brushBlue
Draw-Arrow $gfx 905 260 985 260 '' $penBlue $brushBlue
Draw-Arrow $gfx 905 292 1150 292 '' $penBlue $brushBlue
Draw-Arrow $gfx 800 302 520 380 '' $penDashed $brushMuted
Draw-Arrow $gfx 800 302 750 380 '' $penDashed $brushMuted
Draw-Arrow $gfx 800 302 990 390 '' $penDashed $brushMuted
Draw-Arrow $gfx 800 302 1190 390 '' $penDashed $brushMuted

Draw-Arrow $gfx 750 500 490 805 '' $penGreen $brushGreen
Draw-Arrow $gfx 575 850 610 850 '' $penGreen $brushGreen
Draw-Arrow $gfx 850 850 885 850 '' $penGreen $brushGreen
Draw-Arrow $gfx 1065 850 1095 850 '' $penGreen $brushGreen
Draw-Arrow $gfx 1065 805 1460 340 '' $penOrange $brushOrange
Draw-Arrow $gfx 1600 385 1600 445 '' $penOrange $brushOrange
Draw-Arrow $gfx 1600 533 1600 595 '' $penOrange $brushOrange
Draw-Arrow $gfx 1460 788 1270 844 '' $penDashed $brushMuted

# Important notes
Draw-Box $gfx 1380 965 455 95 'Nguyên tắc lưu trữ' "PDF công chứng lưu ở MinIO; metadata lưu ở PostgreSQL. Blockchain chỉ lưu hash tài liệu, transactionHash, timestamp và documentId." $penOrange $brushWhite
Draw-Box $gfx 1380 1085 455 95 'Vai trò PQC' "PQC không ký trực tiếp vào PDF. PQC được tích hợp ở transaction Blockchain để tăng khả năng chống nguy cơ lượng tử." $penGreen $brushWhite

$output = Join-Path $PSScriptRoot 'kien-truc-tong-the-he-thong-cong-chung-dien-tu-blockchain-pqc.png'
$bmp.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)

$gfx.Dispose()
$bmp.Dispose()

Write-Host "Rendered kien-truc-tong-the-he-thong-cong-chung-dien-tu-blockchain-pqc.png"
