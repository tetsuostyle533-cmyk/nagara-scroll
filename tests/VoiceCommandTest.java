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
        System.out.println("PASS: 10 voice-command cases");
    }
    private static void check(String input, int expected) {
        if (VoiceCommand.parse(input) != expected) throw new AssertionError(input);
    }
}
