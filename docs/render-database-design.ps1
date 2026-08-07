Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = 'Stop'

$fontTitle = New-Object System.Drawing.Font('Arial', 24, [System.Drawing.FontStyle]::Bold)
$fontGroup = New-Object System.Drawing.Font('Arial', 15, [System.Drawing.FontStyle]::Bold)
$fontTable = New-Object System.Drawing.Font('Arial', 12, [System.Drawing.FontStyle]::Bold)
$fontCol = New-Object System.Drawing.Font('Consolas', 9, [System.Drawing.FontStyle]::Regular)
$fontSmall = New-Object System.Drawing.Font('Arial', 9, [System.Drawing.FontStyle]::Regular)

$ink = [System.Drawing.Color]::FromArgb(17, 24, 39)
$muted = [System.Drawing.Color]::FromArgb(75, 85, 99)
$line = [System.Drawing.Color]::FromArgb(100, 116, 139)
$blue = [System.Drawing.Color]::FromArgb(37, 99, 235)
$green = [System.Drawing.Color]::FromArgb(22, 163, 74)
$orange = [System.Drawing.Color]::FromArgb(217, 119, 6)
$purple = [System.Drawing.Color]::FromArgb(124, 58, 237)
$red = [System.Drawing.Color]::FromArgb(220, 38, 38)

$brushInk = New-Object System.Drawing.SolidBrush($ink)
$brushMuted = New-Object System.Drawing.SolidBrush($muted)
$brushWhite = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$brushUser = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(239, 246, 255))
$brushWorkflow = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(240, 253, 244))
$brushConfig = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 247, 237))
$brushSecurity = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(250, 245, 255))
$brushBlockchain = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(254, 242, 242))

$penInk = New-Object System.Drawing.Pen($ink, 1.6)
$penMuted = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(148, 163, 184), 1.2)
$penBlue = New-Object System.Drawing.Pen($blue, 2.2)
$penGreen = New-Object System.Drawing.Pen($green, 2.2)
$penOrange = New-Object System.Drawing.Pen($orange, 2.2)
$penPurple = New-Object System.Drawing.Pen($purple, 2.2)
$penRed = New-Object System.Drawing.Pen($red, 2.2)
$penRel = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(71, 85, 105), 1.8)

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

function Draw-Group($gfx, $x, $y, $w, $h, $title, $pen, $brush) {
    Draw-RoundedRect $gfx $pen $brush $x $y $w $h 16
    Draw-Text $gfx $title $fontGroup $brushInk ($x + 18) ($y + 10) ($w - 36) 34 'Near' 'Center'
}

function Draw-Table($gfx, $x, $y, $w, $title, $cols, $pen, $headerColor) {
    $rowH = 23
    $headerH = 36
    $h = $headerH + ($cols.Count * $rowH) + 12
    Draw-RoundedRect $gfx $pen $brushWhite $x $y $w $h 8
    $headerBrush = New-Object System.Drawing.SolidBrush($headerColor)
    $gfx.FillRectangle($headerBrush, $x + 1, $y + 1, $w - 2, $headerH)
    Draw-Text $gfx $title $fontTable $brushInk ($x + 8) ($y + 4) ($w - 16) 28
    $gfx.DrawLine($pen, $x, $y + $headerH, $x + $w, $y + $headerH)
    $cy = $y + $headerH + 6
    foreach ($c in $cols) {
        $brush = $brushMuted
        if ($c.StartsWith('PK')) { $brush = $brushInk }
        elseif ($c.StartsWith('FK')) { $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(30, 64, 175)) }
        elseif ($c.StartsWith('UK')) { $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(146, 64, 14)) }
        Draw-Text $gfx $c $fontCol $brush ($x + 10) $cy ($w - 20) $rowH 'Near' 'Center'
        $cy += $rowH
    }
    $headerBrush.Dispose()
    return @{ X = $x; Y = $y; W = $w; H = $h; Cx = $x + ($w / 2); Cy = $y + ($h / 2) }
}

