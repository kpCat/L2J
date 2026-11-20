using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

public sealed record OpenAiTokenUsage(int Input, int Cached, int Output, int CacheWrite = 0, int ExtendedCacheWrite = 0)
{
    public bool Valid => Input >= 0 && Output >= 0 && Cached >= 0 && CacheWrite >= 0
        && ExtendedCacheWrite >= 0 && ExtendedCacheWrite <= CacheWrite && (long)Cached + CacheWrite <= Input;
}

public sealed record OpenAiTariff(string Model, decimal InputUsd, decimal CachedUsd, decimal OutputUsd,
    decimal CacheWriteUsd, decimal ExtendedCacheWriteUsd, DateOnly Checked, string Source)
{
    public const string Tier = "default";
    public const string PricingPage = "https://platform.openai.com/pricing/";
    public const string SuggestedModel = "gpt-6.1-terra";

    public static OpenAiTariff? Known(string model) => model switch
    {
        SuggestedModel => new(model, 1m, .05m, 5m, 1.25m, 2.50m, new(2026, 11, 20),
            "https://developers.openai.com/api/docs/models/gpt-6.1-terra"),
        "gpt-4.1-mini" or "gpt-4.1-mini-2025-04-14" => new(model, .40m, .10m, 1.60m, .40m, .40m, new(2026, 11, 20),
            "https://developers.openai.com/api/docs/models/gpt-4.1-mini"),
        _ => null
    };

    public void Validate()
    {
        OpenAiResponsesClient.ValidateModel(Model);
        V4Rules.Require(InputUsd is > 0 and <= 10000 && OutputUsd is > 0 and <= 10000
            && CachedUsd >= 0 && CachedUsd <= InputUsd && CacheWriteUsd is >= 0 and <= 10000
            && ExtendedCacheWriteUsd >= CacheWriteUsd && ExtendedCacheWriteUsd <= 10000
            && Checked.Year >= 2024 && Checked <= DateOnly.FromDateTime(DateTime.UtcNow)
            && (Source == PricingPage || Known(Model)?.Source == Source), "OPENAI_PRICE_REQUIRED");
    }

    public decimal Reserve(int maxInput, int maxOutput) =>
        (maxInput * Math.Max(InputUsd, ExtendedCacheWriteUsd) + maxOutput * OutputUsd) / 1000000m;

    public decimal Cost(OpenAiTokenUsage usage)
    {
        V4Rules.Require(usage.Valid, "OPENAI_USAGE_INVALID");
        return ((usage.Input - usage.Cached - usage.CacheWrite) * InputUsd + usage.Cached * CachedUsd
            + (usage.CacheWrite - usage.ExtendedCacheWrite) * CacheWriteUsd
            + usage.ExtendedCacheWrite * ExtendedCacheWriteUsd + usage.Output * OutputUsd) / 1000000m;
    }
}

public sealed record OpenAiBudget(decimal GrantUsd = 0, decimal ReserveUsd = 1, decimal PilotUsd = 2,
    decimal CampaignUsd = 0, decimal DailyUsd = 2, decimal RequestUsd = .10m, decimal MonthlyUsd = 0)
{
    public void Validate() => V4Rules.Require(GrantUsd is >= 0 and <= 10000 && ReserveUsd is >= 0 and <= 10000
        && PilotUsd is > 0 and <= 2 && CampaignUsd is >= 0 and <= 10000 && DailyUsd is > 0 and <= 10000
        && RequestUsd is > 0 and <= 10000 && MonthlyUsd is >= 0 and <= 10000, "OPENAI_BUDGET_INVALID");

    public decimal Available(bool pilot, decimal pilotUsed, decimal campaignUsed, decimal dailyUsed, decimal accountUsed,
        decimal monthlyUsed = 0) => Math.Min(RequestUsd, Remaining(pilot, pilotUsed, campaignUsed, dailyUsed, accountUsed, monthlyUsed));

    public decimal Remaining(bool pilot, decimal pilotUsed, decimal campaignUsed, decimal dailyUsed, decimal accountUsed,
        decimal monthlyUsed = 0) => Math.Max(0m, new[]
        {
            (pilot ? PilotUsd - pilotUsed : CampaignUsd - campaignUsed),
            DailyUsd - dailyUsed, GrantUsd - ReserveUsd - accountUsed,
            MonthlyUsd > 0 ? MonthlyUsd - monthlyUsed : GrantUsd - ReserveUsd - monthlyUsed
        }.Min());
}

