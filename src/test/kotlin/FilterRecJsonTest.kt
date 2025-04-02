import com.toybox.envSetup
import com.toybox.handler.FilterRecJsonHandler
import com.toybox.util.println
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertNotNull

class FilterRecJsonTest {

    @Test
    fun main() {
        envSetup()
        val byteBuf = javaClass.openAssets("/zhihu_rec.json")
        assertNotNull(byteBuf)
        FilterRecJsonHandler().handleBody(byteBuf, true)
        byteBuf.toString(StandardCharsets.UTF_8).println()
    }
}