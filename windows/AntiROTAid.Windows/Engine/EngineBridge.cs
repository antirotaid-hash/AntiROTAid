using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;

namespace AntiROTAid.Windows.Engine;

/// <summary>
/// The REAL AntiROTAid engine, reused verbatim from the phone (product/engine_ara,
/// the same Python package the Android app drives through its Kotlin EngineBridge).
/// This C# host spawns `python engine/ara_cli.py`, a persistent stdin/stdout JSON
/// loop, and mirrors the Android `Bridge.call(method, args_json)` contract:
///   request : {"id":N,"method":M,"args":{...}}\n
///   response: {"id":N,"envelope":"{ok,api_version,data|error}"}\n
/// No card logic lives on the C# side — every deck comes from the engine, exactly
/// like the phone. Calls are serialized (@Synchronized on Android → a lock here).
/// </summary>
public sealed class EngineBridge : IDisposable
{
    private static readonly Lazy<EngineBridge> _shared = new(() => new EngineBridge());
    public static EngineBridge Shared => _shared.Value;

    private readonly object _gate = new();
    private Process? _proc;
    private StreamWriter? _stdin;
    private StreamReader? _stdout;
    private int _nextId = 1;
    public bool Ready { get; private set; }
    public string? StartError { get; private set; }

    // A single engine call must never hang the caller forever: reads are bounded, and a
    // wedged/dead engine is killed and restarted so later calls recover instead of failing
    // silently. File imports (PDF/PPTX extraction) can be slow, so the budget is generous.
    private const int CallTimeoutMs = 30000;
    private const int ReadyTimeoutMs = 20000;
    private int _restarts;
    private const int MaxRestarts = 5;   // stop thrashing on a genuinely broken engine

    private EngineBridge() { TryStart(); }

    /// <summary>Locate the engine folder (dev tree or packaged next to the exe).</summary>
    private static string? EngineDir()
    {
        foreach (var cand in new[]
        {
            Path.Combine(AppContext.BaseDirectory, "engine"),                 // packaged
            Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "engine"), // dev: windows/engine
            @"C:\mindlift\windows\engine",                                    // dev fallback
        })
        {
            var p = Path.GetFullPath(cand);
            if (File.Exists(Path.Combine(p, "ara_cli.py"))) return p;
        }
        return null;
    }

    /// <summary>Find a Python interpreter: a bundled one first, then the system.</summary>
    private static string PythonExe()
    {
        var bundled = Path.Combine(AppContext.BaseDirectory, "python", "python.exe");
        if (File.Exists(bundled)) return bundled;
        return "python"; // system PATH (dev)
    }

    private void TryStart()
    {
        try
        {
            var dir = EngineDir();
            if (dir is null) { StartError = "engine folder not found"; return; }

            var psi = new ProcessStartInfo
            {
                FileName = PythonExe(),
                Arguments = "ara_cli.py",
                WorkingDirectory = dir,
                RedirectStandardInput = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true,
                // UTF-8 with NO BOM: a BOM on the first line poisons json.loads.
                StandardInputEncoding = new UTF8Encoding(false),
                StandardOutputEncoding = new UTF8Encoding(false),
            };
            psi.EnvironmentVariables["PYTHONIOENCODING"] = "utf-8";
            psi.EnvironmentVariables["PYTHONUTF8"] = "1";

            _proc = Process.Start(psi);
            if (_proc is null) { StartError = "could not start python"; return; }
            _stdin = _proc.StandardInput;
            _stdout = _proc.StandardOutput;

            // Continuously drain stderr on a background thread. If we never read it, a chatty
            // engine can fill the OS pipe buffer and Python blocks on its next write → deadlock.
            var err = _proc.StandardError;
            var t = new Thread(() => { try { while (err.ReadLine() != null) { } } catch { } })
            { IsBackground = true, Name = "ara-engine-stderr" };
            t.Start();

            // First line signals readiness (or an engine_missing envelope). Bounded so a
            // broken interpreter can't freeze app launch — after the timeout we give up.
            var reader = _stdout;
            var readyTask = Task.Run(() => reader.ReadLine());
            if (!readyTask.Wait(ReadyTimeoutMs)) { StartError = "engine start timed out"; return; }
            var first = readyTask.Result;
            if (first != null && first.Contains("\"ready\": true")) Ready = true;
            else { StartError = first ?? "no ready signal"; }
        }
        catch (Exception ex) { StartError = $"{ex.GetType().Name}: {ex.Message}"; }
    }

    /// <summary>Ensure the engine process is alive; if it exited, restart it (bounded).</summary>
    private void EnsureAliveLocked()
    {
        if (_proc is { HasExited: false } && Ready) return;
        RestartLocked();
    }

    /// <summary>Tear down the current engine process and start a fresh one. Called when a call
    /// times out (wedged) or the pipe closed (crashed), so the next call has a live engine.</summary>
    private void RestartLocked()
    {
        try { _proc?.Kill(true); } catch { }
        try { _proc?.Dispose(); } catch { }
        _proc = null; _stdin = null; _stdout = null; Ready = false;
        if (_restarts++ >= MaxRestarts) { StartError = "engine restart limit reached"; return; }
        TryStart();
    }

    /// <summary>Call an engine method; returns the parsed envelope, or null on failure. Reads
    /// are bounded by <see cref="CallTimeoutMs"/>: a wedged or dead engine is killed and
    /// restarted (so the next call recovers) rather than hanging the caller forever. The
    /// blocking ReadLine runs on a worker thread, never directly on the caller's thread.</summary>
    public JsonElement? Call(string method, object? args = null)
    {
        lock (_gate)
        {
            EnsureAliveLocked();
            if (!Ready || _stdin is null || _stdout is null) return null;

            int id = _nextId++;
            try
            {
                var req = JsonSerializer.Serialize(new { id, method, args = args ?? new { } });
                _stdin.Write(req);
                _stdin.Write('\n');
                _stdin.Flush();
            }
            catch { RestartLocked(); return null; }   // pipe broke → restart for next call

            var reader = _stdout;
            var readTask = Task.Run<JsonElement?>(() =>
            {
                string? line;
                while ((line = reader.ReadLine()) != null)
                {
                    try
                    {
                        using var doc = JsonDocument.Parse(line);
                        var root = doc.RootElement;
                        if (root.TryGetProperty("id", out var ridEl) && ridEl.GetInt32() == id
                            && root.TryGetProperty("envelope", out var envEl))
                            return JsonDocument.Parse(envEl.GetString() ?? "").RootElement.Clone();
                    }
                    catch { /* skip a malformed line, keep reading for our id */ }
                }
                return null; // stream closed → engine died
            });

            if (!readTask.Wait(CallTimeoutMs)) { RestartLocked(); return null; } // wedged
            var result = readTask.Result;
            if (result is null) RestartLocked();   // died mid-call → restart for next call
            return result;
        }
    }

    /// <summary>Convenience: call and return envelope.data if ok, else null.</summary>
    public JsonElement? CallData(string method, object? args = null)
    {
        var env = Call(method, args);
        if (env is { } e && e.TryGetProperty("ok", out var ok) && ok.GetBoolean()
            && e.TryGetProperty("data", out var data))
            return data.Clone();
        return null;
    }

    /// <summary>Off-thread call, so UI handlers can await a slow import without freezing.</summary>
    public Task<JsonElement?> CallDataAsync(string method, object? args = null)
        => Task.Run(() => CallData(method, args));

    public void Dispose()
    {
        try { _stdin?.Close(); _proc?.Kill(true); _proc?.Dispose(); } catch { }
    }
}
