using System.Text.Json;
using System.Text.Json.Serialization;

namespace N3NAgent.Core.Protocol;

/// <summary>
/// Gateway message types
/// </summary>
public enum GatewayMessageType
{
    [JsonPropertyName("req")] Request,
    [JsonPropertyName("res")] Response,
    [JsonPropertyName("event")] Event
}

/// <summary>
/// Base gateway message
/// </summary>
public class GatewayMessage
{
    [JsonPropertyName("type")]
    public string Type { get; set; } = "";

    [JsonPropertyName("id")]
    public string? Id { get; set; }
}

/// <summary>
/// Gateway request message
/// </summary>
public class GatewayRequest : GatewayMessage
{
    [JsonPropertyName("method")]
    public string Method { get; set; } = "";

    [JsonPropertyName("params")]
    public Dictionary<string, object>? Params { get; set; }

    public GatewayRequest()
    {
        Type = "req";
        Id = Guid.NewGuid().ToString();
    }

    public static GatewayRequest Create(string method, Dictionary<string, object>? parameters = null)
    {
        return new GatewayRequest
        {
            Method = method,
            Params = parameters
        };
    }

    public static GatewayRequest HandshakeAuth(string deviceId, string deviceToken, List<string> capabilities)
    {
        return Create("handshake.auth", new Dictionary<string, object>
        {
            ["deviceId"] = deviceId,
            ["deviceToken"] = deviceToken,
            ["capabilities"] = capabilities,
            ["client"] = new Dictionary<string, object>
            {
                ["version"] = "1.0.0",
                ["platform"] = "windows",
                ["arch"] = Environment.Is64BitOperatingSystem ? "x64" : "x86"
            }
        });
    }

    public static GatewayRequest NodeRegister(List<string> capabilities)
    {
        return Create("node.register", new Dictionary<string, object>
        {
            ["capabilities"] = capabilities
        });
    }

    public static GatewayRequest Ping()
    {
        return Create("ping");
    }

    public static GatewayRequest InvokeResult(string invokeId, object? result, string? error)
    {
        var parameters = new Dictionary<string, object>
        {
            ["invokeId"] = invokeId
        };

        if (result != null)
            parameters["result"] = result;

        if (error != null)
            parameters["error"] = error;

        return Create("node.invoke.result", parameters);
    }
}

/// <summary>
/// Gateway response message
/// </summary>
public class GatewayResponse : GatewayMessage
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("result")]
    public Dictionary<string, object>? Result { get; set; }

    [JsonPropertyName("error")]
    public ResponseError? Error { get; set; }

    public GatewayResponse()
    {
        Type = "res";
    }

    public class ResponseError
    {
        [JsonPropertyName("code")]
        public string Code { get; set; } = "";

        [JsonPropertyName("message")]
        public string Message { get; set; } = "";
    }

    public static GatewayResponse CreateSuccess(string id, Dictionary<string, object> result)
    {
        return new GatewayResponse
        {
            Id = id,
            Success = true,
            Result = result
        };
    }

    public static GatewayResponse CreateError(string id, string code, string message)
    {
        return new GatewayResponse
        {
            Id = id,
            Success = false,
            Error = new ResponseError { Code = code, Message = message }
        };
    }
}

/// <summary>
/// Gateway event message
/// </summary>
public class GatewayEvent : GatewayMessage
{
    [JsonPropertyName("event")]
    public string Event { get; set; } = "";

    [JsonPropertyName("payload")]
    public Dictionary<string, object>? Payload { get; set; }

    public GatewayEvent()
    {
        Type = "event";
    }

    public static GatewayEvent Create(string eventName, Dictionary<string, object>? payload = null)
    {
        return new GatewayEvent
        {
            Event = eventName,
            Payload = payload
        };
    }

    // Common events
    public const string HandshakeChallenge = "handshake.challenge";
    public const string Connected = "connected";
    public const string Disconnected = "disconnected";
    public const string NodeInvoke = "node.invoke";
    public const string Ping = "ping";
    public const string Pong = "pong";
}

/// <summary>
/// Node invoke request (from server)
/// </summary>
public class NodeInvokeRequest
{
    [JsonPropertyName("invokeId")]
    public string InvokeId { get; set; } = "";

    [JsonPropertyName("capability")]
    public string Capability { get; set; } = "";

    [JsonPropertyName("args")]
    public Dictionary<string, object>? Args { get; set; }
}
