import org.opentread.console.Protocol;
import java.util.Arrays;

public class ProtocolTest {
  static void check(boolean v) {
    if (!v) throw new AssertionError();
  }

  static void rejected(byte[] p, int size, int count) {
    try {
      Protocol.payload(p, size, count);
      throw new AssertionError("accepted invalid response");
    } catch (IllegalArgumentException ok) {
    }
  }

  static byte[] response(int raw) {
    byte[] p = {4, 7, 2, 2, (byte) raw, (byte) (raw >> 8), 0};
    int sum = 0;
    for (int i = 0; i < 6; i++) sum += p[i] & 255;
    p[6] = (byte) sum;
    return p;
  }

  public static void main(String[] args) {
    for (int id = 0; id < 128; id++) {
      byte[] q = Protocol.readField(id);
      check(q[0] == 2 && q[2] == 2 && q[3] == 0);
      check((q[4] & 255) == id / 8 + 1);
      int bits = 0;
      for (int i = 5; i < q.length - 1; i++) bits += Integer.bitCount(q[i] & 255);
      check(bits == 1);
      check((q[5 + id / 8] & 255) == 1 << (id % 8));
      int sum = 0;
      for (int i = 0; i < q.length - 1; i++) sum += q[i] & 255;
      check((q[q.length - 1] & 255) == (sum & 255));
    }
    byte[] p = response(1931);
    check(Protocol.unsigned(Protocol.payload(p, 2, p.length)) == 1931);
    p = response(-300);
    check((short) Protocol.unsigned(Protocol.payload(p, 2, p.length)) == -300);
    rejected(p, 4, p.length);
    rejected(p, 2, 4);
    rejected(null, 2, 7);
    rejected(new byte[0], 2, 7);
    rejected(p, 2, p.length + 1);
    rejected(new byte[65], 2, 65);
    p[6] ^= 1;
    rejected(p, 2, p.length);
    p = response(0);
    p[3] = 8;
    p[6] += 6;
    rejected(p, 2, p.length);
    p = response(0);
    p[2] = 9;
    p[6] += 7;
    rejected(p, 2, p.length);
    p = response(0);
    p[1] = 65;
    rejected(p, 2, p.length);

    // Synthetic protocol boundary fixtures; no captured device identities or session data.
    int[] sizes = {2, 2, 1, 2, 2, 2, 2, 2, 2, 1, 4, 4};
    long[] values = {0, 0, 8, 0, 0, 1500, 65236, 1931, 80, 1, 0, 0};
    for (int j = 0; j < sizes.length; j++) {
      byte[] r = new byte[sizes[j] + 5];
      r[0] = 4; r[1] = (byte)r.length; r[2] = 2; r[3] = 2;
      for (int k = 0; k < sizes[j]; k++) r[4+k] = (byte)(values[j] >>> (8*k));
      for (int k = 0; k < r.length-1; k++) r[r.length-1] += r[k];
      check(Protocol.unsigned(Protocol.payload(r,sizes[j],r.length)) == values[j]);
    }
    p = response(0);
    p[0] = 2;
    p[6] -= 2;
    rejected(p, 2, p.length);
    System.out.println(
        "PASS: all 12 synthetic boundary replies decode; wrong equipment identity rejected.");
    ManualTest.run();
    System.out.println(
        "PASS: 128 requests contain zero write fields; valid decoding, signed incline, checksum,"
            + " length, command and security-block rejection.");
  }
}
