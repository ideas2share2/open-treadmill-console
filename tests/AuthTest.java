import org.opentread.console.*;
import java.util.*;

public final class AuthTest {
  static void check(boolean b, String s) {
    if (!b) throw new AssertionError(s);
  }

  static byte[] reply(int command, int status, byte[] body) {
    byte[] p = new byte[body.length + 5];
    p[0] = 4;
    p[1] = (byte) p.length;
    p[2] = (byte) command;
    p[3] = (byte) status;
    System.arraycopy(body, 0, p, 4, body.length);
    int sum = 0;
    for (int i = 0; i < p.length - 1; i++) sum += p[i] & 255;
    p[p.length - 1] = (byte) sum;
    return p;
  }

  static byte[] system() {
    byte[] p = new byte[11];
    int[] vals = {19221, 425467};
    for (int j = 0; j < 2; j++)
      for (int i = 0; i < 4; i++) p[3 + j * 4 + i] = (byte) (vals[j] >>> (8 * i));
    return p;
  }

  static final class Fake implements ControllerAuth.Wire {
    final ArrayList<Integer> commands = new ArrayList<>();
    boolean missing = false, refuse = false;

    public byte[] exchange(byte[] req) {
      int cmd = req[2] & 255;
      commands.add(cmd);
      int sum = 0;
      for (int i = 0; i < req.length - 1; i++) sum += req[i] & 255;
      check(
          req.length == (req[1] & 255) && (sum & 255) == (req[req.length - 1] & 255),
          "metadata frame checksum/length");
      check(
          cmd == 129 || cmd == 136 || cmd == 130 || cmd == 132 || cmd == 144,
          "no motion/calibration/firmware opcode");
      if (cmd == 129) {
        check(req[0] == 2 && req.length == 4, "DeviceInfo request");
        return reply(cmd, 2, new byte[] {83, 1, 1, 0, 0, 0});
      }
      if (cmd == 136) {
        check(req[0] == 4, "SupportedCommands address");
        return reply(
            cmd,
            2,
            missing
                ? new byte[] {(byte) 130, (byte) 132}
                : new byte[] {
                  9, 113, (byte) 130, (byte) 131, (byte) 132, (byte) 137, (byte) 144, (byte) 149
                });
      }
      if (cmd == 130) {
        check(
            req.length == 7 && req[3] == 0 && req[4] == 0 && req[5] == 0,
            "SystemInfo fixed payload");
        return reply(cmd, 2, system());
      }
      if (cmd == 132) return reply(cmd, 2, new byte[] {83, 113, 2});
      check(req.length == 40, "verification length");
      check(
          (req[35] & 255) == 152 && (req[36] & 255) == 2 && req[37] == 0 && req[38] == 0,
          "master library *8 little endian");
      return reply(cmd, refuse ? 8 : 2, new byte[] {8});
    }
  }

  static void bad(byte[] raw, int cmd, int size) {
    try {
      ControllerAuth.payload(raw, cmd, size);
      throw new AssertionError("invalid session reply accepted");
    } catch (IllegalArgumentException good) {
    }
  }

  public static void run() throws Exception {
    check(
        Protocol.hex(ControllerAuth.hash(1, 425467, 19221), 32)
            .equals("072e4664789abae80c265e8ca0d2025064bef634488aca387cb6ee5c90c232a0"),
        "independent known hash vector");
    Fake wire = new Fake();
    ControllerAuth.Identity id = ControllerAuth.verify(wire);
    check(wire.commands.equals(Arrays.asList(129, 136, 130, 132, 144)), "normal startup sequence");
    check(
        id.software == 83
            && id.serial == 1
            && id.part == 425467
            && id.model == 19221
            && id.library == 83,
        "strict identity decode");
    Fake absent = new Fake();
    absent.missing = true;
    try {
      ControllerAuth.verify(absent);
      throw new AssertionError("undeclared verification accepted");
    } catch (IllegalArgumentException good) {
    }
    check(absent.commands.equals(Arrays.asList(129, 136)), "undeclared command never sent");
    Fake refused = new Fake();
    refused.refuse = true;
    try {
      ControllerAuth.verify(refused);
      throw new AssertionError("refused verification accepted");
    } catch (IllegalArgumentException good) {
    }
    check(refused.commands.size() == 5, "no automatic retries/replayed start in verifier");
    byte[] good = reply(129, 2, new byte[] {83, 1, 1, 0, 0, 0});
    check(ControllerAuth.payload(Arrays.copyOf(good, 64), 129, 6).length == 6, "padded USB reply");
    bad(good, 130, 6);
    byte[] parent = reply(130, 2, system());
    parent[0] = 2;
    parent[parent.length - 1] -= 2;
    check(
        ControllerAuth.payload(parent, 130, 11).length == 11,
        "MAIN metadata echo accepted after treadmill identity");
    parent[0] = 3;
    parent[parent.length - 1] += 1;
    bad(parent, 130, 11);
    bad(Arrays.copyOf(good, good.length - 1), 129, 6);
    bad(good, 129, 7);
    byte[] broken = good.clone();
    broken[broken.length - 1] ^= 1;
    bad(broken, 129, 6);
    broken = good.clone();
    broken[0] = 2;
    broken[broken.length - 1] -= 2;
    bad(broken, 129, 6);
    bad(reply(129, 8, new byte[0]), 129, 6);
    bad(reply(144, 2, new byte[0]), 144, 1);
    check(
        Arrays.equals(ControllerAuth.hash(7, 370357, 39915), ControllerAuth.hash(7, 374677, 39915)),
        "known reported-part compatibility");
    byte[] blocked = reply(2, 8, new byte[0]);
    try {
      Protocol.payload(blocked, 2, blocked.length);
      throw new AssertionError("status8 not typed");
    } catch (ControllerAuth.Required required) {
    }
    blocked[4] ^= 1;
    try {
      Protocol.payload(blocked, 2, blocked.length);
      throw new AssertionError("bad checksum accepted");
    } catch (ControllerAuth.Required wrong) {
      throw new AssertionError("malformed frame must not trigger verification");
    } catch (IllegalArgumentException goodError) {
    }
    System.out.println(
        "PASS: normal metadata/verification sequence only; identity and supported commands;"
            + " checksums/lengths/status/identity rejection; no motion or replay in verifier;"
            + " authenticated status8 distinction.");
  }
}
