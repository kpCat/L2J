using System.Globalization;
using System.Buffers.Binary;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace PhantomSemanticStudio.Core;

/// <summary>Read-only ZIP stream. No extraction, identity storage, network or model calls.</summary>
public static class ChatCorpusImporter
{
    public const long MaxBytes = 256L * 1024 * 1024;
    public const int MaxLineBytes = 16 * 1024;
    public const long MaxLines = 3_000_000;
    public static readonly IReadOnlyList<string> PublicChannels = Array.AsReadOnly(new[]
        { "ALL", "SHOUT", "TRADE", "PARTY", "CLAN", "ALLIANCE", "HERO_VOICE", "HERO", "BATTLEFIELD", "COMMANDCHANNEL_ALL", "COMMANDCHANNEL_COMMANDER" });
    private static readonly Regex Header = new(@"^\[(?<date>\d{2}\.\d{2}\.\d{2} \d{2}:\d{2}:\d{2})\]\s+(?<channel>[A-Za-z_]+)\s+\[[^\]\r\n]+\]\s*(?<text>.*)$", RegexOptions.CultureInvariant, TimeSpan.FromMilliseconds(100));
    private static readonly UTF8Encoding Utf8 = new(false, true);
    private static readonly uint[] CrcTable = CreateCrcTable();
    private static uint[] CreateCrcTable()
    {
        var table = new uint[256];
        for (uint i = 0; i < table.Length; i++)
        {
            var value = i;
            for (var bit = 0; bit < 8; bit++) value = (value & 1) != 0 ? (value >> 1) ^ 0xedb88320U : value >> 1;
            table[i] = value;
        }
        return table;
    }

    private static void PreflightDirectory(FileStream input, CancellationToken token)
    {
        // PKWARE APPNOTE 4.3.12/4.3.16: bounded classic ZIP only. Inspect before
        // ZipArchive materializes names/entry objects; no payload or name is decoded.
        var tail = new byte[(int)Math.Min(input.Length, 65557)];
        input.Position = input.Length - tail.Length; input.ReadExactly(tail);
        var end = -1;
        for (var i = tail.Length - 22; i >= 0; i--)
        {
            token.ThrowIfCancellationRequested();
            if (BinaryPrimitives.ReadUInt32LittleEndian(tail.AsSpan(i)) == 0x06054b50
                && i + 22 + BinaryPrimitives.ReadUInt16LittleEndian(tail.AsSpan(i + 20)) == tail.Length) { end = i; break; }
        }
        if (end < 0) throw new CorpusException("CORRUPT_ARCHIVE");
        var eocd = tail.AsSpan(end);
        var count = BinaryPrimitives.ReadUInt16LittleEndian(eocd[10..]);
        var size = BinaryPrimitives.ReadUInt32LittleEndian(eocd[12..]);
        var start = BinaryPrimitives.ReadUInt32LittleEndian(eocd[16..]);
        if (count is < 1 or > 500 || size > 512 * 1024) throw new CorpusException("BLOCKED_RESOURCE");
        if (BinaryPrimitives.ReadUInt16LittleEndian(eocd[4..]) != 0 || BinaryPrimitives.ReadUInt16LittleEndian(eocd[6..]) != 0
            || BinaryPrimitives.ReadUInt16LittleEndian(eocd[8..]) != count
            || start == uint.MaxValue || (long)start + size != input.Length - tail.Length + end
            || end >= 20 && BinaryPrimitives.ReadUInt32LittleEndian(tail.AsSpan(end - 20)) == 0x07064b50)
            throw new CorpusException("UNSAFE_ARCHIVE");
        input.Position = start; var header = new byte[46]; var extra = new byte[4096]; var actual = 0;
        while (input.Position < (long)start + size)
        {
            token.ThrowIfCancellationRequested();
            if (++actual > 500) throw new CorpusException("BLOCKED_RESOURCE");
            if (input.Position + 46 > (long)start + size) throw new CorpusException("CORRUPT_ARCHIVE");
            input.ReadExactly(header);
            if (BinaryPrimitives.ReadUInt32LittleEndian(header) != 0x02014b50) throw new CorpusException("UNSAFE_ARCHIVE");
            var nameLength = BinaryPrimitives.ReadUInt16LittleEndian(header.AsSpan(28));
            var extraLength = BinaryPrimitives.ReadUInt16LittleEndian(header.AsSpan(30));
            var commentLength = BinaryPrimitives.ReadUInt16LittleEndian(header.AsSpan(32));
            if (nameLength is < 1 or > 256 || extraLength > 4096 || commentLength > 4096) throw new CorpusException("BLOCKED_RESOURCE");
            if (input.Position + nameLength + extraLength + commentLength > (long)start + size
                || BinaryPrimitives.ReadUInt16LittleEndian(header.AsSpan(34)) != 0
                || BinaryPrimitives.ReadUInt32LittleEndian(header.AsSpan(42)) >= start
                || BinaryPrimitives.ReadUInt32LittleEndian(header.AsSpan(20)) == uint.MaxValue
                || BinaryPrimitives.ReadUInt32LittleEndian(header.AsSpan(24)) == uint.MaxValue) throw new CorpusException("UNSAFE_ARCHIVE");
            input.Position += nameLength; input.ReadExactly(extra.AsSpan(0, extraLength));
            for (var offset = 0; offset < extraLength;)
            {
                if (offset + 4 > extraLength) throw new CorpusException("CORRUPT_ARCHIVE");
                var tag = BinaryPrimitives.ReadUInt16LittleEndian(extra.AsSpan(offset));
                var length = BinaryPrimitives.ReadUInt16LittleEndian(extra.AsSpan(offset + 2));
                if (tag == 1) throw new CorpusException("UNSAFE_ARCHIVE"); // ZIP64 not supported.
                offset += 4 + length;
                if (offset > extraLength) throw new CorpusException("CORRUPT_ARCHIVE");
            }
            input.Position += commentLength;
        }
        if (actual != count) throw new CorpusException("CORRUPT_ARCHIVE");
        input.Position = 0;
    }

