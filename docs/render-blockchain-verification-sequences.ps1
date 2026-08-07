Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$script:fontTitle = New-Object System.Drawing.Font('Arial', 40, [System.Drawing.FontStyle]::Bold)
$script:fontHeader = New-Object System.Drawing.Font('Arial', 21, [System.Drawing.FontStyle]::Bold)
$script:fontBody = New-Object System.Drawing.Font('Arial', 18, [System.Drawing.FontStyle]::Regular)
$script:fontSmall = New-Object System.Drawing.Font('Arial', 18, [System.Drawing.FontStyle]::Regular)
$script:fontNote = New-Object System.Drawing.Font('Arial', 18, [System.Drawing.FontStyle]::Italic)

$script:brushInk = [System.Drawing.Brushes]::Black
$script:brushMuted = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(72, 84, 105))
$script:brushBlue = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(37, 99, 235))
$script:brushHeader = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(248, 250, 252))
$script:brushNote = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 251, 235))
$script:penInk = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(17, 24, 39), 3)
$script:penMuted = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(148, 163, 184), 1.5)
$script:penBlue = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(37, 99, 235), 3)
$script:penNote = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(217, 119, 6), 2)

function New-Format($align, $lineAlign) {
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = $align
    $format.LineAlignment = $lineAlign
    $format.Trimming = [System.Drawing.StringTrimming]::Word
    $format.FormatFlags = 0
    return $format
}

function Draw-TextCentered($gfx, $text, $font, $brush, $x, $y, $w, $h) {
    $rect = New-Object System.Drawing.RectangleF -ArgumentList $x, $y, $w, $h
    $gfx.DrawString($text, $font, $brush, $rect, (New-Format 'Center' 'Center'))
}

function Draw-TextLeft($gfx, $text, $font, $brush, $x, $y, $w, $h) {
    $rect = New-Object System.Drawing.RectangleF -ArgumentList $x, $y, $w, $h
    $gfx.DrawString($text, $font, $brush, $rect, (New-Format 'Near' 'Center'))
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

function Draw-Actor($gfx, $x, $y, $label) {
    $head = New-Object System.Drawing.RectangleF -ArgumentList ($x - 14), $y, 28, 28
    $gfx.DrawEllipse($script:penInk, $head)
    $gfx.DrawLine($script:penInk, $x, $y + 28, $x, $y + 78)
    $gfx.DrawLine($script:penInk, $x - 32, $y + 48, $x + 32, $y + 48)
    $gfx.DrawLine($script:penInk, $x, $y + 78, $x - 32, $y + 120)
    $gfx.DrawLine($script:penInk, $x, $y + 78, $x + 32, $y + 120)
    Draw-TextCentered $gfx $label $script:fontHeader $script:brushInk ($x - 110) ($y + 132) 220 54
}

function Draw-SystemHeader($gfx, $x, $y, $w, $h, $label) {
    Draw-RoundedRect $gfx $script:penInk $script:brushHeader ($x - $w / 2) $y $w $h 8
    Draw-TextCentered $gfx $label $script:fontHeader $script:brushInk ($x - $w / 2) $y $w $h
}

function Draw-Database($gfx, $x, $y, $w, $h, $label) {
    $left = $x - $w / 2
    $gfx.FillRectangle($script:brushHeader, $left, $y + 18, $w, $h - 36)
    $gfx.DrawRectangle($script:penInk, $left, $y + 18, $w, $h - 36)
    $gfx.FillEllipse($script:brushHeader, $left, $y, $w, 36)
    $gfx.DrawEllipse($script:penInk, $left, $y, $w, 36)
    $gfx.DrawArc($script:penInk, $left, $y + $h - 36, $w, 36, 0, 180)
    $gfx.DrawLine($script:penInk, $left, $y + 18, $left, $y + $h - 18)
    $gfx.DrawLine($script:penInk, $left + $w, $y + 18, $left + $w, $y + $h - 18)
    Draw-TextCentered $gfx $label $script:fontHeader $script:brushInk $left ($y + 32) $w ($h - 36)
}

function Draw-Lifelines($gfx, $participants, $top, $bottom) {
    $dashPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(148, 163, 184), 1)
    $dashPen.DashPattern = @(3, 4)
    foreach ($p in $participants) {
        $gfx.DrawLine($dashPen, $p.X, $top, $p.X, $bottom)
    }
    $dashPen.Dispose()
}

function Draw-Activation($gfx, $x, $y, $h) {
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(226, 232, 240))
    $gfx.FillRectangle($brush, $x - 7, $y, 14, $h)
    $gfx.DrawRectangle($script:penMuted, $x - 7, $y, 14, $h)
    $brush.Dispose()
}

