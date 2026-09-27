param([int]$Port = 8085)
& (Join-Path $PSScriptRoot 'run-classification-copy.ps1') -Port $Port -Database '.cache/react-phase3b2a-data/aica-phase3c3.mv.db'
