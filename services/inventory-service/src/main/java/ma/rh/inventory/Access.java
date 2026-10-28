package ma.rh.inventory;
import java.util.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import ma.rh.inventory.Models.Site;
@Component
public class Access {
 private final SiteAccess sites;
 public Access(SiteAccess sites){this.sites=sites;}
 public boolean owner(Jwt jwt){return role(jwt,"OWNER");}
 public boolean manager(Jwt jwt){return owner(jwt)||role(jwt,"STOCK_MANAGER");}
 private boolean role(Jwt jwt,String role){
  Object realm=jwt.getClaim("realm_access");
  return realm instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> roles && roles.contains(role);
 }
 public List<Site> allowed(Jwt jwt){return sites.allowed(jwt);}
 public void requireOwner(Jwt jwt){if(!owner(jwt))throw InventoryProblem.forbidden();allowed(jwt);}
 public void requireManager(Jwt jwt){if(!manager(jwt))throw InventoryProblem.forbidden();}
 public void requireSite(UUID id,List<Site> allowed){if(allowed.stream().noneMatch(s->s.id().equals(id)))throw InventoryProblem.forbidden();}
 public List<Site> directory(Jwt jwt){requireManager(jwt);return sites.directory(jwt);}
}
