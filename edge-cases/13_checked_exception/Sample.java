// Hypothesis: Java's checked exceptions disappear in Kotlin. Method
// signatures lose `throws IOException`. Calls to such methods from Kotlin
// don't need try/catch. J2K should drop the `throws` clause silently.
// I'm watching for it to keep `@Throws(IOException::class)` everywhere
// "just in case" - over-annotation is the failure mode.
package edgecases.checked;

import java.io.IOException;

public class Sample {
    public void writeOut(String s) throws IOException {
        if (s == null) throw new IOException("null");
        System.out.println(s);
    }

    public void wrap() {
        try {
            writeOut("ok");
        } catch (IOException e) {
            // expected: still a try/catch in Kotlin (IOException isn't fatal),
            // but the @Throws annotation upstream shouldn't be required.
            throw new RuntimeException(e);
        }
    }
}
