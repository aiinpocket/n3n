using System.Text;

namespace N3NAgent.Core.Capabilities;

/// <summary>
/// Capability for file system operations.
/// </summary>
public class FileSystemCapability : ICapability
{
    public string Id => "fs.operation";
    public string Name => "File System";
    public string Description => "Read, write, list, and manage files and directories";

    private const long MaxFileSize = 10 * 1024 * 1024; // 10MB

    public async Task<object?> ExecuteAsync(Dictionary<string, object> args)
    {
        if (!args.TryGetValue("operation", out var opObj) || opObj == null)
            throw CapabilityException.InvalidArguments("Missing 'operation' argument");

        var operation = opObj.ToString()!.ToLower();

        return operation switch
        {
            "read" => await ReadFile(args),
            "write" => await WriteFile(args),
            "list" => await ListDirectory(args),
            "exists" => CheckExists(args),
            "delete" => await DeletePath(args),
            "mkdir" => await CreateDirectory(args),
            "info" => GetFileInfo(args),
            "copy" => await CopyPath(args),
            "move" => await MovePath(args),
            _ => throw CapabilityException.InvalidArguments($"Unknown operation: {operation}")
        };
    }

    private async Task<Dictionary<string, object>> ReadFile(Dictionary<string, object> args)
    {
        var path = GetRequiredPath(args);

        if (!File.Exists(path))
            throw CapabilityException.ExecutionFailed($"File not found: {path}");

        var fileInfo = new FileInfo(path);
        if (fileInfo.Length > MaxFileSize)
            throw CapabilityException.ExecutionFailed($"File too large: {fileInfo.Length} bytes (max {MaxFileSize})");

        var encoding = args.TryGetValue("encoding", out var enc) ? enc.ToString() ?? "utf-8" : "utf-8";
        var asBase64 = args.TryGetValue("base64", out var b64) && Convert.ToBoolean(b64);

        if (asBase64)
        {
            var bytes = await File.ReadAllBytesAsync(path);
            return new Dictionary<string, object>
            {
                ["content"] = Convert.ToBase64String(bytes),
                ["encoding"] = "base64",
                ["size"] = bytes.Length
            };
        }
        else
        {
            var content = await File.ReadAllTextAsync(path, GetEncoding(encoding));
            return new Dictionary<string, object>
            {
                ["content"] = content,
                ["encoding"] = encoding,
                ["size"] = fileInfo.Length
            };
        }
    }

    private async Task<Dictionary<string, object>> WriteFile(Dictionary<string, object> args)
    {
        var path = GetRequiredPath(args);

        if (!args.TryGetValue("content", out var contentObj) || contentObj == null)
            throw CapabilityException.InvalidArguments("Missing 'content' argument");

        var content = contentObj.ToString()!;
        var encoding = args.TryGetValue("encoding", out var enc) ? enc.ToString() ?? "utf-8" : "utf-8";
        var asBase64 = args.TryGetValue("base64", out var b64) && Convert.ToBoolean(b64);
        var append = args.TryGetValue("append", out var app) && Convert.ToBoolean(app);

        // Ensure directory exists
        var dir = Path.GetDirectoryName(path);
        if (!string.IsNullOrEmpty(dir) && !Directory.Exists(dir))
            Directory.CreateDirectory(dir);

        if (asBase64)
        {
            var bytes = Convert.FromBase64String(content);
            if (append && File.Exists(path))
            {
                await using var fs = new FileStream(path, FileMode.Append);
                await fs.WriteAsync(bytes);
            }
            else
            {
                await File.WriteAllBytesAsync(path, bytes);
            }

            return new Dictionary<string, object>
            {
                ["success"] = true,
                ["path"] = path,
                ["size"] = bytes.Length
            };
        }
        else
        {
            if (append)
                await File.AppendAllTextAsync(path, content, GetEncoding(encoding));
            else
                await File.WriteAllTextAsync(path, content, GetEncoding(encoding));

            return new Dictionary<string, object>
            {
                ["success"] = true,
                ["path"] = path,
                ["size"] = new FileInfo(path).Length
            };
        }
    }

