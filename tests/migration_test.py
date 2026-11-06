from pathlib import Path
import sqlite3,re,json
root=Path(__file__).resolve().parents[1]
# Read the actual SQL passed to SQLiteDatabase, not a hand-copied new schema.
source=(root/'app/src/main/java/org/opentread/console/WorkoutStore.java').read_text()
start=source.index('"CREATE TABLE workouts(');end=source.index(');',start)
new_schema=''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"',source[start:end]))
old_schema=new_schema[:new_schema.index(',walk_ms')]+')'
db=sqlite3.connect(':memory:');db.execute(old_schema)
rows=[(1,'slot-1','Walk',1000,2000,2000,100000,100.5,3,4,0,0,'Finished'),(2,'slot-2','Run',3000,4000,4000,200000,200.75,5,9,180,2,'Finished')]
db.executemany('INSERT INTO workouts VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',rows)
before=db.execute('SELECT * FROM workouts ORDER BY id').fetchall()
db.executescript((root/'app/src/main/assets/db/migrate-1-to-2.sql').read_text())
after=db.execute('SELECT * FROM workouts ORDER BY id').fetchall()
assert [r[:13] for r in after]==before
assert after[0][13:]==(100000,0,100.5,0)
assert after[1][13:]==(0,200000,0,200.75)
new=sqlite3.connect(':memory:');new.execute(new_schema[:new_schema.index(',weight_kg')]+')')
assert [r[1] for r in db.execute('PRAGMA table_info(workouts)')]==[r[1] for r in new.execute('PRAGMA table_info(workouts)')]
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('PASS: exact v1-to-v2 migration preserves all existing rows and columns; initializes walk/run totals from existing labels; new and migrated schemas match.')
