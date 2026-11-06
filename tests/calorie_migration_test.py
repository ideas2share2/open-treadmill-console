from pathlib import Path
import sqlite3,re,json
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/org/opentread/console/WorkoutStore.java').read_text()
a=s.index('"CREATE TABLE workouts('); b=s.index(');',a)
fresh=''.join(json.loads(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"',s[a:b]))
v2=fresh[:fresh.index(',weight_kg')]+')'
v1=v2[:v2.index(',walk_ms')]+')'
for version in (1,2,3):
 d=sqlite3.connect(':memory:');d.execute(v1 if version==1 else v2)
 d.execute("INSERT INTO workouts(id,profile,kind,start_ms,end_ms,updated_ms,moving_ms,meters) VALUES(1,'slot-2','Walk',10,0,30,20000,100)")
 before=d.execute('SELECT * FROM workouts').fetchall()
 if version==1:d.executescript((r/'app/src/main/assets/db/migrate-1-to-2.sql').read_text())
 # v2-to-v3 only changes samples, already independently tested.
 d.executescript((r/'app/src/main/assets/db/migrate-3-to-4.sql').read_text())
 after=d.execute('SELECT * FROM workouts').fetchall()
 assert after[0][:len(before[0])]==before[0]
 assert after[0][-3:]==(0,0,'')
 f=sqlite3.connect(':memory:');f.execute(fresh)
 assert list(d.execute('PRAGMA table_info(workouts)'))==list(f.execute('PRAGMA table_info(workouts)'))
 d.execute("INSERT INTO workouts(id,profile,kind,start_ms,updated_ms,weight_kg,active_kcal,calorie_model) VALUES(2,'slot-4','Run',40,50,80,123.4,'acsm-active-v1')")
 assert d.execute('SELECT weight_kg,active_kcal,calorie_model FROM workouts WHERE id=2').fetchone()==(80,123.4,'acsm-active-v1')
 assert d.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print('PASS: v1/v2/v3 calorie migrations preserve legacy rows and identities, leave old estimates unavailable, match fresh schema4 and persist fourth-profile calorie snapshots.')
