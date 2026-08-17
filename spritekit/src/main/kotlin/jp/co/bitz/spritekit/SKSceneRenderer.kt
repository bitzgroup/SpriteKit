package jp.co.bitz.spritekit

import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val VERTEX_SHADER_SOURCE = """
    uniform mat4 u_MVP;
    attribute vec2 a_Position;
    attribute vec2 a_TexCoord;
    attribute vec4 a_Color;
    varying vec2 v_TexCoord;
    varying vec4 v_Color;
    void main() {
        gl_Position = u_MVP * vec4(a_Position, 0.0, 1.0);
        v_TexCoord = a_TexCoord;
        v_Color = a_Color;
    }
"""

private const val FRAGMENT_SHADER_SOURCE = """
    precision mediump float;
    uniform sampler2D u_Texture;
    varying vec2 v_TexCoord;
    varying vec4 v_Color;
    void main() {
        gl_FragColor = texture2D(u_Texture, v_TexCoord) * v_Color;
    }
"""

private const val FLOATS_PER_VERTEX = 8 // position(2) + texCoord(2) + color(4)
private const val BYTES_PER_FLOAT = 4

/**
 * The OpenGL ES 2.0 scene renderer: compiles the default shader program, uploads/re-uploads
 * [SKTexture]s on demand (see [SKResourceRegistry.generation]), and draws the [SKRenderCommand]
 * list [buildRenderCommands] produces — [SKSpriteNode]s, [SKLabelNode]s, and [SKShapeNode]
 * fills/strokes alike, since they all reduce to the same "flat triangle list, texture, blend
 * mode, vertex color" shape — batched into as few `glDrawArrays` calls as practical by runs of
 * consecutive commands sharing a texture and [SKBlendMode].
 *
 * Not part of the public API — an [SKView] implementation detail. Touches real
 * `GLES20`/`GLUtils`/`Matrix` calls throughout, so — unlike this file's siblings — it isn't
 * covered by unit tests; see `docs/ROADMAP.md`'s testing notes.
 */
internal class SKSceneRenderer {
    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var colorAttrib = 0
    private var mvpUniform = 0
    private var textureUniform = 0
    private var whiteTextureId = 0
    private val mvpMatrix = FloatArray(16)
    private var vertexBuffer = allocateVertexBuffer(64)

    /** (Re)compiles the shader program and the fallback white texture. Call from `onSurfaceCreated`. */
    fun onSurfaceCreated() {
        program = compileProgram(VERTEX_SHADER_SOURCE, FRAGMENT_SHADER_SOURCE)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        colorAttrib = GLES20.glGetAttribLocation(program, "a_Color")
        mvpUniform = GLES20.glGetUniformLocation(program, "u_MVP")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
        whiteTextureId = createWhiteTexture()
    }

    /**
     * Clears the frame to [SKScene.backgroundColor] (unless [clearFirst] is `false`, for a layer
     * drawn on top of one already on screen — see `SKTransitionLayers.kt`), then draws [scene]'s
     * content into a `glViewport` of [viewportSize] (defaulting to the full [viewWidth]x
     * [viewHeight]) at [viewportOffset], each vertex's alpha additionally scaled by [globalAlpha],
     * and — if set — every command further clipped to [outerClip] (in the same space as
     * [SKRenderCommand.clipRect], intersected with each command's own).
     */
    @Suppress("LongParameterList")
    fun draw(
        scene: SKScene,
        viewWidth: Int,
        viewHeight: Int,
        resourceRegistry: SKResourceRegistry,
        viewportOffset: Vector2 = Vector2.Zero,
        viewportSize: Vector2? = null,
        globalAlpha: Float = 1f,
        clearFirst: Boolean = true,
        outerClip: Rect? = null,
    ) {
        val projection = computeSceneProjection(scene.size, scene.anchorPoint, scene.scaleMode, viewWidth, viewHeight)
        Matrix.orthoM(mvpMatrix, 0, projection.left, projection.right, projection.bottom, projection.top, -1f, 1f)

        val size = viewportSize ?: Vector2(viewWidth.toFloat(), viewHeight.toFloat())
        GLES20.glViewport(viewportOffset.x.toInt(), viewportOffset.y.toInt(), size.x.toInt(), size.y.toInt())

        if (clearFirst) {
            GLES20.glClearColor(
                redOf(scene.backgroundColor),
                greenOf(scene.backgroundColor),
                blueOf(scene.backgroundColor),
                1f,
            )
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        val commands = buildRenderCommands(scene)
        if (commands.isEmpty()) return

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpUniform, 1, false, mvpMatrix, 0)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glEnableVertexAttribArray(colorAttrib)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(textureUniform, 0)

        var index = 0
        while (index < commands.size) {
            val run = takeRun(commands, index)
            drawRun(run, resourceRegistry, projection, viewWidth, viewHeight, globalAlpha, outerClip)
            index += run.size
        }

        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
        GLES20.glDisableVertexAttribArray(colorAttrib)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
    }

