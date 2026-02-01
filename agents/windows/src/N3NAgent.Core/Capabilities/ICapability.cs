namespace N3NAgent.Core.Capabilities;

/// <summary>
/// Interface for agent capabilities.
/// </summary>
public interface ICapability
{
    /// <summary>
    /// Unique capability identifier.
    /// </summary>
    string Id { get; }

    /// <summary>
    /// Human-readable name.
    /// </summary>
    string Name { get; }

    /// <summary>
    /// Description of what this capability does.
    /// </summary>
    string Description { get; }

    /// <summary>
    /// Execute the capability with given arguments.
    /// </summary>
    Task<object?> ExecuteAsync(Dictionary<string, object> args);
}

/// <summary>
/// Registry of available capabilities.
/// </summary>
public class CapabilityRegistry
{
    private static readonly Lazy<CapabilityRegistry> _instance = new(() => new CapabilityRegistry());
    public static CapabilityRegistry Instance => _instance.Value;

    private readonly Dictionary<string, ICapability> _capabilities = new();

    private CapabilityRegistry()
    {
        // Register default capabilities
        Register(new ScreenCaptureCapability());
        Register(new ShellCapability());
        Register(new FileSystemCapability());
        Register(new ClipboardCapability());
        Register(new NotificationCapability());
    }

    public void Register(ICapability capability)
    {
        _capabilities[capability.Id] = capability;
    }

    public ICapability? Get(string id)
    {
        return _capabilities.TryGetValue(id, out var capability) ? capability : null;
    }

    public IEnumerable<ICapability> GetAll() => _capabilities.Values;

    public List<string> GetAllIds() => _capabilities.Keys.ToList();
}

/// <summary>
/// Capability execution error.
/// </summary>
public class CapabilityException : Exception
{
    public string Code { get; }

    public CapabilityException(string code, string message) : base(message)
    {
        Code = code;
    }

    public static CapabilityException NotFound(string id) =>
        new("NOT_FOUND", $"Capability not found: {id}");

    public static CapabilityException InvalidArguments(string message) =>
        new("INVALID_ARGUMENTS", message);

    public static CapabilityException ExecutionFailed(string message) =>
        new("EXECUTION_FAILED", message);

    public static CapabilityException PermissionDenied(string message) =>
        new("PERMISSION_DENIED", message);
}
