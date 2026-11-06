import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.util.Random;

/** JDK API reference for synthetic seeds; no device code, input data, network or files. */
public final class JavaRandomOracle {
  public static void main(String[] args) throws Exception {
    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(System.out));
    for (int seed = 0; seed < 1000000; seed++) out.writeInt(new Random((long) seed).nextInt(999999));
    out.flush();
  }
}
