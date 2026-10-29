package ma.rh.inventory;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
public interface SupplierAccess {
 record Supplier(UUID id,String name,String phone,boolean active){}
 Supplier get(Jwt jwt,UUID id);
}
