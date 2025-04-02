import com.toybox.envSetup
import com.toybox.handler.FilterRecHtmlHandler
import com.toybox.util.println
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertNotNull

class FilterRecHtmlTest {

    @Test
    fun main() {
        envSetup()
        val byteBuf = javaClass.openAssets("/zhihu_rec.html")
        assertNotNull(byteBuf)
        FilterRecHtmlHandler().handleBody(byteBuf, true)
        byteBuf.toString(StandardCharsets.UTF_8).println()
    }
}