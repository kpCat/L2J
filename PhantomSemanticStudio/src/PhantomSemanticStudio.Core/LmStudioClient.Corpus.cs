using System.Diagnostics;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace PhantomSemanticStudio.Core;

public sealed record CorpusLoadedModel(string ModelKey, string InstanceId, int ContextLength, string[] AllowedReasoning);
public sealed record CorpusModelInventory(string[] CompatibleIds, IReadOnlyList<CorpusLoadedModel> Loaded, string CompatibleRaw, string NativeRaw);
public sealed record CorpusTelemetry(long? InputTokens, long? OutputTokens, long? ReasoningTokens, double? TokensPerSecond, double? FirstTokenSeconds, double WallSeconds)
{
    public string ThinkingEvidence => ReasoningTokens is > 0 ? "REASONING_OBSERVED" : ReasoningTokens == 0 ? "ZERO_REPORTED_NOT_VERIFIED" : "UNKNOWN";
}
public sealed record CorpusGenerationResult(string RequestJson, string RawEnvelope, string Content, CorpusTelemetry Telemetry, string Rejection, int HttpStatus, long? RequestedSeed, long? EffectiveSeed);

public sealed partial class LmStudioClient
{
    public async Task<CorpusModelInventory> ProbeCorpusModelsAsync(StudioSettings settings, string apiKey, CancellationToken token)
    {
        ValidateSettings(settings);
        var compat = await SendAsync(settings, "models", HttpMethod.Get, null, apiKey, token).ConfigureAwait(false);
        var native = await SendAsync(settings, "../api/v1/models", HttpMethod.Get, null, apiKey, token).ConfigureAwait(false);
        return ParseCorpusModels(compat, native);
    }

    public static CorpusModelInventory ParseCorpusModels(string compatibleRaw, string nativeRaw)
    {
        using var compat = JsonDocument.Parse(compatibleRaw, new JsonDocumentOptions { MaxDepth = 24 }); UniqueFields(compat.RootElement);
        using var native = JsonDocument.Parse(nativeRaw, new JsonDocumentOptions { MaxDepth = 24 }); UniqueFields(native.RootElement);
        var ids = compat.RootElement.GetProperty("data").EnumerateArray().Select(x => V4Rules.String(x, "id")).ToArray();
        V4Rules.Require(ids.Length <= 256 && ids.All(x => x.Length is > 0 and <= 256) && ids.Distinct(StringComparer.Ordinal).Count() == ids.Length, "MODEL_LIST");
        var models = native.RootElement.GetProperty("models"); V4Rules.Require(models.ValueKind == JsonValueKind.Array && models.GetArrayLength() <= 256, "NATIVE_MODEL_LIST");
        var loaded = new List<CorpusLoadedModel>();
        foreach (var model in models.EnumerateArray())
        {
            var key = V4Rules.String(model, "key");
            if (model.TryGetProperty("type", out var type) && type.GetString() != "llm") continue;
            var reasoning = Array.Empty<string>();
            if (model.TryGetProperty("capabilities", out var cap) && cap.TryGetProperty("reasoning", out var r) && r.TryGetProperty("allowed_options", out var allowed))
                reasoning = allowed.EnumerateArray().Select(x => x.GetString() ?? "").ToArray();
            foreach (var instance in model.GetProperty("loaded_instances").EnumerateArray())
            {
                var id = V4Rules.String(instance, "id"); var context = instance.GetProperty("config").GetProperty("context_length").GetInt32();
                V4Rules.Require(id.Length is > 0 and <= 256 && context is >= 2048 and <= 16_777_216 && key.Length is > 0 and <= 256, "LOADED_MODEL_METADATA");
                loaded.Add(new(key, id, context, reasoning));
            }
        }
        V4Rules.Require(loaded.Count <= 256 && loaded.Select(x => x.InstanceId).Distinct(StringComparer.Ordinal).Count() == loaded.Count, "LOADED_MODEL_COLLISION");
        return new(ids, loaded.AsReadOnly(), compatibleRaw, nativeRaw);
    }

