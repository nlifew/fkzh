import com.toybox.util.outputStream
import io.netty.buffer.ByteBuf
import io.netty.buffer.UnpooledByteBufAllocator
import java.io.InputStream


fun Class<*>.openAssets(path: String): ByteBuf? {
    return getResourceAsStream(path)?.use { it.toByteBuf() }
}

fun InputStream.toByteBuf(): ByteBuf {
    val byteBuf = UnpooledByteBufAllocator.DEFAULT
        .buffer(16 * 1024)
    transferTo(byteBuf.outputStream())
    return byteBuf
}
