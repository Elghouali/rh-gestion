package ma.rh.inventory;
import org.springframework.http.HttpStatus;
public class InventoryProblem extends RuntimeException {
 final HttpStatus status;
 final String code;
 public InventoryProblem(HttpStatus status,String code){super(code);this.status=status;this.code=code;}
 public static InventoryProblem invalid(String code){return new InventoryProblem(HttpStatus.BAD_REQUEST,code);}
 public static InventoryProblem conflict(String code){return new InventoryProblem(HttpStatus.CONFLICT,code);}
 public static InventoryProblem forbidden(){return new InventoryProblem(HttpStatus.FORBIDDEN,"ACCESS_DENIED");}
}
