package ma.rh.inventory;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import ma.rh.inventory.Models.Site;
public interface SiteAccess {
 List<Site> allowed(Jwt jwt);
 List<Site> directory(Jwt jwt);
}
