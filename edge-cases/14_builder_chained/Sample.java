// Hypothesis: a Java builder with chained setters returning `this` is the
// pattern Kotlin replaces with `apply { }` or, more idiomatically, with
// a data class + named arguments. J2K can't infer either rewrite from a
// single class - it should produce a 1:1 builder in Kotlin, which is fine
// but not idiomatic. The TEST is: are the chained setters' return types
// preserved? (Bad: `: Builder!` everywhere from platform-type leakage.)
package edgecases.builder;

public class Sample {
    private String host = "localhost";
    private int port = 80;
    private boolean tls = false;

    public Sample host(String host) { this.host = host; return this; }
    public Sample port(int port) { this.port = port; return this; }
    public Sample tls(boolean tls) { this.tls = tls; return this; }

    public String url() {
        return (tls ? "https" : "http") + "://" + host + ":" + port;
    }
}