function Draw-Line($gfx, $from, $to, $label = '', $pen = $penRel) {
    $x1 = $from[0]; $y1 = $from[1]; $x2 = $to[0]; $y2 = $to[1]
    $midX = ($x1 + $x2) / 2
    $gfx.DrawLine($pen, $x1, $y1, $midX, $y1)
    $gfx.DrawLine($pen, $midX, $y1, $midX, $y2)
    $gfx.DrawLine($pen, $midX, $y2, $x2, $y2)
    $size = 7
    $gfx.DrawLine($pen, $x2, $y2, $x2 - $size, $y2 - $size)
    $gfx.DrawLine($pen, $x2, $y2, $x2 - $size, $y2 + $size)
    if ($label) {
        Draw-Text $gfx $label $fontSmall $brushMuted ([Math]::Min($x1, $x2)) ([Math]::Min($y1, $y2) - 18) ([Math]::Abs($x2 - $x1) + 80) 22
    }
}

function Draw-Relation($gfx, $points, $pen) {
    for ($i = 0; $i -lt $points.Count - 1; $i++) {
        $gfx.DrawLine($pen, $points[$i][0], $points[$i][1], $points[$i + 1][0], $points[$i + 1][1])
    }
    $last = $points[$points.Count - 1]
    $prev = $points[$points.Count - 2]
    $dx = $last[0] - $prev[0]
    $dy = $last[1] - $prev[1]
    $size = 7
    if ([Math]::Abs($dx) -ge [Math]::Abs($dy)) {
        $dir = if ($dx -ge 0) { 1 } else { -1 }
        $gfx.DrawLine($pen, $last[0], $last[1], $last[0] - ($dir * $size), $last[1] - $size)
        $gfx.DrawLine($pen, $last[0], $last[1], $last[0] - ($dir * $size), $last[1] + $size)
    } else {
        $dir = if ($dy -ge 0) { 1 } else { -1 }
        $gfx.DrawLine($pen, $last[0], $last[1], $last[0] - $size, $last[1] - ($dir * $size))
        $gfx.DrawLine($pen, $last[0], $last[1], $last[0] + $size, $last[1] - ($dir * $size))
    }
}

$width = 2300
$height = 1900
$bmp = New-Object System.Drawing.Bitmap($width, $height)
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gfx.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$gfx.Clear([System.Drawing.Color]::White)

Draw-Text $gfx 'Thiết kế cơ sở dữ liệu hệ thống công chứng điện tử' $fontTitle $brushInk 80 25 2140 60

Draw-Group $gfx 60 110 460 640 'Người dùng và bảo mật' $penBlue $brushUser
Draw-Group $gfx 570 110 760 1580 'Nghiệp vụ công chứng điện tử' $penGreen $brushWorkflow
Draw-Group $gfx 1390 110 480 920 'Cấu hình dịch vụ và tài liệu' $penOrange $brushConfig
Draw-Group $gfx 1390 1085 480 520 'Thông báo và nhật ký' $penPurple $brushSecurity
Draw-Group $gfx 1930 360 320 495 'Blockchain Verification' $penRed $brushBlockchain

$tables = @{}

$tables.users = Draw-Table $gfx 95 180 360 'users' @(
    'PK user_id UUID',
    'UK email, phone_number',
    'password_hash',
    'role: CLIENT / NOTARY / ADMIN',
    'verification_status',
    'is_active',
    'created_at, updated_at'
) $penBlue ([System.Drawing.Color]::FromArgb(219, 234, 254))

$tables.user_profiles = Draw-Table $gfx 95 410 360 'user_profiles' @(
    'PK/FK user_id -> users',
    'UK identity_number',
    'full_name, date_of_birth',
    'gender, nationality',
    'place_of_origin',
    'place_of_residence',
    'issue_date, issue_place'
) $penBlue ([System.Drawing.Color]::FromArgb(219, 234, 254))

$tables.refresh_tokens = Draw-Table $gfx 95 640 170 'refresh_tokens' @(
    'PK id',
    'user_id',
    'token / jti',
    'expires_at',
    'revoked'
) $penBlue ([System.Drawing.Color]::FromArgb(219, 234, 254))

$tables.revoked_tokens = Draw-Table $gfx 285 640 170 'revoked_tokens' @(
    'PK id',
    'token / jti',
    'expires_at',
    'created_at'
) $penBlue ([System.Drawing.Color]::FromArgb(219, 234, 254))

