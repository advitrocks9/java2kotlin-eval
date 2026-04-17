// Hypothesis: Java array literals with `new String[]{...}` should map to
// `arrayOf(...)`. Multi-dimensional arrays (`new int[3][4]`) are the stress -
// they should use `Array(3) { IntArray(4) }`. I expect single-dim correct,
// multi-dim either wrong or with a TODO comment.
package edgecases.arrays;

public class Sample {
    public static String[] words() {
        return new String[]{"alpha", "beta", "gamma"};
    }

    public static int[][] grid() {
        // expected: Array(3) { IntArray(4) } in idiomatic Kotlin
        int[][] g = new int[3][4];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                g[i][j] = i * 4 + j;
            }
        }
        return g;
    }
}
