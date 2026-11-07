package com.ztmanager.ui.editor

import android.text.Spannable
import android.text.style.ForegroundColorSpan
import com.ztmanager.fs.FileType

/** Small regex based highlighter for common languages. */
object SyntaxHighlighter {

    enum class Lang { NONE, C_LIKE, PYTHON, SHELL, XML, JSON, CSS, INI, SQL, LUA, SMALI }

    class HlSpan(color: Int) : ForegroundColorSpan(color)

    data class Palette(
        val keyword: Int,
        val string: Int,
        val comment: Int,
        val number: Int,
        val annotation: Int,
        val tag: Int,
        val attr: Int
    )

    val darkPalette = Palette(0xFFCC7832.toInt(), 0xFF6A8759.toInt(), 0xFF808080.toInt(), 0xFF6897BB.toInt(), 0xFFBBB529.toInt(), 0xFFE8BF6A.toInt(), 0xFF9876AA.toInt())
    val lightPalette = Palette(0xFF0033B3.toInt(), 0xFF067D17.toInt(), 0xFF8C8C8C.toInt(), 0xFF1750EB.toInt(), 0xFF9E880D.toInt(), 0xFF0033B3.toInt(), 0xFF871094.toInt())

    fun langFor(name: String): Lang = when (FileType.extensionOf(name)) {
        "java", "kt", "kts", "gradle", "c", "h", "cpp", "hpp", "cc", "cs", "js", "mjs", "ts", "tsx", "jsx",
        "go", "rs", "swift", "dart", "php", "scala", "groovy", "vue" -> Lang.C_LIKE
        "py" -> Lang.PYTHON
        "sh", "bash", "rc", "mk", "zsh" -> Lang.SHELL
        "xml", "html", "htm", "svg", "xhtml", "plist" -> Lang.XML
        "json" -> Lang.JSON
        "css", "scss", "less" -> Lang.CSS
        "ini", "cfg", "conf", "properties", "prop", "toml", "yml", "yaml", "env" -> Lang.INI
        "sql" -> Lang.SQL
        "lua" -> Lang.LUA
        "smali" -> Lang.SMALI
        else -> Lang.NONE
    }

    private val cKeywords = (
        "abstract|as|async|await|break|case|catch|class|const|continue|data|default|defer|do|else|enum|export|" +
            "extends|false|final|finally|for|fun|func|function|go|if|implements|import|in|interface|internal|is|" +
            "let|match|namespace|new|null|nil|object|open|override|package|private|protected|public|return|" +
            "sealed|static|struct|super|switch|this|throw|throws|true|try|typeof|val|var|void|when|while|yield|" +
            "int|long|short|byte|char|float|double|boolean|bool|string|String|Int|Long|Boolean|Unit|Any|" +
            "include|define|ifdef|ifndef|endif|typedef|unsigned|signed|auto|mut|impl|pub|use|mod|self|lateinit|" +
            "companion|suspend|inline|operator|undefined|delete|instanceof|with|from|type"
        )
    private val pyKeywords = "and|as|assert|async|await|break|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|None|nonlocal|not|or|pass|raise|return|True|try|while|with|yield|self|print"
    private val shKeywords = "if|then|else|elif|fi|for|while|until|do|done|case|esac|in|function|return|exit|export|local|echo|read|set|unset|shift|source|alias|cd|test"
    private val sqlKeywords = "(?i:select|from|where|insert|into|values|update|set|delete|create|table|drop|alter|index|join|left|right|inner|outer|on|group|by|order|having|limit|offset|and|or|not|null|primary|key|foreign|references|as|distinct|union|all|exists|in|like|between|case|when|then|else|end|integer|text|real|blob|varchar|default)"
    private val luaKeywords = "and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while"

    private data class Rule(val regex: Regex, val color: (Palette) -> Int, val group: Int = 0)

    private val number = Rule(Regex("\\b(0[xX][0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)[fFlLdD]?\\b"), { it.number })

