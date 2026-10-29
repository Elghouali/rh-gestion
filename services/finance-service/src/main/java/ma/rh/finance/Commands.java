package ma.rh.finance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Commands {
 private final JdbcClient jdbc;
 private final ObjectMapper json;
 public Commands(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 @Transactional
 public <T>T execute(String actor,String key,String operation,Object input,Class<T> resultType,Supplier<T> action){
  if(key==null||!key.matches("[A-Za-z0-9_-]{8,100}"))throw FinanceProblem.invalid("IDEMPOTENCY_KEY_REQUIRED");
  String request=encode(input);
  String hash;
  try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception failure){throw new IllegalStateException("Cannot hash command",failure);}
  jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scope,0))").param("scope",actor+"|"+operation+"|"+key).query((rs,n)->true).single();
  var previous=jdbc.sql("SELECT request_hash,response::text FROM command_receipts WHERE actor=:actor AND operation=:op AND command_key=:key").param("actor",actor).param("op",operation).param("key",key).query((rs,n)->Map.of("hash",rs.getString(1),"response",rs.getString(2))).optional();
  if(previous.isPresent()){
   if(!previous.get().get("hash").equals(hash))throw FinanceProblem.conflict("IDEMPOTENCY_CONFLICT");
   try{return json.readValue(previous.get().get("response"),resultType);}catch(Exception failure){throw new IllegalStateException("Cannot read saved command",failure);}
  }
  T result=action.get();
  jdbc.sql("INSERT INTO command_receipts(actor,operation,command_key,request_hash,response) VALUES(:a,:o,:k,:h,CAST(:r AS jsonb))").param("a",actor).param("o",operation).param("k",key).param("h",hash).param("r",encode(result)).update();
  jdbc.sql("INSERT INTO outbox_events(event_id,event_type,actor,payload) VALUES(:id,:type,:actor,CAST(:payload AS jsonb))").param("id",UUID.randomUUID()).param("type",operation).param("actor",actor).param("payload",encode(result)).update();
  return result;
 }
 String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception failure){throw new IllegalStateException("Cannot encode command",failure);}}
}
