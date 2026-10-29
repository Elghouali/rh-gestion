package ma.rh.finance;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(FinanceProblem.class)
 ResponseEntity<ProblemDetail> business(FinanceProblem failure){return response(failure.status,failure.code);}
 @ExceptionHandler(DataIntegrityViolationException.class)
 ResponseEntity<ProblemDetail> integrity(){return response(HttpStatus.CONFLICT,"DATA_CONFLICT");}
 @ExceptionHandler({MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
 ResponseEntity<ProblemDetail> validation(){return response(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED");}
 private ResponseEntity<ProblemDetail> response(HttpStatus status,String code){
  var problem=ProblemDetail.forStatusAndDetail(status,code);problem.setProperty("code",code);
  return ResponseEntity.status(status).body(problem);
 }
}