    private fun rules(lang: Lang): List<Rule> = when (lang) {
        Lang.C_LIKE -> listOf(
            Rule(Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"), { it.comment }),
            Rule(Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|`(?:\\\\.|[^`\\\\])*`"), { it.string }),
            Rule(Regex("@[A-Za-z_][\\w.]*|#\\s*\\w+"), { it.annotation }),
            Rule(Regex("\\b($cKeywords)\\b"), { it.keyword }),
            number
        )
        Lang.PYTHON -> listOf(
            Rule(Regex("#[^\\n]*"), { it.comment }),
            Rule(Regex("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"), { it.string }),
            Rule(Regex("@[\\w.]+"), { it.annotation }),
            Rule(Regex("\\b($pyKeywords)\\b"), { it.keyword }),
            number
        )
        Lang.SHELL -> listOf(
            Rule(Regex("(?m)(^|\\s)#[^\\n]*"), { it.comment }),
            Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'"), { it.string }),
            Rule(Regex("\\$\\{?[\\w@#?*!-]+\\}?"), { it.attr }),
            Rule(Regex("\\b($shKeywords)\\b"), { it.keyword }),
            number
        )
        Lang.XML -> listOf(
            Rule(Regex("<!--[\\s\\S]*?-->"), { it.comment }),
            Rule(Regex("<!\\[CDATA\\[[\\s\\S]*?]]>"), { it.string }),
            Rule(Regex("\"[^\"]*\"|'[^']*'"), { it.string }),
            Rule(Regex("</?[\\w:.-]+|/?>|<\\?[\\w-]+|\\?>"), { it.tag }),
            Rule(Regex("\\b[\\w:.-]+(?==)"), { it.attr }),
            Rule(Regex("&[\\w#]+;"), { it.number })
        )
        Lang.JSON -> listOf(
            Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)"), { it.attr }),
            Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\""), { it.string }),
            Rule(Regex("\\b(true|false|null)\\b"), { it.keyword }),
            Rule(Regex("-?\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), { it.number })
        )
        Lang.CSS -> listOf(
            Rule(Regex("/\\*[\\s\\S]*?\\*/"), { it.comment }),
            Rule(Regex("\"[^\"]*\"|'[^']*'"), { it.string }),
            Rule(Regex("[\\w-]+(?=\\s*:)"), { it.attr }),
            Rule(Regex("[.#][\\w-]+|@[\\w-]+"), { it.tag }),
            Rule(Regex("#[0-9a-fA-F]{3,8}\\b|\\b\\d+(\\.\\d+)?(px|dp|sp|em|rem|%|vh|vw|s|ms)?\\b"), { it.number })
        )
        Lang.INI -> listOf(
            Rule(Regex("(?m)^\\s*[#;][^\\n]*"), { it.comment }),
            Rule(Regex("(?m)^\\s*\\[[^\\]\\n]*]"), { it.tag }),
            Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\"|'[^']*'"), { it.string }),
            Rule(Regex("(?m)^\\s*[\\w.\\- ]+(?=\\s*[=:])"), { it.attr }),
            Rule(Regex("\\b(true|false|yes|no|on|off|null)\\b"), { it.keyword }),
            number
        )
        Lang.SQL -> listOf(
            Rule(Regex("--[^\\n]*|/\\*[\\s\\S]*?\\*/"), { it.comment }),
            Rule(Regex("'(?:''|[^'])*'"), { it.string }),
            Rule(Regex("\\b$sqlKeywords\\b"), { it.keyword }),
            number
        )
        Lang.LUA -> listOf(
            Rule(Regex("--\\[\\[[\\s\\S]*?]]|--[^\\n]*"), { it.comment }),
            Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\[\\[[\\s\\S]*?]]"), { it.string }),
            Rule(Regex("\\b($luaKeywords)\\b"), { it.keyword }),
            number
        )
        Lang.SMALI -> listOf(
            Rule(Regex("#[^\\n]*"), { it.comment }),
            Rule(Regex("\"(?:\\\\.|[^\"\\\\])*\""), { it.string }),
            Rule(Regex("(?m)^\\s*\\.[\\w-]+"), { it.keyword }),
            Rule(Regex("\\b[vp]\\d+\\b"), { it.attr }),
            Rule(Regex("L[\\w/$]+;"), { it.tag }),
            Rule(Regex("(?m)^\\s*[a-z][\\w/-]*(?=\\s)"), { it.annotation }),
            number
        )
        Lang.NONE -> emptyList()
    }

    data class Span(val start: Int, val end: Int, val color: Int)

    const val MAX_LENGTH = 400_000

    fun compute(text: String, lang: Lang, palette: Palette): List<Span> {
        if (lang == Lang.NONE || text.length > MAX_LENGTH) return emptyList()
        val covered = BooleanArray(text.length)
        val out = ArrayList<Span>()
        for (rule in rules(lang)) {
            val color = rule.color(palette)
            for (m in rule.regex.findAll(text)) {
                val s = m.range.first
                val e = m.range.last + 1
                if (e <= s) continue
                if (covered[s] || covered[e - 1]) continue
                out.add(Span(s, e, color))
                for (i in s until e) covered[i] = true
                if (out.size > 60_000) return out
            }
        }
        return out
    }

    fun apply(target: Spannable, spans: List<Span>) {
        target.getSpans(0, target.length, HlSpan::class.java).forEach { target.removeSpan(it) }
        val len = target.length
        for (s in spans) {
            if (s.end > len) continue
            target.setSpan(HlSpan(s.color), s.start, s.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun clear(target: Spannable) {
        target.getSpans(0, target.length, HlSpan::class.java).forEach { target.removeSpan(it) }
    }
}