    private async Task<Dictionary<string, object>> ListDirectory(Dictionary<string, object> args)
    {
        var path = GetRequiredPath(args);

        if (!Directory.Exists(path))
            throw CapabilityException.ExecutionFailed($"Directory not found: {path}");

        var includeHidden = args.TryGetValue("includeHidden", out var ih) && Convert.ToBoolean(ih);
        var recursive = args.TryGetValue("recursive", out var rec) && Convert.ToBoolean(rec);

        var entries = new List<Dictionary<string, object>>();
        var searchOption = recursive ? SearchOption.AllDirectories : SearchOption.TopDirectoryOnly;

        await Task.Run(() =>
        {
            foreach (var dir in Directory.GetDirectories(path, "*", searchOption))
            {
                var dirInfo = new DirectoryInfo(dir);
                if (!includeHidden && (dirInfo.Attributes & FileAttributes.Hidden) != 0)
                    continue;

                entries.Add(new Dictionary<string, object>
                {
                    ["name"] = dirInfo.Name,
                    ["path"] = dirInfo.FullName,
                    ["type"] = "directory",
                    ["created"] = dirInfo.CreationTimeUtc.ToString("O"),
                    ["modified"] = dirInfo.LastWriteTimeUtc.ToString("O")
                });
            }

            foreach (var file in Directory.GetFiles(path, "*", searchOption))
            {
                var fileInfo = new FileInfo(file);
                if (!includeHidden && (fileInfo.Attributes & FileAttributes.Hidden) != 0)
                    continue;

                entries.Add(new Dictionary<string, object>
                {
                    ["name"] = fileInfo.Name,
                    ["path"] = fileInfo.FullName,
                    ["type"] = "file",
                    ["size"] = fileInfo.Length,
                    ["created"] = fileInfo.CreationTimeUtc.ToString("O"),
                    ["modified"] = fileInfo.LastWriteTimeUtc.ToString("O")
                });
            }
        });

        return new Dictionary<string, object>
        {
            ["path"] = path,
            ["entries"] = entries,
            ["count"] = entries.Count
        };
    }

    private Dictionary<string, object> CheckExists(Dictionary<string, object> args)
    {
        var path = GetRequiredPath(args);

        var fileExists = File.Exists(path);
        var dirExists = Directory.Exists(path);

        return new Dictionary<string, object>
        {
            ["exists"] = fileExists || dirExists,
            ["isFile"] = fileExists,
            ["isDirectory"] = dirExists,
            ["path"] = path
        };
    }

    private async Task<Dictionary<string, object>> DeletePath(Dictionary<string, object> args)
    {
        var path = GetRequiredPath(args);
        var recursive = args.TryGetValue("recursive", out var rec) && Convert.ToBoolean(rec);

        await Task.Run(() =>
        {
            if (File.Exists(path))
            {
                File.Delete(path);
            }
            else if (Directory.Exists(path))
            {
                Directory.Delete(path, recursive);
            }
            else
            {
                throw CapabilityException.ExecutionFailed($"Path not found: {path}");
            }
        });

        return new Dictionary<string, object>
        {
            ["success"] = true,
            ["path"] = path
        };
    }

    private async Task<Dictionary<string, object>> CreateDirectory(Dictionary<string, object> args)
    {
        var path = GetRequiredPath(args);

        await Task.Run(() => Directory.CreateDirectory(path));

        return new Dictionary<string, object>
        {
            ["success"] = true,
            ["path"] = path
        };
    }

