param([int]$Port = 8084)
& (Join-Path $PSScriptRoot 'run-classification-copy.ps1') -Port $Port -Database '.cache/react-phase3b2a-data/aica-phase3c2.mv.db'
