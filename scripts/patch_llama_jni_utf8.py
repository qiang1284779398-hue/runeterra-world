from pathlib import Path

p = Path("llama-android/library/src/main/jni/llama_jni.cpp")
s = p.read_text(encoding="utf-8")
needle = 'std::string json_escape(const std::string &in) {'
helper = r"""
// Convert arbitrary UTF-8 bytes to UTF-16 explicitly. Model token pieces may
// end on a partial/malformed UTF-8 sequence; NewStringUTF expects Modified UTF-8
// and aborts the Android process on malformed input. NewString(jchar*) avoids
// that ABI trap entirely. Invalid bytes are replaced with U+FFFD.
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
assert old in s
s = s.replace(old, 'return new_string_from_utf8(env, json);', 1)
p.write_text(s, encoding="utf-8")
