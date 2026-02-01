using System.CommandLine;
using N3NAgent.Core;
using N3NAgent.Core.Network;

namespace N3NAgent.CLI;

class Program
{
    static async Task<int> Main(string[] args)
    {
        var rootCommand = new RootCommand("N3N Agent for Windows - Remote control agent for N3N platform");

        // pair command
        var pairCommand = new Command("pair", "Pair this agent with N3N platform");
        var urlOption = new Option<string>(
            "--url",
            description: "Platform URL (e.g., https://n3n.example.com)")
        { IsRequired = true };
        urlOption.AddAlias("-u");

        var codeOption = new Option<string>(
            "--code",
            description: "6-digit pairing code from the platform")
        { IsRequired = true };
        codeOption.AddAlias("-c");

        pairCommand.AddOption(urlOption);
        pairCommand.AddOption(codeOption);
        pairCommand.SetHandler(async (url, code) =>
        {
            await PairAsync(url, code);
        }, urlOption, codeOption);

        // run command
        var runCommand = new Command("run", "Run the agent and connect to platform");
        runCommand.SetHandler(async () =>
        {
            await RunAsync();
        });

        // status command
        var statusCommand = new Command("status", "Show agent status");
        statusCommand.SetHandler(() =>
        {
            ShowStatus();
        });

        // unpair command
        var unpairCommand = new Command("unpair", "Unpair this agent from the platform");
        unpairCommand.SetHandler(async () =>
        {
            await UnpairAsync();
        });

        // config command
        var configCommand = new Command("config", "Show or modify configuration");
        var showSubCommand = new Command("show", "Show current configuration");
        showSubCommand.SetHandler(() =>
        {
            ShowConfig();
        });
        configCommand.AddCommand(showSubCommand);

        rootCommand.AddCommand(pairCommand);
        rootCommand.AddCommand(runCommand);
        rootCommand.AddCommand(statusCommand);
        rootCommand.AddCommand(unpairCommand);
        rootCommand.AddCommand(configCommand);

        return await rootCommand.InvokeAsync(args);
    }

    static async Task PairAsync(string url, string code)
    {
        Console.WriteLine($"Pairing with {url}...");
        Console.WriteLine($"Pairing code: {code}");

        try
        {
            var pairingService = new PairingService(url);

            if (pairingService.IsPaired())
            {
                Console.Write("Agent is already paired. Unpair first? (y/n): ");
                var answer = Console.ReadLine()?.Trim().ToLower();
                if (answer != "y" && answer != "yes")
                {
                    Console.WriteLine("Pairing cancelled.");
                    return;
                }
                await pairingService.UnpairAsync();
            }

            var result = await pairingService.PairAsync(code);

            if (result.Success)
            {
                Console.WriteLine();
                Console.WriteLine("✓ Pairing successful!");
                Console.WriteLine($"  Device ID: {result.DeviceId}");
                Console.WriteLine($"  Device Name: {result.DeviceName}");
                Console.WriteLine();
                Console.WriteLine("Run 'n3n-agent run' to start the agent.");
            }
            else
            {
                Console.WriteLine($"✗ Pairing failed: {result.Error}");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"✗ Error: {ex.Message}");
        }
    }

    static async Task RunAsync()
    {
        Console.WriteLine("N3N Agent for Windows v1.0.0");
        Console.WriteLine("============================");

        await using var agent = new Agent();

        agent.LogMessage += (s, msg) =>
        {
            Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] {msg}");
        };

        agent.StatusChanged += (s, e) =>
        {
            var statusIcon = e.Status switch
            {
                AgentStatus.Connected => "●",
                AgentStatus.Connecting => "○",
                AgentStatus.Disconnected => "○",
                AgentStatus.Error => "✗",
                _ => "○"
            };

            Console.Title = $"N3N Agent - {e.Status}";
        };

        // Handle Ctrl+C
        var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (s, e) =>
        {
            e.Cancel = true;
            Console.WriteLine("\nShutting down...");
            cts.Cancel();
        };

        try
        {
            await agent.StartAsync(cts.Token);
        }
        catch (OperationCanceledException)
        {
            // Normal shutdown
        }
        catch (InvalidOperationException ex)
        {
            Console.WriteLine($"✗ {ex.Message}");
            Console.WriteLine("Run 'n3n-agent pair' first to pair with the platform.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"✗ Error: {ex.Message}");
        }
    }

    static void ShowStatus()
    {
        var agent = new Agent();
        var info = agent.GetInfo();

        Console.WriteLine("N3N Agent Status");
        Console.WriteLine("================");
        Console.WriteLine();

        if (string.IsNullOrEmpty(info.DeviceId))
        {
            Console.WriteLine("Status: Not paired");
            Console.WriteLine();
            Console.WriteLine("Run 'n3n-agent pair' to pair with the platform.");
        }
        else
        {
            Console.WriteLine($"Status:       Paired");
            Console.WriteLine($"Device ID:    {info.DeviceId}");
            Console.WriteLine($"Platform:     {info.PlatformUrl}");
            Console.WriteLine($"Version:      {info.Version}");
            Console.WriteLine($"OS:           Windows ({info.Platform})");
            Console.WriteLine();
            Console.WriteLine("Capabilities:");
            foreach (var cap in info.Capabilities)
            {
                Console.WriteLine($"  - {cap}");
            }
        }
    }

    static async Task UnpairAsync()
    {
        Console.Write("Are you sure you want to unpair this agent? (y/n): ");
        var answer = Console.ReadLine()?.Trim().ToLower();

        if (answer != "y" && answer != "yes")
        {
            Console.WriteLine("Cancelled.");
            return;
        }

        try
        {
            var pairingService = new PairingService(""); // URL not needed for unpair
            await pairingService.UnpairAsync();
            Console.WriteLine("✓ Agent unpaired successfully.");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"✗ Error: {ex.Message}");
        }
    }

    static void ShowConfig()
    {
        var agent = new Agent();
        var info = agent.GetInfo();

        Console.WriteLine("Configuration");
        Console.WriteLine("=============");
        Console.WriteLine();
        Console.WriteLine($"Device ID:    {info.DeviceId ?? "(not set)"}");
        Console.WriteLine($"Platform URL: {info.PlatformUrl ?? "(not set)"}");
        Console.WriteLine($"Version:      {info.Version}");
        Console.WriteLine();
        Console.WriteLine("Registered Capabilities:");
        foreach (var cap in info.Capabilities)
        {
            Console.WriteLine($"  - {cap}");
        }
    }
}