function Draw-Arrow($gfx, $fromX, $toX, $y, $label, $return = $false) {
    $pen = if ($return) { New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(75, 85, 99), 1.5) } else { $script:penBlue }
    if ($return) { $pen.DashPattern = @(6, 4) }

    $gfx.DrawLine($pen, $fromX, $y, $toX, $y)
    $dir = if ($toX -ge $fromX) { 1 } else { -1 }
    $arrowSize = 9
    $points = @(
        (New-Object System.Drawing.PointF -ArgumentList $toX, $y),
        (New-Object System.Drawing.PointF -ArgumentList ($toX - ($dir * $arrowSize)), ($y - 5)),
        (New-Object System.Drawing.PointF -ArgumentList ($toX - ($dir * $arrowSize)), ($y + 5))
    )
    $arrowBrush = $script:brushBlue
    if ($return) { $arrowBrush = $script:brushMuted }
    $gfx.FillPolygon($arrowBrush, $points)
    $labelX = [Math]::Min($fromX, $toX)
    $labelW = [Math]::Abs($toX - $fromX)
    Draw-TextCentered $gfx $label $script:fontSmall $script:brushMuted $labelX ($y - 76) $labelW 70

    if ($return) { $pen.Dispose() }
}

function Draw-Note($gfx, $x, $y, $w, $h, $text) {
    Draw-RoundedRect $gfx $script:penNote $script:brushNote $x $y $w $h 6
    Draw-TextLeft $gfx $text $script:fontNote $script:brushMuted ($x + 12) ($y + 6) ($w - 24) ($h - 12)
}

function Draw-Frame($gfx, $x, $y, $w, $h, $label) {
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(100, 116, 139), 1.5)
    $gfx.DrawRectangle($pen, $x, $y, $w, $h)
    $gfx.FillRectangle([System.Drawing.Brushes]::White, $x + 1, $y + 1, 170, 30)
    $gfx.DrawRectangle($pen, $x, $y, 170, 30)
    Draw-TextCentered $gfx $label $script:fontSmall $script:brushMuted $x $y 170 30
    $pen.Dispose()
}

function Render-Sequence($outputPath, $title, $participants, $messages, $notes, $frames, $width, $height) {
    $bmp = New-Object System.Drawing.Bitmap($width, $height)
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $gfx.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $gfx.Clear([System.Drawing.Color]::White)

    Draw-TextCentered $gfx $title $script:fontTitle $script:brushInk 80 28 ($width - 160) 70

    foreach ($p in $participants) {
        if ($p['Type'] -eq 'actor') {
            Draw-Actor $gfx ($p['X']) 112 ($p['Label'])
        } elseif ($p['Type'] -eq 'database') {
            Draw-Database $gfx ($p['X']) 120 225 108 ($p['Label'])
        } else {
            Draw-SystemHeader $gfx ($p['X']) 130 235 92 ($p['Label'])
        }
    }

    $lifelineTop = 270
    $lifelineBottom = $height - 80
    Draw-Lifelines $gfx $participants $lifelineTop $lifelineBottom

    foreach ($frame in $frames) {
        Draw-Frame $gfx ($frame['X']) ($frame['Y']) ($frame['W']) ($frame['H']) ($frame['Label'])
    }

    foreach ($note in $notes) {
        Draw-Note $gfx ($note['X']) ($note['Y']) ($note['W']) ($note['H']) ($note['Text'])
    }

    $index = @{}
    foreach ($p in $participants) { $index[$p['Id']] = $p['X'] }

    $messageYOffset = 80
    foreach ($m in $messages) {
        $messageY = $m['Y'] + $messageYOffset
        Draw-Arrow $gfx ($index[$m['From']]) ($index[$m['To']]) $messageY ($m['Label']) ($m['Return'])
        if ($m['Activate']) {
            Draw-Activation $gfx ($index[$m['To']]) ($messageY - 10) 36
        }
    }

    $bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $gfx.Dispose()
    $bmp.Dispose()
}

$participantsRecord = @(
    @{ Id = 'notary'; Type = 'actor'; X = 115; Label = 'Công chứng viên' },
    @{ Id = 'frontend'; Type = 'system'; X = 360; Label = "Frontend`nVideoRoomPage" },
    @{ Id = 'api'; Type = 'system'; X = 630; Label = "Backend API`nVideoSessionController" },
    @{ Id = 'video'; Type = 'system'; X = 910; Label = "VideoSession`nService" },
    @{ Id = 'blockchain'; Type = 'system'; X = 1190; Label = "Blockchain`nService" },
    @{ Id = 'besu'; Type = 'system'; X = 1480; Label = "Besu RPC /`nSmart Contract" },
    @{ Id = 'db'; Type = 'database'; X = 1740; Label = 'Database' }
)

