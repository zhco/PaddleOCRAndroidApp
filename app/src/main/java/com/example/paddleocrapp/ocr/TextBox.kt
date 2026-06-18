package com.example.paddleocrapp.ocr

/**
 * 文本框数据类
 *
 * 表示检测到的文本区域，使用四个顶点坐标描述一个任意四边形文本框。
 * 坐标顺序为：左上 -> 右上 -> 右下 -> 左下（顺时针方向）。
 *
 * @property points 包含 8 个浮点值的数组，表示四个顶点的 x,y 坐标：
 *                  [x1, y1, x2, y2, x3, y3, x4, y4]
 *                  分别对应：左上、右上、右下、左下
 * @property score 文本框的检测置信度得分，范围 [0, 1]
 */
data class TextBox(
    val points: FloatArray, // 8个值: x1,y1,x2,y2,x3,y3,x4,y4 四个顶点
    val score: Float
) {
    /**
     * 获取文本框中心点的 x 坐标
     */
    fun centerX(): Float = (points[0] + points[2] + points[4] + points[6]) / 4f

    /**
     * 获取文本框中心点的 y 坐标
     */
    fun centerY(): Float = (points[1] + points[3] + points[5] + points[7]) / 4f

    /**
     * 获取文本框的宽度（取上边和下边的平均值）
     */
    fun width(): Float {
        val topWidth = points[2] - points[0]
        val bottomWidth = points[4] - points[6]
        return (topWidth + bottomWidth) / 2f
    }

    /**
     * 获取文本框的高度（取左边和右边的平均值）
     */
    fun height(): Float {
        val leftHeight = points[7] - points[1]
        val rightHeight = points[5] - points[3]
        return (leftHeight + rightHeight) / 2f
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TextBox
        if (!points.contentEquals(other.points)) return false
        if (score != other.score) return false
        return true
    }

    override fun hashCode(): Int {
        var result = points.contentHashCode()
        result = 31 * result + score.hashCode()
        return result
    }
}
