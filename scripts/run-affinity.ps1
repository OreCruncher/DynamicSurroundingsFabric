# Pins a dev client to some of the CPUs, for the "potato" run configs (see build.gradle). Started by the run task before
# the game's JVM; waits for a java process whose command line carries the marker, started since the run task began
# (passed in: the game's JVM can be up before PowerShell is), and sets its affinity (all its threads, present and
# future). Something during the game's start resets it, so it keeps watching until the game exits, pinning it again and
# logging each time. The IDE's run configs don't start it: run it by hand once the client is up, without -Since, e.g.
#   powershell -ExecutionPolicy Bypass -File scripts/run-affinity.ps1 -Marker dsurround.devRun=clientPotato -Mask 15 Gives up if no such process starts within three minutes.
param(
    [Parameter(Mandatory = $true)][string]$Marker,
    [Parameter(Mandatory = $true)][long]$Mask,
    # When the run task began, in milliseconds since 1970 (UTC); 0, any process with the marker
    [long]$Since = 0
)

function Log($message) {
    Write-Host "$((Get-Date).ToString('HH:mm:ss')) run-affinity: $message"
}

$started = [DateTimeOffset]::FromUnixTimeMilliseconds($Since).LocalDateTime
$deadline = (Get-Date).AddMinutes(3)
$found = $null
while (-not $found -and (Get-Date) -lt $deadline) {
    $found = Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" |
            Where-Object { $_.CommandLine -like "*$Marker*" -and $_.CreationDate -ge $started } |
            Select-Object -First 1
    if (-not $found)
        { Start-Sleep -Milliseconds 500 }
}
if (-not $found) {
    Log "no process with $Marker started; affinity not set"
    exit 1
}

$process = Get-Process -Id $found.ProcessId
$pins = 0
while (-not $process.HasExited) {
    try {
        $process.Refresh()
        $current = [long]$process.ProcessorAffinity
        if ($current -ne $Mask) {
            $process.ProcessorAffinity = [IntPtr]$Mask
            $pins++
            if ($pins -eq 1)
                { Log "process $($process.Id) pinned to CPU mask 0x$('{0:X}' -f $Mask)" }
            else
                { Log "process $($process.Id) affinity was reset to 0x$('{0:X}' -f $current); pinned again" }
        }
    } catch {
        # Exited between the check and the read
    }
    Start-Sleep -Seconds 1
}
Log "process $($process.Id) exited"
exit 0
