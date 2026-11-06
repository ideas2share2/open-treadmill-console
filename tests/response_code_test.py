"""All synthetic 0..999999 seeds against the installed JDK, not a console or service."""
from pathlib import Path
import importlib.util
import os
import struct
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('response_code', root / 'tools/response_code.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
java_home = os.environ.get('JAVA_HOME')
java = str(Path(java_home) / 'bin/java') if java_home else 'java'
javac = str(Path(java_home) / 'bin/javac') if java_home else 'javac'
with tempfile.TemporaryDirectory(prefix='response-oracle-') as temp:
    subprocess.run([javac, '-d', temp, str(root / 'tests/response_code/JavaRandomOracle.java')], check=True)
    reference = subprocess.check_output([java, '-cp', temp, 'JavaRandomOracle'])
assert len(reference) == 4000000
differences = 0
for seed, (expected,) in enumerate(struct.iter_unpack('>I', reference)):
    value = str(seed)
    assert module.calculate(value) == expected, seed
    state = (((seed ^ 0x5DEECE66D) * 0x5DEECE66D) + 11) & ((1 << 48) - 1)
    helper = (state >> 17) % 999999
    assert module.calculate(value, 'helper') == helper, seed
    differences += helper != expected
assert differences == 224
assert module.calculate('123456') == 382410
assert module.calculate('005189') == 204721
assert module.calculate('005189', 'helper') == 443980
assert module.calculate('000000') == module.calculate('0')
for invalid in ('', '-1', '+1', '1.0', '1000000', '１２３４５６', '123 456', ' 123456', '123456\n', None):
    try:
        module.calculate(invalid)
        raise AssertionError('invalid input accepted')
    except ValueError:
        pass
try:
    module.calculate('123456', 'unknown')
    raise AssertionError('unknown mode accepted')
except ValueError:
    pass
print('PASS: offline response calculator matches JDK Random.nextInt for every synthetic six-digit-domain seed; helper mode matches one-step JS semantics;224 documented differences; leading zeros and invalid input handled.')