$messagesRecord = @(
    @{ From = 'notary'; To = 'frontend'; Y = 340; Label = 'Ký số xác nhận công chứng'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 430; Label = 'POST /api/video/sessions/{id}/signatures'; Activate = $true },
    @{ From = 'api'; To = 'video'; Y = 520; Label = 'signDocument(...)'; Activate = $true },
    @{ From = 'video'; To = 'db'; Y = 615; Label = 'Tải VideoSession, NotaryRequest, văn bản trình chiếu'; Activate = $true },
    @{ From = 'db'; To = 'video'; Y = 690; Label = 'Session + hồ sơ + DRAFT_CONTRACT'; Return = $true },
    @{ From = 'video'; To = 'db'; Y = 795; Label = 'Tìm hoặc tạo SIGNED_DOCUMENT từ bản dự thảo'; Activate = $true },
    @{ From = 'db'; To = 'video'; Y = 870; Label = 'Signed document có fileHash ban đầu'; Return = $true },
    @{ From = 'video'; To = 'video'; Y = 980; Label = 'Kiểm tra người dân đã ký trước công chứng viên'; Activate = $true },
    @{ From = 'video'; To = 'video'; Y = 1070; Label = 'Stamp chữ ký lên PDF bằng PDFBox'; Activate = $true },
    @{ From = 'video'; To = 'db'; Y = 1160; Label = 'Cập nhật fileHash SHA-256, fileSize, Signature'; Activate = $true },
    @{ From = 'db'; To = 'video'; Y = 1240; Label = 'Đã lưu chữ ký và hash văn bản đã ký'; Return = $true },
    @{ From = 'video'; To = 'video'; Y = 1360; Label = 'clientSigned && notarySigned = true'; Activate = $true },
    @{ From = 'video'; To = 'blockchain'; Y = 1465; Label = 'anchorSignedDocument(...)'; Activate = $true },
    @{ From = 'blockchain'; To = 'db'; Y = 1555; Label = 'Kiểm tra transaction đã neo cho documentId'; Activate = $true },
    @{ From = 'db'; To = 'blockchain'; Y = 1635; Label = 'Chưa có transaction cho văn bản'; Return = $true },
    @{ From = 'blockchain'; To = 'besu'; Y = 1745; Label = 'notarize(documentHash, requestId, documentId)'; Activate = $true },
    @{ From = 'besu'; To = 'besu'; Y = 1845; Label = 'Validator xác thực transaction và ghi block'; Activate = $true },
    @{ From = 'besu'; To = 'blockchain'; Y = 1945; Label = 'Receipt: transactionHash, blockNumber, CONFIRMED'; Return = $true },
    @{ From = 'blockchain'; To = 'db'; Y = 2050; Label = 'Lưu BlockchainTransaction từ receipt on-chain'; Activate = $true },
    @{ From = 'db'; To = 'blockchain'; Y = 2130; Label = 'transactionHash + confirmedAt'; Return = $true },
    @{ From = 'blockchain'; To = 'video'; Y = 2235; Label = 'BlockchainTransaction'; Return = $true },
    @{ From = 'video'; To = 'db'; Y = 2335; Label = 'Đổi hồ sơ sang AWAITING_PAYMENT, tạo Payment PENDING'; Activate = $true },
    @{ From = 'db'; To = 'video'; Y = 2415; Label = 'Đã lưu trạng thái hồ sơ'; Return = $true },
    @{ From = 'video'; To = 'api'; Y = 2515; Label = 'SignVideoDocumentResponse(completed=true)'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 2605; Label = 'Response ký số văn bản thành công'; Return = $true },
    @{ From = 'frontend'; To = 'notary'; Y = 2695; Label = 'Hiển thị văn bản đã ký và mã transaction'; Return = $true }
)

$notesRecord = @(
    @{ X = 690; Y = 280; W = 720; H = 62; Text = 'Điều kiện trước: người dân đã ký điện tử lên PDF, văn bản đã ký có fileHash SHA-256.' }
)

$framesRecord = @()

Render-Sequence `
    -outputPath (Join-Path $PSScriptRoot 'sequence-luu-du-lieu-xac-minh-len-blockchain.png') `
    -title 'Sequence lưu dữ liệu xác minh lên Blockchain' `
    -participants $participantsRecord `
    -messages $messagesRecord `
    -notes $notesRecord `
    -frames $framesRecord `
    -width 1860 `
    -height 2940