internal sealed record OpenAiPost(string Model, byte[] Body, int MaxInput, int MaxOutput, string Hash, bool Structured);
internal sealed record OpenAiReply(string Status, string Text = "", OpenAiTokenUsage? Usage = null,
    string Model = "", string ResponseId = "", TimeSpan? RetryAfter = null, DateTimeOffset? RetryAtUtc = null);

/// <summary>Only standalone text requests to the official Responses API. No tools, redirects or implicit retries.</summary>
public sealed class OpenAiResponsesClient : IDisposable
{
    public const string Endpoint = "https://api.openai.com/v1/";
    private const int ResponseLimit = 512 * 1024;
    private static readonly Regex ModelId = new(@"^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}\z", RegexOptions.CultureInvariant);
    private static readonly Regex SecretPattern = new(@"(?<![\w-])sk-[A-Za-z0-9_-]{12,}", RegexOptions.CultureInvariant);
    private static readonly JsonDocumentOptions Strict = new() { MaxDepth = 24, AllowDuplicateProperties = false };
    private readonly HttpClient http;
    private readonly bool ownsClient;

    public OpenAiResponsesClient()
    {
        http = new(new SocketsHttpHandler { AllowAutoRedirect = false, UseCookies = false, UseProxy = false })
        { Timeout = TimeSpan.FromSeconds(100) };
        ownsClient = true;
    }

    internal OpenAiResponsesClient(HttpClient http) => this.http = http;

    internal static void ValidateModel(string value) => V4Rules.Require(value != null && ModelId.IsMatch(value)
        && !SecretPattern.IsMatch(value), "OPENAI_MODEL_INVALID");

    internal static void ValidateKey(string key) => V4Rules.Require(key is { Length: >= 8 and <= 1024 }
        && key.All(c => char.IsAsciiLetterOrDigit(c) || c is '-' or '_'), "OPENAI_KEY_INVALID");

    internal static bool HasSecret(string candidate, string key) => candidate.Contains(key, StringComparison.Ordinal)
        || SecretPattern.IsMatch(candidate);

    internal static OpenAiPost Prepare(string model, string instructions, string input, int maxOutput, object? schema)
    {
        ValidateModel(model);
        V4Rules.Require(maxOutput is >= 128 and <= 8192, "OPENAI_OUTPUT_LIMIT");
        var body = JsonSerializer.SerializeToUtf8Bytes(new
        {
            model, instructions, input, max_output_tokens = maxOutput, store = false, service_tier = OpenAiTariff.Tier,
            reasoning = model.StartsWith("gpt-6", StringComparison.Ordinal) ? new { effort = "none" } : null,
            text = schema == null ? null : new { format = new { type = "json_schema", name = "semantic_replicas", strict = true, schema } }
        }, new JsonSerializerOptions(PromptProfiles.CompactJson) { DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull });
        V4Rules.Require(body.Length <= 32768, "OPENAI_INPUT_LIMIT");
        // UTF-8 wire length includes the full schema and instructions. The additional allowance covers provider framing.
        return new(model, body, body.Length + 4096, maxOutput, V4Rules.Hash(body), schema != null);
    }

