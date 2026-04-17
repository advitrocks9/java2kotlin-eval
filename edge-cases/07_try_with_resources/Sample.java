// Hypothesis: try-with-resources should map to `.use { }`. With multiple
// resources in one try header, J2K has to nest the `use` blocks. I expect
// the single-resource case to convert cleanly and the two-resource case
// to either nest correctly or fall back to a manual try/finally.
package edgecases.twr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Sample {
    public String readOne(String path) throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        }
    }

    public String readTwo(String pathA, String pathB) throws IOException {
        try (BufferedReader a = new BufferedReader(new FileReader(pathA));
             BufferedReader b = new BufferedReader(new FileReader(pathB))) {
            return a.readLine() + b.readLine();
        }
    }
}
