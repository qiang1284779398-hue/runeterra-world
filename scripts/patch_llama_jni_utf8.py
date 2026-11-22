from pathlib import Path

p = Path("llama-android/library/src/main/jni/llama_jni.cpp")
s = p.read_text(encoding="utf-8")
if "#include <utility>" not in s:
    s = s.replace("#include <vector>", "#include <vector>" + chr(10) + "#include <utility>", 1)

# Convert UTF-8 output into Java UTF-16; JNI NewStringUTF only accepts
# Modified UTF-8 and is unsafe for arbitrary model output.
needle = 'std::string json_escape(const std::string &in) {'
helper = r"""
jstring new_string_from_utf8(JNIEnv *env, const std::string &input) {
    std::vector<jchar> out;
    out.reserve(input.size());
    size_t i = 0;
    while (i < input.size()) {
        const unsigned char c = static_cast<unsigned char>(input[i]);
        uint32_t cp = 0;
        size_t len = 0;
        if (c <= 0x7F) { cp = c; len = 1; }
        else if (c >= 0xC2 && c <= 0xDF) { cp = c & 0x1F; len = 2; }
        else if (c >= 0xE0 && c <= 0xEF) { cp = c & 0x0F; len = 3; }
        else if (c >= 0xF0 && c <= 0xF4) { cp = c & 0x07; len = 4; }
        else { out.push_back(0xFFFD); ++i; continue; }
        if (i + len > input.size()) { out.push_back(0xFFFD); break; }
        bool valid = true;
        for (size_t k = 1; k < len; ++k) {
            const unsigned char d = static_cast<unsigned char>(input[i + k]);
            if ((d & 0xC0) != 0x80) { valid = false; break; }
            cp = (cp << 6) | (d & 0x3F);
        }
        if (!valid ||
            (len == 3 && ((c == 0xE0 && static_cast<unsigned char>(input[i+1]) < 0xA0) ||
                          (c == 0xED && static_cast<unsigned char>(input[i+1]) >= 0xA0))) ||
            (len == 4 && ((c == 0xF0 && static_cast<unsigned char>(input[i+1]) < 0x90) ||
                          (c == 0xF4 && static_cast<unsigned char>(input[i+1]) >= 0x90)))) {
            out.push_back(0xFFFD); ++i; continue;
        }
        if (cp <= 0xFFFF) out.push_back(static_cast<jchar>(cp));
        else {
            cp -= 0x10000;
            out.push_back(static_cast<jchar>(0xD800 + (cp >> 10)));
            out.push_back(static_cast<jchar>(0xDC00 + (cp & 0x3FF)));
        }
        i += len;
    }
    return env->NewString(out.data(), static_cast<jsize>(out.size()));
}

"""
if "new_string_from_utf8" not in s:
    assert needle in s
    s = s.replace(needle, helper + needle, 1)
old = 'return env->NewStringUTF(json.c_str());'
if old in s:
    s = s.replace(old, 'return new_string_from_utf8(env, json);', 1)

# A chat template already emits its own BOS marker (Qwen3 uses <|im_start|>).
# Return whether a template was applied so the tokenizer does not prepend BOS again.
s=s.replace(
'''std::string build_prompt(const llama_model *model, const std::string &system,
                         const std::string &user) {''',
'''std::pair<std::string, bool> build_prompt(const llama_model *model, const std::string &system,
                                         const std::string &user) {'''
)
s=s.replace(
'''        return system.empty() ? user : (system + "\\n\\n" + user);''',
'''        return {system.empty() ? user : (system + "\\n\\n" + user), false};'''
)
s=s.replace(
'''    if (n < 0) return {system.empty() ? user : (system + "\\n\\n" + user), false};
    return {std::string(buf.data(), n), true};''',
'''    if (n < 0) return {system.empty() ? user : (system + "\\n\\n" + user), false};
    return {std::string(buf.data(), n), true};'''
)


# The upstream source has two fallback branches. Patch both (not just the first)
# and make the successful template return carry the "template applied" flag.
import re
s, fallback_count = re.subn(
    r'if (n < 0) return system\\.empty\\(\\) \\? user : \\(system \\+ "\\\\n\\\\n" \\+ user\\);',
    r'if (n < 0) return {system.empty() ? user : (system + "\\\\n\\\\n" + user), false};',
    s
)
if fallback_count == 0 and 'if (n < 0) return {system.empty()' not in s:
    raise RuntimeError("Could not patch chat-template fallback branch")
s=s.replace(
    '    return std::string(buf.data(), n);',
    '    return {std::string(buf.data(), n), true};'
)

# Token pieces can exceed a fixed stack buffer. Retry with the exact required size.
s=s.replace(
'''    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n < 0) return {};
    return std::string(buf, n);''',
'''    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
    if (n >= 0) return std::string(buf, n);
    std::vector<char> large(static_cast<size_t>(-n));
    n = llama_token_to_piece(vocab, token, large.data(), static_cast<int32_t>(large.size()), 0, true);
    if (n < 0) return {};
    return std::string(large.data(), static_cast<size_t>(n));'''
)

s=s.replace(
'''    const std::string text = build_prompt(h->model, jstr(env, system), jstr(env, prompt));''',
'''    const auto formatted = build_prompt(h->model, jstr(env, system), jstr(env, prompt));
    const std::string &text = formatted.first;'''
)
s=s.replace(
'''    std::vector<llama_token> tokens = tokenize(vocab, text, true);''',
'''    // Template-formatted prompts already contain their BOS token. Only add BOS
    // for plain fallback prompts, otherwise Qwen3 receives a duplicated BOS.
    std::vector<llama_token> tokens = tokenize(vocab, text, !formatted.second);''',
1
)

assert "std::pair<std::string, bool> build_prompt" in s, "build_prompt patch failed"
assert "tokenize(vocab, text, !formatted.second)" in s, "BOS patch failed"
assert "return new_string_from_utf8(env, json);" in s, "UTF-8 patch failed"
p.write_text(s, encoding="utf-8")