$tables.notary_requests = Draw-Table $gfx 795 190 315 'notary_requests' @(
    'PK request_id UUID',
    'FK client_id -> users',
    'FK notary_id -> users',
    'FK selected_template_id',
    'service_type ONLINE/OFFLINE',
    'contract_type, description',
    'status',
    'created_at, updated_at'
) $penGreen ([System.Drawing.Color]::FromArgb(220, 252, 231))

$tables.appointments = Draw-Table $gfx 625 520 300 'appointments' @(
    'PK appointment_id UUID',
    'FK request_id -> notary_requests',
    'scheduled_time',
    'meeting_url',
    'physical_address',
    'status',
    'created_at'
) $penGreen ([System.Drawing.Color]::FromArgb(220, 252, 231))

$tables.video_sessions = Draw-Table $gfx 625 790 300 'video_sessions' @(
    'PK session_id UUID',
    'FK appointment_id -> appointments',
    'session_token, room_id',
    'meeting_url',
    'status',
    'joined_at, ended_at',
    'duration_seconds'
) $penGreen ([System.Drawing.Color]::FromArgb(220, 252, 231))

$tables.documents = Draw-Table $gfx 1010 520 285 'documents' @(
    'PK document_id UUID',
    'FK request_id -> notary_requests',
    'FK doc_type -> document_types',
    'file_path / storage key',
    'file_hash SHA-256',
    'content_type, file_size',
    'created_at, updated_at'
) $penGreen ([System.Drawing.Color]::FromArgb(220, 252, 231))

$tables.signatures = Draw-Table $gfx 1010 805 285 'signatures' @(
    'PK signature_id UUID',
    'FK document_id -> documents',
    'FK user_id -> users',
    'signature_value',
    'cert_serial',
    'signed_at',
    'is_valid'
) $penGreen ([System.Drawing.Color]::FromArgb(220, 252, 231))

$tables.payments = Draw-Table $gfx 785 1080 315 'payments' @(
    'PK payment_id UUID',
    'FK request_id -> notary_requests',
    'amount',
    'payment_status',
    'transaction_reference',
    'bank_code / transfer_content',
    'created_at'
) $penGreen ([System.Drawing.Color]::FromArgb(220, 252, 231))

$tables.notary_service_types = Draw-Table $gfx 1430 180 390 'notary_service_types' @(
    'PK id UUID',
    'UK service_code',
    'name, description',
    'base_price',
    'requires_template',
    'is_active',
    'created_at, updated_at'
) $penOrange ([System.Drawing.Color]::FromArgb(255, 237, 213))

$tables.contract_templates = Draw-Table $gfx 1430 430 390 'contract_templates' @(
    'PK id UUID',
    'FK service_type_id',
    'name',
    'file_url',
    'version',
    'is_active',
    'created_at, updated_at'
) $penOrange ([System.Drawing.Color]::FromArgb(255, 237, 213))

$tables.document_types = Draw-Table $gfx 1430 675 180 'document_types' @(
    'PK code',
    'name',
    'source',
    'allowed_file_group',
    'is_active',
    'sort_order'
) $penOrange ([System.Drawing.Color]::FromArgb(255, 237, 213))

$tables.notary_service_document_requirements = Draw-Table $gfx 1635 675 235 'service_document_requirements' @(
    'PK id UUID',
    'FK service_type_id',
    'FK doc_type',
    'sort_order',
    'UK service + doc_type'
) $penOrange ([System.Drawing.Color]::FromArgb(255, 237, 213))

$tables.notary_offices = Draw-Table $gfx 1430 850 390 'notary_offices' @(
    'PK office_id UUID',
    'name',
    'address, phone',
    'working_hours',
    'is_active',
    'created_at, updated_at'
) $penOrange ([System.Drawing.Color]::FromArgb(255, 237, 213))

$tables.notifications = Draw-Table $gfx 1430 1155 390 'notifications' @(
    'PK notification_id UUID',
    'FK user_id -> users',
    'FK request_id -> notary_requests',
    'FK appointment_id -> appointments',
    'title, message, type',
    'is_read, read_at',
    'created_at'
) $penPurple ([System.Drawing.Color]::FromArgb(243, 232, 255))

