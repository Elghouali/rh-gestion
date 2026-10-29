package ma.rh.finance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.text.Normalizer;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
@Service
public class Suppliers {
 public record Supplier(UUID id,String name,String phone,boolean active,long version){}
 public record Input(@NotBlank @Size(max=160) String name,@NotNull @Size(max=40) String phone){}
 public record Update(@NotBlank @Size(max=160) String name,@NotNull @Size(max=40) String phone,boolean active,@Min(0) long version){}
 public record Page(List<Supplier> items,int page,int size,long total){}
 private final JdbcClient jdbc;private final Commands commands;
 public Suppliers(JdbcClient jdbc,Commands commands){this.jdbc=jdbc;this.commands=commands;}
 private void owner(Jwt jwt){Object realm=jwt.getClaim("realm_access");if(!(realm instanceof Map<?,?> m)||!(m.get("roles") instanceof Collection<?> r)||!r.contains("OWNER"))throw FinanceProblem.forbidden();}
 static String text(String s){return Normalizer.normalize(s.strip(),Normalizer.Form.NFKC).replaceAll("\\s+"," ");}
 Supplier find(UUID id){return jdbc.sql("SELECT * FROM suppliers WHERE id=:id").param("id",id).query(Supplier.class).optional().orElseThrow(()->new FinanceProblem(HttpStatus.NOT_FOUND,"SUPPLIER_NOT_FOUND"));}
 public Supplier get(Jwt jwt,UUID id){owner(jwt);return find(id);}
 public Page list(Jwt jwt,String q,int page,int size){owner(jwt);if(q.length()>100||page<0||page>1000000||size<1||size>100)throw FinanceProblem.invalid("INVALID_PAGE");String query="%"+text(q).replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  var items=jdbc.sql("SELECT * FROM suppliers WHERE name ILIKE :q OR phone ILIKE :q ORDER BY name,id LIMIT :n OFFSET :o").param("q",query).param("n",size).param("o",(long)page*size).query(Supplier.class).list();
  long count=jdbc.sql("SELECT count(*) FROM suppliers WHERE name ILIKE :q OR phone ILIKE :q").param("q",query).query(Long.class).single();return new Page(items,page,size,count);
 }
 public Supplier create(Jwt jwt,String key,Input input){owner(jwt);return commands.execute(jwt.getSubject(),key,"supplier.create",input,Supplier.class,()->{UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO suppliers(id,name,phone) VALUES(:id,:n,:p)").param("id",id).param("n",text(input.name())).param("p",text(input.phone())).update();var s=find(id);audit(jwt,"CREATED",s);return s;});}
 public Supplier update(Jwt jwt,String key,UUID id,Update input){owner(jwt);return commands.execute(jwt.getSubject(),key,"supplier.update:"+id,input,Supplier.class,()->{int n=jdbc.sql("UPDATE suppliers SET name=:n,phone=:p,active=:a,version=version+1 WHERE id=:id AND version=:v").param("n",text(input.name())).param("p",text(input.phone())).param("a",input.active()).param("id",id).param("v",input.version()).update();if(n!=1)throw FinanceProblem.conflict("STALE_VERSION");var s=find(id);audit(jwt,"UPDATED",s);return s;});}
 private void audit(Jwt jwt,String action,Supplier s){jdbc.sql("INSERT INTO supplier_audit(id,supplier_id,action,actor,payload) VALUES(:id,:s,:a,:u,CAST(:p AS jsonb))").param("id",UUID.randomUUID()).param("s",s.id()).param("a",action).param("u",jwt.getSubject()).param("p",commands.encode(s)).update();}
}
@RestController @RequestMapping("/api/v1/suppliers")
class SupplierController {
 private final Suppliers suppliers;SupplierController(Suppliers s){suppliers=s;}
 @GetMapping Suppliers.Page list(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return suppliers.list(jwt,q,page,size);}
 @GetMapping("/{id}") Suppliers.Supplier get(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return suppliers.get(jwt,id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) Suppliers.Supplier create(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Suppliers.Input in){return suppliers.create(jwt,key,in);}
 @PutMapping("/{id}") Suppliers.Supplier update(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@PathVariable UUID id,@Valid @RequestBody Suppliers.Update in){return suppliers.update(jwt,key,id,in);}
}