    internal static CorpusMetadata Read(FileStream input, string id, Action<CorpusRecord> insert, CancellationToken token, IProgress<CorpusProgress>? progress)
    {
        if (input.Length is < 22 or > MaxBytes) throw new CorpusException("BLOCKED_RESOURCE");
        token.ThrowIfCancellationRequested();
        PreflightDirectory(input, token);
        var archiveHash = CorpusStore.HashStream(input, token); input.Position = 0;
        using var zip = new ZipArchive(input, ZipArchiveMode.Read, true);
        if (zip.Entries.Count is < 1 or > 500) throw new CorpusException("BLOCKED_RESOURCE");
        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase); long declared = 0;
        foreach (var entry in zip.Entries)
        {
            token.ThrowIfCancellationRequested(); var name = entry.FullName;
            // Flat log entries only; filenames never form a destination path or persisted metadata.
            if (string.IsNullOrWhiteSpace(name) || name.Length > 256 || name.Any(char.IsControl) || name.Contains('/') || name.Contains('\\')
                || name.Contains(':') || name.Contains("..", StringComparison.Ordinal) || !names.Add(name)
                || !(name.EndsWith(".log", StringComparison.OrdinalIgnoreCase) || name.StartsWith("chat.log.", StringComparison.OrdinalIgnoreCase)
                    && name[9..].All(c => char.IsAsciiDigit(c) || c == '-'))
                || entry.IsEncrypted || ((entry.ExternalAttributes >> 16) & 0xF000) is 0xA000 or 0x6000 or 0x4000 || (entry.ExternalAttributes & 0x410) != 0)
                throw new CorpusException("UNSAFE_ARCHIVE");
            declared = checked(declared + entry.Length);
            if (entry.Length > 64L * 1024 * 1024 || declared > MaxBytes || entry.Length > Math.Max(1, entry.CompressedLength) * 100) throw new CorpusException("BLOCKED_RESOURCE");
        }
        long bytes = 0, lines = 0, publicCount = 0, privateCount = 0, malformed = 0, noise = 0;
        var stamps = new List<CorpusEntryStamp>(); var channels = new Dictionary<string, long>();
        var languages = new Dictionary<string, long>(); var lengths = new Dictionary<string, long>(); int ordinal = 0;
        static void Count(Dictionary<string, long> map, string key) => map[key] = map.GetValueOrDefault(key) + 1;
        foreach (var entry in zip.Entries.OrderBy(e => e.FullName, StringComparer.Ordinal))
        {
            ordinal++; long entryBytes = 0, lineNumber = 0; var first = true; uint crc = uint.MaxValue;
            using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
            using var source = entry.Open(); var buffer = new byte[8192]; var line = new byte[MaxLineBytes]; int lineSize = 0, read;
            void ParseLine()
            {
                token.ThrowIfCancellationRequested(); lineNumber++; lines++;
                if (lines > MaxLines) throw new CorpusException("BLOCKED_RESOURCE");
                var size = lineSize > 0 && line[lineSize - 1] == 13 ? lineSize - 1 : lineSize;
                var text = Utf8.GetString(line, 0, size); lineSize = 0;
                if (first) { text = text.TrimStart('\uFEFF'); first = false; }
                var parsed = Header.Match(text); if (!parsed.Success) { malformed++; return; }
                var channel = parsed.Groups["channel"].Value.ToUpperInvariant();
                // Private payload never reaches the insert callback, journal, row or preview.
                if (channel is "TELL" or "FRIENDTELL" or "FRIEND_TELL" or "WHISPER" or "PM" or "PRIVATE") { privateCount++; return; }
                if (!PublicChannels.Contains(channel, StringComparer.Ordinal)
                    || !DateTime.TryParseExact(parsed.Groups["date"].Value, "dd.MM.yy HH:mm:ss", CultureInfo.InvariantCulture, DateTimeStyles.None, out var date)) { malformed++; return; }
                var original = parsed.Groups["text"].Value;
                if (original.Length is < 1 or > 4000 || original.Any(char.IsControl)) { malformed++; return; }
                var normalized = TextRules.Normalize(original); var triage = CorpusLanguageTriage.Classify(original);
                var fingerprint = TextRules.Hash(channel + "\n" + normalized);
                var timestamp = DateTime.SpecifyKind(date, DateTimeKind.Unspecified).ToString("yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture);
                var rowId = TextRules.Hash(ordinal.ToString(CultureInfo.InvariantCulture) + "|" + lineNumber + "|" + timestamp + "|" + channel + "|" + TextRules.Hash(original));
                var isNoise = normalized.Length < 2 || original.Length > 500;
                insert(new(rowId, ordinal, lineNumber, timestamp, channel, original, normalized, fingerprint, triage.Language, triage.Reason,
                    CorpusLanguageTriage.Scrub(original) != original, isNoise, false));
                publicCount++; if (isNoise) noise++;
                Count(channels, channel); Count(languages, triage.Language); Count(lengths, original.Length switch { <= 80 => "1-80", <= 240 => "81-240", <= 500 => "241-500", _ => "501-4000" });
            }
            while ((read = source.Read(buffer)) > 0)
            {
                token.ThrowIfCancellationRequested(); entryBytes += read; bytes += read;
                if (bytes > MaxBytes || entryBytes > 64L * 1024 * 1024 || entryBytes > entry.Length || entryBytes > Math.Max(1, entry.CompressedLength) * 100) throw new CorpusException("BLOCKED_RESOURCE");
                hash.AppendData(buffer, 0, read);
                for (var i = 0; i < read; i++)
                {
                    crc = CrcTable[(crc ^ buffer[i]) & 255] ^ (crc >> 8);
                    if (buffer[i] == 10) ParseLine();
                    else { if (lineSize == MaxLineBytes) throw new CorpusException("BLOCKED_LINE"); line[lineSize++] = buffer[i]; }
                }
                if (lines % 1000 < 150) progress?.Report(new(bytes, lines));
            }
            if (lineSize > 0) ParseLine();
            if (entryBytes != entry.Length || (crc ^ uint.MaxValue) != entry.Crc32) throw new CorpusException("CORRUPT_ARCHIVE");
            stamps.Add(new(ordinal, entryBytes, Convert.ToHexString(hash.GetHashAndReset()).ToLowerInvariant()));
        }
        input.Position = 0; if (archiveHash != CorpusStore.HashStream(input, token)) throw new CorpusException("SOURCE_DRIFT");
        progress?.Report(new(bytes, lines)); token.ThrowIfCancellationRequested();
        return new() { Id = id, ArchiveHash = archiveHash, DatasetHash = TextRules.Hash(string.Join("\n", stamps.Select(e => e.Ordinal + ":" + e.Bytes + ":" + e.Sha256))),
            Lines = lines, Public = publicCount, PrivateSkipped = privateCount, MalformedSkipped = malformed, Noise = noise,
            Entries = stamps, Channels = channels, Languages = languages, Lengths = lengths };
    }
}