$tables.audit_logs = Draw-Table $gfx 1430 1400 390 'audit_logs' @(
    'PK log_id UUID',
    'FK user_id -> users',
    'action, table_name',
    'record_id',
    'old_value JSONB',
    'new_value JSONB',
    'timestamp'
) $penPurple ([System.Drawing.Color]::FromArgb(243, 232, 255))

$tables.blockchain_transactions = Draw-Table $gfx 1965 455 250 'blockchain_transactions' @(
    'PK transaction_id UUID',
    'FK request_id',
    'FK document_id',
    'document_hash',
    'UK transaction_hash',
    'block_number',
    'network_name, chain_id',
    'status, node_name',
    'confirmed_at'
) $penRed ([System.Drawing.Color]::FromArgb(254, 226, 226))

Draw-Text $gfx 'PK: Primary Key    FK: Foreign Key    UK: Unique Key' $fontSmall $brushMuted 80 1820 700 28 'Near'
Draw-Text $gfx 'Ghi chú: PDF và file người dùng lưu bằng file_path/storage key; blockchain_transactions chỉ lưu hash và metadata xác minh.' $fontSmall $brushMuted 820 1820 980 28 'Near'

# Relationships
function Anchor($name, $dx, $dy) {
    $t = $tables[$name]
    return @(([double]$t['X'] + $dx), ([double]$t['Y'] + $dy))
}

# Core user and authentication relations
Draw-Relation $gfx @((Anchor 'users' 180 $tables['users']['H']), (Anchor 'user_profiles' 180 0)) $penBlue
Draw-Relation $gfx @((Anchor 'users' 180 $tables['users']['H']), @(275, 628), @(180, 628), (Anchor 'refresh_tokens' 85 0)) $penBlue
Draw-Relation $gfx @((Anchor 'users' 360 66), @(560, 246), (Anchor 'notary_requests' 0 66)) $penBlue

# Main notarization workflow
Draw-Relation $gfx @((Anchor 'notary_requests' 90 $tables['notary_requests']['H']), @(885, 410), @(775, 410), (Anchor 'appointments' 150 0)) $penGreen
Draw-Relation $gfx @((Anchor 'appointments' 150 $tables['appointments']['H']), (Anchor 'video_sessions' 150 0)) $penGreen
Draw-Relation $gfx @((Anchor 'notary_requests' $tables['notary_requests']['W'] 132), @(1160, 322), @(1160, 500), @(990, 500), @(990, 586), (Anchor 'documents' 0 66)) $penGreen
Draw-Relation $gfx @((Anchor 'documents' 140 $tables['documents']['H']), (Anchor 'signatures' 140 0)) $penGreen
Draw-Relation $gfx @((Anchor 'notary_requests' 150 $tables['notary_requests']['H']), (Anchor 'payments' 160 0)) $penGreen

# Service configuration relations
Draw-Relation $gfx @((Anchor 'notary_service_types' 195 $tables['notary_service_types']['H']), (Anchor 'contract_templates' 195 0)) $penOrange
Draw-Relation $gfx @((Anchor 'notary_service_types' $tables['notary_service_types']['W'] 96), @(1850, 276), @(1850, 675), (Anchor 'notary_service_document_requirements' 118 0)) $penOrange
Draw-Relation $gfx @((Anchor 'document_types' $tables['document_types']['W'] 88), (Anchor 'notary_service_document_requirements' 0 88)) $penOrange

# Blockchain verification relation. Routed around the configuration area, not through tables.
Draw-Relation $gfx @((Anchor 'documents' $tables['documents']['W'] 118), @(1340, 638), @(1340, 78), @(1935, 78), @(1935, 500), (Anchor 'blockchain_transactions' 0 100)) $penRed

$output = Join-Path $PSScriptRoot 'thiet-ke-co-so-du-lieu-he-thong-cong-chung-dien-tu.png'
$bmp.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)

$gfx.Dispose()
$bmp.Dispose()

Write-Host 'Rendered thiet-ke-co-so-du-lieu-he-thong-cong-chung-dien-tu.png'