    @Suppress("LongParameterList")
    private fun drawRun(
        run: List<SKRenderCommand>,
        resourceRegistry: SKResourceRegistry,
        projection: SKSceneProjection,
        viewWidth: Int,
        viewHeight: Int,
        globalAlpha: Float,
        outerClip: Rect?,
    ) {
        applyScissor(combinedClip(run.first().clipRect, outerClip), projection, viewWidth, viewHeight)
        applyBlendMode(run.first().blendMode)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId(run.first().texture, resourceRegistry))

        val vertexCount = run.sumOf { it.vertices.size }
        val buffer = ensureCapacity(vertexCount)
        buffer.clear()
        for (command in run) appendCommand(buffer, command, globalAlpha)
        buffer.flip()

        val stride = FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(2)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(4)
        GLES20.glVertexAttribPointer(colorAttrib, 4, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
    }

    private fun textureId(
        texture: SKTexture?,
        resourceRegistry: SKResourceRegistry,
    ): Int {
        if (texture == null) return whiteTextureId
        val state = texture.gpuState
        val alreadyUploaded = state.glTextureId != 0 && state.uploadedGeneration == resourceRegistry.generation
        if (!alreadyUploaded) uploadTexture(texture, state, resourceRegistry)
        return state.glTextureId
    }

    private fun uploadTexture(
        texture: SKTexture,
        state: SKTextureGpuState,
        resourceRegistry: SKResourceRegistry,
    ) {
        if (state.glTextureId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            state.glTextureId = ids[0]
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, state.glTextureId)
        val filter =
            if (texture.filteringMode == SKTextureFilteringMode.Nearest) GLES20.GL_NEAREST else GLES20.GL_LINEAR
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texture.bitmap, 0)
        state.uploadedGeneration = resourceRegistry.generation
    }

    private fun createWhiteTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        val pixel =
            ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).apply {
                put(byteArrayOf(-1, -1, -1, -1)) // opaque white (0xFF per channel, as a signed byte)
                position(0)
            }
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            1,
            1,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            pixel,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        return ids[0]
    }

    private fun ensureCapacity(vertexCount: Int): FloatBuffer {
        if (vertexBuffer.capacity() < vertexCount * FLOATS_PER_VERTEX) {
            vertexBuffer = allocateVertexBuffer(vertexCount)
        }
        return vertexBuffer
    }
}

/** Groups the run of consecutive commands, starting at [startIndex], sharing a texture, blend mode, and clip rect. */
private fun takeRun(
    commands: List<SKRenderCommand>,
    startIndex: Int,
): List<SKRenderCommand> {
    val first = commands[startIndex]

    fun batchesWithFirst(command: SKRenderCommand) =
        command.texture === first.texture && command.blendMode == first.blendMode && command.clipRect == first.clipRect
    var end = startIndex + 1
    while (end < commands.size && batchesWithFirst(commands[end])) {
        end++
    }
    return commands.subList(startIndex, end)
}

/**
 * Enables/configures (or disables) `GL_SCISSOR_TEST` for [clipRect] — in the same space as
 * render command vertices — mapped through [projection] into `glScissor`'s viewport-pixel,
 * bottom-left-origin coordinates. `null` disables scissoring. For [SKCropNode].
 */
