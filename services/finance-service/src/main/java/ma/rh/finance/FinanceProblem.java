package ma.rh.finance;
import org.springframework.http.HttpStatus;
public class FinanceProblem extends RuntimeException {
 final HttpStatus status;
 final String code;
 public FinanceProblem(HttpStatus status,String code){super(code);this.status=status;this.code=code;}
 public static FinanceProblem invalid(String code){return new FinanceProblem(HttpStatus.BAD_REQUEST,code);}
 public static FinanceProblem conflict(String code){return new FinanceProblem(HttpStatus.CONFLICT,code);}
 public static FinanceProblem forbidden(){return new FinanceProblem(HttpStatus.FORBIDDEN,"ACCESS_DENIED");}
}