    public static CorpusTelemetry ReadCorpusTelemetry(string envelope, bool native, double wallSeconds)
    {
        using var doc = JsonDocument.Parse(envelope); var root = doc.RootElement;
        long? Long(JsonElement element, string key) => element.TryGetProperty(key, out var value) && value.TryGetInt64(out var n) && n >= 0 ? n : null;
        double? Number(JsonElement element, string key) => element.TryGetProperty(key, out var value) && value.TryGetDouble(out var n) && double.IsFinite(n) && n >= 0 ? n : null;
        if (native && root.TryGetProperty("stats", out var stats))
            return new(Long(stats, "input_tokens"), Long(stats, "total_output_tokens"), Long(stats, "reasoning_output_tokens"), Number(stats, "tokens_per_second"), Number(stats, "time_to_first_token_seconds"), wallSeconds);
        if (!native && root.TryGetProperty("usage", out var usage))
        {
            long? thinking = usage.TryGetProperty("completion_tokens_details", out var details) ? Long(details, "reasoning_tokens") : null;
            return new(Long(usage, "prompt_tokens"), Long(usage, "completion_tokens"), thinking, null, null, wallSeconds);
        }
        return new(null, null, null, null, null, wallSeconds);
    }

    public async Task<CorpusGenerationResult> GenerateCorpusAsync(StudioSettings settings, string apiKey, CorpusLoadedModel loaded,
        V4GenerationRequest request, PromptProfiles profiles, CancellationToken token)
    {
        ValidateSettings(settings); request.Validate(profiles);
        V4Rules.Require(request.Transport is "LM_NATIVE" or "OPENAI_COMPAT", "LM_CORPUS_TRANSPORT");
        V4Rules.Require(loaded.InstanceId.Length is > 0 and <= 256 && loaded.ContextLength >= 2048, "LOADED_INSTANCE_REQUIRED");
        var native = request.Transport == "LM_NATIVE";
        var system = request.Components == null ? profiles.StrategyText(request.Strategy) + "\n" + profiles.LegacyRules(request.Style) : PromptHierarchy.Compose(request.Components);
        var user = V4CorpusGeneration.UserData(request);
        // UTF-8 bytes are a conservative authoring bound, not a claim of exact tokenization.
        var promptBound = Encoding.UTF8.GetByteCount(system) + Encoding.UTF8.GetByteCount(user);
        V4Rules.Require(promptBound <= 16 * 1024 && promptBound + request.MaxOutputTokens + 2048 <= Math.Min(65536, loaded.ContextLength), "CONTEXT_BUDGET_REJECTED");
        var payload = CorpusPayload(loaded, request, system, user, V4CorpusGeneration.ResponseSchema(request));
        var requestJson = JsonSerializer.Serialize(payload, PromptProfiles.ConfigJson);
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token); timeout.CancelAfter(TimeSpan.FromSeconds(settings.TimeoutSeconds));
        using var message = new HttpRequestMessage(HttpMethod.Post, new Uri(ValidateEndpoint(settings.Endpoint), native ? "../api/v1/chat" : "chat/completions"));
        V4Rules.Require(apiKey.Length <= 4096 && !apiKey.Any(char.IsControl), "API_TOKEN_FORMAT");
        if (!string.IsNullOrWhiteSpace(apiKey)) message.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey.Trim());
        message.Content = new StringContent(requestJson, Encoding.UTF8, "application/json");
        var timer = Stopwatch.StartNew();
        using var response = await http.SendAsync(message, HttpCompletionOption.ResponseHeadersRead, timeout.Token).ConfigureAwait(false);
        V4Rules.Require(response.Content.Headers.ContentLength is null or <= MaxResponseBytes, "HTTP_LIMIT");
        await using var input = await response.Content.ReadAsStreamAsync(timeout.Token).ConfigureAwait(false);
        using var output = new MemoryStream(); var buffer = new byte[8192]; int n;
        while ((n = await input.ReadAsync(buffer, timeout.Token).ConfigureAwait(false)) != 0)
        { V4Rules.Require(output.Length + n <= MaxResponseBytes, "HTTP_LIMIT"); output.Write(buffer, 0, n); }
        timer.Stop(); var raw = new UTF8Encoding(false, true).GetString(output.ToArray());
        if (apiKey.Length > 0) raw = raw.Replace(apiKey, "[REDACTED_API_TOKEN]", StringComparison.Ordinal);
        return ParseCorpusEnvelope(requestJson, raw, native, (int)response.StatusCode, loaded, request, timer.Elapsed.TotalSeconds);
    }

    private static Dictionary<string, object?> CorpusPayload(CorpusLoadedModel loaded, V4GenerationRequest request, string system, string user, object schema)
    {
        var native = request.Transport == "LM_NATIVE";
        var payload = new Dictionary<string, object?>
        {
            ["model"] = loaded.InstanceId, ["stream"] = false, ["temperature"] = request.Sampler.Temperature,
            ["top_p"] = request.Sampler.TopP, ["top_k"] = request.Sampler.TopK, ["repeat_penalty"] = request.Sampler.RepeatPenalty
        };
        if (native)
        {
            V4Rules.Require(request.CompatibleThinkingControl == "none", "THINKING_TRANSPORT_MISMATCH");
            payload["system_prompt"] = system; payload["input"] = user; payload["store"] = false; payload["max_output_tokens"] = request.MaxOutputTokens;
            if (request.MinP.HasValue) payload["min_p"] = request.MinP.Value;
            if (request.Reasoning != "default") payload["reasoning"] = request.Reasoning;
        }
        else
        {
            payload["messages"] = new[] { new { role = "system", content = system }, new { role = "user", content = user } };
            payload["max_tokens"] = request.MaxOutputTokens;
            payload["response_format"] = new { type = "json_schema", json_schema = new { name = "pw_semantic_transport", strict = true, schema } };
            if (request.Sampler.Seed.HasValue) payload["seed"] = request.Sampler.Seed.Value;
            if (request.Reasoning != "default")
            {
                V4Rules.Require(request.CompatibleThinkingControl != "none", "EXPLICIT_COMPAT_THINKING_CONTROL_REQUIRED");
                if (request.CompatibleThinkingControl == "reasoning_effort") payload["reasoning_effort"] = request.Reasoning == "off" ? "none" : "high";
                else payload["chat_template_kwargs"] = new { enable_thinking = request.Reasoning == "on" };
            }
        }
        return payload;
    }

    private static CorpusGenerationResult ParseCorpusEnvelope(string requestJson, string raw, bool native, int status,
        CorpusLoadedModel loaded, V4GenerationRequest request, double wallSeconds, bool checkReasoningLeakage = true)
    {
        var telemetry = new CorpusTelemetry(null, null, null, null, null, wallSeconds); string content = "", rejection = "";
        if (status is < 200 or >= 300) rejection = "HTTP_" + status;
        else
        {
            try
            {
                telemetry = ReadCorpusTelemetry(raw, native, wallSeconds);
                using var envelope = JsonDocument.Parse(raw, new JsonDocumentOptions { MaxDepth = 24 }); UniqueFields(envelope.RootElement);
                if (native)
                {
                    V4Rules.Require(V4Rules.String(envelope.RootElement, "model_instance_id") == loaded.InstanceId, "LOST_MODEL");
                    var items = envelope.RootElement.GetProperty("output").EnumerateArray().ToArray();
                    V4Rules.Require(items.All(x => V4Rules.String(x, "type") is "message" or "reasoning") && items.Count(x => V4Rules.String(x, "type") == "message") == 1, "NATIVE_OUTPUT_SHAPE");
                    content = V4Rules.String(items.Single(x => V4Rules.String(x, "type") == "message"), "content");
                    V4Rules.Require(telemetry.OutputTokens < request.MaxOutputTokens, "NATIVE_TRUNCATION_RISK");
                }
                else
                {
                    if (envelope.RootElement.TryGetProperty("model", out var model))
                        V4Rules.Require(model.GetString() == loaded.InstanceId || model.GetString() == loaded.ModelKey, "LOST_MODEL");
                    content = CompletionText(raw, allowEmptyToolCalls: true);
                }
                V4Rules.Require(!checkReasoningLeakage || !content.Contains("<think", StringComparison.OrdinalIgnoreCase) && !content.Contains("[THINK", StringComparison.OrdinalIgnoreCase), "REASONING_LEAKAGE");
                if (telemetry.InputTokens.HasValue && telemetry.OutputTokens.HasValue)
                    V4Rules.Require(telemetry.InputTokens.Value + telemetry.OutputTokens.Value <= Math.Min(65536, loaded.ContextLength), "CONTEXT_USAGE_EXCEEDED");
            }
            catch (Exception ex) when (ex is InvalidDataException or JsonException or LmStudioException or InvalidOperationException or KeyNotFoundException)
            { rejection = ex is LmStudioException lm ? lm.Diagnostic.FailureCategory : ex.Message; content = ""; }
        }
        return new(requestJson, raw, content, telemetry, rejection, status, native ? null : request.Sampler.Seed, null);
    }
}
