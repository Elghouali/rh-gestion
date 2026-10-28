package ma.rh.admin;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
class BranchAccessTest {
 private final UUID depot=UUID.fromString("10000000-0000-0000-0000-000000000001");
 private final UUID shop=UUID.fromString("10000000-0000-0000-0000-000000000002");
 @Test void ownerCanReadEveryBranch(){assertThat(BranchAccess.canRead(true,Set.of(),shop)).isTrue();}
 @Test void assignedEmployeeCanReadAssignedBranch(){assertThat(BranchAccess.canRead(false,Set.of(depot),depot)).isTrue();}
 @Test void employeeCannotReadAnotherBranch(){assertThat(BranchAccess.canRead(false,Set.of(depot),shop)).isFalse();}
 @Test void employeeWithoutAssignmentCannotReadAnything(){assertThat(BranchAccess.canRead(false,Set.of(),shop)).isFalse();}
}