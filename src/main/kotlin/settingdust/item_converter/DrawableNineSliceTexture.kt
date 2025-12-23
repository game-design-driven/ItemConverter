package settingdust.item_converter

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f

/**
 * Breaks a texture into 9 pieces so that it can be scaled to any size.
 * Draws the corners and then repeats any middle textures to fill the remaining area.
 */
class DrawableNineSliceTexture(
    private val location: ResourceLocation,

    private val textureWidth: Int,
    private val textureHeight: Int,
    u0: Int,
    v0: Int,
    u1: Int,
    v1: Int,
    private val sliceLeft: Int,
    private val sliceRight: Int,
    private val sliceTop: Int,
    private val sliceBottom: Int
) {
    private val u0 = u0 / textureWidth.toFloat()
    private val v0 = v0 / textureHeight.toFloat()
    private val u1 = u1 / textureWidth.toFloat()
    private val v1 = v1 / textureHeight.toFloat()

    fun draw(guiGraphics: GuiGraphics, xOffset: Int, yOffset: Int, width: Int, height: Int) {
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.setShaderTexture(0, location)

        val uLeft = sliceLeft.toFloat() / textureWidth
        val uRight = u1 - sliceRight.toFloat() / textureWidth
        val vTop = sliceTop.toFloat() / textureHeight
        val vBottom = v1 - sliceBottom.toFloat() / textureHeight

        val tessellator = Tesselator.getInstance()
        val bufferBuilder = tessellator.builder
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        val matrix = guiGraphics.pose().last().pose()

        // left top
        draw(bufferBuilder, matrix, u0, v0, uLeft, vTop, xOffset, yOffset, sliceLeft, sliceTop)
        // left bottom
        draw(
            bufferBuilder,
            matrix,
            u0,
            vBottom,
            uLeft,
            v1,
            xOffset,
            yOffset + height - sliceBottom,
            sliceLeft,
            sliceBottom
        )
        // right top
        draw(
            bufferBuilder,
            matrix,
            uRight,
            v0,
            u1,
            vTop,
            xOffset + width - sliceRight,
            yOffset,
            sliceRight,
            sliceTop
        )
        // right bottom
        draw(
            bufferBuilder,
            matrix,
            uRight,
            vBottom,
            u1,
            v1,
            xOffset + width - sliceRight,
            yOffset + height - sliceBottom,
            sliceRight,
            sliceBottom
        )

        val middleWidth = ((u1 - u0) * textureWidth - sliceLeft - sliceRight).toInt()
        val middleHeight = ((v1 - v0) * textureHeight - sliceTop - sliceBottom).toInt()
        val tiledMiddleWidth = width - sliceLeft - sliceRight
        val tiledMiddleHeight = height - sliceTop - sliceBottom
        if (tiledMiddleWidth > 0) {
            // top edge
            drawTiled(
                bufferBuilder,
                matrix,
                uLeft,
                v0,
                uRight,
                vTop,
                xOffset + sliceLeft,
                yOffset,
                tiledMiddleWidth,
                sliceTop,
                middleWidth,
                sliceTop
            )
            // bottom edge
            drawTiled(
                bufferBuilder,
                matrix,
                uLeft,
                vBottom,
                uRight,
                v1,
                xOffset + sliceLeft,
                yOffset + height - sliceBottom,
                tiledMiddleWidth,
                sliceBottom,
                middleWidth,
                sliceBottom
            )
        }
        if (tiledMiddleHeight > 0) {
            // left side
            drawTiled(
                bufferBuilder,
                matrix,
                u0,
                vTop,
                uLeft,
                vBottom,
                xOffset,
                yOffset + sliceTop,
                sliceLeft,
                tiledMiddleHeight,
                sliceLeft,
                middleHeight
            )
            // right side
            drawTiled(
                bufferBuilder,
                matrix,
                uRight,
                vTop,
                u1,
                vBottom,
                xOffset + width - sliceRight,
                yOffset + sliceTop,
                sliceRight,
                tiledMiddleHeight,
                sliceRight,
                middleHeight
            )
        }
        if (tiledMiddleHeight > 0 && tiledMiddleWidth > 0) {
            // middle area
            drawTiled(
                bufferBuilder,
                matrix,
                uLeft,
                vTop,
                uRight,
                vBottom,
                xOffset + sliceLeft,
                yOffset + sliceTop,
                tiledMiddleWidth,
                tiledMiddleHeight,
                middleWidth,
                middleHeight
            )
        }

        tessellator.end()
    }

    companion object {
        private fun drawTiled(
            bufferBuilder: BufferBuilder,
            matrix: Matrix4f,
            u0: Float,
            v0: Float,
            u1: Float,
            v1: Float,
            xOffset: Int,
            yOffset: Int,
            tiledWidth: Int,
            tiledHeight: Int,
            width: Int,
            height: Int
        ) {
            val xTileCount = tiledWidth / width
            val xRemainder = tiledWidth - (xTileCount * width)
            val yTileCount = tiledHeight / height
            val yRemainder = tiledHeight - (yTileCount * height)

            val yStart = yOffset + tiledHeight

            val uSize = u1 - u0
            val vSize = v1 - v0

            for (xTile in 0..xTileCount) {
                for (yTile in 0..yTileCount) {
                    val tileWidth = if ((xTile == xTileCount)) xRemainder else width
                    val tileHeight = if ((yTile == yTileCount)) yRemainder else height
                    val x = xOffset + (xTile * width)
                    val y = yStart - ((yTile + 1) * height)
                    if (tileWidth > 0 && tileHeight > 0) {
                        val maskRight = width - tileWidth
                        val maskTop = height - tileHeight
                        val uOffset = (maskRight / width.toFloat()) * uSize
                        val vOffset = (maskTop / height.toFloat()) * vSize

                        draw(
                            bufferBuilder,
                            matrix,
                            u0,
                            v0 + vOffset,
                            u1 - uOffset,
                            v1,
                            x,
                            y + maskTop,
                            tileWidth,
                            tileHeight
                        )
                    }
                }
            }
        }

        private fun draw(
            bufferBuilder: BufferBuilder,
            matrix: Matrix4f,
            minU: Float,
            minV: Float,
            maxU: Float,
            maxV: Float,
            xOffset: Int,
            yOffset: Int,
            width: Int,
            height: Int
        ) {
            bufferBuilder.vertex(matrix, xOffset.toFloat(), (yOffset + height).toFloat(), 0f)
                .uv(minU, maxV)
                .endVertex()
            bufferBuilder.vertex(matrix, (xOffset + width).toFloat(), (yOffset + height).toFloat(), 0f)
                .uv(maxU, maxV)
                .endVertex()
            bufferBuilder.vertex(matrix, (xOffset + width).toFloat(), yOffset.toFloat(), 0f)
                .uv(maxU, minV)
                .endVertex()
            bufferBuilder.vertex(matrix, xOffset.toFloat(), yOffset.toFloat(), 0f)
                .uv(minU, minV)
                .endVertex()
        }
    }
}