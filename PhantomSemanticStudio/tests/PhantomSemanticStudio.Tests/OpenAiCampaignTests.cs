using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using PhantomSemanticStudio.Core;

internal static partial class Program
{
    private const string FakeCredential = "offline-placeholder";
    private const string TestModel = "gpt-4.1-mini-2025-04-14";

    private static async Task OpenAiCampaignTests()
    {
        await TestAsync("OpenAI Responses pins HTTPS, usage and typed failures without exposing the credential", async () =>
        {
            var requests = 0;
            using var wire = new HttpClient(new CampaignHttp(async (request, token) =>
            {
                Assert(request.RequestUri!.Host == "api.openai.com" && request.RequestUri.Scheme == "https", "official HTTPS required");
                Assert(Equals(request.Headers.Authorization, new AuthenticationHeaderValue("Bearer", FakeCredential)), "bearer missing");
                if (request.Method == HttpMethod.Get)
                    return CampaignReply(HttpStatusCode.OK, """{"data":[{"id":"gpt-4.1-mini-2025-04-14"}]}""");
                var body = await request.Content!.ReadAsStringAsync(token);
                using var document = JsonDocument.Parse(body);
                var json = document.RootElement;
                Assert(request.RequestUri.AbsolutePath == "/v1/responses" && !body.Contains(FakeCredential), "POST path or secret");
                Assert(!json.GetProperty("store").GetBoolean() && json.GetProperty("service_tier").GetString() == "default", "storage or tier");
                Assert(json.GetProperty("text").GetProperty("format").GetProperty("strict").GetBoolean(), "strict schema");
                return ++requests switch
                {
                    1 => CampaignResponse("""{"text":"Готово."}""", 44, 12, 8),
                    2 => RateLimit("rate_limit_exceeded"),
                    3 => RateLimit("insufficient_quota"),
                    4 => CampaignReply(HttpStatusCode.BadRequest, JsonSerializer.Serialize(new { error = new { message = FakeCredential } })),
                    5 => CampaignReply(HttpStatusCode.Unauthorized, "{}"),
                    6 => CampaignReply(HttpStatusCode.InternalServerError, "{}"),
                    7 => CampaignReply(HttpStatusCode.OK, "{"),
                    8 => CampaignReply(HttpStatusCode.OK, """{"status":"completed","model":"gpt-4.1-mini-2025-04-14","service_tier":"default","output":[{"type":"message","role":"assistant","content":[{"type":"refusal","refusal":"Отказ"}]}],"usage":{"input_tokens":4,"output_tokens":2}}"""),
                    9 => CampaignResponse("""{"text":"\u006fffline-placeholder"}""", 44, 12, 8),
                    10 => CampaignResponse("""{"text":"Напиши мне на example@example.com."}""", 44, 12, 8),
                    _ => throw new OperationCanceledException()
                };
            }));
            using var client = new OpenAiResponsesClient(wire);
            Assert((await client.ModelsAsync(FakeCredential, default)).SequenceEqual([TestModel]), "model list");
            var payload = OpenAiResponsesClient.Prepare(TestModel, "Правила", "Сцена", 512, new { type = "object" });
            var reply = await client.GenerateAsync(payload, FakeCredential, default);
            Assert(reply.Status == "COMPLETE" && reply.Text == """{"text":"Готово."}""" && reply.Usage == new OpenAiTokenUsage(44, 12, 8), "usage or output");
            foreach (var expected in new[] { "RATE_LIMIT", "BILLING", "HTTP_400", "AUTH", "INDETERMINATE", "INDETERMINATE", "REFUSAL",
                "SECURITY_REDACTED", "SECURITY_REDACTED", "INDETERMINATE" })
            {
                reply = await client.GenerateAsync(payload, FakeCredential, default);
                Assert(reply.Status == expected && !reply.Text.Contains(FakeCredential), expected);
                if (expected == "RATE_LIMIT") Assert(reply.RetryAfter == TimeSpan.FromSeconds(3), "Retry-After");
            }
            Assert(requests == 11, "transport must never replay a POST");
        });
        await TestAsync("OpenAI pilot limits reserve uncached input and all output before a POST", () =>
        {
            var price = OpenAiTariff.Known(TestModel)!;
            var budget = new OpenAiBudget(16m, 1m, 2m, 10m, 4m, .1m);
            var reserve = price.Reserve(1000, 4000);
            Assert(reserve > price.Cost(new(1000, 500, 2000)), "worst-case reserve");
            Assert(budget.Available(true, 1.99m, 0m, 1.99m, 1.99m) == .01m, "pilot cap");
            Assert(budget.Available(false, 0m, 0m, 3.96m, 3.96m) == .04m, "daily cap");
            Throws(() => (budget with { PilotUsd = 2.01m }).Validate());
            return Task.CompletedTask;
        });
    }

    private static void Assert(bool condition, string message)
    {
        if (!condition) throw new AssertionFailure(message);
    }

    private static HttpResponseMessage RateLimit(string code)
    {
        var result = CampaignReply(HttpStatusCode.TooManyRequests, JsonSerializer.Serialize(new { error = new { code } }));
        result.Headers.RetryAfter = new RetryConditionHeaderValue(TimeSpan.FromSeconds(3));
        return result;
    }

    private static HttpResponseMessage CampaignReply(HttpStatusCode status, string body) => new(status)
    { Content = new StringContent(body, System.Text.Encoding.UTF8, "application/json") };

    private static HttpResponseMessage CampaignResponse(string text, int input, int cached, int output) =>
        CampaignReply(HttpStatusCode.OK, JsonSerializer.Serialize(new
        {
            id = "resp_offline", status = "completed", model = TestModel, service_tier = "default",
            output = new[] { new { type = "message", role = "assistant", content = new[] { new { type = "output_text", text } } } },
            usage = new { input_tokens = input, input_tokens_details = new { cached_tokens = cached }, output_tokens = output }
        }));

    private sealed class CampaignHttp(Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> handler) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            handler(request, cancellationToken);
    }
}