private fun applyScissor(
    clipRect: Rect?,
    projection: SKSceneProjection,
    viewWidth: Int,
    viewHeight: Int,
) {
    if (clipRect == null) {
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        return
    }
    val scaleX = viewWidth / (projection.right - projection.left)
    val scaleY = viewHeight / (projection.top - projection.bottom)
    val x = (clipRect.left - projection.left) * scaleX
    // clipRect.top is the smaller-y (this library's Rect is normalized left<=right/top<=bottom),
    // which in this y-up world is the visually *lower* edge -- matching glScissor's own
    // bottom-left-origin y parameter directly, with no flip needed.
    val y = (clipRect.top - projection.bottom) * scaleY
    val width = (clipRect.right - clipRect.left) * scaleX
    val height = (clipRect.bottom - clipRect.top) * scaleY
    GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
    GLES20.glScissor(x.toInt(), y.toInt(), width.toInt().coerceAtLeast(0), height.toInt().coerceAtLeast(0))
}

/**
 * [ownClip] (an [SKRenderCommand]'s own, possibly-`null`, [SKCropNode]-derived clip) intersected
 * with [outerClip] (a whole-`draw`-call clip — see `SKTransitionLayers.kt`'s [SKTransitionSplit]).
 * Not `ownClip?.intersection(outerClip) ?: outerClip`: that would (wrongly) fall back to
 * [outerClip] even when [ownClip] and [outerClip] genuinely don't overlap, instead of drawing
 * nothing — the same trap [SKRenderCommandList.kt]'s nested-crop-node clipping already avoids.
 * A non-`null`-but-empty intersection is represented as a zero-area rect (which `glScissor`
 * naturally renders as "nothing"), rather than `null` ("unclipped").
 */
private fun combinedClip(
    ownClip: Rect?,
    outerClip: Rect?,
): Rect? =
    when {
        outerClip == null -> ownClip
        ownClip == null -> outerClip
        else -> ownClip.intersection(outerClip) ?: Rect(0f, 0f, 0f, 0f)
    }

private fun appendCommand(
    buffer: FloatBuffer,
    command: SKRenderCommand,
    globalAlpha: Float,
) {
    for (vertex in command.vertices) {
        buffer.put(vertex.position.x).put(vertex.position.y)
        buffer.put(vertex.u).put(vertex.v)
        buffer.put(command.color.r).put(command.color.g).put(command.color.b).put(command.color.a * globalAlpha)
    }
}

private fun applyBlendMode(blendMode: SKBlendMode) {
    GLES20.glBlendEquation(
        if (blendMode == SKBlendMode.Subtract) GLES20.GL_FUNC_REVERSE_SUBTRACT else GLES20.GL_FUNC_ADD,
    )
    val (src, dst) =
        when (blendMode) {
            SKBlendMode.Alpha -> GLES20.GL_SRC_ALPHA to GLES20.GL_ONE_MINUS_SRC_ALPHA
            SKBlendMode.Add, SKBlendMode.Subtract -> GLES20.GL_SRC_ALPHA to GLES20.GL_ONE
            SKBlendMode.Multiply -> GLES20.GL_DST_COLOR to GLES20.GL_ZERO
            SKBlendMode.Screen -> GLES20.GL_ONE to GLES20.GL_ONE_MINUS_SRC_COLOR
            SKBlendMode.Replace -> GLES20.GL_ONE to GLES20.GL_ZERO
        }
    GLES20.glBlendFunc(src, dst)
}

private fun compileProgram(
    vertexSource: String,
    fragmentSource: String,
): Int {
    val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    check(status[0] != 0) { "Shader program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
    return program
}

private fun compileShader(
    type: Int,
    source: String,
): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    check(status[0] != 0) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
    return shader
}

private fun allocateVertexBuffer(vertexCapacity: Int): FloatBuffer =
    ByteBuffer
        .allocateDirect(vertexCapacity * FLOATS_PER_VERTEX * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
