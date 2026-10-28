package ma.rh.admin;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.List;
@Repository
public class BranchRepository {
 private final JdbcClient jdbc;
 public BranchRepository(JdbcClient jdbc){this.jdbc=jdbc;}
 public List<Branch> findAll(){return jdbc.sql("SELECT id,code,name,type FROM branches WHERE active=true ORDER BY code").query(Branch.class).list();}
 public List<Branch> findAssigned(String subject){
  return jdbc.sql("SELECT b.id,b.code,b.name,b.type FROM branches b JOIN branch_assignments a ON a.branch_id=b.id WHERE a.subject=:subject AND b.active=true ORDER BY b.code")
   .param("subject",subject).query(Branch.class).list();
 }
}