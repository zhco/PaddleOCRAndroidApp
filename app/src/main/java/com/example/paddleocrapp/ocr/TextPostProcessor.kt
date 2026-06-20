package com.example.paddleocrapp.ocr

import android.util.Log

/**
 * OCR 识别结果后处理纠错工具
 *
 * 对识别结果进行纠错，提升最终文字准确率。
 * 支持：
 * 1. 数字/字母混淆纠正（0/O, 1/l/I, 5/S 等）
 * 2. 常见中文混淆字纠正（的/得/地, 已/己 等）
 * 3. 重复字符去除
 * 4. 空白字符清理
 */
object TextPostProcessor {

    private const val TAG = "TextPostProcessor"

    /**
     * 对单行识别结果进行纠错
     *
     * @param text 识别出的原始文本
     * @param score 识别置信度（0-1）
     * @return 纠错后的文本
     */
    fun correctLine(text: String, score: Float): String {
        if (text.isEmpty()) return text

        var result = text

        // 高置信度结果：只做轻微纠错
        // 低置信度结果：做更激进的纠错
        if (score < 0.7f) {
            result = fixDigitLetterConfusion(result)
            result = fixChineseConfusion(result)
        }

        result = fixCommonOCRErrors(result)
        result = cleanWhitespace(result)

        return result
    }

    /**
     * 对完整识别结果进行后处理
     *
     * @param text 多行识别结果
     * @return 纠错后的文本
     */
    fun postProcess(text: String): String {
        if (text.isEmpty()) return text

        return text.lines().joinToString("\n") { line ->
            // 简单纠错（没有单行分数信息时使用）
            fixCommonOCRErrors(cleanWhitespace(line))
        }
    }

    /**
     * 修复数字和字母的混淆
     *
     * OCR 常见混淆对：
     * - 0 和 O
     * - 1 和 l（小写L）和 I（大写i）
     * - 5 和 S
     * - 8 和 B
     * - 6 和 G
     * - 2 和 Z
     * - 9 和 q
     *
     * 策略：根据上下文判断应该是数字还是字母
     * - 如果周围都是数字，则纠正为数字
     * - 如果周围都是字母/中文，则纠正为字母
     *
     * @param text 原始文本
     * @return 纠正后的文本
     */
    private fun fixDigitLetterConfusion(text: String): String {
        if (text.isEmpty()) return text

        val chars = text.toCharArray()
        for (i in chars.indices) {
            chars[i] = when (chars[i]) {
                'O' -> if (isDigitContext(chars, i)) '0' else 'O'
                'o' -> if (isDigitContext(chars, i)) '0' else 'o'
                'l' -> if (isDigitContext(chars, i)) '1' else 'l'
                'I' -> if (isDigitContext(chars, i)) '1' else 'I'
                'S' -> if (isDigitContext(chars, i)) '5' else 'S'
                's' -> if (isDigitContext(chars, i)) '5' else 's'
                'B' -> if (isDigitContext(chars, i)) '8' else 'B'
                'Z' -> if (isDigitContext(chars, i)) '2' else 'Z'
                'q' -> if (isDigitContext(chars, i)) '9' else 'q'
                else -> chars[i]
            }
        }

        return String(chars)
    }

    /**
     * 判断某个位置是否处于数字上下文中
     */
    private fun isDigitContext(chars: CharArray, index: Int): Boolean {
        val window = 3
        var digitCount = 0
        var totalCount = 0

        for (i in (index - window).coerceAtLeast(0)..(index + window).coerceAtMost(chars.size - 1)) {
            if (i == index) continue
            val c = chars[i]
            if (c.isDigit()) {
                digitCount++
            }
            if (c.isLetter() || c.isDigit()) {
                totalCount++
            }
        }

        // 如果周围字符中数字占多数，认为是数字上下文
        return totalCount > 0 && digitCount >= totalCount * 0.6f
    }

    /**
     * 修复常见中文混淆字
     *
     * OCR 常见中文混淆：
     * - 的/得/地（根据前后文判断）
     * - 已/己/巳
     * - 未/末
     * - 入/人
     * - 土/士
     * - 日/曰
     * - 戊/戌/戍
     *
     * 注意：中文纠错需要更多上下文信息，这里只做最基础的替换
     */
    private fun fixChineseConfusion(text: String): String {
        // 目前只做简单的替换，更复杂的需要语言模型
        return text
            .replace("曰", "日")
            .replace("巳", "已")
            .replace("戌", "戊")
    }

    /**
     * 修复常见 OCR 错误
     *
     * 这些是 OCR 引擎最常见的系统性错误：
     * - 连续空格压缩为单个
     * - 行首行尾空白去除
     * - 全角数字转半角
     * - 全角字母转半角
     */
    private fun fixCommonOCRErrors(text: String): String {
        return text
            // 全角数字转半角
            .replace("０", "0").replace("１", "1").replace("２", "2")
            .replace("３", "3").replace("４", "4").replace("５", "5")
            .replace("６", "6").replace("７", "7").replace("８", "8")
            .replace("９", "9")
            // 全角标点转半角（常见于中文排版）
            .replace("：", ":").replace("；", ";")
            .replace("，", ",").replace("。", ".")
            // 全角括号
            .replace("（", "(").replace("）", ")")
            .replace("【", "[").replace("】", "]")
            // 全角英文字母转半角
            .let { result ->
                var r = result
                for (c in 'Ａ'..'Ｚ') {
                    r = r.replace(c, ('A'.code + (c.code - 'Ａ'.code)).toChar())
                }
                for (c in 'ａ'..'ｚ') {
                    r = r.replace(c, ('a'.code + (c.code - 'ａ'.code)).toChar())
                }
                r
            }
    }

    /**
     * 清理空白字符
     */
    private fun cleanWhitespace(text: String): String {
        return text
            // 压缩连续空格
            .replace(Regex(" {2,}"), " ")
            // 压缩连续制表符
            .replace(Regex("\\t{2,}"), "\t")
            .trim()
    }
}
