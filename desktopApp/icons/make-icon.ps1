param([string]$OutDir)
# Genera el icono provisional de la app: cuadrado redondeado con el degradado de la marca (morado -> azul) y "IRC".
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $OutDir | Out-Null

function New-IconBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::Transparent)
    $rect = New-Object System.Drawing.RectangleF 0, 0, $size, $size
    $r = [single]($size * 0.22)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc(0, 0, $d, $d, 180, 90)
    $path.AddArc($size - $d, 0, $d, $d, 270, 90)
    $path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
    $path.AddArc(0, $size - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $c1 = [System.Drawing.Color]::FromArgb(255, 0x6A, 0x11, 0xCB)
    $c2 = [System.Drawing.Color]::FromArgb(255, 0x25, 0x75, 0xFC)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $c1, $c2, 90
    $g.FillPath($brush, $path)
    $font = New-Object System.Drawing.Font 'Segoe UI', ([single]($size * 0.36)), ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = 'Center'; $sf.LineAlignment = 'Center'
    $g.DrawString('IRC', $font, [System.Drawing.Brushes]::White, $rect, $sf)
    $g.Dispose()
    return $bmp
}

# PNG 512 (Linux y bandeja)
$png = New-IconBitmap 512
$png.Save((Join-Path $OutDir 'icon.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$png.Dispose()

# ICO con PNG incrustados (16, 32, 48, 64, 128, 256)
$sizes = 16, 32, 48, 64, 128, 256
$images = foreach ($s in $sizes) {
    $b = New-IconBitmap $s
    $ms = New-Object System.IO.MemoryStream
    $b.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $b.Dispose()
    , $ms.ToArray()
}
$out = New-Object System.IO.MemoryStream
$w = New-Object System.IO.BinaryWriter $out
$w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $s = $sizes[$i]
    $dim = if ($s -ge 256) { 0 } else { $s }
    $w.Write([byte]$dim); $w.Write([byte]$dim); $w.Write([byte]0); $w.Write([byte]0)
    $w.Write([uint16]1); $w.Write([uint16]32)
    $w.Write([uint32]$images[$i].Length); $w.Write([uint32]$offset)
    $offset += $images[$i].Length
}
foreach ($img in $images) { $w.Write($img) }
$w.Flush()
[System.IO.File]::WriteAllBytes((Join-Path $OutDir 'icon.ico'), $out.ToArray())
Write-Output "ok"
