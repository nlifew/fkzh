import com.toybox.config
import com.toybox.envSetup
import com.toybox.util.println
import kotlin.test.Test


class BlackListTest {

    @Test
    fun main() {
        envSetup()
        config.http.blackList
            .isQuestionTitleBlack("千早爱音和丰川祥子这两个人谁更适合当女朋友/妻子？")
            .println()
    }
}