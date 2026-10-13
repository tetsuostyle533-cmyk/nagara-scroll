import com.kobatetsu.flowscroll.VoiceCommand;
public class VoiceCommandTest {
    public static void main(String[] args) {
        check("スクロール", VoiceCommand.START);
        check(" スクロール。", VoiceCommand.START);
        check("ストップ！", VoiceCommand.STOP);
        check("止めて", VoiceCommand.STOP);
        check("停止", VoiceCommand.STOP);
        check("スクロールしないで", VoiceCommand.NONE);
        check("ストップと言いました", VoiceCommand.NONE);
        check("スクロール ストップ", VoiceCommand.NONE);
        check(null, VoiceCommand.NONE);
        check("", VoiceCommand.NONE);
        check("すくろーる", VoiceCommand.START);
        check("ｽｸﾛｰﾙ", VoiceCommand.START);
        check("ＳＣＲＯＬＬ", VoiceCommand.START);
        check("スクロールして", VoiceCommand.START);
        check("スクロールしてください", VoiceCommand.START);
        check("すとっぷ", VoiceCommand.STOP);
        check("とめて", VoiceCommand.STOP);
        check("止まって", VoiceCommand.STOP);
        check("スクロール停止", VoiceCommand.STOP);
        check("スクロールを止めて", VoiceCommand.STOP);
        check("スクロールしない", VoiceCommand.NONE);
        check("スクロールしないでください", VoiceCommand.NONE);
        check("ストップしないで", VoiceCommand.NONE);
        check("スクロールについて教えて", VoiceCommand.NONE);
        check("ストップモーション", VoiceCommand.NONE);
        check("スクロールストップ", VoiceCommand.NONE);
        check("スクロールを止めてない", VoiceCommand.NONE);
        check("ストップって言ったら止まる", VoiceCommand.NONE);
        check("scrolling", VoiceCommand.NONE);
        check("stop scrolling please", VoiceCommand.NONE);
        System.out.println("PASS: 30 voice-command cases");
    }
    private static void check(String input, int expected) {
        if (VoiceCommand.parse(input) != expected) throw new AssertionError(input);
    }
}
