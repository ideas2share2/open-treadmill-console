package org.opentread.console;
import android.content.Context;import java.util.*;
/** Host-only recorder sink. No SQLite, ADB, network or device I/O. */
public final class WorkoutStore {
 public static WorkoutRecord recovery;
 public int saves,points; public WorkoutRecord latest;
 public WorkoutStore(Context c){}
 public void recover(String p,java.util.function.Consumer<WorkoutRecord> callback){callback.accept(recovery);}
 public void save(WorkoutRecord r,List<WorkoutRecord.Point> batch){saves++;points+=batch.size();latest=r.copy();}
}
