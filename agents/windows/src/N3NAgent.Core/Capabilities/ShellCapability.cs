using System.Diagnostics;

namespace N3NAgent.Core.Capabilities;

/// <summary>
/// Capability for executing shell commands.
/// </summary>
public class ShellCapability : ICapability
{
    public string Id => "shell.execute";
    public string Name => "Shell Execute";
    public string Description => "Execute shell commands and return output";

    private const int MaxTimeout = 300; // seconds

    public async Task<object?> ExecuteAsync(Dictionary<string, object> args)
    {
        if (!args.TryGetValue("command", out var commandObj) || commandObj == null)
            throw CapabilityException.InvalidArguments("Missing 'command' argument");

        var command = commandObj.ToString()!;
        var timeout = args.TryGetValue("timeout", out var t) ? Math.Min(Convert.ToInt32(t), MaxTimeout) : 60;
        var workingDirectory = args.TryGetValue("workingDirectory", out var wd) ? wd.ToString() : null;

        return await ExecuteCommand(command, timeout, workingDirectory);
    }

    private async Task<Dictionary<string, object>> ExecuteCommand(string command, int timeout, string? workingDirectory)
    {
        using var process = new Process();

        process.StartInfo = new ProcessStartInfo
        {
            FileName = "cmd.exe",
            Arguments = $"/c {command}",
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
            WorkingDirectory = workingDirectory ?? Environment.CurrentDirectory
        };

        var stdout = new StringBuilder();
        var stderr = new StringBuilder();

        process.OutputDataReceived += (_, e) =>
        {
            if (e.Data != null) stdout.AppendLine(e.Data);
        };

        process.ErrorDataReceived += (_, e) =>
        {
            if (e.Data != null) stderr.AppendLine(e.Data);
        };

        try
        {
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();

            var completed = await Task.Run(() => process.WaitForExit(timeout * 1000));

            if (!completed)
            {
                process.Kill();
                throw CapabilityException.ExecutionFailed("Command timed out");
            }

            return new Dictionary<string, object>
            {
                ["exitCode"] = process.ExitCode,
                ["stdout"] = stdout.ToString(),
                ["stderr"] = stderr.ToString(),
                ["success"] = process.ExitCode == 0
            };
        }
        catch (Exception ex) when (ex is not CapabilityException)
        {
            throw CapabilityException.ExecutionFailed($"Failed to execute command: {ex.Message}");
        }
    }
}
