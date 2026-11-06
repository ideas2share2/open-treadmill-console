"""Host SQLite crash tests. No device power cycle, ADB or motor commands."""
from pathlib import Path
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/org/opentread/console/WorkoutStore.java').read_text()


def sql_table(name):
    start = source.index('"CREATE TABLE ' + name + '(')
    end = source.index(');', start)
    return ''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', source[start:end]))


recover = json.loads(re.search(r'RECOVER_SQL\s*=\s*("(?:[^"\\]|\\.)*")', source).group(1))

if len(sys.argv) > 1:
    db = sqlite3.connect(sys.argv[1])
    db.execute('PRAGMA journal_mode=WAL')
    db.execute('PRAGMA synchronous=NORMAL')
    db.execute('BEGIN IMMEDIATE')
    db.execute("UPDATE workouts SET moving_ms=700000,meters=1000,updated_ms=900000,walk_ms=700000,walk_meters=1000,weight_kg=80,active_kcal=42.5,calorie_model='acsm-active-v1' WHERE id=1")
    db.execute('INSERT INTO samples(workout_id,at_ms,moving_ms,meters,speed_raw,grade_raw,hr) VALUES(1,900000,700000,1000,500,0,120)')
    if sys.argv[2] == 'committed':
        db.commit()
    os._exit(0)  # Intentionally skip connection close/checkpoint and all cleanup.

with tempfile.TemporaryDirectory(prefix='console-crash-') as temp:
    path = Path(temp) / 'workouts.db'
    db = sqlite3.connect(path)
    db.execute('PRAGMA journal_mode=WAL')
    db.execute('PRAGMA synchronous=NORMAL')
    db.execute(sql_table('workouts'))
    db.execute(sql_table('samples'))
    db.execute("INSERT INTO workouts(id,profile,kind,start_ms,end_ms,updated_ms,moving_ms,meters,walk_ms,walk_meters) VALUES(1,'slot-1','Walk',100000,0,800000,600000,900,600000,900)")
    db.execute("INSERT INTO workouts(id,profile,kind,start_ms,end_ms,updated_ms,moving_ms,meters) VALUES(2,'slot-2','Run',10000,20000,20000,10000,100)")
    db.commit()
    db.execute('PRAGMA wal_checkpoint(TRUNCATE)').fetchall()
    finished = db.execute('SELECT * FROM workouts WHERE id=2').fetchone()
    db.close()

    subprocess.run([sys.executable, __file__, str(path), 'uncommitted'], check=True)
    db = sqlite3.connect(path)
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert db.execute('SELECT moving_ms,meters FROM workouts WHERE id=1').fetchone() == (600000, 900)
    assert db.execute('SELECT weight_kg,active_kcal,calorie_model FROM workouts WHERE id=1').fetchone() == (0,0,'')
    assert db.execute('SELECT COUNT(*) FROM samples').fetchone()[0] == 0
    db.close()

    subprocess.run([sys.executable, __file__, str(path), 'committed'], check=True)
    db = sqlite3.connect(path)
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert db.execute('SELECT moving_ms,meters FROM workouts WHERE id=1').fetchone() == (700000, 1000)
    assert db.execute('SELECT weight_kg,active_kcal,calorie_model FROM workouts WHERE id=1').fetchone() == (80,42.5,'acsm-active-v1')
    assert db.execute('SELECT COUNT(*) FROM samples').fetchone()[0] == 1
    assert db.execute('PRAGMA wal_checkpoint(PASSIVE)').fetchone()[0] == 0

    # A fully checkpointed main file contains the saved state without a WAL tail.
    checkpoint_copy = Path(temp) / 'checkpointed.db'
    shutil.copyfile(path, checkpoint_copy)
    checkpointed = sqlite3.connect(checkpoint_copy)
    assert checkpointed.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    assert checkpointed.execute('SELECT moving_ms,meters FROM workouts WHERE id=1').fetchone() == (700000, 1000)
    checkpointed.close()

    db.execute(recover)
    db.commit()
    recovered = db.execute('SELECT * FROM workouts WHERE id=1').fetchone()
    assert db.execute('SELECT end_ms,updated_ms,moving_ms,meters,reason FROM workouts WHERE id=1').fetchone() == (900000, 900000, 700000, 1000, 'Interrupted')
    assert db.execute('SELECT weight_kg,active_kcal,calorie_model FROM workouts WHERE id=1').fetchone() == (80,42.5,'acsm-active-v1')
    assert db.execute('SELECT * FROM workouts WHERE id=2').fetchone() == finished
    db.execute(recover)
    db.commit()
    assert db.execute('SELECT * FROM workouts WHERE id=1').fetchone() == recovered
    assert db.execute('SELECT COUNT(*) FROM samples').fetchone()[0] == 1
    assert db.execute('SELECT COUNT(*) FROM workouts WHERE end_ms=0').fetchone()[0] == 0
    db.close()

print('PASS: abrupt process exit before/after SQLite commit; atomic summary/sample rollback; committed WAL recovery; checkpointed main-file integrity; actual startup recovery SQL is idempotent, preserves finished workouts and adds no outage time.')