$participantsVerify = @(
    @{ Id = 'viewer'; Type = 'actor'; X = 115; Label = 'Người dân' },
    @{ Id = 'frontend'; Type = 'system'; X = 370; Label = "Frontend`nChi tiết hồ sơ" },
    @{ Id = 'api'; Type = 'system'; X = 635; Label = "Backend API`nDocument" },
    @{ Id = 'doc'; Type = 'system'; X = 900; Label = "Document`nService" },
    @{ Id = 'crypto'; Type = 'system'; X = 1165; Label = "PQC / Signature`nVerifier" },
    @{ Id = 'blockchain'; Type = 'system'; X = 1430; Label = "Besu RPC /`nSmart Contract" },
    @{ Id = 'storage'; Type = 'database'; X = 1740; Label = "Database /`nMinIO" }
)

$messagesVerify = @(
    @{ From = 'viewer'; To = 'frontend'; Y = 330; Label = 'Mở văn bản công chứng trong chi tiết hồ sơ'; Activate = $true },
    @{ From = 'frontend'; To = 'api'; Y = 430; Label = 'GET /api/documents/{documentId}?inline=true'; Activate = $true },
    @{ From = 'api'; To = 'doc'; Y = 525; Label = 'loadSignedDocument(documentId, user)'; Activate = $true },
    @{ From = 'doc'; To = 'storage'; Y = 620; Label = 'Lấy metadata Document, NotaryRequest, Signature'; Activate = $true },
    @{ From = 'storage'; To = 'doc'; Y = 700; Label = 'SIGNED_DOCUMENT + filePath + fileHash đã lưu'; Return = $true },
    @{ From = 'doc'; To = 'storage'; Y = 805; Label = 'Đọc file PDF đã công chứng từ storage'; Activate = $true },
    @{ From = 'storage'; To = 'doc'; Y = 885; Label = 'PDF bytes'; Return = $true },
    @{ From = 'doc'; To = 'doc'; Y = 995; Label = 'Tính SHA-256 từ nội dung PDF hiện tại'; Activate = $true },
    @{ From = 'doc'; To = 'api'; Y = 1090; Label = 'Document bytes + currentHash + metadata'; Return = $true },
    @{ From = 'api'; To = 'crypto'; Y = 1195; Label = 'Xác minh chữ ký người dân và công chứng viên'; Activate = $true },
    @{ From = 'crypto'; To = 'api'; Y = 1280; Label = 'Kết quả chữ ký ECDSA / ML-DSA/PQC'; Return = $true },
    @{ From = 'api'; To = 'blockchain'; Y = 1390; Label = 'getVerification(documentId)'; Activate = $true },
    @{ From = 'blockchain'; To = 'api'; Y = 1490; Label = 'On-chain record: documentHash, blockNumber, status, chainId'; Return = $true },
    @{ From = 'api'; To = 'api'; Y = 1610; Label = 'So sánh currentHash với documentHash on-chain'; Activate = $true },
    @{ From = 'api'; To = 'api'; Y = 1725; Label = 'Kiểm tra chữ ký, status=CONFIRMED và chainId/network'; Activate = $true },
    @{ From = 'api'; To = 'frontend'; Y = 1885; Label = 'PDF + kết quả xác minh hợp lệ + transactionHash'; Return = $true },
    @{ From = 'frontend'; To = 'viewer'; Y = 1975; Label = 'Hiển thị văn bản và trạng thái đã xác minh'; Return = $true },
    @{ From = 'api'; To = 'frontend'; Y = 2140; Label = 'PDF + cảnh báo hash lệch hoặc blockchain chưa xác nhận'; Return = $true },
    @{ From = 'frontend'; To = 'viewer'; Y = 2230; Label = 'Hiển thị văn bản kèm cảnh báo toàn vẹn'; Return = $true }
)

$notesVerify = @(
    @{ X = 620; Y = 275; W = 760; H = 62; Text = 'Người dân chỉ mở văn bản đã công chứng; hệ thống tự lấy file và metadata rồi đối chiếu với dữ liệu đã neo trên Blockchain.' },
    @{ X = 875; Y = 1540; W = 525; H = 62; Text = 'Hash hợp lệ khi SHA-256(PDF lấy từ storage) bằng documentHash đã neo trên smart contract.' }
)

$framesVerify = @()

Render-Sequence `
    -outputPath (Join-Path $PSScriptRoot 'sequence-xac-minh-tai-lieu-cong-chung-dien-tu.png') `
    -title 'Sequence xác minh tài liệu công chứng điện tử' `
    -participants $participantsVerify `
    -messages $messagesVerify `
    -notes $notesVerify `
    -frames $framesVerify `
    -width 1860 `
    -height 2380

Write-Host 'Rendered sequence-luu-du-lieu-xac-minh-len-blockchain.png'
Write-Host 'Rendered sequence-xac-minh-tai-lieu-cong-chung-dien-tu.png'
