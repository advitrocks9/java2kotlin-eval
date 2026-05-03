// Hypothesis: JLS 9.8 says a single-abstract-method type can inherit its
// abstract method from a super-interface. JavaScan.kt currently only counts
// methods DECLARED on the interface itself, so the recall-side SAM count
// will report this case as 0 SAMs (false negative). Shipped as a fixture to
// pin the gap; see TODO in JavaScan.scan().
package edgecases.inheritedsam;

public class Sample {
    public interface Base {
        void run();
    }

    // Empty body: still a SAM. Inherits run() from Base. JavaScan won't
    // recognise it; J2K may or may not lift it to fun interface (it does
    // not on this case in the current corpus).
    public interface Inherited extends Base {
    }
}