    private Dictionary<string, object> GetFileInfo(Dictionary<string, object> args)
    {
        var path = GetRequiredPath(args);

        if (File.Exists(path))
        {
            var info = new FileInfo(path);
            return new Dictionary<string, object>
            {
                ["path"] = info.FullName,
                ["name"] = info.Name,
                ["extension"] = info.Extension,
                ["type"] = "file",
                ["size"] = info.Length,
                ["created"] = info.CreationTimeUtc.ToString("O"),
                ["modified"] = info.LastWriteTimeUtc.ToString("O"),
                ["accessed"] = info.LastAccessTimeUtc.ToString("O"),
                ["isReadOnly"] = info.IsReadOnly,
                ["isHidden"] = (info.Attributes & FileAttributes.Hidden) != 0
            };
        }
        else if (Directory.Exists(path))
        {
            var info = new DirectoryInfo(path);
            return new Dictionary<string, object>
            {
                ["path"] = info.FullName,
                ["name"] = info.Name,
                ["type"] = "directory",
                ["created"] = info.CreationTimeUtc.ToString("O"),
                ["modified"] = info.LastWriteTimeUtc.ToString("O"),
                ["accessed"] = info.LastAccessTimeUtc.ToString("O"),
                ["isHidden"] = (info.Attributes & FileAttributes.Hidden) != 0
            };
        }
        else
        {
            throw CapabilityException.ExecutionFailed($"Path not found: {path}");
        }
    }

    private async Task<Dictionary<string, object>> CopyPath(Dictionary<string, object> args)
    {
        var source = GetRequiredPath(args, "source");
        var destination = GetRequiredPath(args, "destination");
        var overwrite = args.TryGetValue("overwrite", out var ow) && Convert.ToBoolean(ow);

        await Task.Run(() =>
        {
            if (File.Exists(source))
            {
                var destDir = Path.GetDirectoryName(destination);
                if (!string.IsNullOrEmpty(destDir) && !Directory.Exists(destDir))
                    Directory.CreateDirectory(destDir);

                File.Copy(source, destination, overwrite);
            }
            else if (Directory.Exists(source))
            {
                CopyDirectory(source, destination, overwrite);
            }
            else
            {
                throw CapabilityException.ExecutionFailed($"Source not found: {source}");
            }
        });

        return new Dictionary<string, object>
        {
            ["success"] = true,
            ["source"] = source,
            ["destination"] = destination
        };
    }

    private async Task<Dictionary<string, object>> MovePath(Dictionary<string, object> args)
    {
        var source = GetRequiredPath(args, "source");
        var destination = GetRequiredPath(args, "destination");
        var overwrite = args.TryGetValue("overwrite", out var ow) && Convert.ToBoolean(ow);

        await Task.Run(() =>
        {
            if (File.Exists(source))
            {
                var destDir = Path.GetDirectoryName(destination);
                if (!string.IsNullOrEmpty(destDir) && !Directory.Exists(destDir))
                    Directory.CreateDirectory(destDir);

                if (overwrite && File.Exists(destination))
                    File.Delete(destination);

                File.Move(source, destination);
            }
            else if (Directory.Exists(source))
            {
                if (overwrite && Directory.Exists(destination))
                    Directory.Delete(destination, true);

                Directory.Move(source, destination);
            }
            else
            {
                throw CapabilityException.ExecutionFailed($"Source not found: {source}");
            }
        });

        return new Dictionary<string, object>
        {
            ["success"] = true,
            ["source"] = source,
            ["destination"] = destination
        };
    }

    private static void CopyDirectory(string source, string destination, bool overwrite)
    {
        var dir = new DirectoryInfo(source);

        if (!Directory.Exists(destination))
            Directory.CreateDirectory(destination);

        foreach (var file in dir.GetFiles())
        {
            var targetPath = Path.Combine(destination, file.Name);
            file.CopyTo(targetPath, overwrite);
        }

        foreach (var subDir in dir.GetDirectories())
        {
            var targetPath = Path.Combine(destination, subDir.Name);
            CopyDirectory(subDir.FullName, targetPath, overwrite);
        }
    }

    private static string GetRequiredPath(Dictionary<string, object> args, string key = "path")
    {
        if (!args.TryGetValue(key, out var pathObj) || pathObj == null)
            throw CapabilityException.InvalidArguments($"Missing '{key}' argument");

        return pathObj.ToString()!;
    }

    private static Encoding GetEncoding(string name)
    {
        return name.ToLower() switch
        {
            "utf-8" or "utf8" => Encoding.UTF8,
            "utf-16" or "utf16" or "unicode" => Encoding.Unicode,
            "ascii" => Encoding.ASCII,
            "utf-32" or "utf32" => Encoding.UTF32,
            _ => Encoding.UTF8
        };
    }
}