    public async Task<IReadOnlyList<string>> ModelsAsync(string key, CancellationToken token)
    {
        ValidateKey(key);
        try
        {
            using var request = Request(HttpMethod.Get, "models", key);
            using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
            if (!response.IsSuccessStatusCode)
                throw new InvalidDataException((await HttpFailure(response, token).ConfigureAwait(false)).Status);
            using var json = await ReadJson(response, token).ConfigureAwait(false);
            var models = json.RootElement.GetProperty("data").EnumerateArray().Select(model => Value(model, "id"))
                .Where(id => ModelId.IsMatch(id) && !HasSecret(id, key)).Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal).ToArray();
            V4Rules.Require(models.Length > 0, "OPENAI_MODELS_EMPTY");
            return models;
        }
        catch (Exception error) when (error is HttpRequestException or JsonException or KeyNotFoundException or InvalidOperationException or OperationCanceledException)
        { throw new InvalidDataException("OPENAI_MODELS_UNAVAILABLE"); }
    }

    internal async Task<OpenAiReply> GenerateAsync(OpenAiPost post, string key, CancellationToken token)
    {
        ValidateKey(key);
        V4Rules.Require(!HasSecret(V4Rules.Utf8.GetString(post.Body), key), "OPENAI_OUTBOUND_SECRET");
        try
        {
            using var request = Request(HttpMethod.Post, "responses", key);
            request.Content = new ByteArrayContent(post.Body);
            request.Content.Headers.ContentType = new("application/json");
            using var response = await http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
            if (!response.IsSuccessStatusCode) return await HttpFailure(response, token).ConfigureAwait(false);
            using var json = await ReadJson(response, token).ConfigureAwait(false);
            return Decode(json.RootElement, post, key);
        }
        catch (Exception error) when (error is HttpRequestException or IOException or JsonException or KeyNotFoundException
            or InvalidOperationException or OperationCanceledException or FormatException)
        { return new("INDETERMINATE"); }
    }

    private static HttpRequestMessage Request(HttpMethod method, string suffix, string key)
    {
        var request = new HttpRequestMessage(method, Endpoint + suffix);
        request.Headers.Authorization = new("Bearer", key);
        return request;
    }

    private static async Task<OpenAiReply> HttpFailure(HttpResponseMessage response, CancellationToken token)
    {
        string code = "";
        if ((int)response.StatusCode is 400 or 403 or 429)
        {
            try
            {
                using var json = await ReadJson(response, token).ConfigureAwait(false);
                if (json.RootElement.TryGetProperty("error", out var problem) && problem.ValueKind == JsonValueKind.Object)
                    code = Value(problem, "code");
            }
            catch (Exception error) when (error is JsonException or IOException or InvalidOperationException) { }
        }

        if (code is "insufficient_quota" or "billing_hard_limit_reached" or "credit_balance_exhausted" or "billing_not_active")
            return new("BILLING");
        if (code is "content_policy_violation" or "content_filter" or "moderation_blocked") return new("REFUSAL");
        switch ((int)response.StatusCode)
        {
            case 401 or 403: return new("AUTH");
            case 402: return new("BILLING");
            case 400 or 404 or 422: return new("HTTP_400");
            case 429 when code == "rate_limit_exceeded":
                var now = DateTimeOffset.UtcNow;
                var retry = response.Headers.RetryAfter?.Delta ?? response.Headers.RetryAfter?.Date - now ?? TimeSpan.FromSeconds(2);
                if (retry > TimeSpan.FromSeconds(30)) return new("RATE_LIMIT_STOP");
                if (retry < TimeSpan.FromSeconds(1)) retry = TimeSpan.FromSeconds(1);
                return new("RATE_LIMIT", RetryAfter: retry, RetryAtUtc: now + retry);
            case 429: return new("RATE_LIMIT_STOP");
            default: return new("INDETERMINATE");
        }
    }

    private static OpenAiReply Decode(JsonElement root, OpenAiPost post, string key)
    {
        var model = Value(root, "model");
        var pinned = model == post.Model || (model.StartsWith(post.Model + "-", StringComparison.Ordinal)
            && DateOnly.TryParseExact(model[(post.Model.Length + 1)..], "yyyy-MM-dd",
                System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out _));
        if (!pinned || Value(root, "service_tier") != OpenAiTariff.Tier) return new("MODEL_OR_TIER_DRIFT");
        if (!root.TryGetProperty("usage", out var data) || data.ValueKind != JsonValueKind.Object) return new("INDETERMINATE");
        var details = data.TryGetProperty("input_tokens_details", out var breakdown) && breakdown.ValueKind == JsonValueKind.Object ? breakdown : default;
        var cache = details.ValueKind == JsonValueKind.Object && details.TryGetProperty("cache_write_tokens_details", out var writes)
            && writes.ValueKind == JsonValueKind.Object ? writes : default;
        var usage = new OpenAiTokenUsage(data.GetProperty("input_tokens").GetInt32(), Count(details, "cached_tokens"),
            data.GetProperty("output_tokens").GetInt32(), Count(details, "cache_write_tokens"), Count(cache, "ttl_12h_tokens"));
        if (!usage.Valid) return new("USAGE_DRIFT");
        if (usage.Input > post.MaxInput || usage.Output > post.MaxOutput) return new("USAGE_DRIFT", Usage: usage);
        var id = Value(root, "id");
        if (id.Length > 100 || HasSecret(id, key) || HasSecret(model, key)) return new("SECURITY_REDACTED", Usage: usage);
        var status = Value(root, "status");
        if (status == "incomplete")
        {
            var filtered = root.TryGetProperty("incomplete_details", out var why) && why.ValueKind == JsonValueKind.Object
                && Value(why, "reason") == "content_filter";
            return new(filtered ? "REFUSAL" : "INCOMPLETE", Usage: usage, Model: model, ResponseId: id);
        }
        if (status != "completed") return new("INDETERMINATE", Usage: usage, Model: model, ResponseId: id);

        var parts = new List<string>();
        foreach (var item in root.GetProperty("output").EnumerateArray())
        {
            if (Value(item, "type") == "reasoning") continue;
            if (Value(item, "type") != "message" || Value(item, "role") != "assistant")
                return new("UNEXPECTED_OUTPUT", Usage: usage, Model: model, ResponseId: id);
            foreach (var part in item.GetProperty("content").EnumerateArray())
            {
                var type = Value(part, "type");
                if (type == "refusal") return new("REFUSAL", Usage: usage, Model: model, ResponseId: id);
                if (type != "output_text") return new("UNEXPECTED_OUTPUT", Usage: usage, Model: model, ResponseId: id);
                parts.Add(Value(part, "text"));
            }
        }

        var text = string.Concat(parts);
        if (UnsafeOutput(text, key)) return new("SECURITY_REDACTED", Usage: usage, Model: model, ResponseId: id);
        if (post.Structured)
        {
            try
            {
                using var content = JsonDocument.Parse(text, Strict);
                if (UnsafeJson(content.RootElement, key)) return new("SECURITY_REDACTED", Usage: usage, Model: model, ResponseId: id);
            }
            catch (JsonException) { return new("INVALID_OUTPUT", Usage: usage, Model: model, ResponseId: id); }
        }
        return new(text.Length == 0 ? "UNEXPECTED_OUTPUT" : "COMPLETE", text, usage, model, id);
    }

    private static bool UnsafeJson(JsonElement item, string key) => item.ValueKind switch
    {
        JsonValueKind.String => UnsafeOutput(item.GetString()!, key),
        JsonValueKind.Object => item.EnumerateObject().Any(property => UnsafeOutput(property.Name, key) || UnsafeJson(property.Value, key)),
        JsonValueKind.Array => item.EnumerateArray().Any(child => UnsafeJson(child, key)),
        _ => false
    };

    private static bool UnsafeOutput(string content, string key)
    {
        try { return HasSecret(content, key) || CorpusLanguageTriage.Scrub(content) != content; }
        catch (RegexMatchTimeoutException) { return true; }
    }

    private static string Value(JsonElement element, string name) =>
        element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString()! : "";

    private static int Count(JsonElement element, string name) =>
        element.ValueKind == JsonValueKind.Object && element.TryGetProperty(name, out var value) ? value.GetInt32() : 0;

    private static async Task<JsonDocument> ReadJson(HttpResponseMessage response, CancellationToken token)
    {
        if (response.Content.Headers.ContentLength > ResponseLimit) throw new IOException("OPENAI_RESPONSE_LIMIT");
        using var readTimeout = CancellationTokenSource.CreateLinkedTokenSource(token);
        readTimeout.CancelAfter(TimeSpan.FromSeconds(100));
        using var stream = await response.Content.ReadAsStreamAsync(readTimeout.Token).ConfigureAwait(false);
        using var body = new MemoryStream();
        var buffer = new byte[8192];
        int read;
        while ((read = await stream.ReadAsync(buffer, readTimeout.Token).ConfigureAwait(false)) > 0)
        {
            if (body.Length + read > ResponseLimit) throw new IOException("OPENAI_RESPONSE_LIMIT");
            body.Write(buffer, 0, read);
        }
        return JsonDocument.Parse(body.GetBuffer().AsMemory(0, checked((int)body.Length)), Strict);
    }

    public void Dispose()
    {
        if (ownsClient) http.Dispose();
    }
}
