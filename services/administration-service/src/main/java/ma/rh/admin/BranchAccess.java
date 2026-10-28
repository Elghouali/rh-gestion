package ma.rh.admin;
import java.util.Set;
import java.util.UUID;
public final class BranchAccess {
 private BranchAccess(){}
 public static boolean canRead(boolean owner,Set<UUID> assigned,UUID branch){return owner||assigned.contains(branch);}
}