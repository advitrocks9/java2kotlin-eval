// Hypothesis: Java overloads where the only difference is "extra parameter
// with sensible default" are the canonical pattern Kotlin replaces with
// default arguments. J2K produces overloads anyway because rewriting requires
// proving the short form's body equals the long form's body with a literal
// substituted. I expect three separate `fun render` declarations rather than
// one `fun render(s: String, prefix: String = "", upper: Boolean = false)`.
package edgecases.overloads;

public class Sample {
    public String render(String s) {
        return render(s, "", false);
    }

    public String render(String s, String prefix) {
        return render(s, prefix, false);
    }

    public String render(String s, String prefix, boolean upper) {
        String r = prefix + s;
        return upper ? r.toUpperCase() : r;
    }
}
