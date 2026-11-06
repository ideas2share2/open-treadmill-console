from pathlib import Path
import json,re,sqlite3

r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/org/opentread/console/WorkoutStore.java').read_text()
def table(name):
    a=s.index('"CREATE TABLE '+name+'(');b=s.index(');',a)
    return ''.join(json.loads(v) for v in re.findall(r'"(?:[^"\\]|\\.)*"',s[a:b]))
new=table('samples');old=new[:new.index(',grip_hr')]+')'
db=sqlite3.connect(':memory:');db.execute(old)
rows=[(1,20,1000,900,12.3,160,50,120),(2,21,2000,1000,15.1,180,0,0)]
db.executemany('INSERT INTO samples VALUES(?,?,?,?,?,?,?,?)',rows)
before=db.execute('SELECT * FROM samples ORDER BY id').fetchall()
db.executescript((r/'app/src/main/assets/db/migrate-2-to-3.sql').read_text())
after=db.execute('SELECT * FROM samples ORDER BY id').fetchall()
assert [x[:8] for x in after]==before
assert after[0][8:]==(0,0,'legacy') and after[1][8:]==(0,0,'none')
fresh=sqlite3.connect(':memory:');fresh.execute(new)
assert list(db.execute('PRAGMA table_info(samples)'))==list(fresh.execute('PRAGMA table_info(samples)'))
db.execute("INSERT INTO samples(workout_id,at_ms,moving_ms,meters,speed_raw,grade_raw,hr,grip_hr,bluetooth_hr,hr_source) VALUES(22,3000,1200,30,160,0,108,94,108,'bluetooth')")
assert db.execute('SELECT hr,grip_hr,bluetooth_hr,hr_source FROM samples WHERE workout_id=22').fetchone()==(108,94,108,'bluetooth')
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('PASS: actual schema2-to3 samples migration preserves every original timestamp/value; legacy source remains explicit; both raw sources and selected reading persist; migrated/fresh schemas match.')
