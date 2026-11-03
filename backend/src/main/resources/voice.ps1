param([ValidateSet('status','listen','speak','test')] [string]$Action)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding=New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Speech
$recognizer=$null; $speaker=$null; $audio=$null
try {
    $engines=@([System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers())
    if ($Action -eq 'status') {
        $speaker=New-Object System.Speech.Synthesis.SpeechSynthesizer
        @{listeningAvailable=($engines.Count -gt 0); voices=@($speaker.GetInstalledVoices() | Where-Object Enabled | ForEach-Object {$_.VoiceInfo.Name}); language=if($engines.Count){$engines[0].Culture.Name}else{''}} | ConvertTo-Json -Compress
    } elseif ($Action -eq 'speak') {
        $request=[Console]::In.ReadToEnd() | ConvertFrom-Json
        if (!$request.text -or $request.text.Length -gt 2000) { throw 'Invalid text' }
        $speaker=New-Object System.Speech.Synthesis.SpeechSynthesizer
        if ($request.voice) {
            try { $speaker.SelectVoice([string]$request.voice) } catch {}
        }
        if ($null -ne $request.rate) {
            try { $speaker.Rate = [int]$request.rate } catch {}
        } else {
            $speaker.Rate = 0
        }
        $speaker.Speak([string]$request.text)
        '{"spoken":true}'
    } else {
        if (!$engines.Count) { throw 'Install a Windows speech language' }
        $recognizer=New-Object System.Speech.Recognition.SpeechRecognitionEngine($engines[0])
        $recognizer.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))
        $recognizer.BabbleTimeout=[TimeSpan]::FromSeconds(8)
        $recognizer.EndSilenceTimeout=[TimeSpan]::FromMilliseconds(800)
        if($Action -eq 'test') {
            $speaker=New-Object System.Speech.Synthesis.SpeechSynthesizer
            $audio=New-Object System.IO.MemoryStream
            $speaker.SetOutputToWaveStream($audio)
            $speaker.Speak('Hello Jarvis. What time is it?')
            $speaker.SetOutputToNull()
            $audio.Position=0
            $recognizer.SetInputToWaveStream($audio)
        } else { $recognizer.SetInputToDefaultAudioDevice() }
        $result=$recognizer.Recognize([TimeSpan]::FromSeconds(8))
        @{text=if($result){$result.Text}else{''}; confidence=if($result){$result.Confidence}else{0}} | ConvertTo-Json -Compress
    }
} catch { [Console]::Error.WriteLine('Windows speech unavailable. Check the speech language, microphone and output device.'); exit 1 }
finally { if($recognizer){$recognizer.Dispose()}; if($speaker){$speaker.Dispose()}; if($audio){$audio.Dispose()} }
